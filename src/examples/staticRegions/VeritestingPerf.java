package staticRegions;/*
 * example to demonstrate veritesting
*/


import com.ibm.wala.util.intset.Bits;
import gov.nasa.jpf.symbc.Debug;
import sun.reflect.annotation.ExceptionProxy;

import java.util.ArrayList;

import static com.ibm.wala.util.math.Logs.isPowerOf2;

public class VeritestingPerf {

    public int count = 0;

    public static void main(String[] args) {
        //(new VeritestingPerf()).cfgTest(1);
        (new VeritestingPerf()).countBitsSet(1);

        // (new VeritestingPerf()).nestedRegion(1);
        //(new VeritestingPerf()).testSimple1(1);
        //(new VeritestingPerf()).testDynObject(false, 1);
                //int x[] = {1, 2};
                //(new VeritestingPerf()).inRangeloadArrayTC(22, 2);
                //(new VeritestingPerf()).innerCatchOutRangeloadArrayTC(22, 2);
                //(new VeritestingPerf()).outRangeloadArrayTC( 22, 2);
                // (new VeritestingPerf()).catchOutRangeloadArrayTC(22, 2);
                //(new VeritestingPerf()).boundedOutRangeloadArrayTC(22, 2);
                //(new VeritestingPerf()).ifNull("Test");
                //(new VeritestingPerf()).foo(true);
                //(new VeritestingPerf()).segmantTest(22, 2);

                // System.out.println("!!!!!!!!!!!!!!! Start Testing! ");
                //  (new VeritestingPerf()).testMe2(0,true);
        //(new VeritestingPerf()).readAfterWriteTest(1);
        //(new VeritestingPerf()).testSimple(1);
        //(new VeritestingPerf()).testNested(1);
        //(new VeritestingPerf()).testSimple1(1);
        //(new VeritestingPerf()).simpleRegion(1);
        //(new VeritestingPerf()).fieldWriteTestBranch2(1);
        //(new VeritestingPerf()).fieldWriteTestBranch1(1);
        //(new VeritestingPerf()).testSimple2(1);
        //(new VeritestingPerf()).testSimpleFail(1);
        //(new VeritestingPerf()).nestedRegion(1);
        //(new VeritestingPerf()).nestedRegion1(true, true);
        int x[] = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
//        (new VeritestingPerf()).inRangeloadArrayTC( 22, 10);
//        (new VeritestingPerf()).outRangeloadArrayTC( 2, 10);
        //       (new VeritestingPerf()).outRangeConcreteTC( 20, 10);
        //(new VeritestingPerf()).testMe5(x, 1);
        //(new VeritestingPerf()).testMe6(x, 12, -1, 1);
        //(new VeritestingPerf()).testMe4(x, 12, -1, 1);
        //       (new VeritestingPerf()).arrayTest(x, 6);
        //(new VeritestingPerf()).checkOperator();
//        ArrayList<Integer> list = new ArrayList<>();
//        list.add(Debug.makeSymbolicInteger("a1"));
//        list.add(Debug.makeSymbolicInteger("a2"));
//        (new VeritestingPerf()).countArrayList(list);
    }

    public static void testMe2(int x, boolean b) {
        System.out.println("!!!!!!!!!!!!!!! First step! ");
        //System.out.println("x = " + x);
        int[] y = {1, 2};
        if (b) {
            x++;
            System.out.println("Program then branch");
        } else {
            x--;
            System.out.println("Program else branch");
        }
        x++;
    }

    private void testNested(int x) {
        testNestedMiddle(x);
        assert(x != 0 && x > 0 ? count == 3 : true);
        assert(x != 0 && x <= 0 ? count == 4 : true);
        assert(x ==0 ? count == 5 : true);
    }

    private int testNestedMiddle(int x) {
        int retval = 0;
        retval += nestedRegion(x);
        return retval;
    }

    public int nestedRegion(int x) {
        int count = 0;
        if (x != 0) {
            if (x > 0) { count = 3; } else { count = 4;  }
        } else { count = 5; }
//        assert(x != 0 && x > 0 ? count == 3 : true);
//        assert(x != 0 && x <= 0 ? count == 4 : true);
//        assert(x ==0 ? count == 5 : true);
        return count;
    }

    public int nestedRegion1(boolean x, boolean y) {
        int a = 0;
        if (y) {
            a = 1;
            if (x) {
                a = 3;
            } else {
                a = 2;
            }
        }
        return a;
    }


    // MWW:
    // Here is the problem.  If I uncomment 'count', then the program works correctly.
    // There is a problem with nested fields and regions right now.
    public int simpleRegion(int x) {
        //count = 4;
        if (x > 0) { count = 1; count = 3; }
        else { count = 2; count = 4; }
        return count;
    }

    // this fails.
    public void testSimple(int x) {
        count = simpleRegion(x);
        System.out.println("x: " + x + "; count: " + count);
        assert(x > 0 ? count == 3 : true);
        assert(x <= 0 ? count == 4 : true);
    }

    // MWW fails incorrectly: 4/8/2018
    // If I uncomment count, it works correctly.
    public void testSimple1(int x) {
        //int count;
        System.out.println("Executing success case!");
        if (x != 0) {
            count = 3;
        } else {
            count = 4;
        }

        assert(x != 0 ? count == 3 : true);
        assert(x == 0 ? count == 4 : true);
    }

    // MWW checks correctly: 4/8/2018
    public void testSimpleFail(int x) {
        System.out.println("Executing fail case!");
        int count;
        if (x > 0) {
            count = 3;
        } else {
            count = 4;
        }
        assert(x != 0 ? count == 3 : true);
        assert(x == 0 ? count == 4 : true);
    }

    // MWW checks correctly: 4/8/2018
    public void testSimple2(int x) {
        int count;
        if (x != 0) {
            if (x > 0) { count = 3; } else { count = 4;  }
        } else { count = 5; }

        assert(x > 0 ? count == 3 : true);
        assert(x < 0 ? count == 4 : true);
        assert(x == 0 ? count == 5 : true);
    }


    public int countBitsSetSimple(int x) {
        //int count = 0;
        while (x != 0) {
            int lowbit = x & 1;
            int flag;// = 0;
            if (lowbit != 0) flag = 1;
            else flag = 0;
            count += flag;
            x = x >>> 1; // logical right shift
        }
        return count;
    }

    public int countBitsSet(int x) {
        TempClass tempClass = new TempClassDerived();
        count = 0;
        int a = 1;
        int xOrig = x;
        //TempClass tempClass = new TempClass();
        while (x != 0) {
            if ((x & 1) != 0) {
                // nested field access test case 1
                //count += tempClass.tempClass2.tempInt2;
                // nested field access test case 2
                //TempClass2 tempClass2 = tempClass.tempClass2;
                //tempClass2.tempInt2 += count;
                // Test case 3: method summary test + higher order region test

                count += tempClass.getOne(0);
                //TempClassDerived.myInt = 1; //creates r/w interference with tempClass.getOne's method summary
                // Test case 4: use this to test dynamic field access
                //count += tempClass.myInt;
                // Test case 5: testing read-after-write in a simple region
                //count += 1;
//                a += count;
//                count += 2;
                // Test case 6
                //count += tempClass.nestedRegion(a);
            }
            x = x >>> 1; // logical right shift
        }
        assert(xOrig == 0 || TempClassDerived.tempInt == 6);
        //assert(Bits.populationCount(xOrig) == count);
        System.out.println("TempClassDerived.tempInt = " + TempClassDerived.tempInt);
        System.out.println("TempClass.tempInt = " + TempClass.tempInt);
        return count;
    }

    public int readAfterWriteTest(int x) {
        TempClass tempClass1 = new TempClassDerived();
        TempClass tempClass2 = new TempClassDerived();
        count = 0;
        int a = 1;
        int xOrig = x;
        //TempClass tempClass = new TempClass();
        while (x != 0) {
            if ((x & 1) != 0) {
                tempClass1.tempInt += 1;
                a = tempClass2.tempInt; // should not cause a read after write
                //tempClass1.tempInt += 1;
                count += 1;
            }
            x = x >>> 1; // logical right shift
        }
        assert(xOrig == 0 ? count == 0 : true);
        assert(isPowerOf2(xOrig) ? count == 1 : true);
        System.out.println("a = " + a);
        return count;
    }

    public int fieldWriteTestBranch2(int x) {
        if(x != 0) count = 1;
        else count = 2;
        return count;
    }

    public int fieldWriteTestBranch1(int x) {
        if(x != 0) count = 1;
        return count;
    }

    //testing inRangeArrayLoad for symbolic & concrete index
    public int inRangeloadArrayTC(int index, int length) {
        int[] x = {300, 400};
        int temp = 1;
        if (length > 0) {
            //     System.out.println("executing then branch");
            temp = 2;
        } else {
            // System.out.println("executing else branch");
            temp = x[index];
        }
        System.out.println("now temp =" + temp);
        return temp;
    }

    //testing outRangeArrayLoad for symbolic index
    public int outRangeloadArrayTC(int index, int length) throws ArrayIndexOutOfBoundsException {
        int[] x = {300};
        int temp = 2;
        int y = 1;
        try {
            if (length > 0) {
                temp = x[index];
            } else {
                temp = 1;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("catch array out of bound");
        }

        if (temp == 1)
            System.out.println("then branch");
        else
            System.out.println("else branch");
        return y;
    }

    public int catchOutRangeloadArrayTC(int index, int length) throws ArrayIndexOutOfBoundsException {
        int[] x = {1, 2};
        int temp = 1;
        int y = 1;
        try {
            if (length > 0) {
                temp = x[index];
            } else {
                temp = 1;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("catch array out of bound");
        }
        return temp;
    }


    public int innerCatchOutRangeloadArrayTC(int index, int length) throws ArrayIndexOutOfBoundsException {
        int[] x = {300};
        int temp = 1;
        int y = 1;
        if (length > 0) {
            try {
                temp = x[index];
            } catch (ArrayIndexOutOfBoundsException e) {
                System.out.println("catch array is out of bound");
            }
        } else {
            temp = 2;
        }
        return temp;
    }

    public int boundedOutRangeloadArrayTC(int index, int length) throws ArrayIndexOutOfBoundsException {
        int[] x = {300};
        int temp = 0;
        int y = 2;
        if (length > 0) {
            try {
                temp = x[index];
            } catch (ArrayIndexOutOfBoundsException e) {
                System.out.println("catch array out of bound");
            }
        } else {
            temp = 2;
        }

        if (temp != 0)
            y = 1;
        else
            y = 0;
        return y;
    }



    int foo(boolean x) {
        int a;
        if (x) {
            if (x) {
                a = 3;
            } else { a = 4; }
        } else { a = 5; }
        return a;
    }


    public int segmantTest(int index, int length) throws ArrayIndexOutOfBoundsException {
        int[] x = {300};
        int temp = 1;
        int y = 1;
        if (length >= 0) {
            if (length < 20)
                temp = x[index];
        } else {
            temp = 2;
        }
        return temp;
    }


    public int ifNull(String x){
        if(x == null){
            System.out.println("x is null");
            return 0;
        }
        else{
            System.out.println("x is not null");
            return 1;
        }

    }

    public class Silly {
        public int f = 10;
    };

    void testDynObject(boolean cond, int index) {
        Silly [] arrayOfSilly = { new Silly(), new Silly() } ;
        int l;
        if (cond) {
            l = arrayOfSilly[index].f;
        }
    }

    public int countArrayList(ArrayList<Integer> x) {
        // x = ArrayList of symbolic integers with
        // concrete length
        int sum = 0;
        for (int i = 0; i < x.size(); i++) {
            // Begin region for static unrolling
            if (x.get(i) < 0) sum += -1;
            else if (x.get(i) > 0) sum += 1;
            // End region for static unrolling
        }
        if (sum < 0) System.out.println("neg");
        else if (sum > 0) System.out.println("pos");
        else System.out.println("bug");
        return sum;
    }

    static int a, b, c, d, e, f;

    public int checkOperator() {
        int ret = -1;
        a = Debug.makeSymbolicInteger("a");
        b = Debug.makeSymbolicInteger("b");
        if (a < b) ret = 1;
        else ret = 0;
        return ret;
    }

    public int cfgTest(int x) {
        int ret = 0;
        while (x >= 0) {
            x--;
            if (x == 0) ret = 1;
            else ret = -1;
            x = x - 1;
        }
        return ret;
    }

    public void testMe4(int[] x, int len, int minusOne, int plusOne) {
        int sum = 0; //Debug.makeSymbolicInteger("sum");
        int temp = 2;
        for (int i = 0; i < len; i++)
            x[i] = Debug.makeSymbolicInteger("x" + i);
        for (int i = 0; i < len; i++) {
            if (x[i] < 0) {
                int temp2 = x[0];
                temp = minusOne;
                sum += temp;
            } else {
                temp = plusOne;
                sum += temp;
            }
        }
        if (sum < 0) System.out.println("neg");
        else if (sum > 0) System.out.println("pos");
        else System.out.println("bug");
    }

    public void testMe5(int[] x, int len) {
        int sum = 0; //Debug.makeSymbolicInteger("sum");
        for (int i = 0; i < len; i++)
            x[i] = Debug.makeSymbolicInteger("x" + i);
        for (int i = 0; i < len; i++) {
            int val = x[i];
            if (val < 0) sum += -1;
            else if (val > 0) sum += 1;
            else sum += 0;
        }
        if (sum < 0) System.out.println("neg");
        else if (sum > 0) System.out.println("pos");
        else System.out.println("bug");
    }


    public int testMe6(int[] x, int len, int minusOne, int plusOne) {
//        int sum = 0; //Debug.makeSymbolicInteger("sum");
//        int temp = 2;
//        for(int i=0; i < len; i++)
//            x[i] = Debug.makeSymbolicInteger("x"+i);
//        int temp2 =0;
//        for (int i = 0; i < len; i++) {
//            if (len < 0) {
//                temp2 = x[minusOne];
//                temp = minusOne;
//                sum += temp;
//            }
//            else {
//                x[0] = 0;
//                temp = plusOne;
//                sum += temp;
//            }
//        }
//        if (sum < 0) {System.out.println("neg"); temp2=x[minusOne];}
//        else if (sum > 0) System.out.println("pos");
//        else System.out.println("bug");
        return 1;
    }

    public void arrayTest(int[] x, int len) {
        for (int i = 0; i < len; i++)
            x[i] = Debug.makeSymbolicInteger("x" + i);
        for (int i = 0; i < len; i++) {
            if (x[i] < 0) x[i] *= -1;
            else x[i] *= 2;
        }
    }
};

class TempClassDerived extends TempClass {

    public static int tempInt = 2; //change this to 2 to test read after write on a class field inside a Veritesting region

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
        tempInt = a +1; //LOCAL_INPUT,  FIELD_OUTPUT holes
        a = tempInt + 2; //LOCAL_OUTPUT, FIELD_INPUT holes
        tempInt = a+ 3; //LOCAL_INPUT,  FIELD_INPUT holes
        //tempInt = 6 + a;

        //VeritestingPerf.count += 1;
        //return tempInt;
        //return nestedRegion(myInt);
        return getTempInt(tempInt);
        //return 1;
    }

    public int nestedRegion(int x) {
        if (x != 0) {
            if (x != 0) { tempInt = 3; } else { tempInt = 4;  }
        } else { tempInt = 5; }
        return tempInt + x;
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
        tempInt = a; return tempInt;
    }

    TempClass2 tempClass2;

    public int nestedRegion(int a) { return 0; }
}

class TempClass2 {

    public int tempInt2 = 1;

    public int tempMethod() { return 0;}
}




/*
  public void collatz(int n) {
    int inter;
    while(n != 1) {
      if( (n & 1) == 1) {
        inter = 3*n + 1;
      } else {
        inter = (n >> 1);
      }
      n = inter;
    }
  }

  public void testMe4 (int[] x, int len) {
    int sum = 0; //Debug.makeSymbolicInteger("sum");
    // for(int i=0; i < len; i++) 
    //   x[i] = Debug.makeSymbolicInteger("x"+i);
    for (int i=0; i < len; i++) {
      if (x[i] < 0) sum += -1;
      else if (x[i] > 0) sum += 1;
    }
    if (sum < 0) System.out.println("neg");
    else if (sum > 0) System.out.println("pos");
    else System.out.println("bug");
  }

  public int gcd(int a, int b) {
    while( a != b ) {
      if ( a > b ) a = a - b;
      else b = b - a;
    }
    return a;
  }



  public int oneBranch(int x) {
    int sum=0;
    if(x < 0) sum += -1;
    else sum += 1;
	return sum;
  }

}*/
