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
import gov.nasa.jpf.symbc.VeritestingListener;
import gov.nasa.jpf.symbc.veritesting.*;
import gov.nasa.jpf.symbc.veritesting.SPFCase.ArrayBoundsReason;
import gov.nasa.jpf.symbc.veritesting.SPFCase.SPFCase;
import gov.nasa.jpf.symbc.veritesting.SPFCase.TrueReason;
import ia_parser.Exp;
import za.ac.sun.cs.green.expr.Expression;

import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;

import java.util.ArrayList;

import static gov.nasa.jpf.symbc.VeritestingListener.DEBUG_VERBOSE;

public class MyIVisitor implements SSAInstruction.IVisitor {
    private final HoleExpression conditionHole;
    private int thenUseNum;
    private int elseUseNum;
    private boolean isMeetVisitor;
    private Expression PLAssign;
    boolean isPhiInstruction = false;
    VarUtil varUtil;
    private Expression phiExprThen = null;
    private Expression phiExprElse = null;
    private Expression phiExprLHS = null;
    private String invokeClassName;
    private boolean isInvoke = false;
    private boolean hasNewOrThrow = false;
    private boolean hasPhiExpr = false;

    public boolean isHasNewOrThrow() {
        return hasNewOrThrow;
    }

    public Expression getIfExpr() {
        return ifExpr;
    }

    private Expression ifExpr = null;

    public boolean isReturnNode() {
        return isReturnNode;
    }
    private boolean isReturnNode = false;

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

    public MyIVisitor(VarUtil _varUtil, int _thenUseNum, int _elseUseNum, boolean _isMeetVisitor, Expression PLAssign,
                      HoleExpression conditionHole) {
        varUtil = _varUtil;
        thenUseNum = _thenUseNum;
        elseUseNum = _elseUseNum;
        isMeetVisitor = _isMeetVisitor;
        this.PLAssign = PLAssign;
        this.conditionHole = conditionHole;
        //SPFExpr = new String();
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
        LogUtil.log(DEBUG_VERBOSE, "SSAGotoInstruction = " + instruction);
        setCanVeritest(true, instruction);
    }

    @Override
    public void visitArrayLoad(SSAArrayLoadInstruction instruction) {
        if(isMeetVisitor) return;
        LogUtil.log(DEBUG_VERBOSE, "SSAArrayLoadInstruction = " + instruction);
        if (VeritestingListener.veritestingMode < 4) {
            setCanVeritest(false, instruction);
            return;
        }
        int lhs = instruction.getDef();
        HoleExpression lhsExpr = (HoleExpression) varUtil.makeIntermediateVar(lhs, true, PLAssign);
        // Expression arrayLoadResult = new IntVariable("arrayLoadResult", Integer.MIN_VALUE, Integer.MAX_VALUE);
        int arrayRef = instruction.getUse(0);
        int arrayIndex = instruction.getUse(1);
        TypeReference arrayType = instruction.getElementType();
        Expression arrayRefHole = varUtil.addVal(arrayRef, PLAssign);
        Expression arrayIndexHole = varUtil.addVal(arrayIndex, PLAssign);
        Expression arrayLoadHole = varUtil.addArrayLoadVal(arrayRefHole, arrayIndexHole, lhsExpr, arrayType,
                instruction, PLAssign);

        // MWW: new code!  TODO: get rid of arrayRefHole and arrayIndexHole: they are discoverable.
        ArrayBoundsReason reason = new ArrayBoundsReason(arrayRefHole, arrayIndexHole, arrayLoadHole);
        SPFCase c = new SPFCase(conditionHole, reason);
        varUtil.addSpfCase(c);
        // MWW: end new code!
        // SPFExpr will be handled
        SPFExpr = new Operation(Operator.EQ, lhsExpr, arrayLoadHole);
//        SPFExpr = arrayLoadHole;
       setCanVeritest(true, instruction);
    }

    @Override
    public void visitArrayStore(SSAArrayStoreInstruction instruction) {
        if(isMeetVisitor) return;
        LogUtil.log(DEBUG_VERBOSE, "SSAArrayStoreInstruction = " + instruction);
        if (VeritestingListener.veritestingMode < 4) {
            setCanVeritest(false, instruction);
            return;
        }

        int arrayRef = instruction.getUse(0);
        int arrayIndex = instruction.getUse(1);
        int rhs = instruction.getUse(2);

        TypeReference arrayType = instruction.getElementType();
        Expression arrayRefHole = varUtil.addVal(arrayRef, PLAssign);
        Expression arrayIndexHole = varUtil.addVal(arrayIndex, PLAssign);
        Expression value = varUtil.addVal(rhs, PLAssign);

        Expression arrayStoreHole = varUtil.addArrayStoreHole(arrayRefHole, arrayIndexHole, value, arrayType, instruction, PLAssign );

        ArrayBoundsReason reason = new ArrayBoundsReason(arrayRefHole, arrayIndexHole, arrayStoreHole);
        SPFCase c = new SPFCase(conditionHole, reason);
        varUtil.addSpfCase(c);

        //SPFExpr = arrayStoreHole;
        setCanVeritest(true, instruction);
    }

    @Override
    public void visitBinaryOp(SSABinaryOpInstruction instruction) {
        if(isMeetVisitor) return;
        LogUtil.log(DEBUG_VERBOSE, "SSABinaryOpInstruction = " + instruction);
        assert(instruction.getNumberOfUses()==2);
        assert(instruction.getNumberOfDefs()==1);
        if (instruction.mayBeIntegerOp() != true) {
            setCanVeritest(false, instruction);
            return;
        }
        int lhs = instruction.getDef();
        int operand1 = instruction.getUse(0);
        int operand2 = instruction.getUse(1);
        //variables written to in a veritesting region will always become intermediates because they will be
        //phi'd at the end of the region or be written into a class field later
        //lhsExpr will also be a intermediate variable if we are summarizing a method
        Expression lhsExpr = varUtil.makeIntermediateVar(lhs, true, PLAssign);
        Expression operand1Expr = varUtil.addVal(operand1, PLAssign);
        Expression operand2Expr = varUtil.addVal(operand2, PLAssign);

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
        LogUtil.log(DEBUG_VERBOSE, "SSAUnaryOpInstruction = " + instruction);
        //TODO: make SPFExpr
        setCanVeritest(false, instruction);
    }

    @Override
    public void visitConversion(SSAConversionInstruction instruction) {
        if(isMeetVisitor) return;
        LogUtil.log(DEBUG_VERBOSE, "SSAConversionInstruction = " + instruction);
        setCanVeritest(false, instruction);
    }

    @Override
    public void visitComparison(SSAComparisonInstruction instruction) {
        if(isMeetVisitor) return;
        LogUtil.log(DEBUG_VERBOSE, "SSAComparisonInstruction = " + instruction);
        setCanVeritest(false, instruction);
    }

    @Override
    public void visitConditionalBranch(SSAConditionalBranchInstruction instruction) {
        if(isMeetVisitor) return;
        LogUtil.log(DEBUG_VERBOSE, "SSAConditionalBranchInstruction = " + instruction);
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
        Expression operand1 = varUtil.addVal(instruction.getUse(0), PLAssign);
        Expression operand2 = varUtil.addVal(instruction.getUse(1), PLAssign);
        ifExpr = new Operation(opGreen, operand1, operand2);
        //Expression ifNotExpr = new Operation(negOpGreen, operand1, operand2);
        //SPFExpr = new Operation(Operator.OR, ifExpr, ifNotExpr);
        canVeritest=true;
    }

    @Override
    public void visitSwitch(SSASwitchInstruction instruction) {
        if(isMeetVisitor) return;
        LogUtil.log(DEBUG_VERBOSE, "SSASwitchInstruction = " + instruction);
        setCanVeritest(false, instruction);
    }

    @Override
    public void visitReturn(SSAReturnInstruction instruction) {
        if(isMeetVisitor) return;
        LogUtil.log(DEBUG_VERBOSE, "SSAReturnInstruction = " + instruction);
        //we can only handle a return value not associated with an object
        if (instruction.getNumberOfUses() > 1) {
            setCanVeritest(false, instruction);
            return;
        }
        isReturnNode = true;
        if(instruction.getNumberOfUses()==1) {
            try {
                varUtil.addRetValHole(conditionHole, instruction.getUse(0));
                setCanVeritest(true, instruction);
            } catch (StaticRegionException e) {
                System.out.println(e.getMessage());
                setCanVeritest(false, instruction);
            }
        }
        else if (instruction.getNumberOfUses() == 0) //SH: supporting return with no values
                setCanVeritest(true, instruction);

    }

    @Override
    public void visitGet(SSAGetInstruction instruction) {
        if(isMeetVisitor) return;
        LogUtil.log(DEBUG_VERBOSE, "SSAGetInstruction = " + instruction);
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
        String declaringClass = fieldReference.getDeclaringClass().getName().getClassName().toString();
        if (fieldReference.getDeclaringClass().getName().getPackage() != null) {
            String packageName = fieldReference.getDeclaringClass().getName().getPackage().toString();
            packageName = packageName.replace("/", ".");
            declaringClass = packageName + "." + declaringClass;
        }
        String fieldName = fieldReference.getName().toString();
        LogUtil.log(DEBUG_VERBOSE, "declaringClass = " + declaringClass + ", currentMethodName = " + fieldName);
        int def = instruction.getDef(0);
        if(varUtil.addFieldInputVal(def, objRef, declaringClass, fieldName,
                instruction.isStatic(), this.PLAssign) == null) {
                setCanVeritest(false, instruction);
        } else {
            setCanVeritest(true, instruction);
        }
    }

    @Override
    public void visitPut(SSAPutInstruction instruction) {
        if(isMeetVisitor) return;
        LogUtil.log(DEBUG_VERBOSE, "SSAPutInstruction = " + instruction);
        String holeName = "";
        if(instruction.isStatic()) {
            assert(instruction.getNumberOfUses()==1);
            assert(instruction.getNumberOfDefs()==0);
            holeName = "putStatic.";
        } else {
            assert (instruction.getNumberOfUses() == 2);
            assert (instruction.getNumberOfDefs() == 0);
            holeName = "putField.";
        }
        int objRef = instruction.getRef();
        FieldReference fieldReference = instruction.getDeclaredField();
        String declaringClass = fieldReference.getDeclaringClass().getName().getClassName().toString();
        if (fieldReference.getDeclaringClass().getName().getPackage() != null) {
            String packageName = fieldReference.getDeclaringClass().getName().getPackage().toString();
            packageName = packageName.replace("/", ".");
            declaringClass = packageName + "." + declaringClass;
        }
        String fieldName = fieldReference.getName().toString();
        holeName += objRef + ".";
        holeName += declaringClass + "." + fieldName + VarUtil.nextInt();
        Expression writeVal = varUtil.addVal(instruction.getVal(), PLAssign);
        if(varUtil.addFieldOutputVal(writeVal, objRef, declaringClass, fieldName.toString(),
                instruction.isStatic(), PLAssign, holeName) == null) {
            setCanVeritest(false, instruction);
        } else {
            setCanVeritest(true, instruction);
        }
    }

    @Override
    public void visitInvoke(SSAInvokeInstruction instruction) {
        LogUtil.log(DEBUG_VERBOSE, "SSAInvokeInstruction = " + instruction);
        MethodReference methodReference = instruction.getDeclaredTarget();
        CallSiteReference site = instruction.getCallSite();
        //Only adding support for invokeVirtual statements

        if(instruction.getNumberOfReturnValues() > 1 || site.getInvocationCode() == IInvokeInstruction.Dispatch.INTERFACE
    || site.getInvocationCode() == IInvokeInstruction.Dispatch.SPECIAL) {
            setCanVeritest(false, instruction);
            return;
        }
        assert(instruction.getNumberOfUses() == instruction.getNumberOfParameters());
        String declaringClass = methodReference.getDeclaringClass().getName().getClassName().toString();
        if (methodReference.getDeclaringClass().getName().getPackage() != null) {
            String packageName = methodReference.getDeclaringClass().getName().getPackage().toString();
            packageName = packageName.replace("/", ".");
            declaringClass = packageName + "." + declaringClass;
        }
        Atom methodName = methodReference.getName();
        String methodSig = methodReference.getSignature();
        methodSig = methodSig.substring(methodSig.indexOf('('));
        int defVal = -1;
        if(instruction.getNumberOfReturnValues() == 1) defVal = instruction.getDef(); // represents the return value
        ArrayList<Expression> paramList = new ArrayList<>();
        for(int i=0; i < instruction.getNumberOfParameters(); i++) {
            paramList.add(varUtil.addVal(instruction.getUse(i), PLAssign));
        }
        InvokeInfo callSiteInfo = new InvokeInfo();
        callSiteInfo.isVirtualInvoke = (site.getInvocationCode() == IInvokeInstruction.Dispatch.VIRTUAL);
        callSiteInfo.isStaticInvoke = (site.getInvocationCode() == IInvokeInstruction.Dispatch.STATIC);
        callSiteInfo.setDefVal(defVal);
        callSiteInfo.setClassName(declaringClass);
        callSiteInfo.setMethodName(methodName.toString());
        callSiteInfo.setMethodSignature(methodSig);
        callSiteInfo.setParamList(paramList);
        varUtil.addInvokeHole(callSiteInfo, PLAssign);
        invokeClassName = declaringClass;
        isInvoke = true;
        setCanVeritest(true, instruction);
    }

    @Override
    public void visitNew(SSANewInstruction instruction) {
        if(isMeetVisitor) return;
        LogUtil.log(DEBUG_VERBOSE, "SSANewInstruction = " + instruction);
        if (VeritestingListener.veritestingMode < 4) {
            setCanVeritest(false, instruction);
            return;
        }
        TrueReason reason = new TrueReason(TrueReason.Cause.OBJECT_CREATION);
        SPFCase c = new SPFCase(conditionHole, reason);
        varUtil.addSpfCase(c);
        canVeritest = true;
        hasNewOrThrow = true;
    }

    @Override
    public void visitArrayLength(SSAArrayLengthInstruction instruction) {
        if(isMeetVisitor) return;
        LogUtil.log(DEBUG_VERBOSE, "SSAArrayLengthInstruction = " + instruction);
        setCanVeritest(false, instruction);
    }

    @Override
    public void visitThrow(SSAThrowInstruction instruction) {
        if(isMeetVisitor) return;
        LogUtil.log(DEBUG_VERBOSE, "SSAThrowInstruction = " + instruction);
        if (VeritestingListener.veritestingMode < 4) {
            setCanVeritest(false, instruction);
            return;
        }
        TrueReason reason = new TrueReason(TrueReason.Cause.EXCEPTION_THROWN);
        SPFCase c = new SPFCase(ExpressionUtil.nonNullOp(Operator.AND, PLAssign, conditionHole), reason);
        varUtil.addSpfCase(c);
        setCanVeritest(true, instruction);
        hasNewOrThrow = true;
    }

    @Override
    public void visitMonitor(SSAMonitorInstruction instruction) {
        if(isMeetVisitor) return;
        LogUtil.log(DEBUG_VERBOSE, "SSAMonitorInstruction = " + instruction);
        setCanVeritest(false, instruction);
    }

    @Override
    public void visitCheckCast(SSACheckCastInstruction instruction) {
        if(isMeetVisitor) return;
        LogUtil.log(DEBUG_VERBOSE, "SSACheckCastInstruction = " + instruction);
        setCanVeritest(false, instruction);
    }

    @Override
    public void visitInstanceof(SSAInstanceofInstruction instruction) {
        if(isMeetVisitor) return;
        LogUtil.log(DEBUG_VERBOSE, "SSAInstanceofInstruction = " + instruction);
        setCanVeritest(false, instruction);
    }

    @Override
    public void visitPhi(SSAPhiInstruction instruction) {
        if(!isMeetVisitor) {
            setCanVeritest(true, instruction);
            return;
        }
        hasPhiExpr = true;
        LogUtil.log(DEBUG_VERBOSE, "SSAPhiInstruction = " + instruction);
        assert(instruction.getNumberOfUses()>=2);
        assert(instruction.getNumberOfDefs()==1);

        if (thenUseNum != -1) phiExprThen = varUtil.addVal(instruction.getUse(thenUseNum), PLAssign);
        if (elseUseNum != -1) phiExprElse = varUtil.addVal(instruction.getUse(elseUseNum), PLAssign);
        if (thenUseNum != -1 || elseUseNum != -1) {
            try {
                phiExprLHS = varUtil.addDefVal(instruction.getDef(0), PLAssign);
            } catch (StaticRegionException e) {
                System.out.println(e.getMessage());
                setCanVeritest(false, instruction);
                return;
            }
            assert ((phiExprLHS instanceof HoleExpression));
            assert (varUtil.getIR().getSymbolTable().isConstant(instruction.getDef(0)) == false);
        }
        setCanVeritest(true, instruction);
        //while other instructions may also update local variables, those should always become lhsExpr variables
    }

    @Override
    public void visitPi(SSAPiInstruction instruction) {
        if(isMeetVisitor) return;
        LogUtil.log(DEBUG_VERBOSE, "SSAPiInstruction = " + instruction);
        setCanVeritest(false, instruction);
    }

    @Override
    public void visitGetCaughtException(SSAGetCaughtExceptionInstruction instruction) {
        if(isMeetVisitor) return;
        LogUtil.log(DEBUG_VERBOSE, "SSAGetCaughtExceptionInstruction = " + instruction);
        setCanVeritest(false, instruction);
    }

    @Override
    public void visitLoadMetadata(SSALoadMetadataInstruction instruction) {
        if(isMeetVisitor) return;
        LogUtil.log(DEBUG_VERBOSE, "SSALoadMetadataInstruction = " + instruction);
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
        return hasPhiExpr;
    }

    public String getInvokeClassName() {
        return invokeClassName;
    }

    public boolean isInvoke() {
        return isInvoke;
    }
}
