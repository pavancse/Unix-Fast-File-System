/**
 * Copyright (c) 1992-1993 The Regents of the University of California.
 * All rights reserved.  See copyright.h for copyright notice and limitation 
 * of liability and disclaimer of warranty provisions.
 *
 *  Created by Patrick McSweeney on 12/13/08.
 *
 */
package jnachos.kern;

import jnachos.machine.*;

/**
 * The ExceptionHanlder class handles all exceptions raised by the simulated
 * machine. This class is abstract and should not be instantiated.
 */
public abstract class ExceptionHandler {

	/**
	 * This class does all of the work for handling exceptions raised by the
	 * simulated machine. This is the only funciton in this class.
	 *
	 * @param pException
	 *            The type of exception that was raised.
	 * @see ExceptionType
	 */
	public static void handleException(ExceptionType pException) {
		switch (pException) {
		// If this type was a system call
		case SyscallException:

			// Get what type of system call was made
			int type = Machine.readRegister(2);

			// Invoke the System call handler
			SystemCallHandler.handleSystemCall(type);
			break;

		//Page not found in RAM, load page to RAM
		case PageFaultException:

			//Get the faulted virtual address
			int virtAddr = Machine.readRegister(Machine.BadVAddrReg);

			AddrSpace addrSpace = JNachos.getCurrentProcess().getSpace();

			//Call method to load the page
			addrSpace.loadPageFault(virtAddr);
			break;



		// All other exceptions shut down for now
		default:
			System.exit(0);
		}
	}
}
