package gov.nasa.jpf.symbc.veritesting;

//import com.ibm.wala.types.TypeReference;

import gov.nasa.jpf.vm.ArrayFields;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.StackFrame;
import gov.nasa.jpf.vm.ThreadInfo;
import ia_parser.Exp;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.IntVariable;
import za.ac.sun.cs.green.expr.Operation;

import java.util.HashMap;
import java.util.LinkedHashSet;

public class FillArrayStoreHoles {
    public enum Where {
        CONCRETE,
        SYM;
    }

    private Expression additionalAST;
    // This needs to store sufficient information in the holeHashMap so that I can discover it for the exception computation.
    private ThreadInfo ti;
    private HashMap<Expression, Expression> holeHashMap, retHoleHashMap;

    public FillArrayStoreHoles(ThreadInfo ti, HashMap<Expression, Expression> holeHashMap,
                               HashMap<Expression, Expression> retHoleHashMap, Expression additionalAST) {
        this.ti = ti;
        this.holeHashMap = holeHashMap;
        this.retHoleHashMap = retHoleHashMap;
        this.additionalAST = additionalAST;
    }

    public FillArrayStoreOutput invoke() throws StaticRegionException {
        LinkedHashSet<HoleExpression> additionalOutputVars = new LinkedHashSet<>();
        for (HashMap.Entry<Expression, Expression> entry : holeHashMap.entrySet()) {
            Expression key = entry.getKey(), finalValueGreen;
            assert (key instanceof HoleExpression);
            HoleExpression keyHoleExpression = (HoleExpression) key;
            switch (keyHoleExpression.getHoleType()) {
                case ARRAYSTORE:
                    additionalOutputVars.add(keyHoleExpression);
                    HoleExpression.ArrayInfo arrayInfo = keyHoleExpression.getArrayInfo();
                    HoleExpression arrayRefHole = ((HoleExpression) arrayInfo.arrayRefHole);
                    assert (retHoleHashMap.containsKey(arrayRefHole));
                    Expression arrayRefExpression = retHoleHashMap.get(arrayRefHole);
                    if (!(arrayRefExpression instanceof IntConstant))
                        throw new StaticRegionException("cannot handle symbolic array reference expression: " + arrayRefExpression);
                    int arrayRef = ((IntConstant) arrayRefExpression).getValue();

                    Where indexWhere, operandWhere;

                    indexWhere = (arrayInfo.arrayIndexHole instanceof IntConstant) ? Where.CONCRETE : Where.SYM;
                    operandWhere = (arrayInfo.val instanceof IntConstant) ? Where.CONCRETE : Where.SYM;

                    ElementInfo ei = ti.getElementInfo(arrayRef);
                    int arrayLength = ((ArrayFields) ei.getFields()).arrayLength();
                    arrayInfo.setLength(arrayLength);

                    switch (indexWhere) {
                        case CONCRETE:
                            switch (operandWhere) {
                                case CONCRETE:
                                    int indexVal = ((IntConstant) arrayInfo.arrayIndexHole).getValue();
                                    if (indexVal < 0 || indexVal >= arrayLength) //checking concrete index is out of bound
                                        return new FillArrayStoreOutput(false, null, null);
                                    break;
                                case SYM:
                                    break;
                            }
                            break; // do nothing in terms of building the formula
                        case SYM: // index symbolic and rhs either concrete or sym
                            generatePcCondition(arrayInfo, operandWhere);
                            break;
                    }
                    retHoleHashMap.put(keyHoleExpression, keyHoleExpression);
                    break;
            }
        }
        return new FillArrayStoreOutput(true, additionalAST, additionalOutputVars);
    }


    private void generatePcCondition(HoleExpression.ArrayInfo arrayInfo, Where operandWhere) {

        HoleExpression arrayRefHole = ((HoleExpression) arrayInfo.arrayRefHole);
        Expression arrayRefExpression = retHoleHashMap.get(arrayRefHole);
        int arrayRef = ((IntConstant) arrayRefExpression).getValue();


        ElementInfo ei = ti.getElementInfo(arrayRef);
        ElementInfo eiArray = ei.getModifiableInstance();

        for (int index = 0; index < arrayInfo.length; index++) {
            String newVarName = arrayRefHole.getClass() + "." + arrayRefHole.getMethodName() + ".v_i" + index;
            Expression newVar = new IntVariable(newVarName, Integer.MIN_VALUE, Integer.MAX_VALUE);

            Expression operand = null;
            Expression indexExpression = retHoleHashMap.get(arrayInfo.arrayIndexHole);

            indexExpression = new Operation(Operation.Operator.EQ, indexExpression, new IntConstant(index));

            Expression newValueExp;

            switch (operandWhere) {
                case CONCRETE:
                    operand = arrayInfo.val;
                    break;
                case SYM:
                    operand = retHoleHashMap.get(arrayInfo.val);
                    break;
            }

            newValueExp = new Operation(Operation.Operator.EQ, newVar, operand);

            Expression oldValueExp = new Operation(Operation.Operator.EQ, newVar, new IntConstant(eiArray.getIntElement(index)));

            newValueExp = new Operation(Operation.Operator.AND, newValueExp, indexExpression);
            oldValueExp = new Operation(Operation.Operator.AND, oldValueExp, new Operation(Operation.Operator.NOT, indexExpression));

            Expression arrayStoreExp = new Operation(Operation.Operator.OR, oldValueExp, newValueExp);
            additionalAST = ExpressionUtil.nonNullOp(Operation.Operator.AND, additionalAST, arrayStoreExp);        }
    }
}
