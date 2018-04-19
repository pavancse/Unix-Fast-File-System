package jnachos.filesystem;

import jnachos.kern.JNachos;
import jnachos.machine.JavaSys;

public class TestFS {

    public static void test(){
        String evenFileName = "even";
        String oddFileName = "odd";

        OpenFile even = createAndOpenFile(evenFileName);
        OpenFile odd = createAndOpenFile(oddFileName);
        printHeader();

        writeIntoFile(even, 0, 64, evenFileName);
        writeIntoFile(odd, 0, 64, oddFileName);
        printInfo(even, odd);

        writeIntoFile(even, 64, 150, evenFileName);
        writeIntoFile(odd, 64, 150, oddFileName);
        printInfo(even, odd);

        writeIntoFile(even, 150, 210, evenFileName);
        printInfo(even, odd);

        ((NachosOpenFile)even).writeHeader();
        ((NachosOpenFile)odd).writeHeader();
        NachosFileSystem.mFreeMap.writeBack(new NachosOpenFile(NachosFileSystem.FreeMapSector));

        printFileData(even, 210, evenFileName);
        printFileData(odd, 150, oddFileName);
    }

    public static void writeIntoFile(OpenFile file, int startPos, int endPos, String dataVal){
        if(file != null){
            for(int i=startPos; i<endPos; i++){
                byte[] bytes = new byte[4];
                if("even".equals(dataVal))
                    JavaSys.intToBytes(2*i, bytes, 0);
                else
                    JavaSys.intToBytes(2*i+1, bytes, 0);
                file.writeAt(bytes, 4, 4*i);
            }
        }
    }


    public static void printHeader(){
        System.out.println();
        System.out.println("---------------Printing BitMap headers---------------");
        new NachosOpenFile(NachosFileSystem.FreeMapSector).printHeader();
    }

    public static void printInfo(OpenFile even, OpenFile odd){
        System.out.println("Even file header");
        ((NachosOpenFile) even).printHeader();
        System.out.println("Odd file header");
        ((NachosOpenFile) odd).printHeader();
        NachosFileSystem.mFreeMap.print();
    }

    public static OpenFile createAndOpenFile(String fileName){
        FileSystem fs = JNachos.mFileSystem;

        if(!fs.create(fileName, 40)){
            System.out.println("File " + fileName +  " exists");
        }

        return fs.open(fileName);
    }

    public static void printFileData(OpenFile file, int endPos, String fileName){
        System.out.println();
        System.out.println("---------------Printing "+ fileName +" file contents---------------");
        for(int i=0; i<endPos; i++){
            byte[] bytes = new byte[4];
            file.readAt(bytes, 4, 4*i);
            System.out.print(JavaSys.bytesToInt(bytes, 0) + " ");
        }
    }

}

