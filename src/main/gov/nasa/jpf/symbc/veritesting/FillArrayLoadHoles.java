package gov.nasa.jpf.symbc.veritesting;

//import com.ibm.wala.types.TypeReference;
import gov.nasa.jpf.vm.ArrayFields;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.ThreadInfo;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.Operation;

import java.util.HashMap;

public class FillArrayLoadHoles {
    private Expression additionalAST;
    // This needs to store sufficient information in the holeHashMap so that I can discover it for the exception computation.
    private ThreadInfo ti;
    private HashMap<Expression, Expression> holeHashMap, retHoleHashMap;
    public FillArrayLoadHoles(ThreadInfo ti, HashMap<Expression, Expression> holeHashMap,
                              HashMap<Expression, Expression> retHoleHashMap, Expression additionalAST) {
        this.ti = ti;
        this.holeHashMap = holeHashMap;
        this.retHoleHashMap = retHoleHashMap;
        this.additionalAST = additionalAST;
    }

    public FillArrayOutput invoke() throws StaticRegionException {
        for (HashMap.Entry<Expression, Expression> entry : holeHashMap.entrySet()) {
            Expression key = entry.getKey(), finalValueGreen;
            assert (key instanceof HoleExpression);
            HoleExpression keyHoleExpression = (HoleExpression) key;
            switch (keyHoleExpression.getHoleType()) {
                case ARRAYLOAD:
                    HoleExpression.ArrayInfo arrayInfo = keyHoleExpression.getArrayInfo();
                    HoleExpression arrayRefHole = ((HoleExpression) arrayInfo.arrayRefHole);
                    assert(retHoleHashMap.containsKey(arrayRefHole));
                    assert(retHoleHashMap.containsKey(arrayInfo.arrayIndexHole));
                    Expression arrayRefExpression = retHoleHashMap.get(arrayRefHole);
                    if (!( arrayRefExpression instanceof IntConstant))
                        throw new StaticRegionException("cannot handle symbolic array reference expression: " + arrayRefExpression);
                    int arrayRef = ((IntConstant) arrayRefExpression).getValue();

                    Expression indexExpression = retHoleHashMap.get(arrayInfo.arrayIndexHole);

                    ElementInfo ei = ti.getElementInfo(arrayRef);
                    int arrayLength = ((ArrayFields) ei.getFields()).arrayLength();
                    arrayInfo.setLength(arrayLength);
//                    TypeReference arrayType = arrayInfo.arrayType;
                    if (indexExpression instanceof IntConstant) //attribute is null so index is concrete
                    {
                        int indexVal = ((IntConstant) indexExpression).getValue();
                        if (indexVal < 0 || indexVal >= arrayLength) //checking concrete index is out of bound
                            return new FillArrayOutput(false, null);
                        int value = ei.getIntElement(indexVal);
                        finalValueGreen = new IntConstant(value);
                    } else { //index is symbolic - fun starts here :)
                        Expression lhsExpr = retHoleHashMap.get(arrayInfo.val);
                        //Expression arrayLoadResult = new IntVariable("arrayLoadResult", Integer.MIN_VALUE, Integer.MAX_VALUE);
                        for (int i = 0; i < arrayLength; i++) { //constructing the symbolic index constraint
                            Expression exp1 = new Operation(Operation.Operator.EQ, indexExpression, new IntConstant(i));
                            int value = ei.getIntElement(i);
                            Expression exp2 = new Operation(Operation.Operator.EQ, lhsExpr, new IntConstant(value)); //loadArrayElement(ei, arrayType)
                            additionalAST = ExpressionUtil.nonNullOp(Operation.Operator.AND, additionalAST,
                                    new Operation(Operation.Operator.IMPLIES, exp1, exp2));
                        }
                        finalValueGreen = lhsExpr;
                    }
                    retHoleHashMap.put(keyHoleExpression, finalValueGreen);
                    break;
            }
        }
        return new FillArrayOutput(true, additionalAST);
    }
}
