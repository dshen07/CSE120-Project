package nachos.threads;

import java.util.HashMap;

import nachos.machine.*;

/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {
    /**
     * Allocate a new Rendezvous.
     */
    public Rendezvous() {
        lock = new Lock();
        tagConditionMap = new HashMap<Integer, Condition>();
        conditionValueMap = new HashMap<Condition, Integer>();
    }

    /**
     * Synchronously exchange a value with another thread. The first
     * thread A (with value X) to exhange will block waiting for
     * another thread B (with value Y). When thread B arrives, it
     * will unblock A and the threads will exchange values: value Y
     * will be returned to thread A, and value X will be returned to
     * thread B.
     *
     * Different integer tags are used as different, parallel
     * synchronization points (i.e., threads synchronizing at
     * different tags do not interact with each other). The same tag
     * can also be used repeatedly for multiple exchanges.
     *
     * @param tag   the synchronization tag.
     * @param value the integer to exchange.
     */
    public int exchange(int tag, int value) {
        lock.acquire();
        int returnValue = 0;
        if (tagConditionMap.containsKey(tag)) {
            tagConditionMap.get(tag).wake();
            // using tagConditionMap to get condition
            // and use condition to get value
            returnValue = conditionValueMap.get(tagConditionMap.get(tag));
            conditionValueMap.put(tagConditionMap.get(tag), value);
            tagConditionMap.remove(tag);
        } else {
            Condition cv = new Condition(lock);
            tagConditionMap.put(tag, cv);
            conditionValueMap.put(cv, value);
            cv.sleep();
            returnValue = conditionValueMap.get(cv);
            conditionValueMap.remove(cv);
        }
        lock.release();
        return returnValue;
    }

    public static void rendezTest1() {
        final Rendezvous r = new Rendezvous();

        KThread t1 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = -1;

                System.out.println("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange(tag, send);
                Lib.assertTrue(recv == 1, "Was expecting " + 1 + " but received " + recv);
                System.out.println("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t1.setName("t1");
        KThread t2 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = 1;

                System.out.println("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange(tag, send);
                Lib.assertTrue(recv == -1, "Was expecting " + -1 + " but received " + recv);
                System.out.println("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t2.setName("t2");

        t1.fork();
        t2.fork();
        // assumes join is implemented correctly
        t1.join();
        t2.join();
    }

    public static void fourThreadsSameTag() {
        final Rendezvous r = new Rendezvous();

        KThread t1 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = -1;

                System.out.println("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange(tag, send);
                Lib.assertTrue(recv == 1, "Was expecting " + 1 + " but received " + recv);
                System.out.println("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t1.setName("t1");
        KThread t2 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = 1;

                System.out.println("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange(tag, send);
                Lib.assertTrue(recv == -1, "Was expecting " + -1 + " but received " + recv);
                System.out.println("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t2.setName("t2");
        KThread t3 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = 2;

                System.out.println("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange(tag, send);
                Lib.assertTrue(recv == 3, "Was expecting " + 3 + " but received " + recv);
                System.out.println("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t3.setName("t3");

        KThread t4 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = 3;

                System.out.println("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange(tag, send);
                Lib.assertTrue(recv == 2, "Was expecting " + 2 + " but received " + recv);
                System.out.println("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t4.setName("t4");
        t1.fork();
        t2.fork();
        t3.fork();
        t4.fork();
        // assumes join is implemented correctly
        t1.join();
        t2.join();
        t3.join();
        t4.join();
    }
    // Invoke Rendezvous.selfTest() from ThreadedKernel.selfTest()

    public static void selfTest() {
        // place calls to your Rendezvous tests that you implement here
        rendezTest1();
        //fourThreadsSameTag();
    }

    private Lock lock;
    private HashMap<Integer, Condition> tagConditionMap;
    private HashMap<Condition, Integer> conditionValueMap;
}
