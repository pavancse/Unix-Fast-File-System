/**
 * Copyright (c) 1992-1993 The Regents of the University of California.
 * All rights reserved.  See copyright.h for copyright notice and limitation 
 * of liability and disclaimer of warranty provisions.
 *  
 *  Created by Patrick McSweeney on 12/5/08.
 */
package jnachos.kern;

import jnachos.filesystem.OpenFile;
import jnachos.machine.*;

/** The class handles System calls made from user programs. */
public class SystemCallHandler {
	/** The System call index for halting. */
	public static final int SC_Halt = 0;

	/** The System call index for exiting a program. */
	public static final int SC_Exit = 1;

	/** The System call index for executing program. */
	public static final int SC_Exec = 2;

	/** The System call index for joining with a process. */
	public static final int SC_Join = 3;

	/** The System call index for creating a file. */
	public static final int SC_Create = 4;

	/** The System call index for opening a file. */
	public static final int SC_Open = 5;

	/** The System call index for reading a file. */
	public static final int SC_Read = 6;

	/** The System call index for writting a file. */
	public static final int SC_Write = 7;

	/** The System call index for closing a file. */
	public static final int SC_Close = 8;

	/** The System call index for forking a forking a new process. */
	public static final int SC_Fork = 9;

	/** The System call index for yielding a program. */
	public static final int SC_Yield = 10;
	/**
	 * Entry point into the Nachos kernel. Called when a user program is
	 * executing, and either does a syscall, or generates an addressing or
	 * arithmetic exception.
	 * 
	 * For system calls, the following is the calling convention:
	 * 
	 * system call code -- r2 arg1 -- r4 arg2 -- r5 arg3 -- r6 arg4 -- r7
	 * 
	 * The result of the system call, if any, must be put back into r2.
	 * 
	 * And don't forget to increment the pc before returning. (Or else you'll
	 * loop making the same system call forever!
	 * 
	 * @pWhich is the kind of exception. The list of possible exceptions are in
	 *         Machine.java
	 **/
	public static void handleSystemCall(int pWhichSysCall) {

		Debug.print('a', "!!!!" + Machine.read1 + "," + Machine.read2 + "," + Machine.read4 + "," + Machine.write1 + ","
				+ Machine.write2 + "," + Machine.write4);

		NachosProcess currentProcess;

		switch (pWhichSysCall) {
		// If halt is received shut down
		case SC_Halt:
			Debug.print('a', "Shutdown, initiated by user program.");
			Interrupt.halt();
			break;
		//Exit system call to finish the process
		case SC_Exit:
			// Read in any arguments from the 4th register
			int arg = Machine.readRegister(4);

			System.out
					.println("Current Process " + JNachos.getCurrentProcess().getName() + " exiting with code " + arg);

			/*currentProcess = JNachos.getCurrentProcess();
			NachosProcess waitingProcess = currentProcess.checkJoinedWaitingThread();
			if(waitingProcess != null){
				waitingProcess.writeUserRegister(2,arg);
				Scheduler.readyToRun(waitingProcess);
			}*/

			// Finish the invoking process
			JNachos.getCurrentProcess().finish();
			break;
			//Execute system call to execute the user program
		case SC_Exec:
			// Read address of the file path store in memory from the 4th register
			int args = Machine.readRegister(4);
			StringBuilder filename=new StringBuilder();
			int character;

			//Fetch the user executable file path
			while( (character = Machine.mMainMemory[args++]) != 0){
				filename.append((char) character);
			}


			OpenFile executable = JNachos.mFileSystem.open(filename.toString());

			// If the file does not exist
			if (executable == null) {
				Debug.print('t', "Unable to open file" + filename);
				return;
			}

			// Load the file into the memory space
			currentProcess = JNachos.getCurrentProcess();

			//get the addSpace of current process
			AddrSpace space = currentProcess.getSpace();

			//clear the current addSpace
			AddrSpace.cleanAddrSpcae(space);

			//create and assign the new addSpace of new  to the process
			space = new AddrSpace(executable);
			currentProcess.setSpace(space);
			currentProcess.setProcessSwapFile(space.getSwapFile());

			//reset the registers and load the page table to MMU
			space.initRegisters();
			space.restoreState();
			break;
		case SC_Join:
			//Read the pid from the 4th register
			int pid = Machine.readRegister(4);
			currentProcess = JNachos.getCurrentProcess();

			//Verify the calling process ID and process ID fetch from register are same
			//also verify fetch process ID exists
			if( (currentProcess.getmProcessID() == pid) || (!currentProcess.checkProcessExists(pid)) ) {
				incrementPC();
				return;
			}

			//store the process ID
			currentProcess.addJoinThread(pid);

			//Increment the register to execute next instruction
			incrementPC();

			//sleep the joined process
			currentProcess.sleep();
			break;
		case SC_Fork:
			//Create new child process
			NachosProcess nachosProcess=new NachosProcess("child");

			//Copy the addrSpace of current process
			AddrSpace addrSpace=new AddrSpace(JNachos.getCurrentProcess().getSpace());
			nachosProcess.setSpace(addrSpace);
			nachosProcess.setProcessSwapFile(addrSpace.getSwapFile());
			incrementPC();

			//Write the forked process ID as the return value to called process register
			Machine.writeRegister(2,nachosProcess.getmProcessID());

			//save the process state
			nachosProcess.saveUserState();

			//Write 0 as return value for the forked process register
			nachosProcess.writeUserRegister(2,0);

			//set the forkedChild flag
			nachosProcess.setForkedChild(true);

			//fork method called to begin the execution
			nachosProcess.fork(new VoidFunctionPtr() {
				@Override
				public void call(Object pArg) {
					Machine.run();
				}
			},null);
			break;
		default:
			Interrupt.halt();
			break;
		}
	}

	/**
	 * Increment the program counter to next instruction
	 */
	public static void incrementPC(){
		int pcAfter = Machine.mRegisters[Machine.NextPCReg] + 4;
		Machine.mRegisters[Machine.PrevPCReg] = Machine.mRegisters[Machine.PCReg];
		Machine.mRegisters[Machine.PCReg] = Machine.mRegisters[Machine.NextPCReg];
		Machine.mRegisters[Machine.NextPCReg] = pcAfter;
	}
}
