package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.ArrayList;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		swapFile = ThreadedKernel.fileSystem.open("swapFile", true);
		usedPages = new IPTE[Machine.processor().getNumPhysPages()];
		usedPagesLock = new Lock();
		clockPointer = 0;
		clockPointerLock = new Lock();
		swapPages = new ArrayList<>();
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	public static OpenFile swapFile;
	
	private static final char dbgVM = 'v';

	// inverted page table lock
	public static Lock usedPagesLock; 

	// inverted page table
	public static IPTE[] usedPages;

	// clock algorithm pointer
	public static int clockPointer;

	// clock algorithm pointer lock
	public static Lock clockPointerLock;

	// swap page list
	public static ArrayList<IPTE> swapPages;

	// inverted page table content
	protected static class IPTE {
		public VMProcess process;
		public TranslationEntry translationEntry;

		public IPTE(VMProcess process, TranslationEntry translationEntry){
			this.process = process;
			this.translationEntry = translationEntry;
		}
	}
}
