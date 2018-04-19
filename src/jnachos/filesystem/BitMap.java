/**
 * Copyright (c) 1992-1993 The Regents of the University of California.
 * All rights reserved.  See copyright.h for copyright notice and limitation 
 * of liability and disclaimer of warranty provisions.
 *  
 *  Created by Patrick McSweeney on 12/5/08.
 */
package jnachos.filesystem;

import jnachos.machine.Disk;

/**
 * This class keeps track of an array of bits. Primarily for keeping track of
 * free sectors/pageframes, etc. The main methods involved are find and clear.
 * Find will return an arbitrary bit (AFTER it has set the bit to true), clear
 * will clear out a specified bit.
 * 
 * The bitmap is written to and read from a file for storing.
 * 
 *
 */
public class BitMap {
	/** The number of clear bits. */
	private int mNumClear;

	/** Keep track of all of the bits. */
	private boolean[] mUsed;

	/** Size of the bit array. */
	private int mNumBits;

	/** Keep track od used blocks */
	private boolean[] mUsedBlocks;

	/** Keep track of used fragments */
	private boolean[] mUsedFragments;

	/**
	 * Creates a bitmap witht the specified size.
	 * 
	 * @param pBits
	 *            the size of this bitmap.
	 */
	public BitMap(int pBits) {
		mUsed = new boolean[pBits];
		mNumClear = mUsed.length;
		mNumBits = pBits;
		mUsedBlocks = new boolean[Disk.NumSectors/Disk.NUM_OF_FRAGMENTS_IN_A_BLOCK];
		mUsedFragments = new boolean[Disk.NumSectors/Disk.NUM_OF_SECTORS_IN_A_FRAGMENT];
	}

	/**
	 * Returns the number of unused bits.
	 * 
	 * @return the number of unused bits.
	 */
	public int numClear() {
		return mNumClear;
	}

	/**
	 * Set the bit pBit to false (unused).
	 * 
	 * @param pBit
	 *            the bit to mark as unused.
	 * @throws
	 *             Error if the pBit is less than 0 or greater than the number
	 *             of bits.
	 */
	public void clear(int pBit) {
		assert ((pBit >= 0) && (pBit < mNumBits));

		if (mUsed[pBit]) {
			mNumClear++;
		}

		mUsed[pBit] = false;
		boolean flag = true;
		int num = pBit/Disk.NUM_OF_SECTORS_IN_A_FRAGMENT;
		for(int i = 0; i<Disk.NUM_OF_SECTORS_IN_A_FRAGMENT; i++){
			if(mUsed[num*Disk.NUM_OF_SECTORS_IN_A_FRAGMENT + i]){
				flag = false;
			}
		}

		if(flag){
			mUsedFragments[pBit/Disk.NUM_OF_SECTORS_IN_A_FRAGMENT] = false;
			int block = pBit/ Disk.NUM_OF_SECTORS_IN_A_BLOCK;
			for(int i = 0; i<Disk.NUM_OF_FRAGMENTS_IN_A_BLOCK; i++){
				if(mUsedFragments[block*Disk.NUM_OF_FRAGMENTS_IN_A_BLOCK + i]){
					flag = false;
				}
			}

			if(flag){
				mUsedBlocks[pBit/Disk.NUM_OF_SECTORS_IN_A_BLOCK] = false;
			}
		}
	}


	/**
	 * Prints the bitmap to the screen.
	 */
	public void print() {
		System.out.println("Bitmap Blocks");
		int count = 0;
		for (int i = 0; i <= 47; i++) {
			String out = i + ":" + mUsed[i];
			System.out.print(out);
			int length = out.length();
			while(length<8){
				System.out.print(" ");
				length++;
			}
			System.out.print("|");
			count++;
			if(count%16 == 0)
				System.out.println();
		}
		System.out.println("\n\n");
	}

	/**
	 * Check the fragments which are not used
	 * @param reqFragments
	 * @return
	 */
	private int checkUnusedFragments(int reqFragments){
		for(int i = 0; i< mUsedBlocks.length; i++){
			int j=0;
			int total = 0;
			for(;j<Disk.NUM_OF_FRAGMENTS_IN_A_BLOCK;){
				if(mUsedFragments[i*Disk.NUM_OF_FRAGMENTS_IN_A_BLOCK + j]){
					total = 0;
				} else {
					total++;
				}
				if(total == reqFragments)
					break;
				j++;
			}
			if(total == reqFragments){
				return i*Disk.NUM_OF_FRAGMENTS_IN_A_BLOCK + j - reqFragments + 1;
			}
		}
		return -1;
	}

	/**
	 * Saves the bitmap to a file.
	 * 
	 * @param pFile
	 *            The file to write the bitmap to.
	 */
	public void writeBack(NachosOpenFile pFile) {
		byte[] buffer = new byte[mNumBits];
		for (int i = 0; i < mNumBits; i++) {
			buffer[i] = (byte) (mUsed[i] ? 1 : 0);
		}

		pFile.writeAt(buffer, mNumBits, 0);
	}

	/**
	 * Loads the bitmap from the file.
	 * 
	 * @param pFile
	 *            The file to Load the bitmap from.
	 */
	public void fetchFrom(NachosOpenFile pFile) {
		byte[] buffer = new byte[mNumBits];
		pFile.readAt(buffer, mNumBits, 0);

		for (int i = 0; i < mNumBits; i++) {
			if(buffer[i] == 1){
				//mark the sector
				mUsed[i] = true;
				//mark the block
				mUsedBlocks[i/Disk.NUM_OF_SECTORS_IN_A_BLOCK] = true;
				//mark the fragment
				mUsedFragments[i/Disk.NUM_OF_SECTORS_IN_A_FRAGMENT] = true;
			}
		}
	}

	/**
	 * Checks whether or not the specified bit is in use.
	 * 
	 * @param pBit
	 *            the bit to check if used or not.
	 * @return True if the bit is used, false otherwise.
	 * @throws
	 *             Error if the pBit is less than 0 or greater than the number
	 *             of bits.
	 */
	public boolean test(int pBit) {
		assert ((pBit >= 0) && (pBit < mNumBits));

		return mUsed[pBit];
	}

	/**
	 * Marks the specified bit as used.
	 * 
	 * @param pBit
	 *            The bit to mark as used.
	 * @throws
	 *             Error if the pBit is less than 0 or greater than the number
	 *             of bits.
	 *
	 */
	public void mark(int pBit) {
		assert ((pBit >= 0) && (pBit < mNumBits));

		if (!mUsed[pBit])
			mNumClear--;

		//mark the sector
		mUsed[pBit] = true;
		//mark the block
		mUsedBlocks[pBit/Disk.NUM_OF_SECTORS_IN_A_BLOCK] = true;
		//mark the fragment
		mUsedFragments[pBit/Disk.NUM_OF_SECTORS_IN_A_FRAGMENT] = true;

	}

	/**
	 *
	 */
	public void delete() {
		// ??
	}

	/**
	 * Function is used to find and mark an unused bit.
	 * 
	 * @return The first index of an unused bit if there is one, -1 otherwise.
	 */
	public int find() {
		if (mNumClear == 0)
			return -1;
		for (int i = 0; i < mNumBits; i++) {
			if (!mUsed[i]) {
				mark(i);
				return i;
			}
		}
		return -1;
	}

	/**
	 * Function to get the not used blocks
	 * @return
	 */
	private int fetchUnallocatedBlock(){
		for(int i = 0; i< mUsedBlocks.length; i++){
			if(!mUsedBlocks[i]){
				return i;
			}
		}
		return -1;
	}

	/**
	 * Function to get the sectors of the fragment
	 * @param begin
	 * @param total
	 * @return
	 */
	private int[] fetchSectorsUsingFragments(int begin, int total){
		int[] allSectors = new int[total * Disk.NUM_OF_SECTORS_IN_A_FRAGMENT];
		int r=0;
		for(int i=begin; i<begin+total; i++){
			for(int j = 0; j<Disk.NUM_OF_SECTORS_IN_A_FRAGMENT; j++){
				allSectors[r++] = i*Disk.NUM_OF_SECTORS_IN_A_FRAGMENT + j;
			}
		}
		return allSectors;
	}

	/**
	 * Function to get the sectors in the block
	 * @param blockNo
	 * @return
	 */
	private int[] fetchSectorsInBlock(int blockNo){
		int[] sectors = new int[Disk.NUM_OF_SECTORS_IN_A_BLOCK];
		for(int i = 0; i<Disk.NUM_OF_SECTORS_IN_A_BLOCK; i++){
			sectors[i] = blockNo*Disk.NUM_OF_SECTORS_IN_A_BLOCK + i;
		}
		return sectors;
	}


	/**
	 * Find the blocks for allocating the data
	 * @param bytes
	 * @return
	 */
	public synchronized int[] find(int bytes){
		int[] blocks = new int[(int) Math.ceil(((double)bytes)/Disk.SINGLE_FRAGMENT_SIZE) * Disk.NUM_OF_SECTORS_IN_A_FRAGMENT];
		int l = 0;
		//allocate the blocks
		while(bytes/ Disk.SINGLE_BLOCK_SIZE != 0){
			int notUsedBlock = fetchUnallocatedBlock();
			assert(notUsedBlock != -1);
			int[] getSectors = fetchSectorsInBlock(notUsedBlock);
			for(int i=0; i<getSectors.length; i++){
				blocks[l++] = getSectors[i];
				mark(getSectors[i]);
			}
			bytes -= Disk.SINGLE_BLOCK_SIZE;
		}

		//allocate the fragments
		if(bytes != 0){
			int numFragments = (int) Math.ceil(((double)bytes)/ Disk.SINGLE_FRAGMENT_SIZE);
			int firstUnusedFragment = checkUnusedFragments(numFragments);
			assert(firstUnusedFragment != -1);
			int[] getSectors = fetchSectorsUsingFragments(firstUnusedFragment, numFragments);
			for(int i=0; i<getSectors.length; i++){
				blocks[l++] = getSectors[i];
				mark(getSectors[i]);
			}
		}
		return blocks;
	}

}