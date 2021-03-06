package libbitster;

import java.nio.channels.*;
import java.io.*;
import java.util.*; 

public class Overlord {
  private Selector selector;

  public Overlord () {
    try {
      selector = Selector.open();
    } catch (IOException e) { throw new RuntimeException("select() failed"); }
  }

  /** Selects on sockets and informs their Communicator when there is something
   *  to do. */
  public void communicate (int timeout) {

    try {
      if (selector.select(timeout) == 0) return; // nothing to do
    } catch (IOException e) {
      // Not really sure why/when this happens yet
      return;
    }

    Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

    while (keys.hasNext()) {
      try {
        SelectionKey key = keys.next();
        keys.remove();
        if (!key.isValid()) continue;      // WHY
        Communicator communicator = (Communicator) key.attachment();
        if (key.isConnectable()) if (!communicator.onConnectable()) continue;
        if (key.isReadable())    if (!communicator.onReadable())    continue;
        if (key.isWritable())    if (!communicator.onWritable())    continue;
        if (key.isAcceptable())  if (!communicator.onAcceptable())  continue;
      } catch (CancelledKeyException e) {
        Log.error("Overlord select() error: " + e);
        // just move on
      }
    }
  }

  public boolean register (SelectableChannel sc, Communicator communicator) {
    try {
      sc.register(
        selector, 
        sc.validOps(),
        communicator
      );

      return true;
    } catch (Exception e) { return false; }
  }
}
