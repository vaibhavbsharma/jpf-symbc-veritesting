package gov.nasa.jpf.symbc;

import com.ibm.wala.util.intset.Bits;
import org.junit.Test;

public class TestVeritestingPerf extends InvokeTest {
    private static final String CLASSPATH = "+classpath=../build/tests,../lib/com.ibm.wala.util-1.4.4-SNAPSHOT.jar";
    protected static final String INSN_FACTORY = "+jvm.insn_factory.class=gov.nasa.jpf.symbc.SymbolicInstructionFactory";
    private static final String SYM_METHOD = "+symbolic.method=gov.nasa.jpf.symbc.TestVeritestingPerf.wrapNestedRegion(sym#sym)" +
            ",gov.nasa.jpf.symbc.TestVeritestingPerf.wrapSimple1(sym)" +
            ",gov.nasa.jpf.symbc.TestVeritestingPerf.wrapCountBitsSet1(sym)" +
            ",gov.nasa.jpf.symbc.TestVeritestingPerf.wrapCountBitsSet1_1(sym)" +
            ",gov.nasa.jpf.symbc.TestVeritestingPerf.wrapCountBitsSet2(sym)" +
            ",gov.nasa.jpf.symbc.TestVeritestingPerf.wrapCountBitsSet2_1(sym)" +
            ",gov.nasa.jpf.symbc.TestVeritestingPerf.wrapCountBitsSet3(sym)" +
            ",gov.nasa.jpf.symbc.TestVeritestingPerf.wrapCountBitsSet4(sym)" +
            ",gov.nasa.jpf.symbc.TestVeritestingPerf.wrapCountBitsSet5(sym)" +
            ",gov.nasa.jpf.symbc.TestVeritestingPerf.wrapCountBitsSet6(sym)" +
            ",gov.nasa.jpf.symbc.TestVeritestingPerf.wrapCountBitsSet7(sym)" +
            ",gov.nasa.jpf.symbc.TestVeritestingPerf.wrapCountBitsSet8(sym)" +
            ",gov.nasa.jpf.symbc.TestVeritestingPerf.wrapCountBitsSet9(sym)" +
            ",gov.nasa.jpf.symbc.TestVeritestingPerf.wrapArrayLoad0(sym#sym)" +
            ",gov.nasa.jpf.symbc.TestVeritestingPerf.wrapArrayLoad1(sym#sym)" +
            ",gov.nasa.jpf.symbc.TestVeritestingPerf.wrapArrayLoad2(sym#sym)";
    private static final String VM_STORAGE = "+vm.storage.class=nil";
    private static final String DEBUG = "+symbolic.debug=false";
    private static final String SOLVER = "+symbolic.dp=z3bitvector";
    private static final String MIN_INT = "+symbolic.min_int=-16";//2147483648";
    private static final String MAX_INT = "+symbolic.max_int=15";//2147483647";
    private static final String LISTENER = "+listener=gov.nasa.jpf.symbc.VeritestingListener";
    private static final String VERITESTING_MODE_4 = "+veritestingMode=4";
    private static final String VERITESTING_MODE_3 = "+veritestingMode=3";
    private static final String REPORTER_CONFIG = "+test.report.console.finished=result,statistics";
    private static final String VERITESTING_DEBUG = "+veritestingDebug = 0";



    private static final String[] FULL_INT_VM4 = {INSN_FACTORY, CLASSPATH,
            SYM_METHOD, VM_STORAGE, DEBUG, SOLVER,VERITESTING_DEBUG,
            "+symbolic.min_int=-2147483648", "+symbolic.max_int=2147483647", REPORTER_CONFIG,
            LISTENER,
            VERITESTING_MODE_4};

    private static final String[] FULL_INT_VM3 = {INSN_FACTORY, CLASSPATH,
            SYM_METHOD, VM_STORAGE, DEBUG, SOLVER,VERITESTING_DEBUG,
            "+symbolic.min_int=-2147483648", "+symbolic.max_int=2147483647", REPORTER_CONFIG,
            LISTENER,
            VERITESTING_MODE_3};

    private static final String[] SMALL_INT_VM4 = {INSN_FACTORY, CLASSPATH,
            SYM_METHOD, VM_STORAGE, DEBUG, SOLVER,VERITESTING_DEBUG,
            MIN_INT, MAX_INT, REPORTER_CONFIG,
            LISTENER,
            VERITESTING_MODE_4};

    public static void main(String[] args) {
        hideSummary = false;
        runTestsOfThisClass(args);
    }

    @Test
    public void testNestedRegion() {
        hideSummary = false;
        if (verifyNoPropertyViolation(FULL_INT_VM4)) {
            TestVeritestingPerf test = new TestVeritestingPerf();

            test.wrapNestedRegion(0, 0);
        }
    }
    void wrapNestedRegion(int x, int y) { System.out.println("running wrapNestedRegion"); nestedRegion(x); }
    int count;
    public int nestedRegion(int x) {
        //int count = 0;
        int a=8;
        if (x != 0) {
            if (x > 0) {
                count = a/8;
            } else {
                count = a/4;
            }
        } else {
            count = a/2;
        }
        assert(x != 0 && x > 0 ? count == (a/8) : true);
        assert(x != 0 && x <= 0 ? count == (a/4) : true);
        assert(x == 0 ? count == (a/2) : true);
        return count;
    }

    @Test
    public void testSimple1() {
        hideSummary = false;
        if (verifyNoPropertyViolation(FULL_INT_VM4)) {
            TestVeritestingPerf test = new TestVeritestingPerf();
            test.wrapSimple1(0);
        }
    }
    public void wrapSimple1(int x) { System.out.println("running wrapSimple1");simple1(x);}
    public void simple1(int x) {
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

    @Test
    public void testCountBitsSet1() {
        hideSummary = false;
        if (verifyNoPropertyViolation(SMALL_INT_VM4)) {
            TestVeritestingPerf test = new TestVeritestingPerf();
            test.wrapCountBitsSet1(0);
        }
    }
    public int wrapCountBitsSet1(int x) { System.out.println("running wrapCountBitsSet1"); return countBitsSet1(x);}
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

    @Test
    public void testCountBitsSet1_1() {
        hideSummary = false;
        if (verifyNoPropertyViolation(SMALL_INT_VM4)) {
            TestVeritestingPerf test = new TestVeritestingPerf();
            test.wrapCountBitsSet1_1(0);
        }
    }
    public int wrapCountBitsSet1_1(int x) { System.out.println("running wrapCountBitsSet1_1"); return countBitsSet1_1(x);}
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

    @Test
    public void testCountBitsSet2() {
        hideSummary = false;
        if (verifyNoPropertyViolation(SMALL_INT_VM4)) {
            TestVeritestingPerf test = new TestVeritestingPerf();
            test.wrapCountBitsSet2(0);
        }
    }
    public int wrapCountBitsSet2(int x) { System.out.println("running wrapCountBitsSet2"); return countBitsSet2(x);}
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

    @Test
    public void testCountBitsSet2_1() {
        hideSummary = false;
        if (verifyNoPropertyViolation(SMALL_INT_VM4)) {
            TestVeritestingPerf test = new TestVeritestingPerf();
            test.wrapCountBitsSet2_1(0);
        }
    }
    public int wrapCountBitsSet2_1(int x) { System.out.println("running wrapCountBitsSet2_1"); return countBitsSet2_1(x);}
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

    @Test
    public void testCountBitsSet3() {
        hideSummary = false;
        if (verifyNoPropertyViolation(SMALL_INT_VM4)) {
            TestVeritestingPerf test = new TestVeritestingPerf();
            test.wrapCountBitsSet3(0);
        }
    }
    public int wrapCountBitsSet3(int x) { System.out.println("running wrapCountBitsSet3"); return countBitsSet3(x);}
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

    @Test
    public void testCountBitsSet4() {
        hideSummary = false;
        if (verifyNoPropertyViolation(SMALL_INT_VM4)) {
            TestVeritestingPerf test = new TestVeritestingPerf();
            test.wrapCountBitsSet4(0);
        }
    }
    public int wrapCountBitsSet4(int x) { System.out.println("running wrapCountBitsSet4"); return countBitsSet4(x);}
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

    @Test
    public void testCountBitsSet5() {
        hideSummary = false;
        if (verifyNoPropertyViolation(SMALL_INT_VM4)) {
            TestVeritestingPerf test = new TestVeritestingPerf();
            test.wrapCountBitsSet5(0);
        }
    }
    public int wrapCountBitsSet5(int x) { System.out.println("running wrapCountBitsSet5"); return countBitsSet5(x);}
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

    @Test
    public void testCountBitsSet6() {
        hideSummary = false;
        if (verifyNoPropertyViolation(SMALL_INT_VM4)) {
            TestVeritestingPerf test = new TestVeritestingPerf();
            test.wrapCountBitsSet6(0);
        }
    }
    public int wrapCountBitsSet6(int x) { System.out.println("running wrapCountBitsSet6"); return countBitsSet6(x);}
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

    @Test
    public void testCountBitsSet7() {
        hideSummary = false;
        if (verifyNoPropertyViolation(SMALL_INT_VM4)) {
            TestVeritestingPerf test = new TestVeritestingPerf();
            test.wrapCountBitsSet7(0);
        }
    }
    public int wrapCountBitsSet7(int x) { System.out.println("running wrapCountBitsSet7"); return countBitsSet7(x);}
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

    @Test
    public void testCountBitsSet8() {
        hideSummary = false;
        if (verifyNoPropertyViolation(SMALL_INT_VM4)) {
            TestVeritestingPerf test = new TestVeritestingPerf();
            test.wrapCountBitsSet8(0);
        }
    }
    public int wrapCountBitsSet8(int x) { System.out.println("running wrapCountBitsSet8"); return countBitsSet8(x);}
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

    @Test
    public void testCountBitsSet9() {
        hideSummary = false;

        if (verifyNoPropertyViolation(FULL_INT_VM4)) {
            TestVeritestingPerf test = new TestVeritestingPerf();
            test.wrapCountBitsSet9(0);
        }
    }
    public int wrapCountBitsSet9(int x) { System.out.println("running wrapCountBitsSet9"); return countBitsSet9(x);}
    public int countBitsSet9(int x) {
        TempClass tempClass = new TempClassDerived();
        int count = 1;
        int a = 1;
        int xOrig = x;
        while (x != 0) {
            if ((x & 1) != 0) {
                count += tempClass.getOne(a);
            }
            x = x >>> 1; // logical right shift
        }
        return count;
    }

    @Test
    public void testArrayLoad0() {
        hideSummary = false;
        if (verifyNoPropertyViolation(FULL_INT_VM3)) {
            TestVeritestingPerf test = new TestVeritestingPerf();
            test.wrapArrayLoad0(0, 0);
        }
    }
    public int wrapArrayLoad0(int x, int y) {
        System.out.println("running wrapArrayLoad1");
        return arrayLoad0(x, y);
    }
    public int arrayLoad0(int index, int length) {
        int[] x = {300, 400};
        int temp = 1;
        try {
            if (length <= 0) {
                temp = 2;
            } else {
                temp = x[index] + 2;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            temp = 3;
        }
        assert length <= 0 ? temp == 2 : true;
        assert length > 0 && index == 0 ? temp == 302 : true;
        assert length > 0 && index == 1 ? temp == 402 : true;
        assert length > 0 && index != 0 && index != 1 ? temp == 3 : true;
        return temp;
    }
    @Test
    public void testArrayLoad1() {
        hideSummary = false;
        if (verifyNoPropertyViolation(FULL_INT_VM4)) {
            TestVeritestingPerf test = new TestVeritestingPerf();
            test.wrapArrayLoad1(0, 0);
        }
    }
    public int wrapArrayLoad1(int x, int y) {
        System.out.println("running wrapArrayLoad1");
        return arrayLoad1(x, y);
    }
    public int arrayLoad1(int index, int length) {
        int[] x = {300, 400};
        int temp = 1;
        try {
            if (length <= 0) {
                temp = 2;
            } else {
                temp = x[index] + 2;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            temp = 3;
        }
        assert length <= 0 ? temp == 2 : true;
        assert length > 0 && index == 0 ? temp == 302 : true;
        assert length > 0 && index == 1 ? temp == 402 : true;
        assert length > 0 && index != 0 && index != 1 ? temp == 3 : true;
        return temp;
    }

    @Test
    public void testArrayLoad2() {
        hideSummary = false;
        if (verifyNoPropertyViolation(FULL_INT_VM4)) {
            TestVeritestingPerf test = new TestVeritestingPerf();
            test.wrapArrayLoad2(0, 0);
        }
    }
    public int wrapArrayLoad2(int x, int y) {
        System.out.println("running wrapArrayLoad1");
        return arrayLoad2(x, y);
    }
    public int arrayLoad2(int index, int length) throws ArrayIndexOutOfBoundsException {
        int[] x = {300};
        int temp = 1;
        try {
            if (length > 0) {
                temp = x[index];
            } else {
                temp = 2;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("catch array out of bound");
            temp = 3;
        }
        assert ((length <= 0) ? (temp == 2) : true);
        assert (length > 0) && (index == 0)? (temp == 300 ) : true;
        assert (length > 0) && (index != 0)? (temp == 3 ) : true;
        return 0;
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
