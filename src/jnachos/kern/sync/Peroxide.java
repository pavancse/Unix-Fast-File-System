package jnachos.kern.sync;

import jnachos.kern.*;
import java.io.*;

public class Peroxide {


    /** Semaphore H */
    static Semaphore H = new Semaphore("SemH", 0);

    /**	*/
    static Semaphore O = new Semaphore("SemO", 0);

    /**	*/
    static Semaphore wait = new Semaphore("wait", 0);

    /**	*/
    static Semaphore mutex = new Semaphore("MUTEX", 1);

    /**	*/
    static Semaphore mutex1 = new Semaphore("MUTEX1", 1);

    /**	*/
    static long count = 0, oCount = 0;

    /**	*/
    static int Hcount, Ocount, nH, nO;

    /**	*/
    class HyAtom implements VoidFunctionPtr {
        int mID;

        /**
         *
         */
        public HyAtom(int id) {
            mID = id;
        }

        /**
         * oAtom will call oReady. When this atom is used, do continuous
         * "Yielding" - preserving resource
         */
        public void call(Object pDummy) {
            mutex.P();
            if (count % 2 == 0) // first H atom
            {
                count++; // increment counter for the first H
                mutex.V(); // Critical section ended
                H.P(); // Waiting for the second H atom
            } else // second H atom
            {
                count++; // increment count for next first H
                mutex.V(); // Critical section ended
                H.V(); // wake up the first H atom
                O.V(); // wake up O atom
            }

            wait.P(); // wait for water message done

            System.out.println("H atom #" + mID + " used in making peroxide.");
        }
    }

    /**	*/
    class OxyAtom implements VoidFunctionPtr {
        int mID;

        /**
         * oAtom will call oReady. When this atom is used, do continuous
         * "Yielding" - preserving resource
         */
        public OxyAtom(int id) {
            mID = id;
        }

        /**
         * oAtom will call oReady. When this atom is used, do continuous
         * "Yielding" - preserving resource
         */
        public void call(Object pDummy) {
            O.P(); // waiting for two H atoms.
            mutex1.P();
            if(oCount % 2 == 0){
                oCount++;
                mutex1.V();
                O.V();
                wait.P();
            }else {
                oCount++;
                mutex1.V();
                makePeroxide();
                wait.V(); // wake up H atoms and they will return to
                wait.V(); // resource pool
                wait.V(); // And a oxygen atom too
                mutex1.P();
                Hcount = Hcount - 2;
                Ocount = Ocount - 2;
                System.out.println("Numbers Left: H Atoms: " + Hcount + ", O Atoms: " + Ocount);
                System.out.println("Numbers Used: H Atoms: " + (nH - Hcount) + ", O Atoms: " + (nO - Ocount));
                mutex1.V();
            }
            System.out.println("O atom #" + mID + " used in making peroxide.");
        }
    }

    /**
     * oAtom will call oReady. When this atom is used, do continuous "Yielding"
     * - preserving resource
     */
    public static void makePeroxide() {
        System.out.println("** Peroxide; made! Splash!! **");
    }

    /**
     * oAtom will call oReady. When this atom is used, do continuous "Yielding"
     * - preserving resource
     */
    public Peroxide() {
        runPeroxide();
    }

    /**
     *
     */
    public void runPeroxide() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("Number of H atoms ? ");
            nH = (new Integer(reader.readLine())).intValue();
            System.out.println("Number of O atoms ? ");
            nO = (new Integer(reader.readLine())).intValue();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Hcount = nH;
        Ocount = nO;

        for (int i = 0; i < nH; i++) {
            HyAtom atom = new HyAtom(i);
            (new NachosProcess(new String("hAtom" + i))).fork(atom, null);
        }

        for (int j = 0; j < nO; j++) {
            OxyAtom atom = new OxyAtom(j);
            (new NachosProcess(new String("oAtom" + j))).fork(atom, null);
        }
    }
}
