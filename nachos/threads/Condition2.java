package nachos.threads;

import java.util.ArrayList;
import java.util.LinkedList;

import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 * 
 * <p>
 * You must implement this.
 * 
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 *                      The current thread must hold this lock whenever it uses
	 *                      <tt>sleep()</tt>,
	 *                      <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
		waitQueue = new LinkedList<KThread>();
		waitForQueue = new LinkedList<KThread>();
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		// System.out.println("Sleeping current thread.");

		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();

		waitQueue.add(KThread.currentThread());

		conditionLock.release();

		KThread.currentThread().sleep();
		Machine.interrupt().restore(intStatus);

		conditionLock.acquire();
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {

		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();

		if (!waitForQueue.isEmpty()) {
			// There might be threads in waitForQueue that have already
			// been woken up by the timer but haven't been removed
			while (!waitForQueue.isEmpty()) {
				// we check if the thread was woken up by cancel()
				// if it cancelled successfully, which means it has not
				// been woken up, we ready the thread and remove it
				// otherwise, we remove the first and try again
				if (ThreadedKernel.alarm.cancel(waitForQueue.getFirst())) {
					waitForQueue.removeFirst();
					break;
				} else {
					waitForQueue.removeFirst();
				}
			}
		} else if (!waitQueue.isEmpty()) {
			// System.out.println("Waking one thread.");
			((KThread) waitQueue.removeFirst()).ready();
		}
		// else {
		// System.out.println("waitQueue is empty");
		// }
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		// System.out.println("Waking all threads.");

		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();

		while ((!waitQueue.isEmpty()) || (!waitForQueue.isEmpty())) {
			wake();
		}

		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Atomically release the associated lock and go to sleep on
	 * this condition variable until either (1) another thread
	 * wakes it using <tt>wake()</tt>, or (2) the specified
	 * <i>timeout</i> elapses. The current thread must hold the
	 * associated lock. The thread will automatically reacquire
	 * the lock before <tt>sleep()</tt> returns.
	 */
	public void sleepFor(long timeout) {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();
		waitForQueue.add(KThread.currentThread());
		conditionLock.release();
		ThreadedKernel.alarm.waitUntil(timeout);
		if (waitForQueue.contains(KThread.currentThread())) {
			waitForQueue.remove(KThread.currentThread());
		}
		Machine.interrupt().restore(intStatus);

		conditionLock.acquire();
	}

	// Example of the "interlock" pattern where two threads strictly
	// alternate their execution with each other using a condition
	// variable. (Also see the slide showing this pattern at the end
	// of Lecture 6.)

	private static class InterlockTest {
		private static Lock lock;
		private static Condition2 cv;

		private static class Interlocker implements Runnable {
			public void run() {
				lock.acquire();
				for (int i = 0; i < 10; i++) {
					System.out.println(KThread.currentThread().getName());
					cv.wake(); // signal
					cv.sleep(); // wait
				}
				lock.release();
			}
		}

		public InterlockTest() {
			lock = new Lock();
			cv = new Condition2(lock);

			KThread ping = new KThread(new Interlocker());
			ping.setName("ping");
			KThread pong = new KThread(new Interlocker());
			pong.setName("pong");

			ping.fork();
			pong.fork();

			// We need to wait for ping to finish, and the proper way
			// to do so is to join on ping. (Note that, when ping is
			// done, pong is sleeping on the condition variable; if we
			// were also to join on pong, we would block forever.)
			// For this to work, join must be implemented. If you
			// have not implemented join yet, then comment out the
			// call to join and instead uncomment the loop with
			// yields; the loop has the same effect, but is a kludgy
			// way to do it.
			ping.join();
			// for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
		}
	}
	// private static class C2UnitTest {
	// private static Lock lock;
	// private static Condition cv;

	// private static class WakeAller implements Runnable {
	// public void run() {
	// for (int i = 0; i < 5; ++i) {
	// System.out.println(KThread.currentThread().getName());
	// }
	// }
	// }

	// public WakeAllTest() {
	// lock = new Lock();
	// cv = new Condition2(lock);

	// for(int i = 0; i < 25; ++i) {
	// KThread thread = new KThread(new WakeAller());
	// thread.setName("thread " + i);
	// }
	// }
	// }

	public static void cvTest5() {
		final Lock lock = new Lock();
		// final Condition empty = new Condition(lock);
		final Condition2 empty = new Condition2(lock);
		final LinkedList<Integer> list = new LinkedList<>();

		KThread consumer = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				while (list.isEmpty()) {
					empty.sleep();
				}
				Lib.assertTrue(list.size() == 5, "List should have 5 values.");
				while (!list.isEmpty()) {
					// context swith for the fun of it
					KThread.currentThread().yield();
					System.out.println("Removed " + list.removeFirst());
				}
				lock.release();
			}
		});

		KThread producer = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				for (int i = 0; i < 5; i++) {
					list.add(i);
					System.out.println("Added " + i);
					// context swith for the fun of it
					KThread.currentThread().yield();
				}
				empty.wake();
				lock.release();
			}
		});

		consumer.setName("Consumer");
		producer.setName("Producer");
		consumer.fork();
		producer.fork();

		// We need to wait for the consumer and producer to finish,
		// and the proper way to do so is to join on them. For this
		// to work, join must be implemented. If you have not
		// implemented join yet, then comment out the calls to join
		// and instead uncomment the loop with yield; the loop has the
		// same effect, but is a kludgy way to do it.
		consumer.join();
		producer.join();
		// for (int i = 0; i < 50; i++) {
		// KThread.currentThread().yield();
		// }
	}

	public static void wakeAllTest0() {
		final Lock lock = new Lock();
		// final Condition empty = new Condition(lock);
		final Condition2 empty = new Condition2(lock);
		final LinkedList<Integer> list = new LinkedList<>();

		KThread consumer = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				while (list.isEmpty()) {
					empty.sleep();
				}
				Lib.assertTrue(list.size() == 5, "List should have 5 values.");
				while (!list.isEmpty()) {
					// context swith for the fun of it
					KThread.currentThread().yield();
					System.out.println("Removed " + list.removeFirst());
				}
				lock.release();
			}
		});

		KThread producer = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				for (int i = 0; i < 5; i++) {
					list.add(i);
					System.out.println("Added " + i);
					// context swith for the fun of it
					KThread.currentThread().yield();
				}
				empty.wakeAll();
				lock.release();
			}
		});

		consumer.setName("Consumer");
		producer.setName("Producer");
		consumer.fork();
		producer.fork();

		// We need to wait for the consumer and producer to finish,
		// and the proper way to do so is to join on them. For this
		// to work, join must be implemented. If you have not
		// implemented join yet, then comment out the calls to join
		// and instead uncomment the loop with yield; the loop has the
		// same effect, but is a kludgy way to do it.
		consumer.join();
		producer.join();
		// for (int i = 0; i < 50; i++) {
		// KThread.currentThread().yield();
		// }
	}

	public static void wakeAllTest1() {
		final Lock lock = new Lock();
		// final Condition empty = new Condition(lock);
		final Condition2 empty = new Condition2(lock);
		final LinkedList<Integer> list = new LinkedList<>();

		KThread consumer1 = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				while (list.isEmpty()) {
					empty.sleep();
				}
				Lib.assertTrue(list.size() == 5, "List should have 5 values.");

				// while (!list.isEmpty()) {
				// // context swith for the fun of it
				// // KThread.currentThread().yield();
				// System.out.println("Removed " + list.removeFirst());
				// }

				for (int i = 0; i < 5; ++i) {
					System.out.println("Removed " + list.removeFirst());
				}
				lock.release();
			}
		});

		KThread consumer2 = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				while (list.isEmpty()) {
					empty.sleep();
				}
				Lib.assertTrue(list.size() == 10, "List should have 10 values.");

				// while (!list.isEmpty()) {
				// // context swith for the fun of it
				// // KThread.currentThread().yield();
				// System.out.println("Removed " + list.removeFirst());
				// }
				for (int i = 0; i < 5; ++i) {
					System.out.println("Removed " + list.removeFirst());
				}
				lock.release();
			}
		});

		KThread producer = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				for (int i = 0; i < 10; i++) {
					list.add(i);
					System.out.println("Added " + i);
					// context swith for the fun of it
					KThread.currentThread().yield();
				}
				empty.wakeAll();
				lock.release();
			}
		});

		consumer1.setName("Consumer1");
		consumer2.setName("Consumer2");
		producer.setName("Producer");
		consumer1.fork();
		producer.fork();

		consumer2.fork();
		// We need to wait for the consumer and producer to finish,
		// and the proper way to do so is to join on them. For this
		// to work, join must be implemented. If you have not
		// implemented join yet, then comment out the calls to join
		// and instead uncomment the loop with yield; the loop has the
		// same effect, but is a kludgy way to do it.
		consumer1.join();
		consumer2.join();
		producer.join();
		// for (int i = 0; i < 50; i++) {
		// KThread.currentThread().yield();
		// }
	}

	public static void sleepNoLock() {
		final Lock lock = new Lock();
		// final Condition empty = new Condition(lock);
		final Condition2 empty = new Condition2(lock);
		final LinkedList<Integer> list = new LinkedList<>();

		KThread consumer = new KThread(new Runnable() {
			public void run() {
				// lock.acquire();
				while (list.isEmpty()) {
					empty.sleep();
				}
				Lib.assertTrue(list.size() == 5, "List should have 5 values.");
				while (!list.isEmpty()) {
					// context swith for the fun of it
					KThread.currentThread().yield();
					System.out.println("Removed " + list.removeFirst());
				}
				// lock.release();
			}
		});
		consumer.setName("Consumer");
		consumer.fork();
		consumer.join();

	}

	public static void wakeNoLock() {
		final Lock lock = new Lock();
		// final Condition empty = new Condition(lock);
		final Condition2 empty = new Condition2(lock);
		final LinkedList<Integer> list = new LinkedList<>();

		KThread consumer = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				while (list.isEmpty()) {
					empty.sleep();
				}
				Lib.assertTrue(list.size() == 5, "List should have 5 values.");
				while (!list.isEmpty()) {
					// context swith for the fun of it
					KThread.currentThread().yield();
					System.out.println("Removed " + list.removeFirst());
				}
				lock.release();
			}
		});

		KThread producer = new KThread(new Runnable() {
			public void run() {
				// lock.acquire();
				for (int i = 0; i < 5; i++) {
					list.add(i);
					System.out.println("Added " + i);
					// context swith for the fun of it
					KThread.currentThread().yield();
				}
				empty.wake();
				// lock.release();
			}
		});

		consumer.setName("Consumer");
		producer.setName("Producer");
		consumer.fork();
		producer.fork();

		// We need to wait for the consumer and producer to finish,
		// and the proper way to do so is to join on them. For this
		// to work, join must be implemented. If you have not
		// implemented join yet, then comment out the calls to join
		// and instead uncomment the loop with yield; the loop has the
		// same effect, but is a kludgy way to do it.
		consumer.join();
		producer.join();
		// for (int i = 0; i < 50; i++) {
		// KThread.currentThread().yield();
		// }
	}

	public static void wakeAllNoLock() {
		final Lock lock = new Lock();
		// final Condition empty = new Condition(lock);
		final Condition2 empty = new Condition2(lock);
		final LinkedList<Integer> list = new LinkedList<>();

		KThread consumer = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				while (list.isEmpty()) {
					empty.sleep();
				}
				Lib.assertTrue(list.size() == 5, "List should have 5 values.");
				while (!list.isEmpty()) {
					// context swith for the fun of it
					KThread.currentThread().yield();
					System.out.println("Removed " + list.removeFirst());
				}
				lock.release();
			}
		});

		KThread producer = new KThread(new Runnable() {
			public void run() {
				// lock.acquire();
				for (int i = 0; i < 5; i++) {
					list.add(i);
					System.out.println("Added " + i);
					// context swith for the fun of it
					KThread.currentThread().yield();
				}
				empty.wakeAll();
				// lock.release();
			}
		});

		consumer.setName("Consumer");
		producer.setName("Producer");
		consumer.fork();
		producer.fork();

		// We need to wait for the consumer and producer to finish,
		// and the proper way to do so is to join on them. For this
		// to work, join must be implemented. If you have not
		// implemented join yet, then comment out the calls to join
		// and instead uncomment the loop with yield; the loop has the
		// same effect, but is a kludgy way to do it.
		consumer.join();
		producer.join();
		// for (int i = 0; i < 50; i++) {
		// KThread.currentThread().yield();
		// }
	}

	public static void wakeNoWaitingThreads() {
		final Lock lock = new Lock();
		// final Condition empty = new Condition(lock);
		final Condition2 empty = new Condition2(lock);
		final LinkedList<Integer> list = new LinkedList<>();

		KThread producer = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				for (int i = 0; i < 5; i++) {
					list.add(i);
					System.out.println("Added " + i);
					// context swith for the fun of it
					KThread.currentThread().yield();
				}
				empty.wake();
				lock.release();
			}
		});

		producer.setName("Producer");
		producer.fork();
	}

	public static void wakeAllNoWaitingThreads() {
		final Lock lock = new Lock();
		// final Condition empty = new Condition(lock);
		final Condition2 empty = new Condition2(lock);
		final LinkedList<Integer> list = new LinkedList<>();

		KThread producer = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				for (int i = 0; i < 5; i++) {
					list.add(i);
					System.out.println("Added " + i);
					// context swith for the fun of it
					KThread.currentThread().yield();
				}
				empty.wakeAll();
				lock.release();
			}
		});

		producer.setName("Producer");
		producer.fork();
	}

	public static void testLostWake() {
		final Lock lock = new Lock();
		// final Condition empty = new Condition(lock);
		final Condition2 empty = new Condition2(lock);
		final LinkedList<Integer> list = new LinkedList<>();

		KThread waker = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				System.out.println("Waking nothing.");
				empty.wake();
				lock.release();
			}
		});

		KThread consumer = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				while (list.isEmpty()) {
					empty.sleep();
				}
				Lib.assertTrue(list.size() == 5, "List should have 5 values.");
				while (!list.isEmpty()) {
					// context swith for the fun of it
					KThread.currentThread().yield();
					System.out.println("Removed " + list.removeFirst());
				}
				lock.release();
			}
		});

		KThread producer = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				for (int i = 0; i < 5; i++) {
					list.add(i);
					System.out.println("Added " + i);
					// context swith for the fun of it
					KThread.currentThread().yield();
				}
				empty.wake();
				lock.release();
			}
		});
		waker.setName("Waker");
		consumer.setName("Consumer");
		producer.setName("Producer");
		waker.fork();
		consumer.fork();
		producer.fork();

		// We need to wait for the consumer and producer to finish,
		// and the proper way to do so is to join on them. For this
		// to work, join must be implemented. If you have not
		// implemented join yet, then comment out the calls to join
		// and instead uncomment the loop with yield; the loop has the
		// same effect, but is a kludgy way to do it.
		consumer.join();
		waker.join();
		producer.join();
		// for (int i = 0; i < 50; i++) {
		// KThread.currentThread().yield();
		// }
	}

	private static void sleepForTest1() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);

		lock.acquire();
		long t0 = Machine.timer().getTime();
		System.out.println(KThread.currentThread().getName() + " sleeping");
		// no other thread will wake us up, so we should time out
		cv.sleepFor(2000);
		long t1 = Machine.timer().getTime();
		System.out.println(KThread.currentThread().getName() +
				" woke up, slept for " + (t1 - t0) + " ticks");
		lock.release();
	}

	private static void sleepForTest2() {
		int durations[] = { 1000, 10 * 1000, 100 * 1000 };
		long t0, t1;
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);

		lock.acquire();
		for (int d : durations) {
			t0 = Machine.timer().getTime();
			System.out.println(KThread.currentThread().getName() + " sleeping");
			// no other thread will wake us up, so we should time out
			cv.sleepFor(d);
			t1 = Machine.timer().getTime();
			System.out.println("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
		lock.release();
	}

	private static void wakeOnlyOneThread() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
		lock.acquire();
		KThread sleepingThread1 = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				long t0 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() + " sleeping");
				// no other thread will wake us up, so we should time out
				cv.sleepFor(200000);
				long t1 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks"
						+ " which is less than 200000 ticks");
				lock.release();
			}
		});
		sleepingThread1.setName("sleepingThread1");
		sleepingThread1.fork();
		KThread sleepingThread2 = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				long t0 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() + " sleeping");
				// no other thread will wake us up, so we should time out
				cv.sleepFor(200000);
				long t1 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks"
						+ " which is more than 200000 ticks");
				lock.release();
			}
		});
		sleepingThread2.setName("sleepingThread2");
		sleepingThread2.fork();
		KThread wakeUpThread = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				cv.wake();
				lock.release();
			}
		});
		wakeUpThread.setName("wakeUpThread");
		wakeUpThread.fork();
		lock.release();
		sleepingThread2.join();
		sleepingThread1.join();
		wakeUpThread.join();
	}

	private static void wakeAllSleepForThreads() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
		lock.acquire();
		KThread sleepingThread1 = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				long t0 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() + " sleeping");
				// no other thread will wake us up, so we should time out
				cv.sleepFor(200000);
				long t1 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks"
						+ " which is less than 200000 ticks");
				lock.release();
			}
		});
		sleepingThread1.setName("sleepingThread1");
		sleepingThread1.fork();
		KThread sleepingThread2 = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				long t0 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() + " sleeping");
				// no other thread will wake us up, so we should time out
				cv.sleepFor(200000);
				long t1 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks"
						+ " which is less than 200000 ticks");
				lock.release();
			}
		});
		sleepingThread2.setName("sleepingThread2");
		sleepingThread2.fork();
		KThread wakeUpThread = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				cv.wakeAll();
				lock.release();
			}
		});
		wakeUpThread.setName("wakeUpThread");
		wakeUpThread.fork();
		lock.release();
		sleepingThread2.join();
		sleepingThread1.join();
		wakeUpThread.join();
	}

	public static void wakeAllRandomSleepFor() {
		final Lock lock = new Lock();
		final Condition2 cv = new Condition2(lock);

		final int threadCount = 10;
		ArrayList<KThread> threads = new ArrayList<KThread>();

		for (int i = 0; i < threadCount; ++i) {
			threads.add(new KThread(new Runnable() {
				public void run() {
					lock.acquire();

					double flip = Math.random();
					long t0 = Machine.timer().getTime();

					System.out.println(KThread.currentThread().getName() + " sleepingFor " + (flip * 100000));
					cv.sleepFor((long) (flip * 1000000));

					long t1 = Machine.timer().getTime();
					System.out.println(KThread.currentThread().getName() +
							" woke up, slept for " + (t1 - t0) + " ticks");
					lock.release();
				}
			}).setName("thread" + i));
		}

		KThread wakerThread = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				System.out.println("Waker thread running");
				cv.wakeAll();
				lock.release();
			}
		});
		for (KThread thread : threads) {
			thread.fork();
		}
		wakerThread.fork();

		// for (int i = threadCount - 1; i >= 0; i--) {
		// threads.get(i).join();
		// }
		for (int i = 0; i < threadCount; i++) {
			threads.get(i).join();
		}
		wakerThread.join();
	}

	public static void wakeAfterSleepFor() {
		final Lock lock = new Lock();
		final Condition2 cv = new Condition2(lock);

		KThread thread1 = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				long t0 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() + " sleeping");
				cv.sleepFor(100000);
				long t1 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks");
				lock.release();
			}
		});

		KThread thread2 = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				System.out.println("Thread 2 running");
				cv.wake();
				lock.release();
			}
		});

		thread1.setName("thread1");
		thread2.setName("thread2");
		thread1.fork();
		thread2.fork();

		thread1.join();
		thread2.join();
	}

	public static void wakeAllAfterSleepFor() {
		final Lock lock = new Lock();
		final Condition2 cv = new Condition2(lock);
		final int threadCount = 2;
		// KThread thread = null;
		// for (int i = 0; i < threadCount; ++i) {
		// thread = new KThread(new Runnable() {
		// public void run() {
		// lock.acquire();
		// long t0 = Machine.timer().getTime();
		// System.out.println(KThread.currentThread().getName() + " sleeping");
		// cv.sleepFor(2000);
		// long t1 = Machine.timer().getTime();
		// System.out.println(KThread.currentThread().getName() +
		// " woke up, slept for " + (t1 - t0) + " ticks");
		// lock.release();
		// }
		// });
		// thread.setName("thread" + i);
		// thread.fork();
		// }

		KThread thread1 = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				long t0 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() + " sleeping");
				cv.sleepFor(500000);
				long t1 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks");
				lock.release();
			}
		});

		KThread thread2 = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				long t0 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() + " sleeping");
				cv.sleepFor(100000);
				long t1 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks");
				lock.release();
			}
		});

		KThread thread3 = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				System.out.println("Thread 2 running");
				cv.wakeAll();
				lock.release();
			}
		});

		thread1.setName("sleepFor1");
		thread2.setName("sleepFor2");
		thread3.setName("wakeAll thread");
		thread1.fork();
		thread2.fork();
		thread3.fork();

		thread1.join();
		thread2.join();
		thread3.join();
	}

	public static void wakeAllAfterBothSleeps() {
		final Lock lock = new Lock();
		final Condition2 cv = new Condition2(lock);
		final int threadCount = 2;
		// KThread thread = null;
		// for (int i = 0; i < threadCount; ++i) {
		// thread = new KThread(new Runnable() {
		// public void run() {
		// lock.acquire();
		// long t0 = Machine.timer().getTime();
		// System.out.println(KThread.currentThread().getName() + " sleeping");
		// cv.sleepFor(2000);
		// long t1 = Machine.timer().getTime();
		// System.out.println(KThread.currentThread().getName() +
		// " woke up, slept for " + (t1 - t0) + " ticks");
		// lock.release();
		// }
		// });
		// thread.setName("thread" + i);
		// thread.fork();
		// }

		KThread thread1 = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				long t0 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() + " sleeping");
				cv.sleep();
				long t1 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks");
				lock.release();
			}
		});

		KThread thread2 = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				long t0 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() + " sleeping");
				cv.sleepFor(100000);
				long t1 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks");
				lock.release();
			}
		});

		KThread thread3 = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				System.out.println("Thread 2 running");
				cv.wakeAll();
				lock.release();
			}
		});

		thread1.setName("sleepFor1");
		thread2.setName("sleepFor2");
		thread3.setName("wakeAll thread");
		thread1.fork();
		thread2.fork();
		thread3.fork();

		thread1.join();
		thread2.join();
		thread3.join();
	}

	public static void wakeAllManyThreads() {
		final Lock lock = new Lock();
		final Condition2 cv = new Condition2(lock);
		final int threadCount = 20;
		KThread thread = null;

		for (int i = 0; i < threadCount; ++i) {
			thread = new KThread(new Runnable() {
				public void run() {
					lock.acquire();
					long t0 = Machine.timer().getTime();
					System.out.println(KThread.currentThread().getName() + " sleeping");
					cv.sleepFor(100000);
					long t1 = Machine.timer().getTime();
					System.out.println(KThread.currentThread().getName() +
							" woke up, slept for " + (t1 - t0) + " ticks");
					lock.release();
				}
			});
			thread.setName("thread" + i);
			thread.fork();
		}

		KThread wakerThread = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				System.out.println("Waker thread running");
				cv.wakeAll();
				lock.release();
			}
		});
		wakerThread.fork();

		thread.join();
		wakerThread.join();
	}

	// add threads to an array and fork at the end
	public static void wakeAllManyThreads2() {
		final Lock lock = new Lock();
		final Condition2 cv = new Condition2(lock);
		final int threadCount = 10;
		ArrayList<KThread> threads = new ArrayList<KThread>();

		for (int i = 0; i < threadCount; ++i) {
			threads.add(new KThread(new Runnable() {
				public void run() {
					lock.acquire();
					long t0 = Machine.timer().getTime();
					System.out.println(KThread.currentThread().getName() + " sleeping");
					cv.sleepFor(200000);
					long t1 = Machine.timer().getTime();
					System.out.println(KThread.currentThread().getName() +
							" woke up, slept for " + (t1 - t0) + " ticks");
					lock.release();
				}
			}).setName("thread" + i));
		}

		KThread wakerThread = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				System.out.println("Waker thread running");
				cv.wakeAll();
				lock.release();
			}
		});
		for (KThread thread : threads) {
			thread.fork();
		}
		wakerThread.fork();

		// for (int i = threadCount - 1; i >= 0; i--) {
		// threads.get(i).join();
		// }

		for (int i = 0; i < threadCount; i++) {
			threads.get(i).join();
		}
		wakerThread.join();
	}

	// TODO: in this test, calls to sleepFor wake the thread up after 20 ticks. Why?
	public static void wakeAllManyThreadsRandom() {
		final Lock lock = new Lock();
		final Condition2 cv = new Condition2(lock);
		final int threadCount = 10;
		ArrayList<KThread> threads = new ArrayList<KThread>();

		for (int i = 0; i < threadCount; ++i) {
			threads.add(new KThread(new Runnable() {
				public void run() {
					lock.acquire();
					double flip = Math.random();
					long t0 = Machine.timer().getTime();
					if (flip < 0.5) {
						System.out.println(KThread.currentThread().getName() + " sleepingFor " + (flip * 100000));
						cv.sleepFor((long) flip * 1000000);
					} else {
						System.out.println(KThread.currentThread().getName() + " sleeping");
						cv.sleep();
					}

					long t1 = Machine.timer().getTime();
					System.out.println(KThread.currentThread().getName() +
							" woke up, slept for " + (t1 - t0) + " ticks");
					lock.release();
				}
			}).setName("thread" + i));
		}

		KThread wakerThread = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				System.out.println("Waker thread running");
				cv.wakeAll();
				lock.release();
			}
		});
		for (KThread thread : threads) {
			thread.fork();
		}
		wakerThread.fork();

		// for (int i = threadCount - 1; i >= 0; i--) {
		// threads.get(i).join();
		// }
		for (int i = 0; i < threadCount; i++) {
			threads.get(i).join();
		}
		wakerThread.join();
	}

	// Invoke Condition2.selfTest() from ThreadedKernel.selfTest()

	public static void selfTest() {
		// new InterlockTest();
		// cvTest5();
		// sleepNoLock();
		// wakeNoLock();
		// wakeAllNoLock();
		// wakeAllTest1();
		// wakeNoWaitingThreads();
		// wakeAllNoWaitingThreads();
		// testLostWake();
		// sleepForTest1();
		// sleepForTest2();
		// wakeOnlyOneThread();
		// wakeAllSleepForThreads();
		// wakeAllRandomSleepFor();
		// wakeAllManyThreads2();
	}

	private Lock conditionLock;

	private LinkedList<KThread> waitQueue;
	private LinkedList<KThread> waitForQueue;
}
