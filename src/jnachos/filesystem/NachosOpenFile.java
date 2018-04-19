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
 * 
 * @author pjmcswee
 *
 */
public class NachosOpenFile implements OpenFile {

	/** The header for the file. */
	private FileHeader mHdr;

	/** The position within the file. */
	private int mSeekPosition;

	/** Sector number to store the header sector number */
	private int mHeaderSectorNum;

	/**
	 * Open a Nachos file for reading and writing. Bring the file header into
	 * memory while the file is open.
	 *
	 * @param sector
	 *            the location on disk of the file header for this file
	 */
	public NachosOpenFile(int sector) {
		mHdr = new FileHeader();
		mHdr.fetchFrom(sector);
		mSeekPosition = 0;
		mHeaderSectorNum = sector;
	}

	/**
	 * Close a Nachos file, de-allocating any in-memory data structures.
	 */
	public void delete() {
		mHdr.delete();
	}

	/**
	 * Change the current location within the open file -- the point at which
	 * the next Read or Write will start from.
	 *
	 * @param position
	 *            the location within the file for the next Read/Write.
	 */
	public void seek(int position) {
		mSeekPosition = position;
	}

	/**
	 * Read/write a portion of a file, starting from mSeekPosition. Return the
	 * number of bytes actually written or read, and as a side effect, increment
	 * the current position within the file.
	 *
	 * Implemented using the more primitive ReadAt/WriteAt.
	 *
	 * @param into
	 *            the buffer to contain the data to be read from disk
	 * @param from
	 *            the buffer containing the data to be written to disk
	 * @param numBytes
	 *            the number of bytes to transfer
	 */
	public int read(byte[] into, int numBytes) {
		int result = readAt(into, numBytes, mSeekPosition);
		mSeekPosition += result;
		return result;
	}

	public int write(byte[] into, int numBytes) {
		int result = writeAt(into, numBytes, mSeekPosition);
		mSeekPosition += result;
		return result;
	}

	/**
	 * Read a portion of a file, starting at "position". Return the number
	 * of bytes actually written or read, but has no side effects (except that
	 * Write modifies the file, of course).
	 * 
	 * There is no guarantee the request starts or ends on an even disk sector
	 * boundary; however the disk only knows how to read/write a whole disk
	 * sector at a time. Thus:
	 * 
	 * For ReadAt: We read in all of the full or partial sectors that are part
	 * of the request, but we only copy the part we are interested in. For
	 * WriteAt: We must first read in any sectors that will be partially
	 * written, so that we don't overwrite the unmodified portion. We then copy
	 * in the data that will be modified, and write back all the full or partial
	 * sectors that are part of the request.
	 * 
	 * @param into
	 *            the buffer to contain the data to be read from disk
	 * @param from
	 *            the buffer containing the data to be written to disk
	 * @param numBytes
	 *            the number of bytes to transfer
	 * @param position
	 *            the offset within the file of the first byte to be
	 *            read/written
	 *            ----------------------------------------------------------------------
	 */
	public int readAt(byte[] into, int numBytes, int position) {
		assert (position + numBytes < mHdr.fileLength());
		int begin = position / Disk.SectorSize;
		int end = (int) Math.ceil((position + numBytes) * 1.0 / Disk.SectorSize);
		int beginByte = 0;

		for (int i = begin; i < end; i++) {
			byte[] sectorInfo = new byte[Disk.SectorSize];
			JNachos.mSynchDisk.readSector(mHdr.mDataSectors[i], sectorInfo);
			if (i == begin) {
				System.arraycopy(sectorInfo, position % Disk.SectorSize, into, beginByte, Math.min((Disk.SectorSize - position % Disk.SectorSize), numBytes - beginByte));
				beginByte = Math.min((Disk.SectorSize - position % Disk.SectorSize), numBytes - beginByte);
			} else {
				System.arraycopy(sectorInfo, 0, into, beginByte, Math.min(Disk.SectorSize, numBytes - beginByte));
				beginByte += Math.min(Disk.SectorSize, numBytes - beginByte);
			}
		}

		return numBytes;
	}

	/**
	 * Write a portion of a file, starting at "position". Return the number
	 * of bytes actually written or read, but has no side effects (except that
	 * Write modifies the file, of course).
	 *
	 * @param from
	 * @param numBytes
	 * @param position
	 * @return
	 */
	public int writeAt(byte[] from, int numBytes, int position) {
		int oldFileSize = (int) Math.ceil((((double) mHdr.fileLength()) / Disk.SINGLE_FRAGMENT_SIZE)) * Disk.SINGLE_FRAGMENT_SIZE;
		int newFileSize = position + numBytes;

		int begin = position / Disk.SectorSize;
		int end = newFileSize > oldFileSize ? mHdr.getmNumSectors() : (int) Math.ceil(((double)newFileSize) / Disk.SectorSize);
		int startByte = 0;

		if (newFileSize < oldFileSize || position < oldFileSize) {

			for (int i = begin; i < end; i++) {
				byte[] sectorInfo = new byte[Disk.SectorSize];
				JNachos.mSynchDisk.readSector(mHdr.mDataSectors[i], sectorInfo);
				if (i == begin) {
					System.arraycopy(from, startByte, sectorInfo, position % Disk.SectorSize, Math.min(Disk.SectorSize - (position % Disk.SectorSize), numBytes - startByte));
					startByte = Math.min(Disk.SectorSize - (position % Disk.SectorSize), numBytes - startByte);
				} else {
					System.arraycopy(from, startByte, sectorInfo, 0, Math.min(Disk.SectorSize, numBytes - startByte));
					startByte += Math.min(Disk.SectorSize, numBytes - startByte);
				}
				JNachos.mSynchDisk.writeSector(mHdr.mDataSectors[i], sectorInfo);
			}
		}

		if (newFileSize > oldFileSize) {
			byte[] newData = null;
			if (oldFileSize % Disk.SINGLE_BLOCK_SIZE == 0) {
				newData = new byte[numBytes - startByte];
				System.arraycopy(from, startByte, newData, 0, numBytes - startByte);
			} else {


				int noOfFragments = (oldFileSize % Disk.SINGLE_BLOCK_SIZE) / Disk.SINGLE_FRAGMENT_SIZE;
				newData = new byte[Disk.SectorSize * noOfFragments * Disk.NUM_OF_SECTORS_IN_A_FRAGMENT + (numBytes - startByte)];
				int start = oldFileSize / Disk.SINGLE_BLOCK_SIZE * Disk.NUM_OF_SECTORS_IN_A_BLOCK;
				for (int i = 0; i < noOfFragments * Disk.NUM_OF_SECTORS_IN_A_FRAGMENT; i++) {
					byte[] sectorData = new byte[Disk.SectorSize];
					JNachos.mSynchDisk.readSector(mHdr.mDataSectors[start + i], sectorData);
					System.arraycopy(sectorData, 0, newData, i * Disk.SectorSize, Disk.SectorSize);
					NachosFileSystem.mFreeMap.clear(mHdr.mDataSectors[start + i]);
					mHdr.mDataSectors[start + i] = 0;
				}


				System.arraycopy(from, startByte, newData, noOfFragments * Disk.SINGLE_FRAGMENT_SIZE, numBytes - startByte);
			}

			int[] sectors = NachosFileSystem.mFreeMap.find(newData.length);
			int start = oldFileSize / Disk.SINGLE_BLOCK_SIZE * Disk.NUM_OF_SECTORS_IN_A_BLOCK;
			for (int i = 0; i < sectors.length; i++) {
				mHdr.mDataSectors[start + i] = sectors[i];
				if(newData.length - i*Disk.SectorSize > 0) {
					byte[] sectorData = new byte[Disk.SectorSize];
					System.arraycopy(newData, i * Disk.SectorSize, sectorData, 0, Math.min(Disk.SectorSize, newData.length - i * Disk.SectorSize));
					JNachos.mSynchDisk.writeSector(sectors[i], sectorData);
				} else {
					JNachos.mSynchDisk.writeSector(sectors[i], new byte[Disk.SectorSize]);
				}
			}
		}

		mHdr.setNumBytes(mHdr.fileLength() > position + numBytes ? mHdr.fileLength() : position + numBytes);


		return numBytes;
	}

	/**
	 * Closes the file
	 */
	public void closeFile() {

	}

	/**
	 * Return the number of bytes in the file.
	 */
	public int length() {
		return mHdr.fileLength();
	}

	/**
	 * Print the header information
	 */
	public void printHeader() {
		mHdr.print();
	}

	/**
	 * Write information to header
	 */
	public void writeHeader() {
		mHdr.writeBack(mHeaderSectorNum);
	}

}
