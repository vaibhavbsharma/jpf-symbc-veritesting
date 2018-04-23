package gov.nasa.jpf.symbc;

import gov.nasa.jpf.symbc.numeric.IntegerConstant;
import gov.nasa.jpf.symbc.numeric.IntegerExpression;
import gov.nasa.jpf.symbc.numeric.solvers.SolverTranslator;
import gov.nasa.jpf.vm.StackFrame;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;

public class InstructionInfo {
    private int numOperands;
    private Operation.Operator trueComparator, falseComparator;
    private Expression condition, negCondition;

    public Expression getCondition() {
        return condition;
    }

    public Expression getNegCondition() {
        return negCondition;
    }

    public int getNumOperands() {
        return numOperands;
    }

    public Operation.Operator getTrueComparator() {
        return trueComparator;
    }

    public Operation.Operator getFalseComparator() {
        return falseComparator;
    }

    public InstructionInfo invoke(StackFrame stackFrame) {
        String mnemonic = stackFrame.getPC().getMnemonic();
        //System.out.println("mne = " + mnemonic);
        switch (mnemonic) {
            case "ifeq":
                numOperands = 1;
                trueComparator = Operation.Operator.EQ;
                falseComparator = Operation.Operator.NE;
                break;
            case "ifne":
                trueComparator = Operation.Operator.NE;
                falseComparator = Operation.Operator.EQ;
                numOperands = 1;
                break;
            case "iflt":
                trueComparator = Operation.Operator.LT;
                falseComparator = Operation.Operator.GE;
                numOperands = 1;
                break;
            case "ifle":
                trueComparator = Operation.Operator.LE;
                falseComparator = Operation.Operator.GT;
                numOperands = 1;
                break;
            case "ifgt":
                trueComparator = Operation.Operator.GT;
                falseComparator = Operation.Operator.LE;
                numOperands = 1;
                break;
            case "ifge":
                trueComparator = Operation.Operator.GE;
                falseComparator = Operation.Operator.LT;
                numOperands = 1;
                break;
            case "ifnull":
                trueComparator = Operation.Operator.EQ;
                falseComparator = Operation.Operator.NE;
                numOperands = 1;
                break;
            case "ifnonnull":
                trueComparator = Operation.Operator.EQ;
                falseComparator = Operation.Operator.NE;
                numOperands = 1;
                break;
            case "if_icmpeq":
                trueComparator = Operation.Operator.EQ;
                falseComparator = Operation.Operator.NE;
                numOperands = 2;
                break;
            case "if_icmpne":
                trueComparator = Operation.Operator.NE;
                falseComparator = Operation.Operator.EQ;
                numOperands = 2;
                break;
            case "if_icmpgt":
                trueComparator = Operation.Operator.GT;
                falseComparator = Operation.Operator.LE;
                numOperands = 2;
                break;
            case "if_icmpge":
                trueComparator = Operation.Operator.GE;
                falseComparator = Operation.Operator.LT;
                numOperands = 2;
                break;
            case "if_icmple":
                trueComparator = Operation.Operator.LE;
                falseComparator = Operation.Operator.GT;
                numOperands = 2;
                break;
            case "if_icmplt":
                trueComparator = Operation.Operator.LT;
                falseComparator = Operation.Operator.GE;
                numOperands = 2;
                break;
            default:
                return null;
        }
        assert (numOperands == 1 || numOperands == 2);
        IntegerExpression operand1 = null, operand2 = null;
        boolean isConcreteCondition = true;
        if (numOperands == 1) {
            gov.nasa.jpf.symbc.numeric.Expression operand1_expr = (gov.nasa.jpf.symbc.numeric.Expression)
                    stackFrame.getOperandAttr();
            operand1 = (IntegerExpression) operand1_expr;
            if (operand1 == null) operand1 = new IntegerConstant(stackFrame.peek());
            else isConcreteCondition = false;
            operand2 = new IntegerConstant(0);
        }
        if (numOperands == 2) {
            operand1 = (IntegerExpression) stackFrame.getOperandAttr(1);
            if (operand1 == null) operand1 = new IntegerConstant(stackFrame.peek(1));
            else isConcreteCondition = false;
            operand2 = (IntegerExpression) stackFrame.getOperandAttr(0);
            if (operand2 == null) operand2 = new IntegerConstant(stackFrame.peek(0));
            else isConcreteCondition = false;
        }
        if (isConcreteCondition) {
            return null;
        } else {
            condition = new Operation(trueComparator, SPFToGreenExpr(operand1), SPFToGreenExpr(operand2));
            negCondition = new Operation(falseComparator, SPFToGreenExpr(operand1), SPFToGreenExpr(operand2));
        }
        return this;
    }

    public static Expression SPFToGreenExpr(gov.nasa.jpf.symbc.numeric.Expression spfExp) {
        SolverTranslator.Translator toGreenTranslator = new SolverTranslator.Translator();
        spfExp.accept(toGreenTranslator);
        return toGreenTranslator.getExpression();
    }

}