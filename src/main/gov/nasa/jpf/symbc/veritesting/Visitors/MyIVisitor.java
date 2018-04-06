package gov.nasa.jpf.symbc.veritesting.Visitors;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction;
import com.ibm.wala.shrikeBT.IConditionalBranchInstruction;
import com.ibm.wala.shrikeBT.IInvokeInstruction;
import com.ibm.wala.shrikeBT.IShiftInstruction;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.strings.Atom;
import gov.nasa.jpf.symbc.veritesting.HoleExpression;
import gov.nasa.jpf.symbc.veritesting.InvokeInfo;
import gov.nasa.jpf.symbc.veritesting.SPFCase.ArrayBoundsReason;
import gov.nasa.jpf.symbc.veritesting.SPFCase.SPFCase;
import gov.nasa.jpf.symbc.veritesting.VarUtil;
import za.ac.sun.cs.green.expr.Expression;

import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;

import java.util.ArrayList;

public class MyIVisitor implements SSAInstruction.IVisitor {
    private final int thenUseNum;
    private final int elseUseNum;
    private final boolean isMeetVisitor;
    VarUtil varUtil;
    private Expression phiExprThen = null;
    private Expression phiExprElse = null;
    private Expression phiExprLHS = null;
    private String invokeClassName;
    private boolean isInvoke = false;
    private Expression pathLabelHole;

    public Expression getIfExpr() {
        return ifExpr;
    }

    private Expression ifExpr = null;

    public boolean isExitNode() {
        return isExitNode;
    }
    private boolean isExitNode = false;

    public boolean canVeritest() {
        return canVeritest;
    }

    private boolean canVeritest;

    /*public String getIfExprStr_SPF() {
        return ifExprStr_SPF;
    }

    public String getIfNotExprStr_SPF() {
        return ifNotExprStr_SPF;
    }

    private String ifExprStr_SPF, ifNotExprStr_SPF;*/

    private Expression SPFExpr;

    public MyIVisitor(VarUtil _varUtil, int _thenUseNum, int _elseUseNum, boolean _isMeetVisitor) {
        varUtil = _varUtil;
        thenUseNum = _thenUseNum;
        elseUseNum = _elseUseNum;
        isMeetVisitor = _isMeetVisitor;
        //SPFExpr = new String();
    }

    public MyIVisitor(VarUtil _varUtil, int _thenUseNum, int _elseUseNum, boolean _isMeetVisitor, Expression pathLabelHole) {
        varUtil = _varUtil;
        thenUseNum = _thenUseNum;
        elseUseNum = _elseUseNum;
        isMeetVisitor = _isMeetVisitor;
        //SPFExpr = new String();
        this.pathLabelHole = pathLabelHole;
    }

    public void setCanVeritest(boolean val, SSAInstruction instruction) {
        canVeritest = val;
        if(!canVeritest) {
            System.out.println("Cannot veritest SSAInstruction: " + instruction);
        }
    }

    @Override
    public void visitGoto(SSAGotoInstruction instruction) {
        if(isMeetVisitor) return;
        System.out.println("SSAGotoInstruction = " + instruction);
        setCanVeritest(true, instruction);
    }

    @Override
    public void visitArrayLoad(SSAArrayLoadInstruction instruction) {
        if(isMeetVisitor) return;
        System.out.println("SSAArrayLoadInstruction = " + instruction);
        int lhs = instruction.getDef();
        Expression lhsExpr = varUtil.makeIntermediateVar(lhs);
        // Expression arrayLoadResult = new IntVariable("arrayLoadResult", Integer.MIN_VALUE, Integer.MAX_VALUE);
        int arrayRef = instruction.getUse(0);
        int arrayIndex = instruction.getUse(1);
        TypeReference arrayType = instruction.getElementType();
        Expression arrayRefHole = varUtil.addVal(arrayRef);
        Expression arrayIndexHole = varUtil.addVal(arrayIndex);
        Expression arrayLoadHole = varUtil.addArrayLoadVal(arrayRefHole, arrayIndexHole, lhsExpr, arrayType, HoleExpression.HoleType.ARRAYLOAD, instruction, pathLabelHole);

        // MWW: new code!  TODO: get rid of arrayRefHole and arrayIndexHole: they are discoverable.
        ArrayBoundsReason reason = new ArrayBoundsReason(arrayRefHole, arrayIndexHole, arrayLoadHole);
        SPFCase c = new SPFCase(pathLabelHole, reason);
        varUtil.addSpfCase(c);
        // MWW: end new code!
        // SPFExpr will be handled
        // SPFExpr = new Operation(Operator.IMPLIES, arrayLoadResult, new Operation(Operator.EQ, lhsExpr, arrayLoadResult));
        SPFExpr = arrayLoadHole;
       setCanVeritest(true, instruction);
    }

    @Override
    public void visitArrayStore(SSAArrayStoreInstruction instruction) {
        if(isMeetVisitor) return;
        System.out.println("SSAArrayStoreInstruction = " + instruction);
        setCanVeritest(false, instruction);
    }

    @Override
    public void visitBinaryOp(SSABinaryOpInstruction instruction) {
        if(isMeetVisitor) return;
        System.out.println("SSABinaryOpInstruction = " + instruction);
        assert(instruction.getNumberOfUses()==2);
        assert(instruction.getNumberOfDefs()==1);
        assert(instruction.mayBeIntegerOp()==true);
        int lhs = instruction.getDef();
        int operand1 = instruction.getUse(0);
        int operand2 = instruction.getUse(1);
        //variables written to in a veritesting region will always become intermediates because they will be
        //phi'd at the end of the region or be written into a class field later
        //lhsExpr will also be a lhsExpr variable if we are summarizing a method
        Expression lhsExpr = varUtil.makeIntermediateVar(lhs);
        Expression operand1Expr = varUtil.addVal(operand1);
        Expression operand2Expr = varUtil.addVal(operand2);

        assert(!varUtil.isConstant(lhs));
        Operator operator = null;
        if(instruction.getOperator() instanceof IBinaryOpInstruction.Operator) {
            switch((IBinaryOpInstruction.Operator) instruction.getOperator()) {
                case ADD: operator = Operator.ADD; break;
                case SUB: operator = Operator.SUB; break;
                case MUL: operator = Operator.MUL; break;
                case DIV: operator = Operator.DIV; break;
                case REM: operator = Operator.MOD; break;
                case AND: operator = Operator.BIT_AND; break;
                case OR: operator = Operator.BIT_OR; break;
                case XOR: operator = Operator.BIT_XOR; break;
                default:
                    System.out.println("unsupported operator (" + instruction.getOperator() + ") in SSABinaryOpInstruction");
                    assert(false);
            }
        } else if(instruction.getOperator() instanceof IShiftInstruction.Operator) {
            switch((IShiftInstruction.Operator) instruction.getOperator()) {
                case SHL: operator = Operator.SHIFTL; break;
                case SHR: operator = Operator.SHIFTR; break;
                case USHR: operator = Operator.SHIFTUR; break;
                default:
                    System.out.println("unsupported operator (" + instruction.getOperator() + ") in SSABinaryOpInstruction");
                    assert(false);
            }
        } else {
            System.out.println("unknown type of operator (" + instruction.getOperator() + ") in SSABinaryOpInstruction");
            assert(false);
        }
        SPFExpr =
                new Operation(Operator.EQ, lhsExpr,
                        new Operation(operator, operand1Expr, operand2Expr));
        setCanVeritest(true, instruction);
    }

    @Override
    public void visitUnaryOp(SSAUnaryOpInstruction instruction) {
        if(isMeetVisitor) return;
        System.out.println("SSAUnaryOpInstruction = " + instruction);
        //TODO: make SPFExpr
        setCanVeritest(false, instruction);
    }

    @Override
    public void visitConversion(SSAConversionInstruction instruction) {
        if(isMeetVisitor) return;
        System.out.println("SSAConversionInstruction = " + instruction);
        setCanVeritest(false, instruction);
    }

    @Override
    public void visitComparison(SSAComparisonInstruction instruction) {
        if(isMeetVisitor) return;
        System.out.println("SSAComparisonInstruction = " + instruction);
        setCanVeritest(false, instruction);
    }

    @Override
    public void visitConditionalBranch(SSAConditionalBranchInstruction instruction) {
        if(isMeetVisitor) return;
        System.out.println("SSAConditionalBranchInstruction = " + instruction);
        if(!instruction.isIntegerComparison()) {
            System.out.println("can only veritest with integer comparison-containing conditional branch instructions\n");
            canVeritest=false;
            return;
        }
        assert(instruction.getNumberOfUses() == 2);
        assert(instruction.getNumberOfDefs() == 0);
        IConditionalBranchInstruction.IOperator opWALA = instruction.getOperator();
        Operation.Operator opGreen = null, negOpGreen = null;
        if (opWALA.equals(IConditionalBranchInstruction.Operator.NE)) {
            opGreen = Operator.NE; negOpGreen = Operator.EQ;
        } else if (opWALA.equals(IConditionalBranchInstruction.Operator.EQ)) {
            opGreen = Operator.EQ; negOpGreen = Operator.NE;
        } else if (opWALA.equals(IConditionalBranchInstruction.Operator.LE)) {
            opGreen = Operator.LE; negOpGreen = Operator.GT;
        } else if (opWALA.equals(IConditionalBranchInstruction.Operator.LT)) {
            opGreen = Operator.LT; negOpGreen = Operator.GE;
        } else if (opWALA.equals(IConditionalBranchInstruction.Operator.GE)) {
            opGreen = Operator.GE; negOpGreen = Operator.LT;
        } else if (opWALA.equals(IConditionalBranchInstruction.Operator.GT)) {
            opGreen = Operator.GT; negOpGreen = Operator.LE;
        }
        if(opGreen == null && negOpGreen == null) {
            System.out.println("Don't know how to convert WALA operator (" + opWALA + ") to Green operator");
            setCanVeritest(false, instruction);
            return;
        }
        Expression operand1 = varUtil.addVal(instruction.getUse(0));
        Expression operand2 = varUtil.addVal(instruction.getUse(1));
        ifExpr = new Operation(opGreen, operand1, operand2);
        //Expression ifNotExpr = new Operation(negOpGreen, operand1, operand2);
        //SPFExpr = new Operation(Operator.OR, ifExpr, ifNotExpr);
        canVeritest=true;
    }

    @Override
    public void visitSwitch(SSASwitchInstruction instruction) {
        if(isMeetVisitor) return;
        System.out.println("SSASwitchInstruction = " + instruction);
        setCanVeritest(false, instruction);
    }

    @Override
    public void visitReturn(SSAReturnInstruction instruction) {
        if(isMeetVisitor) return;
        System.out.println("SSAReturnInstruction = " + instruction);
        //we can only handle a return value not associated with an object
        if (instruction.getNumberOfUses() > 1) {
            setCanVeritest(false, instruction);
            return;
        }
        if(varUtil.retValVar != null) {
            System.out.println("cannot handle multiple returns");
            setCanVeritest(false, instruction);
            return;
        }
        isExitNode = true;
        if(instruction.getNumberOfUses()==1) {
            varUtil.addRetValHole(instruction.getUse(0));
        }
        setCanVeritest(true, instruction);
    }

    @Override
    public void visitGet(SSAGetInstruction instruction) {
        if(isMeetVisitor) return;
        System.out.println("SSAGetInstruction = " + instruction);
        int objRef;
        if(instruction.isStatic()) {
            assert (instruction.getNumberOfDefs() == 1);
            assert (instruction.getNumberOfUses() == 0);
            objRef = -1;
        } else {
            assert (instruction.getNumberOfDefs() == 1);
            assert (instruction.getNumberOfUses() == 1);
            objRef = instruction.getUse(0);
        }
        FieldReference fieldReference = instruction.getDeclaredField();
        Atom declaringClass = fieldReference.getDeclaringClass().getName().getClassName();
        Atom fieldName = fieldReference.getName();
        System.out.println("declaringClass = " + declaringClass + ", methodName = " + fieldName);
        int def = instruction.getDef(0);
        if(varUtil.addFieldInputVal(def, objRef, declaringClass.toString(), fieldName.toString(),
                HoleExpression.HoleType.FIELD_INPUT, instruction.isStatic()) == null) {
            setCanVeritest(false, instruction);
        } else {
            setCanVeritest(true, instruction);
        }
    }

    @Override
    public void visitPut(SSAPutInstruction instruction) {
        if(isMeetVisitor) return;
        System.out.println("SSAPutInstruction = " + instruction);
        String intermediateVarName = "";
        if(instruction.isStatic()) {
            assert(instruction.getNumberOfUses()==1);
            assert(instruction.getNumberOfDefs()==0);
            intermediateVarName = "putStatic.";
        } else {
            assert (instruction.getNumberOfUses() == 2);
            assert (instruction.getNumberOfDefs() == 0);
            intermediateVarName = "putField.";
        }
        FieldReference fieldReference = instruction.getDeclaredField();
        int objRef = instruction.getRef();
        String className = fieldReference.getDeclaringClass().getName().getClassName().toString();
        String fieldName = fieldReference.getName().toString();
        intermediateVarName += objRef + ".";
        intermediateVarName += className + "." + fieldName;
        Expression intermediate = varUtil.makeIntermediateVar(intermediateVarName);
        Expression writeVal = varUtil.addVal(instruction.getVal());
        SPFExpr = new Operation(Operator.EQ, intermediate, writeVal);
        if(varUtil.addFieldOutputVal(intermediate, objRef, className.toString(), fieldName.toString(),
                HoleExpression.HoleType.FIELD_OUTPUT, instruction.isStatic()) == null) {
            setCanVeritest(false, instruction);
        } else {
            setCanVeritest(true, instruction);
        }
    }

    @Override
    public void visitInvoke(SSAInvokeInstruction instruction) {
        if(isMeetVisitor) return;
        System.out.println("SSAInvokeInstruction = " + instruction);
        MethodReference methodReference = instruction.getDeclaredTarget();
        CallSiteReference site = instruction.getCallSite();
        //Only adding support for invokeVirtual statements
        if(instruction.getNumberOfReturnValues() > 1 ||
                site.getInvocationCode() == IInvokeInstruction.Dispatch.SPECIAL ||
                site.getInvocationCode() == IInvokeInstruction.Dispatch.INTERFACE) {
            setCanVeritest(false, instruction);
            return;
        }
        assert(instruction.getNumberOfUses() == instruction.getNumberOfParameters());
        Atom declaringClass = methodReference.getDeclaringClass().getName().getClassName();
        Atom methodName = methodReference.getName();
        String methodSig = methodReference.getSignature();
        methodSig = methodSig.substring(methodSig.indexOf('('));
        int defVal = -1;
        if(instruction.getNumberOfReturnValues() == 1) defVal = instruction.getDef(); // represents the return value
        ArrayList<Expression> paramList = new ArrayList<>();
        for(int i=0; i < instruction.getNumberOfParameters(); i++) {
            paramList.add(varUtil.addVal(instruction.getUse(i)));
        }
        InvokeInfo virtualInfo = new InvokeInfo();
        virtualInfo.isVirtualInvoke = (site.getInvocationCode() == IInvokeInstruction.Dispatch.VIRTUAL);
        virtualInfo.isStaticInvoke = (site.getInvocationCode() == IInvokeInstruction.Dispatch.STATIC);
        virtualInfo.setDefVal(defVal);
        virtualInfo.setClassName(declaringClass.toString());
        virtualInfo.setMethodName(methodName.toString());
        virtualInfo.setMethodSignature(methodSig);
        virtualInfo.setParamList(paramList);
        varUtil.addInvokeVirtualHole(virtualInfo);
        invokeClassName = declaringClass.toString();
        isInvoke = true;
        setCanVeritest(true, instruction);
    }

    @Override
    public void visitNew(SSANewInstruction instruction) {
        if(isMeetVisitor) return;
        System.out.println("SSANewInstruction = " + instruction);
        setCanVeritest(false, instruction);
    }

    @Override
    public void visitArrayLength(SSAArrayLengthInstruction instruction) {
        if(isMeetVisitor) return;
        System.out.println("SSAArrayLengthInstruction = " + instruction);
        setCanVeritest(false, instruction);
    }

    @Override
    public void visitThrow(SSAThrowInstruction instruction) {
        if(isMeetVisitor) return;
        System.out.println("SSAThrowInstruction = " + instruction);
        setCanVeritest(false, instruction);
    }

    @Override
    public void visitMonitor(SSAMonitorInstruction instruction) {
        if(isMeetVisitor) return;
        System.out.println("SSAMonitorInstruction = " + instruction);
        setCanVeritest(false, instruction);
    }

    @Override
    public void visitCheckCast(SSACheckCastInstruction instruction) {
        if(isMeetVisitor) return;
        System.out.println("SSACheckCastInstruction = " + instruction);
        setCanVeritest(false, instruction);
    }

    @Override
    public void visitInstanceof(SSAInstanceofInstruction instruction) {
        if(isMeetVisitor) return;
        System.out.println("SSAInstanceofInstruction = " + instruction);
        setCanVeritest(false, instruction);
    }

    @Override
    public void visitPhi(SSAPhiInstruction instruction) {
        if(!isMeetVisitor) return;
        System.out.println("SSAPhiInstruction = " + instruction);
        assert(instruction.getNumberOfUses()>=2);
        assert(instruction.getNumberOfDefs()==1);

        if (thenUseNum != -1) phiExprThen = varUtil.addVal(instruction.getUse(thenUseNum));
        if (elseUseNum != -1) phiExprElse = varUtil.addVal(instruction.getUse(elseUseNum));
        if (thenUseNum != -1 || elseUseNum != -1) {
            phiExprLHS = varUtil.addDefVal(instruction.getDef(0));
            assert (!(phiExprLHS instanceof HoleExpression && !((HoleExpression) phiExprLHS).isHole()));
            assert (varUtil.getIr().getSymbolTable().isConstant(instruction.getDef(0)) == false);
        }
        setCanVeritest(true, instruction);
        //while other instructions may also update local variables, those should always become lhsExpr variables
    }

    @Override
    public void visitPi(SSAPiInstruction instruction) {
        if(isMeetVisitor) return;
        System.out.println("SSAPiInstruction = " + instruction);
        setCanVeritest(false, instruction);
    }

    @Override
    public void visitGetCaughtException(SSAGetCaughtExceptionInstruction instruction) {
        if(isMeetVisitor) return;
        System.out.println("SSAGetCaughtExceptionInstruction = " + instruction);
        setCanVeritest(false, instruction);
    }

    @Override
    public void visitLoadMetadata(SSALoadMetadataInstruction instruction) {
        if(isMeetVisitor) return;
        System.out.println("SSALoadMetadataInstruction = " + instruction);
        setCanVeritest(false, instruction);
    }

    public Expression getSPFExpr() {
        return SPFExpr;
    }

    public Expression getPhiExprSPF(Expression thenPLAssignSPF,
                                    Expression elsePLAssignSPF) {
        if (phiExprThen == null && phiExprElse == null) {
            assert(phiExprLHS == null);
            return new Operation(Operator.OR, thenPLAssignSPF, elsePLAssignSPF);
        }
        if (phiExprThen != null && phiExprElse == null) {
            assert(phiExprLHS != null);
            Operation thenExpr =
                    new Operation(Operator.EQ, phiExprLHS, phiExprThen);
            return new Operation(Operator.OR,
                    new Operation(Operator.AND, thenPLAssignSPF, thenExpr),
                    elsePLAssignSPF);
        }
        if (phiExprThen == null && phiExprElse != null) {
            assert(phiExprLHS != null);
            Operation elseExpr =
                    new Operation(Operator.EQ, phiExprLHS, phiExprElse);
            return new Operation(Operator.OR,
                    thenPLAssignSPF,
                    new Operation(Operator.AND, elsePLAssignSPF, elseExpr));
        }
        if(phiExprThen != null && phiExprElse != null) {
            assert(phiExprLHS != null);
            // (pathLabel == 1 && lhs == phiExprThen) || (pathLabel == 2 && lhs == phiExprElse)
            Operation thenExpr =
                    new Operation(Operator.EQ, phiExprLHS, phiExprThen);
            Operation elseExpr =
                    new Operation(Operator.EQ, phiExprLHS, phiExprElse);
            return new Operation(Operator.OR,
                    new Operation(Operator.AND, thenPLAssignSPF, thenExpr),
                    new Operation(Operator.AND, elsePLAssignSPF, elseExpr)
            );
        }
        assert(false);
        return null;
    }

    public boolean hasPhiExpr() {
        return phiExprLHS != null;
    }

    public String getInvokeClassName() {
        return invokeClassName;
    }

    public boolean isInvoke() {
        return isInvoke;
    }
}
