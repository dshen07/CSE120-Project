package nachos.threads;

import java.util.Random;

import java.util.ArrayList;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
		// initilize arraylist
		sleepingThreads = new ArrayList<KThread>();
		wakeTimes = new ArrayList<Long>();
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		ArrayList<Integer> idxList = new ArrayList<Integer>();
		for (int i = 0; i < sleepingThreads.size(); ++i) {
			// System.out.println("Checking thread: " + sleepingThreads.get(i));
			// System.out.println("Wake time: " + wakeTimes.get(i));
			if (Machine.timer().getTime() > wakeTimes.get(i)) {
				idxList.add(i);
				sleepingThreads.get(i).ready();
			}
		}
		for (int i = sleepingThreads.size() - 1; i >= 0; i--) {
			if (idxList.indexOf(i) > -1) {
				sleepingThreads.remove(i);
				wakeTimes.remove(i);
			}
		}
		KThread.currentThread().yield();
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// disable interrupts
		boolean intStatus = Machine.interrupt().disable();

		// if the wait parameter x is 0 or negative, return without waiting
		if (x <= 0) {
			return;
		}
		// calculate wake time
		long wakeTime = Machine.timer().getTime() + x;
		// place current thread onto ArrayList
		sleepingThreads.add(KThread.currentThread());
		// put the wake time onto ArrayList
		wakeTimes.add(wakeTime);
		// sleep thread
		KThread.sleep();
		// restore interrupts
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true. If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * 
	 * @param thread the thread whose timer should be cancelled.
	 */
	public boolean cancel(KThread thread) {
		boolean intStatus = Machine.interrupt().enabled();
		if (intStatus) {
			Machine.interrupt().disable();
		}
		if (sleepingThreads.contains(thread)) {
			int idx = sleepingThreads.indexOf(thread);
			sleepingThreads.get(idx).ready();
			sleepingThreads.remove(thread);
			wakeTimes.remove(idx);
			if (intStatus) {
				Machine.interrupt().enable();
			}
			return true;
		} else {
			if (intStatus) {
				Machine.interrupt().enable();
			}
			return false;
		}
	}

	// Add Alarm testing code to the Alarm class

	public static void alarmTest1() {
		int durations[] = { 1000, 10 * 1000, 100 * 1000 };
		long t0, t1;

		for (int d : durations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil(d);
			t1 = Machine.timer().getTime();
			System.out.println("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
	}

	// Implement more test methods here ...
	public static void alarmTestNeg() {
		long t0, t1;
		long tNeg = new Random().nextLong(Long.MAX_VALUE) * -1;
		System.out.println("Negative wait time: " + tNeg);
		t0 = Machine.timer().getTime();
		ThreadedKernel.alarm.waitUntil(tNeg);
		t1 = Machine.timer().getTime();
		System.out.println("alarmTestNeg: waited for " + (t1 - t0) + " ticks");

	}

	public static void alarmTestZero() {
		long t0, t1;
		long zero = 0;
		t0 = Machine.timer().getTime();
		ThreadedKernel.alarm.waitUntil(zero);
		t1 = Machine.timer().getTime();
		System.out.println("alarmTest0: waited for " + (t1 - t0) + " ticks");
	}

	public static void alarmTestSingleThreadRandom() {
		long[] randomDurations = new long[10];
		for (int i = 0; i < 10; ++i) {
			randomDurations[i] = new Random().nextLong(100000);
			System.out.println(randomDurations[i]);
		}
		long t0, t1;
		for (long d : randomDurations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil(d);
			t1 = Machine.timer().getTime();
			System.out.println("alarmTestSingleThreadRandom: waited for " + (t1 - t0) + " ticks");
		}
	}

	public static void alarmTestManyThreads() {
		int d[] = { 1000, 10 * 1000, 100 * 1000, 1000 * 1000 };
		new KThread(new WaitUntilTest(d[0])).setName("forked thread 1").fork();
		new WaitUntilTest(d[1]).run();
		new KThread(new WaitUntilTest(d[2])).setName("forked thread 2").fork();
		new WaitUntilTest(d[3]).run();
		// new KThread(new WaitUntilTest(d[1])).setName("forked thread 2").fork();

	}

	public static void alarmTestManyThreadsRandom() {
		int threadCount = 100;
		int scalar = -1;
		long[] durations = new long[threadCount];
		for (int i = 0; i < threadCount; ++i) {
			durations[i] = new Random().nextLong(10000) * scalar;
			scalar *= -1;
			System.out.println(durations[i]);

		}
		for (int i = 0; i < threadCount - 1; i++) {
			new KThread(new WaitUntilTest(durations[i])).setName("forked thread " + i).fork();
			new WaitUntilTest(durations[i + 1]).run();
		}
		// new KThread(new WaitUntilTest(d[1])).setName("forked thread 2").fork();

	}

	private static class WaitUntilTest implements Runnable {
		WaitUntilTest(long d) {
			this.d = d;
		}

		public void run() {
			System.out.println("*** thread " + d);
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil(d);
			t1 = Machine.timer().getTime();
			System.out.println("waitUntilTest: waited for " + (t1 - t0) + " ticks");
		}

		private long d;
		private long t0, t1;
	}

	// Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
	public static void selfTest() {
		// alarmTest1();
		// alarmTestZero();
		// alarmTestNeg();
		// alarmTestSingleThreadRandom();
		// alarmTestManyThreads();
		// alarmTestManyThreadsRandom();
		// Invoke your other test methods here ...
	}

	// data structure to hold threads blocked by waitUntil()
	private ArrayList<KThread> sleepingThreads;
	private ArrayList<Long> wakeTimes;
}
