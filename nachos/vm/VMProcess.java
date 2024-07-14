package nachos.vm;

import java.util.HashMap;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import nachos.vm.VMKernel.IPTE;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr  the first byte of virtual memory to read.
	 * @param data   the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 *               array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		if (!(offset >= 0 && length >= 0
				&& offset + length <= data.length)) {
			return 0;
		}

		byte[] memory = Machine.processor().getMemory();

		// total bytes read
		int amount = 0;
		// if we still have virtual memory space to write data
		while (vaddr < numPages * pageSize) {
			// get vpn from vitrual address
			int vpn = Processor.pageFromAddress(vaddr);
			if (!pageTable[vpn].valid) {
				handlePageFault(vaddr);
			}
			// get page offset from virtual address
			int pageOffset = Processor.offsetFromAddress(vaddr);
			// get ppn from page table
			int ppn = pageTable[vpn].ppn;
			// calculate physcial address
			int physcialAddress = ppn * pageSize + pageOffset;
			// find out the number of remaining bytes to copy
			int bytesRead = Math.min(pageSize - pageOffset, length);
			// write to memory
			System.arraycopy(memory, physcialAddress, data, offset, bytesRead);
			// update the amount copied
			amount += bytesRead;
			// update offset
			offset += bytesRead;
			// update remaining bytes to write
			length = length - bytesRead;
			if (length <= 0) {
				break;
			}
			// update current virtual address
			vaddr += bytesRead;
		}
		return amount;
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr  the first byte of virtual memory to write.
	 * @param data   the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 *               memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		if (!(offset >= 0 && length >= 0
				&& offset + length <= data.length)) {
			return 0;
		}
		byte[] memory = Machine.processor().getMemory();
		// total bytes written
		int amount = 0;
		// if we still have virtual memory space to write data
		while (vaddr < numPages * pageSize) {
			// get vpn from vitrual address
			int vpn = Processor.pageFromAddress(vaddr);
			if (!pageTable[vpn].valid) {
				handlePageFault(vaddr);
			}
			// get page offset from virtual address
			int pageOffset = Processor.offsetFromAddress(vaddr);
			// get ppn from page table
			int ppn = pageTable[vpn].ppn;
			// calculate physcial address
			int physcialAddress = ppn * pageSize + pageOffset;
			// find out the number of remaining bytes to copy
			int bytesCopied = Math.min(pageSize - pageOffset, length);
			// write to memory
			System.arraycopy(data, offset, memory, physcialAddress, bytesCopied);
			pageTable[vpn].dirty = true;
			// update the amount copied
			amount += bytesCopied;
			// update offset
			offset += bytesCopied;
			// update remaining bytes to write
			length = length - bytesCopied;
			if (length <= 0) {
				break;
			}
			// update current virtual address
			vaddr += bytesCopied;
		}

		return amount;
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		// if (numPages > Machine.processor().getNumPhysPages()) {
		// coff.close();
		// Lib.debug(dbgProcess, "\tinsufficient physical memory");
		// return false;
		// }

		swapPagesMap = new int[numPages];
		for (int i = 0; i < swapPagesMap.length; i++) {
			swapPagesMap[i] = -1;
		}
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++) {
			pageTable[i] = new TranslationEntry(i, -1, false, false, false, false);
		}

		// // load sections
		// for (int s = 0; s < coff.getNumSections(); s++) {
		// CoffSection section = coff.getSection(s);
		// Lib.debug(dbgProcess, "\tinitializing " + section.getName()
		// + " section (" + section.getLength() + " pages)");

		// for (int i = 0; i < section.getLength(); i++) {
		// int vpn = section.getFirstVPN() + i;
		// if (section.isReadOnly()) {
		// int freePage1 = pageTable[vpn].ppn;
		// pageTable[vpn] = new TranslationEntry(i, freePage1, true, true, false,
		// false);
		// }
		// // for now, just assume virtual addresses=physical addresses
		// section.loadPage(i, pageTable[vpn].ppn);
		// }
		// }

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		UserKernel.ppnLock.acquire();
		for (int i = 0; i < pageTable.length; i++) {
			if (pageTable[i].valid) {
				UserKernel.freePPN.add(pageTable[i].ppn);
				// System.out.println("unload" + pageTable[i].ppn);
				pageTable[i] = null;
			}
		}
		UserKernel.ppnLock.release();
		for (int i = 0; i < swapPagesMap.length; i++) {
			if (swapPagesMap[i] != -1) {
				VMKernel.swapPages.set(swapPagesMap[i], null);
			}
		}
	}

	protected void swapIn(int vpn, boolean initialize, boolean dirty) {
		if (initialize || !dirty) {
			// if page in COFF, load COFF section
			for (int s = 0; s < coff.getNumSections(); s++) {
				CoffSection section = coff.getSection(s);
				for (int i = 0; i < section.getLength(); i++) {
					int sectionVPN = section.getFirstVPN() + i;
					if (sectionVPN == vpn) {
						if (section.isReadOnly()) {
							pageTable[vpn].readOnly = true;
						}
						Lib.debug(dbgProcess, "\tinitializing " + section.getName()
								+ " section (" + sectionVPN + " page)");
						section.loadPage(i, pageTable[vpn].ppn);
						return;
					}
				}
			}
			// if page in Stack or Arg, zero-fill
			byte[] zeros = new byte[pageSize];
			writeVirtualMemory(vpn * pageSize, zeros);
		} else {
			int spn = swapPagesMap[vpn];
			byte[] data = new byte[pageSize];
			VMKernel.swapFile.read(spn * pageSize, data, 0, pageSize);
			int amount = writeVirtualMemory(vpn * pageSize, data);
			Machine.incrNumSwapReads();
		}
	}

	protected void swapOut(VMKernel.IPTE ipte) {
		if (ipte.translationEntry.dirty == false
				&& ipte.process.swapPagesMap[ipte.translationEntry.vpn] != -1) {
			ipte.translationEntry.valid = false;
			Machine.incrNumSwapSkips();
			return;
		}
		int spn = -1;
		for (int i = 0; i < VMKernel.swapPages.size(); i++) {
			if (VMKernel.swapPages.get(i) == null) {
				spn = i;
				ipte.process.swapPagesMap[ipte.translationEntry.vpn] = spn;
				VMKernel.swapPages.set(spn, ipte);
				byte[] data = new byte[pageSize];
				ipte.process.readVirtualMemory(ipte.translationEntry.vpn * pageSize, data);
				VMKernel.swapFile.write(spn * pageSize, data, 0, pageSize);
				ipte.translationEntry.valid = false;
				Machine.incrNumSwapWrites();
				break;
			}
		}
		if (spn == -1) {
			VMKernel.swapPages.add(ipte);
			spn = VMKernel.swapPages.indexOf(ipte);
			ipte.process.swapPagesMap[ipte.translationEntry.vpn] = spn;
			byte[] data = new byte[pageSize];
			ipte.process.readVirtualMemory(ipte.translationEntry.vpn * pageSize, data);
			VMKernel.swapFile.write(spn * pageSize, data, 0, pageSize);
			ipte.translationEntry.valid = false;
			Machine.incrNumSwapWrites();
		}
	}

	protected void handlePageFault(int bad_vaddr) {
		// get vpn from vitrual address
		int vpn = Processor.pageFromAddress(bad_vaddr);
		// check if the page has been initialized yet
		boolean initialize = false;
		boolean dirty = false;
		// if not, we will load it at the bottom
		if (pageTable[vpn].ppn == -1) {
			initialize = true;
		}
		if (pageTable[vpn].dirty) {
			dirty = true;
		}
		VMKernel.ppnLock.acquire();
		VMKernel.usedPagesLock.acquire();
		// if there are free pages, just grab one
		if (VMKernel.freePPN.size() > 0) {
			int freePage = VMKernel.freePPN.remove();
			pageTable[vpn].ppn = freePage;
			pageTable[vpn].valid = true;
			// use inverted page table to track this page
			VMKernel.usedPages[freePage] = new VMKernel.IPTE(this, pageTable[vpn]);
		} else {
			VMKernel.clockPointerLock.acquire();
			// clock algorithm
			while (true) {
				if (VMKernel.usedPages[VMKernel.clockPointer].translationEntry.used != false) {
					VMKernel.usedPages[VMKernel.clockPointer].translationEntry.used = false;
					VMKernel.clockPointer = (VMKernel.clockPointer + 1) % Machine.processor().getNumPhysPages();
				} else {
					// evict this page
					// page in (depending on if it is first time here)
					swapOut(VMKernel.usedPages[VMKernel.clockPointer]);
					pageTable[vpn].ppn = VMKernel.clockPointer;
					pageTable[vpn].valid = true;
					VMKernel.usedPages[VMKernel.clockPointer] = new VMKernel.IPTE(this, pageTable[vpn]);
					VMKernel.clockPointer = (VMKernel.clockPointer + 1) % Machine.processor().getNumPhysPages();
					break;
				}
			}
			VMKernel.clockPointerLock.release();
		}
		swapIn(vpn, initialize, dirty);
		VMKernel.usedPagesLock.release();
		VMKernel.ppnLock.release();
		// for (int i = 0; i < pageTable.length; i++) {
		// Lib.debug(dbgProcess, "page table " + pageTable[i].vpn + " valid " +
		// pageTable[i].valid);
		// }
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();
		switch (cause) {
			case Processor.exceptionSyscall:
				int result = handleSyscall(processor.readRegister(Processor.regV0),
						processor.readRegister(Processor.regA0),
						processor.readRegister(Processor.regA1),
						processor.readRegister(Processor.regA2),
						processor.readRegister(Processor.regA3));
				processor.writeRegister(Processor.regV0, result);
				processor.advancePC();
				break;
			case Processor.exceptionPageFault:
				handlePageFault(processor.readRegister(Processor.regBadVAddr));
				break;
			default:
				Lib.debug(dbgProcess, "Unexpected exception: "
						+ Processor.exceptionNames[cause]);
				handleExitUnexpected();
		}
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';

	public int[] swapPagesMap;
	private int counter = 0;
}
