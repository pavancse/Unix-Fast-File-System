/**
 * Copyright (c) 1992-1993 The Regents of the University of California.
 * All rights reserved.  See copyright.h for copyright notice and limitation 
 * of liability and disclaimer of warranty provisions.
 *  
 *  Created by Patrick McSweeney on 12/5/08.
 */
package jnachos.filesystem;

import jnachos.machine.*;
import jnachos.kern.*;

/**
 * Routines to manage the overall operation of the file system.
 *
 * Implements routines to map from textual file names to files.
 * 
 * Each file in the file system has: A file header, stored in a sector on disk
 * (the size of the file header data structure is arranged to be precisely the
 * size of 1 disk sector) A number of data blocks An entry in the file system
 * directory The file system consists of several data structures: A bitmap of
 * free disk sectors (cf. bitmap.h) A directory of file names and file headers
 * 
 * Both the bitmap and the directory are represented as normal files. Their file
 * headers are located in specific sectors (sector 0 and sector 1), so that the
 * file system can find them on bootup. The file system assumes that the bitmap
 * and directory files are kept "open" continuously while Nachos is running.
 * 
 * For those operations (such as Create, Remove) that modify the directory
 * and/or bitmap, if the operation succeeds, the changes are written immediately
 * back to disk (the two files are kept open during all this time). If the
 * operation fails, and we have modified part of the directory and/or bitmap, we
 * simply discard the changed version, without writing it back to disk.
 * 
 * Our implementation at this point has the following restrictions: there is no
 * synchronization for concurrent accesses files have a fixed size, set when the
 * file is created files cannot be bigger than about 3KB in size there is no
 * hierarchical directory structure, and only a limited number of files can be
 * added to the system there is no attempt to make the system robust to failures
 * (if Nachos exits in the middle of an operation that modifies the file system,
 * it may corrupt the disk)
 */
public class NachosFileSystem implements FileSystem {

	// Sectors containing the file headers for the bitmap of free sectors,
	// and the directory of files. These file headers are placed in well-known
	// sectors, so that they can be located on boot-up.
	/** The sector where the bitmap is stored. */
	public static final int FreeMapSector = 0;
	/** The sector where the top level sector is stored. */
	public static final int DirectorySector = 1;

	// Initial file sizes for the bitmap and directory; until the file system
	// supports extensible files, the directory size sets the maximum number
	// of files that can be loaded onto the disk.
	public static final int FreeMapFileSize = (Disk.NumSectors);
	public static final int NumDirEntries = 10;
	public static final int DirectoryFileSize = (Directory.sizeOfDirectoryEntry() * NumDirEntries) + 4;

	private NachosOpenFile mDirectoryFile;
	private NachosOpenFile mFreeMapFile;
	public static BitMap mFreeMap;


	/**
	 * Initialize the file system. If format = true, the disk has nothing on it,
	 * and we need to initialize the disk to contain an empty directory, and a
	 * bitmap of free sectors (with almost but not all of the sectors marked as
	 * free).
	 * 
	 * If format = false, we just have to open the files representing the bitmap
	 * and the directory.
	 * 
	 * @param pFormat
	 *            should we initialize the disk?
	 */
	public NachosFileSystem(boolean pFormat) {
		// super(pFormat);
		Debug.print('f', "Initializing the file system.");

		if (pFormat) {
			formatDiskBeforeSetup();
		} else {
			useExistingDisk();
		}
	}

	/**
	 * This function will format the file system disk before it starts allocating the data
	 */
	private void formatDiskBeforeSetup() {
			mFreeMap = new BitMap(Disk.NumSectors);
			Directory directory = new Directory(NumDirEntries);
			FileHeader mapHdr = new FileHeader();
			FileHeader dirHdr = new FileHeader();

			Debug.print('f', "Formatting the file system.");

			mFreeMap.mark(FreeMapSector);
			mFreeMap.mark(DirectorySector);

			if (!mapHdr.allocate(mFreeMap, FreeMapFileSize)) {
				assert (false);
			}
			if (!dirHdr.allocate(mFreeMap, DirectoryFileSize)) {
				assert (false);
			}

			Debug.print('f', "Writing headers back to disk.");
			mapHdr.writeBack(FreeMapSector);
			dirHdr.writeBack(DirectorySector);

			mFreeMapFile = new NachosOpenFile(FreeMapSector);
			mDirectoryFile = new NachosOpenFile(DirectorySector);

			Debug.print('f', "Writing bitmap and directory back to disk.\n");
			mFreeMap.writeBack(mFreeMapFile);
			directory.writeBack(mDirectoryFile);

			if (Debug.isEnabled('f')) {
				mFreeMap.print();
				directory.print();
				mFreeMap.delete();
				directory.delete();
				mapHdr.delete();
				dirHdr.delete();
			}
	}

	/**
	 * This function will use the existing disk, it will load the file system information from the disk
	 */
	private void useExistingDisk(){
		mFreeMapFile = new NachosOpenFile(FreeMapSector);
		mDirectoryFile = new NachosOpenFile(DirectorySector);

		Directory directory = new Directory(DirectoryFileSize);
		directory.fetchFrom(mDirectoryFile);
		if(directory.getmTableSize() == 0){
			formatDiskBeforeSetup();
		}

		mFreeMap = new BitMap(FreeMapFileSize);
		mFreeMap.fetchFrom(mFreeMapFile);
	}

	/**
	 * Create a file in the Nachos file system (similar to UNIX create). Since
	 * we can't increase the size of files dynamically, we have to give Create
	 * the initial size of the file.
	 * 
	 * The steps to create a file are: 1) Make sure the file doesn't already
	 * exist 2) Allocate a sector for the file header 3) Allocate space on disk
	 * for the data blocks for the file 4) Add the name to the directory 5)
	 * Store the new file header on disk 6) Flush the changes to the bitmap and
	 * the directory back to disk
	 * 
	 * Return true if everything goes ok, otherwise, return false.
	 * 
	 * Create fails if: 1) file is already in directory 2) no free space for
	 * file header 3) no free entry for file in directory 4) no free space for
	 * data blocks for the file
	 * 
	 * Note that this implementation assumes there is no concurrent access to
	 * the file system!
	 * 
	 * @param pName
	 *            the name of file to be created.
	 * @param initialSize
	 *            the size of file to be created.
	 */
	public boolean create(String pName, int pInitialSize) {
		Directory directory;
		FileHeader hdr;
		int sector;
		boolean success;

		Debug.print('f', "Creating file " + pName + ", size: " + pInitialSize);

		directory = new Directory(NumDirEntries);
		directory.fetchFrom(mDirectoryFile);

		if (directory.find(pName) != -1) {
			// file is already in directory
			success = false;
		} else {

			// find a sector to hold the file header
			sector = mFreeMap.find(Disk.SectorSize)[0];

			if (sector == -1) {
				success = false; // no free block for file header
			} else if (!directory.add(pName, sector)) {
				success = false; // no space in directory
			} else {
				hdr = new FileHeader();
				if (!hdr.allocate(mFreeMap, pInitialSize)) {
					success = false; // no space on disk for data
				} else {
					success = true;
					// everthing worked, flush all changes back to disk
					hdr.writeBack(sector);
					directory.writeBack(mDirectoryFile);
					mFreeMap.writeBack(mFreeMapFile);
					Debug.print('f', "File created succesffully " + sector + "\t" + mDirectoryFile);
				}
				hdr.delete();
			}
			mFreeMap.delete();
		}
		directory.delete();
		return success;
	}

	/**
	 * Open a file for reading and writing. To open a file: Find the location of
	 * the file's header, using the directory Bring the header into memory
	 *
	 * @param pName
	 *            the text name of the file to be opened
	 */
	public NachosOpenFile open(String pName) {
		Directory directory = new Directory(NumDirEntries);
		NachosOpenFile openFile = null;
		int sector;

		Debug.print('f', "Opening file " + pName);
		directory.fetchFrom(mDirectoryFile);

		sector = directory.find(pName);

		if (sector >= 0) {
			// name was found in directory
			openFile = new NachosOpenFile(sector);
		}

		directory.delete();

		// return null if not found
		return openFile;
	}

	/**
	 * Delete a file from the file system. This requires: Remove it from the
	 * directory Delete the space for its header Delete the space for its data
	 * blocks Write changes to directory, bitmap back to disk
	 *
	 * Return true if the file was deleted, false if the file wasn't in the file
	 * system.
	 *
	 * @param pName
	 *            the text name of the file to be removed.
	 * @return true if successful, false if the file did not exist.
	 */
	public boolean remove(String pName) {
		Directory directory;
		BitMap freeMap;
		FileHeader fileHdr;
		int sector;

		directory = new Directory(NumDirEntries);
		directory.fetchFrom(mDirectoryFile);
		sector = directory.find(pName);

		if (sector == -1) {
			directory.delete();
			return false; // file not found
		}
		fileHdr = new FileHeader();
		fileHdr.fetchFrom(sector);

		freeMap = new BitMap(Disk.NumSectors);
		freeMap.fetchFrom(mFreeMapFile);

		fileHdr.deallocate(freeMap); // remove data blocks
		freeMap.clear(sector); // remove header block
		directory.remove(pName);

		freeMap.writeBack(mFreeMapFile); // flush to disk
		directory.writeBack(mDirectoryFile); // flush to disk
		fileHdr.delete();
		directory.delete();
		freeMap.delete();
		return true;
	}

	/**
	 * List all the files in the file system directory.
	 */

	public void list() {
		Directory directory = new Directory(NumDirEntries);
		directory.fetchFrom(mDirectoryFile);
		directory.list();
		directory.delete();
	}

	/**
	 * Print everything about the file system: the contents of the bitmap the
	 * contents of the directory for each file in the directory, the contents of
	 * the file header the data in the file
	 */
	public void print() {
		FileHeader bitHdr = new FileHeader();
		FileHeader dirHdr = new FileHeader();
		BitMap freeMap = new BitMap(Disk.NumSectors);
		Directory directory = new Directory(NumDirEntries);

		System.out.println("Bit map file header:\n");

		bitHdr.fetchFrom(FreeMapSector);
		bitHdr.print();

		System.out.println("Directory file header:\n");
		dirHdr.fetchFrom(DirectorySector);
		dirHdr.print();

		freeMap.fetchFrom(mFreeMapFile);
		freeMap.print();

		directory.fetchFrom(mDirectoryFile);
		directory.print();

		bitHdr.delete();
		dirHdr.delete();
		freeMap.delete();
		directory.delete();
	}
}
