package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		// int numPhysPages = Machine.processor().getNumPhysPages();
		// pageTable = new TranslationEntry[numPhysPages];
		// for (int i = 0; i < numPhysPages; i++)
		// pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
		fileDescriptorTable = new OpenFile[16];
		fileDescriptorTable[0] = UserKernel.console.openForReading();
		fileDescriptorTable[1] = UserKernel.console.openForWriting();
		childStatus = new HashMap<>();
		childThreads = new HashMap<>();
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		String name = Machine.getProcessClassName();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader. Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals("nachos.userprog.UserProcess")) {
			return new UserProcess();
		} else if (name.equals("nachos.vm.VMProcess")) {
			return new VMProcess();
		} else {
			return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args)) {
			return false;
		}
		// assign processID to child being forked
		processID = UserKernel.processIDCounter;
		// handle the first process ever
		if (UserKernel.processIDCounter == 0) {
			UserKernel.processIDCounter++;
		}
		// increment the active process counter
		UserKernel.activeProcessLock.acquire();
		UserKernel.activeProcess++;
		UserKernel.activeProcessLock.release();
		thread = new UThread(this);
		thread.setName(name).fork();
		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr     the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 *                  including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 *         found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);
		
		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0) 
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data  the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
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

		if (vaddr < 0 || vaddr >= memory.length)
			return 0;
		// total bytes read
		int amount = 0;
		// if we still have virtual memory space to write data
		while (vaddr < numPages * pageSize) {
			// get vpn from vitrual address
			int vpn = Processor.pageFromAddress(vaddr);
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
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data  the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
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

		if (vaddr < 0 || vaddr >= memory.length)
			return 0;
		// total bytes written
		int amount = 0;
		// if we still have virtual memory space to write data
		while (vaddr < numPages * pageSize) {
			// get vpn from vitrual address
			int vpn = Processor.pageFromAddress(vaddr);
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
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		} catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		Lib.debug(dbgProcess, "UserProcess.load: " + numPages + " pages in address space ("
				+ Machine.processor().getNumPhysPages() + " physical pages)");

		/*
		 * Layout of the Nachos user process address space.
		 * The code above calculates the total number of pages
		 * in the address space for this executable.
		 *
		 * +------------------+
		 * | Code and data |
		 * | pages from | size = num pages in COFF file
		 * | executable file |
		 * | (COFF file) |
		 * +------------------+
		 * | Stack pages | size = stackPages
		 * +------------------+
		 * | Arg page | size = 1
		 * +------------------+
		 *
		 * Page 0 is at the top, and the last page at the
		 * bottom is the arg page at numPages-1.
		 */

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		UserKernel.ppnLock.acquire();
		if (UserKernel.freePPN.size() < numPages) {
			UserKernel.ppnLock.release();
			return false;
		}
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++) {
			int freePage = UserKernel.freePPN.remove();
			pageTable[i] = new TranslationEntry(i, freePage, true, false, false, false);
		}

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				if (section.isReadOnly()) {
					int freePage1 = pageTable[vpn].ppn;
					pageTable[vpn] = new TranslationEntry(i, freePage1, true, true, false, false);
				}
				// for now, just assume virtual addresses=physical addresses
				section.loadPage(i, pageTable[vpn].ppn);
			}
		}
		UserKernel.ppnLock.release();

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		UserKernel.ppnLock.acquire();
		for (int i = 0; i < pageTable.length; i++) {
			UserKernel.freePPN.add(pageTable[i].ppn);
			System.out.println("unload" + pageTable[i].ppn);
			pageTable[i] = null;
		}
		UserKernel.ppnLock.release();
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		Lib.debug(dbgProcess, "UserProcess.handleHalt");

		if (processID != 0) {
			return -1;
		}
		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(int status) {
		// Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.

		for (int i = 0; i < fileDescriptorTable.length; i++) {
			if (fileDescriptorTable[i] != null) {
				fileDescriptorTable[i].close();
				fileDescriptorTable[i] = null;
			}
		}
		unloadSections();
		coff.close();
		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");
		// for now, unconditionally terminate with just one process
		if (processID != 0) {
			parentProcess.childStatus.put(processID, status);
		}

		UserKernel.activeProcessLock.acquire();
		if (UserKernel.activeProcess == 1) {
			Kernel.kernel.terminate();
		} else {
			UserKernel.activeProcess--;
		}
		UserKernel.activeProcessLock.release();
		KThread.finish();
		return 0;
	}

	/**
	 * Handle the open() system call.
	 */
	private int handleOpen(int name) {
		String fileName = readVirtualMemoryString(name, 256);
		if (fileName == null) {
			return -1;
		}
		int index = -1;
		for (int i = 0; i < fileDescriptorTable.length; i++) {
			if (fileDescriptorTable[i] == null) {
				OpenFile newFile = ThreadedKernel.fileSystem.open(fileName, false);
				if (newFile == null) {
					return -1;
				}
				fileDescriptorTable[i] = newFile;
				index = i;
				break;
			}
		}

		Lib.debug(dbgProcess, "UserProcess.handleOpen (" + name + ")");

		return index;
	}

	/**
	 * Handle the create() system call.
	 */
	private int handleCreate(int name) {
		String fileName = readVirtualMemoryString(name, 256);
		if (fileName == null) {
			return -1;
		}
		int index = -1;
		for (int i = 0; i < fileDescriptorTable.length; i++) {
			if (fileDescriptorTable[i] == null) {
				index = i;
				OpenFile newFile = ThreadedKernel.fileSystem.open(fileName, true);
				if (newFile == null) {
					return -1;
				}
				fileDescriptorTable[i] = newFile;
				break;
			}
		}

		Lib.debug(dbgProcess, "UserProcess.handleCreate (" + name + ")");

		return index;
	}

	/**
	 * Handle the close() system call.
	 */
	private int handleClose(int fileDescriptor) {
		// handling edge cases
		if (fileDescriptor < 0 || fileDescriptor > 15 ||
				fileDescriptorTable[fileDescriptor] == null) {
			return -1;
		}

		// handling closing console stream
		fileDescriptorTable[fileDescriptor].close();
		fileDescriptorTable[fileDescriptor] = null;

		Lib.debug(dbgProcess, "UserProcess.handleClose (" + fileDescriptor + ")");

		return 0;
	}

	/**
	 * Handle the unlink() system call.
	 */
	private int handleUnlink(int name) {
		String fileName = readVirtualMemoryString(name, 256);
		if (fileName == null) {
			return -1;
		}
		boolean status = ThreadedKernel.fileSystem.remove(fileName);
		Lib.debug(dbgProcess, "UserProcess.handleUnlink (" + name + ")");

		if (status) {
			return 0;
		} else {
			return -1;
		}
	}

	/**
	 * Handle the write() system call.
	 */
	private int handleWrite(int fileDescriptor, int buffer, int count) {
		int length = -1;
		// handling edges cases
		if (fileDescriptor <= 0 || fileDescriptor > 15 ||
				fileDescriptorTable[fileDescriptor] == null || count < 0) {
			return -1;
		}
		byte[] bufferBytes = new byte[count];
		// read into buffer
		int amount = readVirtualMemory(buffer, bufferBytes);
		// if bytes read are lower than count
		if (amount < count) {
			return -1;
		}
		if (fileDescriptor == 1) {
			length = fileDescriptorTable[fileDescriptor].write(bufferBytes, 0, bufferBytes.length);
			if (length == 0 && count != 0) {
				return -1;
			} else if (length < count) {
				return -1;
			}
		} else {
			// int position = fileDescriptorTable[fileDescriptor].tell();
			length = fileDescriptorTable[fileDescriptor].write(bufferBytes, 0, count);
			if (length < count) {
				return -1;
			}
		}
		// get file position
		// write into file
		Lib.debug(dbgProcess, "UserProcess.handleWrite (" + fileDescriptor + ")");

		return length;
	}

	/**
	 * Handle the read() system call.
	 */
	private int handleRead(int fileDescriptor, int buffer, int count) {
		int length = -1;
		// handling edges cases
		if (fileDescriptor < 0 || fileDescriptor > 15 ||
				fileDescriptorTable[fileDescriptor] == null || count < 0 ||
				fileDescriptor == 1) {
			return -1;
		}
		byte[] bufferBytes = new byte[count];

		if (fileDescriptor == 0) {
			length = fileDescriptorTable[fileDescriptor].read(bufferBytes, 0, bufferBytes.length);
		} else {
			// int position = fileDescriptorTable[fileDescriptor].tell();
			length = fileDescriptorTable[fileDescriptor].read(bufferBytes, 0, count);
		}
		writeVirtualMemory(buffer, bufferBytes);
		// get file position
		// write into file
		Lib.debug(dbgProcess, "UserProcess.handleWrite (" + fileDescriptor + ")");

		return length;
	}

	/**
	 * Handle the exec() system call.
	 */
	private int handleExec(int file, int argc, int argv) {
		Lib.debug(dbgProcess, "UserProcess.handleExec (" + file + ")");

		String[] args = new String[argc];
		// handling edge case
		if (argc < 0) {
			return -1;
		}
		String fileName = readVirtualMemoryString(file, 256);
		if (fileName == null) {
			return -1;
		}
		if (argc == 0) {
			args = new String[0];
		} else {
			for (int i = 0; i < argc; i++) {
				byte[] stringAddressBytes = new byte[4];
				int success = readVirtualMemory(argv + i * 4, stringAddressBytes);
				int stringAddressInt = Lib.bytesToInt(stringAddressBytes, 0);
				String s = readVirtualMemoryString(stringAddressInt, 256);
				args[i] = s;
			}
		}
		UserProcess childProcess = UserProcess.newUserProcess();
		if (childProcess == null) {
			return -1;
		} else {
			UserKernel.counterLock.acquire();
			if (childProcess.execute(fileName, args)) {
				int childProcessID = UserKernel.processIDCounter;
				UserKernel.processIDCounter++;
				childStatus.put(childProcessID, null);
				childThreads.put(childProcessID, childProcess.thread);
				childProcess.parentProcess = this;
				UserKernel.counterLock.release();
				return childProcessID;
			} else {
				UserKernel.counterLock.release();
				return -1;
			}
		}
	}

	/**
	 * Handle the join() system call.
	 */
	private int handleJoin(int processID, int status) {

		int returnValue = 1;
		// no such child with processID
		if (!childThreads.containsKey(processID)) {
			return -1;
		}
		// status out of bound
		if (status > numPages*pageSize) {
			return -1;
		}
		childThreads.get(processID).join();
		// if status is null pointer, do nothing about status
		if (status == 0x0) {
			returnValue = 1;
		} else {
			// if status is not null, but child terminates abnormally,
			// set returnValue to 0 and do nothing about status
			// if status is not null but child terminates normally,
			// set returnValue to 1 and write to status
			if (childStatus.get(processID) == null) {
				returnValue = 0;
			} else {
				returnValue = 1;
				byte[] statusBytes = Lib.bytesFromInt(childStatus.get(processID));
				writeVirtualMemory(status, statusBytes);
			}
		}
		childThreads.remove(processID);
		childStatus.remove(processID);

		Lib.debug(dbgProcess, "UserProcess.handleWrite (" + processID + ")");

		return returnValue;
	}

	/**
	 * Handle the exitUnexpected() call.
	 */
	protected int handleExitUnexpected() {
		// Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(999);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.

		for (int i = 0; i < fileDescriptorTable.length; i++) {
			if (fileDescriptorTable[i] != null) {
				fileDescriptorTable[i].close();
				fileDescriptorTable[i] = null;
			}
		}
		unloadSections();
		coff.close();

		UserKernel.activeProcessLock.acquire();
		if (UserKernel.activeProcess == 1) {
			Kernel.kernel.terminate();
		} else {
			UserKernel.activeProcess--;
		}
		UserKernel.activeProcessLock.release();
		KThread.finish();
		Lib.debug(dbgProcess, "UserProcess.handleExitUnexpected (" + "Unexpected exception" + ")");
		return 0;
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0      the first syscall argument.
	 * @param a1      the second syscall argument.
	 * @param a2      the third syscall argument.
	 * @param a3      the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
			case syscallHalt:
				return handleHalt();
			case syscallExit:
				return handleExit(a0);
			case syscallOpen:
				return handleOpen(a0);
			case syscallWrite:
				return handleWrite(a0, a1, a2);
			case syscallRead:
				return handleRead(a0, a1, a2);
			case syscallCreate:
				return handleCreate(a0);
			case syscallClose:
				return handleClose(a0);
			case syscallUnlink:
				return handleUnlink(a0);
			case syscallExec:
				return handleExec(a0, a1, a2);
			case syscallJoin:
				return handleJoin(a0, a1);
			default:
				Lib.debug(dbgProcess, "Unknown syscall " + syscall);
				Lib.assertNotReached("Unknown system call!");
		}
		return 0;
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

			default:
				Lib.debug(dbgProcess, "Unexpected exception: "
						+ Processor.exceptionNames[cause]);
				handleExitUnexpected();
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** The thread that executes the user-level program. */
	protected UThread thread;

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private OpenFile[] fileDescriptorTable;

	public HashMap<Integer, Integer> childStatus;

	public HashMap<Integer, UThread> childThreads;

	private int processID;

	public UserProcess parentProcess;
}
