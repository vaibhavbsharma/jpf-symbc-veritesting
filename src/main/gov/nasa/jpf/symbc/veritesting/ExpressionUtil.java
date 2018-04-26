package gov.nasa.jpf.symbc.veritesting;

import gov.nasa.jpf.symbc.numeric.GreenToSPFTranslator;
import gov.nasa.jpf.symbc.numeric.solvers.SolverTranslator;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;

import java.util.LinkedHashMap;
import java.util.Map;

public class ExpressionUtil {

    public static gov.nasa.jpf.symbc.numeric.Expression GreenToSPFExpression(Expression greenExpression) {
        assert(!(greenExpression instanceof HoleExpression));
        GreenToSPFTranslator toSPFTranslator = new GreenToSPFTranslator();
        return toSPFTranslator.translate(greenExpression);
    }

    public static Expression SPFToGreenExpr(gov.nasa.jpf.symbc.numeric.Expression spfExp) {
        SolverTranslator.Translator toGreenTranslator = new SolverTranslator.Translator();
        spfExp.accept(toGreenTranslator);
        return toGreenTranslator.getExpression();
    }

    // Replace all holes of type CONDITION with conditionExpression
    // Replace all holes of type NEGCONDITION with !(conditionExpression)
    public static Expression replaceCondition(Expression summaryExpression, Expression conditionExpression) {
        if(summaryExpression instanceof HoleExpression) {
            Expression ret = summaryExpression;
            if(((HoleExpression)summaryExpression).getHoleType() == HoleExpression.HoleType.CONDITION)
                ret = conditionExpression;
            if(((HoleExpression)summaryExpression).getHoleType() == HoleExpression.HoleType.NEGCONDITION)
                ret = new Operation(
                        negateOperator(((Operation)conditionExpression).getOperator()),
                        ((Operation) conditionExpression).getOperand(0),
                        ((Operation) conditionExpression).getOperand(1));
            return ret;
        }
        if(summaryExpression instanceof Operation) {
            Operation oldOperation = (Operation) summaryExpression;
            Operation newOperation = new Operation(oldOperation.getOperator(),
                    replaceCondition(oldOperation.getOperand(0), conditionExpression),
                    replaceCondition(oldOperation.getOperand(1), conditionExpression));
            return newOperation;
        }
        return summaryExpression;
    }

    // Replaces all instances of hole1 by hole2 in summaryExpression
    public static Expression replaceOneHoleInExp(Expression summaryExpression, HoleExpression hole1, HoleExpression hole2) {
        if(summaryExpression instanceof HoleExpression) {
            Expression ret = summaryExpression;
            if((summaryExpression) == hole1)
                ret = hole2;
            return ret;
        }
        if(summaryExpression instanceof Operation) {
            Operation oldOperation = (Operation) summaryExpression;
            Operation newOperation = new Operation(oldOperation.getOperator(),
                    replaceOneHoleInExp(oldOperation.getOperand(0), hole1, hole2),
                    replaceOneHoleInExp(oldOperation.getOperand(1), hole1, hole2));
            return newOperation;
        }
        return summaryExpression;
    }

    public static Operation.Operator negateOperator(Operation.Operator operator) {
        switch(operator) {
            case NE: return Operation.Operator.EQ;
            case EQ: return Operation.Operator.NE;
            case GT: return Operation.Operator.LE;
            case GE: return Operation.Operator.LT;
            case LT: return Operation.Operator.GE;
            case LE: return Operation.Operator.GT;
            default:
                System.out.println("Don't know how to negate Green operator (" + operator + ")");
                return null;
        }
    }

    /*
    Makes a copy of every hole in holeHashMap, maps every original hole to its copy in the returned holeHashMap
    CONDITION and NEGCONDITION holes are skipped.
    Every copy of a hole is inserted into varUtil.varCache which internally causes holes to be inserted into
    varUtil.holeHashMap and varUtil.defLocalVars
     */
    public static LinkedHashMap<HoleExpression, HoleExpression> copyHoleHashMap(LinkedHashMap<Expression, Expression> holeHashMap,
                                                                        Expression thisPLAssign,
                                                                        String currentClassName, String currentMethodName) {
        LinkedHashMap<HoleExpression, HoleExpression> retHoleHashMap = new LinkedHashMap<>();
        for (Map.Entry<Expression, Expression> entry : holeHashMap.entrySet()) {
            HoleExpression innerHole = (HoleExpression) entry.getKey();
            // Skip CONDITION and NEGCONDITION because these will be replaced with the condition expression and its
            // corresponding negation later
            if (innerHole.getHoleType() == HoleExpression.HoleType.CONDITION ||
                    innerHole.getHoleType() == HoleExpression.HoleType.NEGCONDITION)
                continue;
            // Update the PLAssign of each hole by conjuncting it with the PLAssign of this branch side
            Expression plAssign = thisPLAssign;
            if (innerHole.PLAssign != null)
                plAssign = new Operation(Operation.Operator.AND,
                        innerHole.PLAssign, thisPLAssign);
            HoleExpression innerHoleCopy = innerHole.clone(currentClassName, currentMethodName, plAssign);
            if(innerHoleCopy.getHoleType() == HoleExpression.HoleType.FIELD_PHI) {
                innerHoleCopy.getFieldInfo().fieldInputHole = retHoleHashMap.get(innerHole.getFieldInfo().fieldInputHole);
            }
            if(FieldUtil.isField(innerHoleCopy) && innerHoleCopy.getFieldInfo().useHole != null) {
                innerHoleCopy.getFieldInfo().useHole = retHoleHashMap.get(innerHole.getFieldInfo().useHole);
            }
            retHoleHashMap.put(innerHole, innerHoleCopy);
        }
        return retHoleHashMap;
    }

    public static Expression replaceHolesInExpression(Expression expression, LinkedHashMap<HoleExpression, HoleExpression> innerHolesCopy) {
        for (Map.Entry<HoleExpression, HoleExpression> entry : innerHolesCopy.entrySet()) {
            expression = replaceOneHoleInExp(expression, entry.getKey(), entry.getValue());
        }
        return expression;
    }

    public static void replaceHolesInPLAssign(LinkedHashMap<HoleExpression, HoleExpression> innerHolesCopyMap) {
        for (Map.Entry<HoleExpression, HoleExpression> entry : innerHolesCopyMap.entrySet()) {
            Expression PLAssign = entry.getValue().PLAssign;
            PLAssign = replaceHolesInExpression(PLAssign, innerHolesCopyMap);
            entry.getValue().PLAssign = PLAssign;
        }
    }

    public static void insertIntoVarUtil(LinkedHashMap<HoleExpression, HoleExpression> innerHolesCopyMap, VarUtil varUtil) {
        for (Map.Entry<HoleExpression, HoleExpression> entry : innerHolesCopyMap.entrySet()) {
            varUtil.varCache.put(entry.getValue().getHoleVarName(), entry.getValue());
        }
    }

    public static Expression nonNullOp(Operation.Operator op, Expression a, Expression b) {
        if(a != null) {
            if (b != null) return new Operation(op, a, b);
            else return a;
        } else return b;
    }
}
