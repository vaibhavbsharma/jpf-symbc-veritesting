package gov.nasa.jpf.symbc.veritesting;

//import com.ibm.wala.types.TypeReference;
import gov.nasa.jpf.symbc.VeritestingListener;
import gov.nasa.jpf.vm.ArrayFields;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.StackFrame;
import gov.nasa.jpf.vm.ThreadInfo;
import ia_parser.Exp;
import nested.A;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.Operation;

import java.util.HashMap;

public class FillArrayStoreHoles {
    enum Where {
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

    public FillArrayOutput invoke() throws StaticRegionException {
        for (HashMap.Entry<Expression, Expression> entry : holeHashMap.entrySet()) {
            Expression key = entry.getKey(), finalValueGreen;
            assert (key instanceof HoleExpression);
            HoleExpression keyHoleExpression = (HoleExpression) key;
            switch (keyHoleExpression.getHoleType()) {
                case ARRAYSTORE:
                    HoleExpression.ArrayInfo arrayInfo = keyHoleExpression.getArrayInfo();
                    HoleExpression arrayRefHole = ((HoleExpression) arrayInfo.arrayRefHole);
                    assert(retHoleHashMap.containsKey(arrayRefHole));
                    assert(retHoleHashMap.containsKey(arrayInfo.arrayIndexHole));
                    Expression arrayRefExpression = retHoleHashMap.get(arrayRefHole);
                    if (!( arrayRefExpression instanceof IntConstant))
                        throw new StaticRegionException("cannot handle symbolic array reference expression: " + arrayRefExpression);
                    int arrayRef = ((IntConstant) arrayRefExpression).getValue();

                    Expression indexExpression = retHoleHashMap.get(arrayInfo.arrayIndexHole);
                    Expression rhsValue = retHoleHashMap.get(arrayInfo.val);

                    ElementInfo ei = ti.getElementInfo(arrayRef);
                    int arrayLength = ((ArrayFields) ei.getFields()).arrayLength();
                    arrayInfo.setLength(arrayLength);

                    Where indexWhere, operandWhere = null;

                    if(indexExpression instanceof  IntConstant)
                        indexWhere = Where.CONCRETE;
                    else
                        indexWhere = Where.SYM;


                    if(rhsValue instanceof IntConstant)
                        operandWhere = Where.CONCRETE;
                    else
                        operandWhere = Where.SYM;

                    StackFrame frame = ti.getModifiableTopFrame();
                    ElementInfo eiArray = ei.getModifiableInstance();
                    switch (indexWhere) {
                        case CONCRETE:
                            int concreteIndex = ((IntConstant) indexExpression).getValue();
                            int concreteValue = ((IntConstant) rhsValue).getValue();
                            switch (operandWhere) {
                                case CONCRETE: //index and rhs both concrete
                                    eiArray.setIntElement(concreteIndex, concreteValue);
                                    break;
                                case SYM: // index concrete but rhs is sym
                                    Object rhsValueAttr = frame.getOperandAttr();
                                    //eiArray.setIntElement(concreteIndex, concreteValue); //even though value is SYM, as per the SPF bytecode, concrete value is also copied.
                                    eiArray.setElementAttrNoClone(concreteIndex,rhsValueAttr);
                                    break;
                            }
                            break;
                        case SYM: // index symbolic and rhs either concrete or sym
                            switch (operandWhere) {
                                case CONCRETE:
                                    Object rhsValueAttr = frame.getOperandAttr();
                                    for(int index = 0; index <= arrayLength; index++){
                                        setArrayAttributes(index, arrayInfo, (gov.nasa.jpf.symbc.numeric.Expression)rhsValueAttr);
                                    }
                                    break;
                                case SYM:
                                    for(int index = 0; index <= arrayLength; index++){
                                        setArrayAttributes(index, arrayInfo);
                                    }
                                    break;
                            }
                            break;
                    }
                    break;
            }
        }
        return new FillArrayOutput(true, additionalAST);
    }


    private void setArrayAttributes(int index, HoleExpression.ArrayInfo arrayInfo) {
        HoleExpression arrayRefHole = ((HoleExpression) arrayInfo.arrayRefHole);
        Expression arrayRefExpression = retHoleHashMap.get(arrayRefHole);
        int arrayRef = ((IntConstant) arrayRefExpression).getValue();

        Expression indexExpression = retHoleHashMap.get(arrayInfo.arrayIndexHole);
        Expression operand = retHoleHashMap.get(arrayInfo.val);

        ElementInfo ei = ti.getElementInfo(arrayRef);
        ElementInfo eiArray = ei.getModifiableInstance();

        indexExpression = new Operation(Operation.Operator.EQ, indexExpression, new IntConstant(index));
        Expression newValueExp = new Operation(Operation.Operator.AND, indexExpression, operand);
        Expression oldValueExp = new Operation(Operation.Operator.AND, new Operation(Operation.Operator.NOT, indexExpression),
                ExpressionUtil.SPFToGreenExpr((gov.nasa.jpf.symbc.numeric.Expression) eiArray.getElementAttr(index)));

        eiArray.setElementAttrNoClone(index,new Operation(Operation.Operator.AND, newValueExp, oldValueExp));
    }


    private void setArrayAttributes(int index, HoleExpression.ArrayInfo arrayInfo, gov.nasa.jpf.symbc.numeric.Expression operandAttr) {
        HoleExpression arrayRefHole = ((HoleExpression) arrayInfo.arrayRefHole);
        Expression arrayRefExpression = retHoleHashMap.get(arrayRefHole);
        int arrayRef = ((IntConstant) arrayRefExpression).getValue();

        Expression indexExpression = retHoleHashMap.get(arrayInfo.arrayIndexHole);

        ElementInfo ei = ti.getElementInfo(arrayRef);
        ElementInfo eiArray = ei.getModifiableInstance();

        indexExpression = new Operation(Operation.Operator.EQ, indexExpression, new IntConstant(index));
        Expression newValueExp = new Operation(Operation.Operator.AND, indexExpression, ExpressionUtil.SPFToGreenExpr(operandAttr));
        Expression oldValueExp = new Operation(Operation.Operator.AND, new Operation(Operation.Operator.NOT, indexExpression),
                ExpressionUtil.SPFToGreenExpr((gov.nasa.jpf.symbc.numeric.Expression) eiArray.getElementAttr(index)));

        eiArray.setElementAttrNoClone(index,new Operation(Operation.Operator.AND, newValueExp, oldValueExp));
    }
}
