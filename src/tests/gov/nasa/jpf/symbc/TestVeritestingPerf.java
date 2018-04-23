package gov.nasa.jpf.symbc;

import gov.nasa.jpf.util.test.TestJPF;
import org.junit.Test;

public class TestVeritestingPerf extends InvokeTest {
    protected static final String INSN_FACTORY = "+jvm.insn_factory.class=gov.nasa.jpf.symbc.SymbolicInstructionFactory";
    private static final String SYM_METHOD = "+symbolic.method=gov.nasa.jpf.symbc.TestVeritestingPerf.testVeritestingPerf(sym#sym)";
    private static final String VM_STORAGE = "+vm.storage.class=nil";
    private static final String DEBUG = "+symbolic.debug=false";
    private static final String SOLVER = "+symbolic.dp=z3bitvector";
    private static final String MIN_INT = "+symbolic.min_int=-2147483648";
    private static final String MAX_INT = "+symbolic.max_int=2147483647";
    private static final String LISTENER = "+listener=gov.nasa.jpf.symbc.VeritestingListener";
    private static final String VERITESTING_MODE = "+veritestingMode=1";


    private static final String[] JPF_ARGS = {INSN_FACTORY, SYM_METHOD, VM_STORAGE, DEBUG, SOLVER,
        MIN_INT, MAX_INT, LISTENER, VERITESTING_MODE}; /* LISTENER, VERITESTING_MODE}; */

    public static void main(String[] args) {
        runTestsOfThisClass(args);
    }

    @Test
    public void mainTest() {
        if (verifyNoPropertyViolation(JPF_ARGS)) {
            TestVeritestingPerf test = new TestVeritestingPerf();

            test.testVeritestingPerf(0, 0);
        }
    }

    int count;

    // MWW fails incorrectly: 4/8/2018
    // If I uncomment count, it works correctly.
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

    void testVeritestingPerf(int x, int y) {
        System.out.println("Got here!");

        // Add real tests here!
        testSimple1(x);
    }

}
