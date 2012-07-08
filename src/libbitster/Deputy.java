package libbitster;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Deputy is the {@link Actor} that communicates with the Tracker.
 * It communicates the list of peers to the Manager upon request.
 * @author Martin Miralles-Cordal
 *
 */
public class Deputy extends Actor {
  
  private String state; // states:
  // 'init': just created, waiting to establish a connection
  // 'error': error occured, exception property will be populated
  // 'normal': operating normally (may add more such states later)
  
  private String announceURL;
  private String infoHash;
  private byte[] peerID;
  //private TorrentInfo metainfo;
  private int listenPort;
  private int announceInterval;
  private Manager manager;
  Calendar lastAnnounce;
  
  public Exception exception;         // set to an exception if one occurs

  /**
   * Constructs a Deputy object
   * @param metainfo The data from the metainfo file
   * @param port The port the manager is listening for incoming connections on
   */
  public Deputy(TorrentInfo metainfo, int port, Manager manager)
  {
      //this.metainfo = metainfo;
      this.listenPort = port;
      this.manager = manager;
      
      // assemble our announce URL from metainfo
      announceURL = metainfo.announce_url.getProtocol() + "://" +
        metainfo.announce_url.getHost() + ":" + metainfo.announce_url.getPort()
        + metainfo.announce_url.getPath();
      
      // encode our info hash
      ByteBuffer rawInfoHash = metainfo.info_hash;
      StringBuffer infoHashSB = new StringBuffer();
      while(rawInfoHash.hasRemaining())
      {
        infoHashSB.append("%");
        String hexEncode = Integer.toHexString(0xFF & rawInfoHash.get());
        if(hexEncode.length() == 1)
          infoHashSB.append("0");
        infoHashSB.append(hexEncode);
      }
      infoHash = infoHashSB.toString();
      
      // generate peer ID if we haven't already
      if(peerID == null)
        peerID = generatePeerID();
      
      this.state = "init";
      
      // we're done setting up variables, now connect
      announce();
  }

  @Override
  protected void receive (Memo memo)
  {
    if(memo.getType().equals("list"))
    {
        announce(); // get updated peer list and send it to manager
    }
  }
  
  /**
   * Announce at regular intervals
   */
  @Override
  protected void idle () {
    try { sleep(1000); } catch (Exception e) {}
    
    if(Calendar.getInstance().getTimeInMillis() - this.lastAnnounce.getTimeInMillis()
        > 1000*this.announceInterval)
    {
      announce();
    }
  }
  
  /**
   * Generates a 20 character {@code byte} array for use as a 
   * peer ID
   * @return A randomly generated peer ID
   */
  private byte[] generatePeerID()
  {
    byte[] id = new byte[20];
    // generating random peer ID. BITS + 16 digits = 20 characters
    Random r = new Random(System.currentTimeMillis());
    id[0] = 'B';
    id[1] = 'I';
    id[2] = 'T';
    id[3] = 'S';
    for(int i = 4; i < 20; i++)
    {
      id[i] = (byte) ('A' +  r.nextInt(26));
    }
    
    return id;
  }
  
  /**
   * Sends an HTTP GET request and gets fresh info from the tracker.
   */
  @SuppressWarnings("unchecked")
  private void announce()
  {
    if(announceURL == null)
      return;
    else
    {
      // reset our timer
      this.lastAnnounce = Calendar.getInstance();
      System.out.println("Announcing...");
      
      StringBuffer finalURL = new StringBuffer();
      // add announce URL
      finalURL.append(announceURL);
      
      // add info hash
      finalURL.append("?info_hash=");
      finalURL.append(infoHash);
      
      // add peer ID
      finalURL.append("&peer_id=");
      finalURL.append(new String(peerID));
      
      // add port
      finalURL.append("&port=");
      finalURL.append(this.listenPort);
      
      /* TODO: Change up/down/left gathering into a memo? */
      // add uploaded
      finalURL.append("&uploaded=");
      finalURL.append(manager.getUploaded());
      
      // add downloaded
      finalURL.append("&downloaded=");
      finalURL.append(manager.getDownloaded());
      
      // add amount left
      finalURL.append("&left=");
      finalURL.append(manager.getLeft());
      
      try {
        // send request to tracker
        URL tracker = new URL(finalURL.toString());
        
        // read response
        InputStream is = tracker.openStream();
        byte[] bytes = new byte[is.available()];
        is.read(bytes);
        
        // bdecode response
        @SuppressWarnings("rawtypes")
        Map response = (Map) Bencoder2.decode(bytes);
        
        // get our peer list and work it into something nicer
        @SuppressWarnings("rawtypes")
        ArrayList<Map> rawPeers =
            (ArrayList<Map>) response.get(ByteBuffer.wrap("peers".getBytes()));
        ArrayList<Map<String,String>> peers = parsePeers(rawPeers);
        
        // send updated peer list to manager
        manager.post(new Memo("peers", peers, this));
        
        // get our announce interval
        announceInterval = (int) response.get(ByteBuffer.wrap("interval".getBytes()));
        
        this.state = "normal";
               
      } catch (MalformedURLException e) {
        this.exception = e;
        this.state = "error";
        e.printStackTrace();
      } catch (IOException e) {
        this.exception = e;
        this.state = "error";
        e.printStackTrace();
      } catch (BencodingException e) {
        this.exception = e;
        this.state = "error";
        e.printStackTrace();
      }
    }
  }
  
  /**
   * Takes the raw peer list from the tracker response and processes it into something
   * that's nicer to work with
   * @param rawPeerList The {@code ArrayList<{@code Map}>} of peers sent from announce()
   * @return An ArrayList<Map<String, String>> of peers and their information
   */
  private ArrayList<Map<String, String>> parsePeers(@SuppressWarnings("rawtypes") ArrayList<Map> rawPeerList)
  {
    ArrayList<Map<String, String>> processedPeerList = new ArrayList<Map<String, String>>();
    for(int i = 0; i < rawPeerList.size(); ++i)
    {
      HashMap<String,String> peerInfo = new HashMap<String,String>();
      
      // get this peer's peer ID
      ByteBuffer peer_id_bytes = (ByteBuffer) rawPeerList.get(i).get(ByteBuffer.wrap("peer id".getBytes()));
      String peer_id = new String(peer_id_bytes.array());
      peerInfo.put("peer id", peer_id);
      
      // get this peer's ip
      ByteBuffer ip_bytes = (ByteBuffer) rawPeerList.get(i).get(ByteBuffer.wrap("ip".getBytes()));
      String ip = new String(ip_bytes.array());
      peerInfo.put("ip", ip);
      
      // get this peer's port
      Integer port = (Integer) rawPeerList.get(i).get(ByteBuffer.wrap("port".getBytes()));
      peerInfo.put("port", port.toString());
      
      // add it to our peer list
      processedPeerList.add(peerInfo);
    }
    return processedPeerList;
  }
  
}
