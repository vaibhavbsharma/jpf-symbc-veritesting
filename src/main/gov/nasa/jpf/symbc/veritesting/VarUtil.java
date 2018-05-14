package gov.nasa.jpf.symbc.veritesting;

import com.ibm.wala.ssa.*;

import com.ibm.wala.types.TypeReference;
import gov.nasa.jpf.symbc.VeritestingListener;
import gov.nasa.jpf.symbc.veritesting.SPFCase.SPFCase;
import gov.nasa.jpf.symbc.veritesting.SPFCase.SPFCaseList;
import gov.nasa.jpf.vm.StackFrame;

import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;

import java.util.*;

// MWW: are all these methods and data essentially static?
// How does this class cohere?

public class VarUtil {
    private SPFCaseList spfCases;
    private static int nextIntermediateCount = 1;
    String className;
    String methodName;

    public IR getIR() {
        return ir;
    }

    IR ir;
    // Maps each WALA IR variable to its corresponding stack slot, if one exists
    HashMap<Integer, Integer> varsMap;

    public LinkedHashMap<String, Expression> varCache;

    // these represent the outputs of a veritesting region
    public LinkedHashSet<Expression> defLocalVars;

    // contains all the holes in the cnlie AST
    public LinkedHashMap<Expression, Expression> holeHashMap;

    public static int pathCounter=0;
    private static long holeID = 0;

    // contains the return values hole expression found in the region
    public Expression retValVar = null;

    public void addSpfCase(SPFCase c) { spfCases.addCase(c); }
    public SPFCaseList getSpfCases() { return spfCases; }

    public static final int getPathCounter() { pathCounter++; return pathCounter; }

    public Expression makeIntermediateVar(int val, boolean useVarCache, Expression PLAssign) {
        String name = "v" + val;
        return makeIntermediateVar(name, useVarCache, PLAssign);
    }

    // makes a intermediate variable hole, does not use varCache if useVarCache is false
    // useVarCache is useful when creating intermediate variable hole to be used in as the writeExpr in a FIELD_OUTPUT
    // hole because we would like to creat a new intermediate variable hole for every write into a field
    public Expression makeIntermediateVar(String name, boolean useVarCache, Expression PLAssign) {
        name = className + "." + methodName + "." + name;
        if(varCache.containsKey(name)) {
            if(useVarCache) return varCache.get(name);
            else name += VarUtil.nextIntermediateCount();
        }
        HoleExpression holeExpression = new HoleExpression(nextInt(), className, methodName,
                HoleExpression.HoleType.INTERMEDIATE, PLAssign, -1, -1);
        holeExpression.setHoleVarName(name);
        varCache.put(name, holeExpression);
        return holeExpression;
    }

    private static String nextIntermediateCount() {
        String ret = "" + VarUtil.nextIntermediateCount;
        VarUtil.nextIntermediateCount++;
        return ret;
    }

    public Expression makeLocalInputVar(int val, Expression PLAssign) {
        assert(varsMap.containsKey(val));
        String name = className + "." + methodName + ".v" + val;
        if(varCache.containsKey(name))
            return varCache.get(name);
        HoleExpression holeExpression = new HoleExpression(nextInt(), className, methodName,
                HoleExpression.HoleType.LOCAL_INPUT, PLAssign, varsMap.get(val), -1);
        holeExpression.setHoleVarName(name);
        varCache.put(name, holeExpression);
        return holeExpression;
    }

    public Expression makeLocalOutputVar(int val, Expression PLAssign) {
        assert(varsMap.containsKey(val));
        String name = className + "." + methodName + ".v" + val;
        if(varCache.containsKey(name))
            return varCache.get(name);
        HoleExpression holeExpression = new HoleExpression(nextInt(), className, methodName,
                HoleExpression.HoleType.LOCAL_OUTPUT, PLAssign, varsMap.get(val), -1);
        holeExpression.setHoleVarName(name);
        varCache.put(name, holeExpression);
        return holeExpression;
    }

    public VarUtil(IR _ir, String _className, String _methodName) {
        spfCases = new SPFCaseList();
        varsMap = new HashMap<> ();
        defLocalVars = new LinkedHashSet<>();
        holeHashMap = new LinkedHashMap<>();
        varCache = new LinkedHashMap<String, Expression> () {
            @Override
            public void putAll(Map<? extends String, ? extends Expression> m) {
                m.forEach((key, value) -> this.put(key, value));
                super.putAll(m);
            }

            @Override
            public Expression put(String key, Expression expression) {
                if(expression instanceof HoleExpression) {
                    // using non-hole IntegerConstant object containing 0 as placeholder
                    // for final filled-up hole object
                    if(!holeHashMap.containsKey(expression)) {
                        /// MWW: in this stmt, I might put 'null' to signify that there is nothing useful in the codomain.
                        holeHashMap.put(expression, expression);
                    }
                    if(((HoleExpression)expression).getHoleType() == HoleExpression.HoleType.FIELD_OUTPUT ||
                            ((HoleExpression)expression).getHoleType() == HoleExpression.HoleType.LOCAL_OUTPUT ||
                            ((HoleExpression)expression).getHoleType() == HoleExpression.HoleType.FIELD_PHI)
                        defLocalVars.add(expression);
                }
                return super.put(key, expression);
            }
        };
        className = _className;
        methodName = _methodName;
        ir = _ir;

        // MWW: Perhaps make this its own visitor class?
        // Report local stack slot information (if it exists) for every WALA IR variable
        _ir.visitNormalInstructions(new SSAInstruction.Visitor() {
            void getStackSlots(SSAInstruction ssaInstruction) {
                for (int v = 0; v < ssaInstruction.getNumberOfUses(); v++) {
                    int valNum = ssaInstruction.getUse(v);
                    int[] localNumbers = _ir.findLocalsForValueNumber(ssaInstruction.iindex, valNum);
                    if (localNumbers != null) {
                        for (int k = 0; k < localNumbers.length; k++) {
                            /*System.out.println("at pc(" + ssaInstruction +
                                    "), valNum(" + valNum + ") is local var(" + localNumbers[k] + ", " +
                                    _ir.getSymbolTable().isConstant(valNum) + ") uses");*/
                            if(!_ir.getSymbolTable().isConstant(valNum))
                                varsMap.put(valNum, localNumbers[k]);
                        }
                    }
                }
                for (int def = 0; def < ssaInstruction.getNumberOfDefs(); def++) {
                    int valNum = ssaInstruction.getDef(def);
                    int[] localNumbers = _ir.findLocalsForValueNumber(ssaInstruction.iindex, valNum);
                    if (localNumbers != null) {
                        for (int k = 0; k < localNumbers.length; k++) {
                            /*System.out.println("at pc(" + ssaInstruction +
                                    "), valNum(" + valNum + ") is local var(" + localNumbers[k] + ", " +
                                    _ir.getSymbolTable().isConstant(valNum) + ") defs");*/
                            if(!_ir.getSymbolTable().isConstant(valNum)) {
                                varsMap.put(valNum, localNumbers[k]);
                                // Assume var defined by phi instruction must be the same local variable as all its uses

                            }
                        }
                    } else if(ssaInstruction instanceof SSAPhiInstruction){
                        // Assume var defined by phi instruction must be the same local variable as one of its uses
                        for(int use = 0; use < ssaInstruction.getNumberOfUses(); use++) {
                            if(isLocalVariable(use)) {
                                if(varsMap.containsKey(def)) {
                                    System.out.println("Multiple local variables merged in SSAPhiInstruction at offset "
                                            + ssaInstruction.iindex);
                                    assert(false);
                                } else {
                                    varsMap.put(def, varsMap.get(use));
                                }
                            }
                        }
                    }
                }
            }
            @Override
            public void visitGoto(SSAGotoInstruction instruction) {
                getStackSlots(instruction);
                super.visitGoto(instruction);
            }

            @Override
            public void visitArrayLoad(SSAArrayLoadInstruction instruction) {
                getStackSlots(instruction);
                super.visitArrayLoad(instruction);
            }

            @Override
            public void visitArrayStore(SSAArrayStoreInstruction instruction) {
                getStackSlots(instruction);
                super.visitArrayStore(instruction);
            }

            @Override
            public void visitBinaryOp(SSABinaryOpInstruction instruction) {
                getStackSlots(instruction);
                super.visitBinaryOp(instruction);
            }

            @Override
            public void visitUnaryOp(SSAUnaryOpInstruction instruction) {
                getStackSlots(instruction);
                super.visitUnaryOp(instruction);
            }

            @Override
            public void visitConversion(SSAConversionInstruction instruction) {
                getStackSlots(instruction);
                super.visitConversion(instruction);
            }

            @Override
            public void visitComparison(SSAComparisonInstruction instruction) {
                getStackSlots(instruction);
                super.visitComparison(instruction);
            }

            @Override
            public void visitConditionalBranch(SSAConditionalBranchInstruction instruction) {
                getStackSlots(instruction);
                super.visitConditionalBranch(instruction);
            }

            @Override
            public void visitSwitch(SSASwitchInstruction instruction) {
                getStackSlots(instruction);
                super.visitSwitch(instruction);
            }

            @Override
            public void visitReturn(SSAReturnInstruction instruction) {
                getStackSlots(instruction);
                super.visitReturn(instruction);
            }

            @Override
            public void visitGet(SSAGetInstruction instruction) {
                getStackSlots(instruction);
                super.visitGet(instruction);
            }

            @Override
            public void visitPut(SSAPutInstruction instruction) {
                getStackSlots(instruction);
                super.visitPut(instruction);
            }

            @Override
            public void visitInvoke(SSAInvokeInstruction instruction) {
                getStackSlots(instruction);
                super.visitInvoke(instruction);
            }

            @Override
            public void visitNew(SSANewInstruction instruction) {
                getStackSlots(instruction);
                super.visitNew(instruction);
            }

            @Override
            public void visitArrayLength(SSAArrayLengthInstruction instruction) {
                getStackSlots(instruction);
                super.visitArrayLength(instruction);
            }

            @Override
            public void visitThrow(SSAThrowInstruction instruction) {
                getStackSlots(instruction);
                super.visitThrow(instruction);
            }

            @Override
            public void visitMonitor(SSAMonitorInstruction instruction) {
                getStackSlots(instruction);
                super.visitMonitor(instruction);
            }

            @Override
            public void visitCheckCast(SSACheckCastInstruction instruction) {
                getStackSlots(instruction);
                super.visitCheckCast(instruction);
            }

            @Override
            public void visitInstanceof(SSAInstanceofInstruction instruction) {
                getStackSlots(instruction);
                super.visitInstanceof(instruction);
            }

            @Override
            public void visitPhi(SSAPhiInstruction instruction) {
                getStackSlots(instruction);
                super.visitPhi(instruction);
            }

            @Override
            public void visitPi(SSAPiInstruction instruction) {
                getStackSlots(instruction);
                super.visitPi(instruction);
            }

            @Override
            public void visitGetCaughtException(SSAGetCaughtExceptionInstruction instruction) {
                getStackSlots(instruction);
                super.visitGetCaughtException(instruction);
            }

            @Override
            public void visitLoadMetadata(SSALoadMetadataInstruction instruction) {
                getStackSlots(instruction);
                super.visitLoadMetadata(instruction);
            }
        });

        //Propagates mapping from WALA IR variable to local stack slot to all WALA IR variables involved in
        //phi assignment statements
        boolean localVarUpdated;
        do {
            localVarUpdated = false;
            Iterator<? extends SSAInstruction> phiIterator = _ir.iteratePhis();
            while(phiIterator.hasNext()) {
                SSAPhiInstruction phiInstruction = (SSAPhiInstruction) phiIterator.next();
                for(int use = 0; use < phiInstruction.getNumberOfUses(); use++) {
                    int valNum = phiInstruction.getUse(use);
                    if(!isConstant(valNum) && varsMap.containsKey(valNum)) {
                        if(updateLocalVarsForPhi(phiInstruction, valNum)) localVarUpdated = true;
                        break;
                    }
                }
                if(localVarUpdated) break;
                for(int def = 0; def < phiInstruction.getNumberOfDefs(); def++) {
                    int valNum = phiInstruction.getDef(def);
                    if(!isConstant(valNum) && varsMap.containsKey(valNum)) {
                        if(updateLocalVarsForPhi(phiInstruction, valNum)) localVarUpdated = true;
                        break;
                    }
                }
                if(localVarUpdated) break;
            }
        } while(localVarUpdated);
    }

    private boolean updateLocalVarsForPhi(SSAPhiInstruction phiInstruction, int val) {
        boolean ret = false;
        for(int use = 0; use < phiInstruction.getNumberOfUses(); use++) {
            int useValNum = phiInstruction.getUse(use);
            if(useValNum == val || isConstant(useValNum)) continue;
            if(varsMap.containsKey(useValNum)) continue;
            else {
                varsMap.put(useValNum, varsMap.get(val));
                ret = true;
            }
        }
        for(int def = 0; def < phiInstruction.getNumberOfDefs(); def++) {
            int defValNum = phiInstruction.getDef(def);
            if(defValNum == val || isConstant(defValNum)) continue;
            if(varsMap.containsKey(defValNum)) continue;
            else {
                varsMap.put(defValNum, varsMap.get(val));
                ret = true;
            }
        }
        return ret;
    }

    public Expression addVal(int val, Expression PLAssign) {
        String name = className + "." + methodName + ".v" + val;
        if(varCache.containsKey(name))
            return varCache.get(name);
        Expression ret;
        if(isConstant(val)) {
            ret = new IntConstant(getConstant(val));
            varCache.put(name, ret);
            return ret;
        }
        if(isLocalVariable(val)) ret = makeLocalInputVar(val, PLAssign);
        else ret = makeIntermediateVar(val, true, PLAssign);
        varCache.put(name, ret);
        return ret;
    }

    public boolean isLocalVariable(int val) {
        return varsMap.containsKey(val);
    }

    public int getLocalVarSlot(int val) {
        if(isLocalVariable(val)) return varsMap.get(val);
        else return -1;
    }

    public Expression addDefVal(int def) throws StaticRegionException {
        //this assumes that we dont need to do anything special for lhsExpr vars defined in a region
        if(isLocalVariable(def)) {
            return makeLocalOutputVar(def, null);
        }
        throw new StaticRegionException("we failed to map region output (" + def + ") to a local stack slot");
    }

    /*private Expression addDefLocalVar(int def) {
        Expression ret = makeLocalOutputVar(def);
        defLocalVars.add(ret);
        return ret;
    }*/

    public Expression addArrayLoadVal(Expression arrayRef, Expression arrayIndex, HoleExpression lhsHole,
                                      TypeReference arrayType,
                                      SSAArrayLoadInstruction instructionName, Expression pathLabelHole,
                                      Expression PLAssign) {
        HoleExpression holeExpression = new HoleExpression(nextInt(), className, methodName,
                HoleExpression.HoleType.ARRAYLOAD, PLAssign, -1, -1);
        holeExpression.setHoleVarName(instructionName.toString());
        holeExpression.setArrayInfo(arrayRef, arrayIndex, lhsHole, arrayType, pathLabelHole);

        varCache.put(holeExpression.getHoleVarName(), holeExpression);
        return holeExpression;
    }

    // def will be value being defined in case of FIELD_INPUT hole
    public Expression addFieldInputVal(int def, int use, String fieldStaticClassName, String fieldName,
                                       boolean isStaticField, Expression PLAssign) {
        HoleExpression useHole = null;
        //If the field does not belong to a local object, then it has to be an already created object or a static field
        // meaning use equals -1
        //But the already created object hole takes priority over a local object
        String string = this.className + "." + this.methodName + ".v" + use;
        if(varsMap.containsKey(use) == false) assert(varCache.containsKey(string) || use == -1);
        if(varCache.containsKey(string)) useHole = (HoleExpression) varCache.get(string);
        int localStackSlot = -1;
        if(!isStaticField && (useHole == null)) {
            assert(use != -1);
            localStackSlot = varsMap.get(use);
        }
        HoleExpression holeExpression = new HoleExpression(nextInt(), this.className, methodName,
                HoleExpression.HoleType.FIELD_INPUT, PLAssign, localStackSlot, -1);
        holeExpression.setFieldInfo(fieldStaticClassName, fieldName, methodName, null,
                isStaticField, useHole);
        String name = this.className + "." + this.methodName + ".v" + def;
        holeExpression.setHoleVarName(name);
        varCache.put(holeExpression.getHoleVarName(), holeExpression);
        return holeExpression;
    }

    // def will be value being defined in case of FIELD_INPUT hole
    public Expression addFieldOutputVal(Expression writeExpr, int use,
                                       String fieldStaticClassName,
                                       String fieldName,
                                        boolean isStaticField,
                                        Expression PLAssign, String holeName) {
        HoleExpression useHole = null;
        //If the field does not belong to a local object, then it has to be an already created object or a static field
        // meaning use equals -1
        //But the already created object hole takes priority over a local object
        String string = this.className + "." + this.methodName + ".v" + use;
        if(varsMap.containsKey(use) == false) assert(varCache.containsKey(string) || use == -1);
        if(varCache.containsKey(string))
            useHole = (HoleExpression) varCache.get(string);
        int localStackSlot = -1;
        if(!isStaticField && (useHole == null)) {
            assert(use != -1);
            localStackSlot = varsMap.get(use);
        }
        // varCache should not already have a hole with "holeName" holeName because every field output variable name
        // contains a monotonically increasing counter as a suffix
        assert(!varCache.containsKey(holeName));
        HoleExpression holeExpression = new HoleExpression(nextInt(), this.className, methodName,
                HoleExpression.HoleType.FIELD_OUTPUT, PLAssign, localStackSlot, -1);
        holeExpression.setFieldInfo(fieldStaticClassName, fieldName, methodName, writeExpr,
                isStaticField, useHole);
        holeExpression.setHoleVarName(holeName);
        varCache.put(holeName, holeExpression);
        return holeExpression;
    }

    public boolean isConstant(int operand1) {
        SymbolTable table = ir.getSymbolTable();
        return table.isNumberConstant(operand1) ||
                table.isBooleanOrZeroOneConstant(operand1) ||
                table.isNullConstant(operand1);
    }

    public int getConstant(int operand1) {
        assert(isConstant(operand1));
        SymbolTable table = ir.getSymbolTable();
        if(table.isNumberConstant(operand1))
            return table.getIntValue(operand1);
        if(table.isBooleanOrZeroOneConstant(operand1))
            return (table.isTrue(operand1) ? 1 : 0);
        if(table.isNullConstant(operand1))
            return 0;
        System.out.println("Unknown constant type");
        assert(false);
        return -1;
    }

    public void reset() {
        defLocalVars = new LinkedHashSet<>();
        varCache.clear();
        holeHashMap.clear();
        spfCases = new SPFCaseList();
        retValVar = null;
    }

    public static long nextInt() {
        holeID++;
        return holeID;
    }

    public Expression addInvokeHole(InvokeInfo invokeInfo, Expression PLAssign) {
        HoleExpression holeExpression = new HoleExpression(nextInt(), className, methodName,
                HoleExpression.HoleType.INVOKE, PLAssign, -1, -1);
        String name = className + "." + methodName + ".v" + invokeInfo.defVal;
        holeExpression.setInvokeInfo(invokeInfo);
        //The return value of this invokeVirtual will be this holeExpression object.
        //The only way to fill this hole is to map it to the corresponding method summary return value
        holeExpression.setHoleVarName(name);
        varCache.put(name, holeExpression);
        return holeExpression;
    }

    public void addRetValHole(int use) throws StaticRegionException {
        if(!isConstant(use)) {
            String name = className + "." + methodName + ".v" + use;
            if(!varCache.containsKey(name)) {
                throw new StaticRegionException("varCache does not contain " + name);
            }
            retValVar = varCache.get(name);
        } else retValVar = new IntConstant(getConstant(use));
    }
}

