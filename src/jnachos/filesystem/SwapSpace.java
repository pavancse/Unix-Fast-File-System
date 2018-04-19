package jnachos.filesystem;

import jnachos.kern.Debug;
import jnachos.kern.JNachos;

public class SwapSpace  {

    static final String basicFilename = "processSwapFile";

    //Create a swap file
    public String createSpace(int spaceSize){
        long createTime = System.currentTimeMillis();
        String filename = basicFilename + createTime;
        boolean status = JNachos.mFileSystem.create(filename,spaceSize);

        if(status){
            //debug it is success full
            Debug.print('a', "Swap space file " + filename + " created");
            return filename;

        }else{
            System.out.println("Filed to create swap file");
        }
        return "";
    }

    //Open the swap file to write an dread
    public OpenFile open(String filename) {
        OpenFile processSpace = JNachos.mFileSystem.open(filename);
        return processSpace;
    }
}
