package libbitster;
import java.util.concurrent.ConcurrentLinkedQueue;

// # Actor
// Simple actor class. Can receive messages. Once it is instantiated, it's a
// thread, spinning, calling its `receive()` method (which should be
// overridden) upon receiving messages, and its `idle()` method when there are
// no messages to handle.

public class Actor extends Thread {
  protected ConcurrentLinkedQueue<Memo> queue;
  private boolean running;

  public Actor () {
    super();
    queue = new ConcurrentLinkedQueue<Memo>();
    start();
  }

  // `post`s a message to this actor.
  public void post (Memo memo) {
    queue.offer(memo);
  }

  // `receive` and `idle` are meant to be overridden.
  protected void receive (Memo memo) {
    System.out.println(this + " received " + memo);
  }

  // Default `idle` method, just sleeps.
  protected void idle () {
    try { sleep(1000); } catch (Exception e) {} 
  }

  public final void run () {
    running = true;
    while (running) {
      Memo memo = queue.poll();       // If there's a message, process it.
      if (memo != null) receive(memo);
                                        // If the queue is empty, call the idle
      if (queue.size() == 0) idle();    // function.
    }
  }

  // Sets `running` to false and stops the thread.
  public void shutdown () {
    running = false;
  }
}