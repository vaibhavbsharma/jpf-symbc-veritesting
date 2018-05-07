package gov.nasa.jpf.symbc;

import com.ibm.wala.util.intset.Bits;
import gov.nasa.jpf.util.test.TestJPF;
import org.junit.Test;

public class TestVeritestingPerf extends InvokeTest {
    private static final String CLASSPATH = "+classpath=../build/tests,../lib/com.ibm.wala.util-1.4.4-SNAPSHOT.jar";
    protected static final String INSN_FACTORY = "+jvm.insn_factory.class=gov.nasa.jpf.symbc.SymbolicInstructionFactory";
    private static final String SYM_METHOD = "+symbolic.method=gov.nasa.jpf.symbc.TestVeritestingPerf.testVeritestingPerf(sym#sym)";
    private static final String VM_STORAGE = "+vm.storage.class=nil";
    private static final String DEBUG = "+symbolic.debug=false";
    private static final String SOLVER = "+symbolic.dp=z3bitvector";
    private static final String MIN_INT = "+symbolic.min_int=-16";//2147483648";
    private static final String MAX_INT = "+symbolic.max_int=15";//2147483647";
    private static final String LISTENER = "+listener=gov.nasa.jpf.symbc.VeritestingListener";
    private static final String VERITESTING_MODE = "+veritestingMode=3";
    private static final String REPORTER_CONFIG = "+test.report.console.finished=result,statistics";



    private static final String[] JPF_ARGS = {INSN_FACTORY, CLASSPATH,
            SYM_METHOD, VM_STORAGE, DEBUG, SOLVER,
            MIN_INT, MAX_INT, REPORTER_CONFIG,
            LISTENER,
            VERITESTING_MODE};

    public static void main(String[] args) {
        hideSummary = false;
        runTestsOfThisClass(args);
    }

    @Test
    public void mainTest() {
        hideSummary = false;
        if (verifyNoPropertyViolation(JPF_ARGS)) {
            TestVeritestingPerf test = new TestVeritestingPerf();

            test.testVeritestingPerf(0, 0);
        }
    }

    void testVeritestingPerf(int x, int y) {
        System.out.println("Got here!");

        // Add real tests here!
//        testSimple1(x);
//        countBitsSet1(x);
//        countBitsSet1_1(x);
//        countBitsSet2(x);
//        countBitsSet2_1(x);
//        countBitsSet3(x);
//        countBitsSet4(x);
//        countBitsSet5(x);
//        countBitsSet6(x);
//        countBitsSet7(x);
        countBitsSet8(x);
    }

    int count;

    public void testSimple1(int x) {
        // int count;
        System.out.println("Executing success case!");
        if (x != 0) {
            count = 3;
        } else {
            count = 4;
        }
        assert(x != 0 ? count == 3 : true);
        assert(x == 0 ? count == 4 : true);
    }


    public int countBitsSet1(int x) {
        int count = 1;
        int xOrig = x;
        while (x != 0) {
            if ((x & 1) != 0) {
                count += 1;
            }
            x = x >>> 1; // logical right shift
        }
        assert (Bits.populationCount(xOrig) == count-1);
        return count;
    }

    public int countBitsSet1_1(int x) {
        count = 1;
        int xOrig = x;
        while (x != 0) {
            if ((x & 1) != 0) {
                count += 1;
            }
            x = x >>> 1; // logical right shift
        }
        assert (Bits.populationCount(xOrig) == count-1);
        return count;
    }

    public int countBitsSet2(int x) {
        TempClass tempClass = new TempClassDerived();
        int count = 0;
        int xOrig = x;
        while (x != 0) {
            if ((x & 1) != 0) {
                // nested field access test case
                count += tempClass.tempClass2.tempInt2;
            }
            x = x >>> 1; // logical right shift
        }
        assert (Bits.populationCount(xOrig) == count);
        return count;
    }

    public int countBitsSet2_1(int x) {
        TempClass tempClass = new TempClassDerived();
        count = 0;
        int xOrig = x;
        while (x != 0) {
            if ((x & 1) != 0) {
                // nested field access test case
                count += tempClass.tempClass2.tempInt2;
            }
            x = x >>> 1; // logical right shift
        }
        assert (Bits.populationCount(xOrig) == count);
        return count;
    }

    public int countBitsSet3(int x) {
        TempClass tempClass = new TempClassDerived();
        int count = 1;
        int xOrig = x;
        while (x != 0) {
            if ((x & 1) != 0) {
                // nested field access test case
                TempClass2 tempClass2 = tempClass.tempClass2;
                tempClass2.tempInt2 += 1;
            }
            x = x >>> 1; // logical right shift
        }
        assert (Bits.populationCount(xOrig) == tempClass.tempClass2.tempInt2-1);
        return count;
    }

    public int countBitsSet4(int x) {
        TempClass tempClass = new TempClassDerived();
        int count = 0;
        int xOrig = x;
        while (x != 0) {
            if ((x & 1) != 0) {
                // method summary test + higher order region test
                count += tempClass.getOne(0);
            }
            x = x >>> 1; // logical right shift
        }
        assert (Bits.populationCount(xOrig) == count);
        return count;
    }

    public int countBitsSet5(int x) {
        TempClass tempClass = new TempClassDerived();
        count = 0;
        int xOrig = x;
        while (x != 0) {
            if ((x & 1) != 0) {
                // method summary test + higher order region test
                count += tempClass.getOne(0);
            }
            x = x >>> 1; // logical right shift
        }
        assert (Bits.populationCount(xOrig) == count);
        assert (xOrig == 0 || TempClassDerived.tempInt == 6);
        return count;
    }

    public int countBitsSet6(int x) {
        TempClass tempClass = new TempClassDerived();
        count = 0;
        int xOrig = x;
        while (x != 0) {
            if ((x & 1) != 0) {
                // method summary test + higher order region test
                count += tempClass.getOne(0);
                TempClassDerived.tempInt = 1; //creates r/w interference with tempClass.getOne's method summary
            }
            x = x >>> 1; // logical right shift
        }
        assert (Bits.populationCount(xOrig) == count);
        return count;
    }

    public int countBitsSet7(int x) {
        count = 0;
        int a = 1;
        int xOrig = x;
        while (x != 0) {
            if ((x & 1) != 0) {
                // testing read-after-write in a simple region
                count += 1;
                a += count;
                count += 2;
            }
            x = x >>> 1; // logical right shift
        }
        assert (Bits.populationCount(xOrig)*3 == count);
        return count;
    }

    public int countBitsSet8(int x) {
        TempClass tempClass = new TempClassDerived();
        int count = 1;
        int a = 1;
        int xOrig = x;
        while (x != 0) {
            if ((x & 1) != 0) {
                count += tempClass.nestedRegion(a);
            }
            x = x >>> 1; // logical right shift
        }
        assert (Bits.populationCount(xOrig)*3 == count-1);
        return count;
    }
}


class TempClassDerived extends TempClass {

    public static int tempInt = 2;

    public static int myInt = 1;

    public int getAnotherAnotherTempInt(int a) {
        //TempClass2 t = new TempClass2();
        //t.tempMethod();
        return 1;
    }

    public int getAnotherTempInt(int a) {
        //TempClass2 t = new TempClass2();
        //t.tempMethod();
        //return tempInt;
        return getAnotherAnotherTempInt(TempClassDerived.myInt);
    }

    public int getTempInt(int a) {
        //TempClass2 t = new TempClass2();
        //t.tempMethod();
        //return tempInt;
        return getAnotherTempInt(TempClassDerived.myInt);
    }

    public int getOne(int a) {
        //read-after-write test on tempInt field
        tempInt = a + 1; //LOCAL_INPUT,  FIELD_OUTPUT holes
        a = tempInt + 2; //LOCAL_OUTPUT, FIELD_INPUT holes
        tempInt = a + 3; //LOCAL_INPUT,  FIELD_INPUT holes
        //tempInt = 6 + a;

        //VeritestingPerf.count += 1;
        //return tempInt;
        //return nestedRegion(myInt);
        return getTempInt(tempInt);
        //return 1;
    }

    public int nestedRegion(int x) {
        if (x != 0) {
            if (x != 0) {
                tempInt = 3;
            } else {
                tempInt = 4;
            }
        } else {
            tempInt = 5;
        }
        return tempInt;
    }

}

class TempClass {

    public static int tempInt = 1;

    public static int myInt = 1;

    public TempClass() {
        this.tempClass2 = new TempClass2();
    }

    public int getTempInt() {
        return tempInt;
    }

    public int getOne(int a) {
        System.out.println("called TempClass.getOne");
        tempInt = a;
        return tempInt;
    }

    TempClass2 tempClass2;

    public int nestedRegion(int a) {
        return 0;
    }
}

class TempClass2 {

    public int tempInt2 = 1;

    public int tempMethod() {
        return 0;
    }
}


class TempClass3 {

    public boolean valid;

    public TempClass3(boolean valid) {
        this.valid = valid;
    }
}
