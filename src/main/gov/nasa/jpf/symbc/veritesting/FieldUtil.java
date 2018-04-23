package gov.nasa.jpf.symbc.veritesting;

import gov.nasa.jpf.symbc.numeric.IntegerConstant;
import gov.nasa.jpf.vm.StackFrame;
import gov.nasa.jpf.vm.ThreadInfo;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.Operation;

import java.util.*;

import static gov.nasa.jpf.symbc.veritesting.LocalUtil.updateStackSlot;
import static gov.nasa.jpf.symbc.veritesting.HoleExpression.HoleType.FIELD_INPUT;
import static gov.nasa.jpf.symbc.veritesting.HoleExpression.HoleType.FIELD_OUTPUT;
import static gov.nasa.jpf.symbc.veritesting.HoleExpression.HoleType.FIELD_PHI;

public class FieldUtil {

    /*
    Checks if a method's holeHashMap has a read-write interference with the outer region's holeHashmap.
    The only kind of interference allowed failure a both the outer region and the method reading the same field.
     */
    public static boolean hasRWInterference(LinkedHashMap<Expression, Expression> holeHashMap,
                                            LinkedHashMap<Expression, Expression> methodHoles,
                                            LinkedHashMap<Expression, Expression> retHoleHashMap,
                                            ThreadInfo ti, StackFrame stackFrame, boolean isMethodSummary,
                                            InvokeInfo callSiteInfo) throws StaticRegionException {
        for(Map.Entry<Expression, Expression> entry: methodHoles.entrySet()) {
            HoleExpression holeExpression = (HoleExpression) entry.getKey();
            if(!(holeExpression.getHoleType() == HoleExpression.HoleType.FIELD_INPUT ||
                    holeExpression.getHoleType() == FIELD_OUTPUT)) continue;
            if(holeExpression.getHoleType() == FIELD_OUTPUT) {
                if(FieldUtil.isFieldHasRWWithMap(holeExpression, FIELD_OUTPUT, ti, stackFrame, holeHashMap, retHoleHashMap,
                        isMethodSummary, callSiteInfo) ||
                        FieldUtil.isFieldHasRWWithMap(holeExpression, FIELD_INPUT, ti, stackFrame, holeHashMap, retHoleHashMap,
                                isMethodSummary, callSiteInfo))
                    return true;
                if(FieldUtil.isFieldHasRWWithMap(holeExpression, FIELD_OUTPUT, ti, stackFrame, retHoleHashMap, retHoleHashMap,
                        isMethodSummary, callSiteInfo) ||
                        FieldUtil.isFieldHasRWWithMap(holeExpression, FIELD_INPUT, ti, stackFrame, retHoleHashMap, retHoleHashMap,
                                isMethodSummary, callSiteInfo))
                    return true;
            }
            if(holeExpression.getHoleType() == HoleExpression.HoleType.FIELD_INPUT) {
                if(FieldUtil.isFieldHasRWWithMap(holeExpression, FIELD_OUTPUT, ti, stackFrame, holeHashMap, retHoleHashMap,
                        isMethodSummary, callSiteInfo))
                    return true;
                if(FieldUtil.isFieldHasRWWithMap(holeExpression, FIELD_OUTPUT, ti, stackFrame, retHoleHashMap, retHoleHashMap,
                        isMethodSummary, callSiteInfo))
                    return true;
            }
        }

        return false;
    }

    /*
    Checks if holeExpression has a write to the same field before holeExpression occurs in methodHoles
     */
    public static boolean hasWriteBefore(HoleExpression holeExpression, ThreadInfo ti, StackFrame sf,
                                         LinkedHashMap<Expression, Expression> methodHoles,
                                         LinkedHashMap<Expression, Expression> retHoleHashMap,
                                         boolean isMethodSummary, InvokeInfo callSiteInfo) throws StaticRegionException {
        if(holeExpression.getHoleType() != FIELD_INPUT && holeExpression.getHoleType() != FIELD_OUTPUT) {
            System.out.println("Warning: Did you really mean to check FieldUtil.hasWriteBefore on holeType = " +
                    holeExpression.getHoleType() + " ?");
            return false;
        }
        for (Map.Entry<Expression, Expression> entry : methodHoles.entrySet()) {
            Expression key = entry.getKey();
            assert(key instanceof HoleExpression);
            if(holeExpression == key || holeExpression.equals(key)) return false;
            if(isTwoFieldsRW(holeExpression, (HoleExpression)key, FIELD_OUTPUT, ti, sf, methodHoles, retHoleHashMap, isMethodSummary, callSiteInfo))
                return true;
        }
        return false;
    }

    /*
    Checks if there is a read/write (specified in holeType) operation happening on the same field in holeExpression
    and some hole in holeHashMap.
    This method assumes that holeExpression comes from a method summary, holeHashMap is the hashmap of holes of the
    outer region.
     */
    public static boolean isFieldHasRWWithMap(HoleExpression holeExpression, HoleExpression.HoleType holeType,
                                              ThreadInfo ti, StackFrame stackFrame,
                                              LinkedHashMap<Expression, Expression> methodHoles,
                                              LinkedHashMap<Expression, Expression> retHoleHashMap,
                                              boolean isMethodSummary, InvokeInfo callSiteInfo) throws StaticRegionException {
        assert(holeExpression.getHoleType() == HoleExpression.HoleType.FIELD_INPUT ||
                holeExpression.getHoleType() == FIELD_OUTPUT || holeExpression.getHoleType() == FIELD_PHI);
        for(HashMap.Entry<Expression, Expression> entry: methodHoles.entrySet()) {
            Boolean x = isTwoFieldsRW(holeExpression, (HoleExpression)entry.getKey(), holeType, ti, stackFrame,
                    methodHoles, retHoleHashMap, isMethodSummary, callSiteInfo);
            if (x != false) return x;
        }
        return false;
    }

    // holeType used as a filter on h1 except if h1 is a FIELD_PHI, in which case holeType is ignored. This allows us
    // to treat FIELD_PHI in the same way as a FIELD_OUTPUT which is a conflict in both
    // read-after-write and write-after-write operations.
    private static Boolean isTwoFieldsRW(HoleExpression h, HoleExpression h1, HoleExpression.HoleType holeType,
                                         ThreadInfo ti, StackFrame stackFrame,
                                         LinkedHashMap<Expression, Expression> methodHoles,
                                         LinkedHashMap<Expression, Expression> retHoleHashMap,
                                         boolean isMethodSummary, InvokeInfo callSiteInfo) throws StaticRegionException {
        // if we aren't checking field interference between holes, return false
        if(!(h.getHoleType() == FIELD_PHI || h.getHoleType() == FIELD_INPUT || h.getHoleType() == FIELD_OUTPUT) ||
                !(h1.getHoleType() == FIELD_INPUT || h1.getHoleType() == FIELD_OUTPUT || h1.getHoleType() == FIELD_PHI))
            return false;
        if(h.getHoleType() == FIELD_INPUT && holeType == FIELD_INPUT) {
            System.out.println("Warning: did you really mean to check read-read interference ?");
        }
        if(h1.getHoleType() != holeType && h1.getHoleType() != FIELD_PHI) return false;
        /*
        HoleExpression.FieldInfo f = h.getFieldInfo();
        HoleExpression.FieldInfo f1 = h1.getFieldInfo();
        //One of the field accesses is non-static, so there cannot be a r/w operation to the same field
        if(f1.isStaticField && !f.isStaticField) return false;
        if(!f1.isStaticField && f.isStaticField) return false;
        if(!f.fieldName.equals(f1.fieldName)) return false;
        //Both field accesses are static and access the same field
        if(f1.isStaticField && f.isStaticField)
            if(f.equals(f1))
                return true;

        if(!h.isSamePLAssign(h1.PLAssign)) return false;
        //At this point, both field accesses operate on the same type of field and are both non-static
        //we now need to determine if these two fields belong to the same object
        //Assume that h comes from a method summary if callSiteInfo is not null
        int objRefMS = -1, objRefOR;
        if(callSiteInfo != null) {
            if(f.localStackSlot == 0)
                objRefMS = stackFrame.getLocalVariable(((HoleExpression) callSiteInfo.paramList.get(0)).getLocalStackSlot());
            else {
                //We cannot load the object reference for an object that is created locally within the method summary
                System.out.println("failed to load local stack object inside a method summary");
                assert(false);
            }
        }
        else
            objRefMS = stackFrame.getLocalVariable(f.localStackSlot);
        objRefOR = stackFrame.getLocalVariable(f1.localStackSlot);
        if(objRefMS == objRefOR) return true;
        else return false;*/
        return isSameField(ti, stackFrame, h, h1, methodHoles, retHoleHashMap, isMethodSummary, callSiteInfo, true);
    }

    public static HoleExpression findPreviousRW(HoleExpression holeExpression,
                                                HoleExpression.HoleType rwOperation,
                                                ThreadInfo ti,
                                                StackFrame stackFrame,
                                                LinkedHashMap<Expression, Expression> methodHoles,
                                                LinkedHashMap<Expression, Expression> retHoleHashMap,
                                                boolean isMethodSummary, InvokeInfo callSiteInfo) throws StaticRegionException {
        assert(FieldUtil.isFieldHasRWWithMap(holeExpression, rwOperation, ti, stackFrame, methodHoles, retHoleHashMap,
                isMethodSummary, callSiteInfo) == true);
        assert(holeExpression.getHoleType() == HoleExpression.HoleType.FIELD_INPUT ||
                holeExpression.getHoleType() == FIELD_OUTPUT || holeExpression.getHoleType() == FIELD_PHI);
        HoleExpression prevWrite = null;
        for(HashMap.Entry<Expression, Expression> entry: methodHoles.entrySet()) {
            HoleExpression holeExpression1 = (HoleExpression) entry.getKey();
            if(holeExpression == holeExpression1 || holeExpression1.equals(holeExpression))
                break;
            if(isTwoFieldsRW(holeExpression, holeExpression1, rwOperation, ti, stackFrame, methodHoles, retHoleHashMap,
                    isMethodSummary, callSiteInfo)) {
                prevWrite = holeExpression1;
                //dont break here, we want to return the latest write we find before the holeExpression
            }

        }
        assert(prevWrite != null);
        return prevWrite;
    }

    /*
    Sets previous writes to the same field as not-the-latest-write and this write to is-latest-write
     */
    public static void setLatestWrite(HoleExpression methodKeyHole, ThreadInfo ti, StackFrame stackFrame,
                                      LinkedHashMap<Expression, Expression> methodHoles,
                                      LinkedHashMap<Expression, Expression> retHoleHashMap,
                                      boolean isMethodSummary, InvokeInfo callSiteInfo) throws StaticRegionException {
        if(hasWriteBefore(methodKeyHole, ti, stackFrame, methodHoles, retHoleHashMap, isMethodSummary, callSiteInfo) &&
                methodKeyHole.getHoleType() == FIELD_OUTPUT) {
            HoleExpression prevWrite = findPreviousRW(methodKeyHole, FIELD_OUTPUT, ti, stackFrame, methodHoles, retHoleHashMap, isMethodSummary, callSiteInfo);
            prevWrite.setIsLatestWrite(false);
        }
        if(methodKeyHole.getHoleType() == FIELD_OUTPUT)
            methodKeyHole.setIsLatestWrite(true);
    }

    public static boolean isSameField(ThreadInfo ti, StackFrame sf, HoleExpression holeExpression,
                                      HoleExpression holeExpression1,
                                      LinkedHashMap<Expression, Expression> methodHoles,
                                      LinkedHashMap<Expression, Expression> retHoleHashMap,
                                      boolean isMethodSummary, InvokeInfo callSiteInfo, boolean checkPLAssign) throws StaticRegionException {
        HoleExpression.FieldInfo f1 = holeExpression1.getFieldInfo();
        HoleExpression.FieldInfo f = holeExpression.getFieldInfo();
        if(!f1.fieldName.equals(f.fieldName) ||
                (f1.isStaticField != f.isStaticField)) return false;
        if(f1.isStaticField && f.isStaticField)
            if(!isSameStaticField(f,f1))
                return false;
        if(checkPLAssign)
            // were both fields created on the same side of a branch (if there was a branch) ?
            if(!holeExpression.isSamePLAssign(holeExpression1.PLAssign))
                return false;
        if(f1.isStaticField && f.isStaticField)
            return true;
        int objRef1 = getObjRef(ti, sf, holeExpression, methodHoles, retHoleHashMap, isMethodSummary, callSiteInfo);
        int objRef2 = getObjRef(ti, sf, holeExpression1, methodHoles, retHoleHashMap, isMethodSummary, callSiteInfo);
        if(objRef1 != objRef2) return false;
        else return true;
    }

    public static boolean isSameStaticField(HoleExpression.FieldInfo f, HoleExpression.FieldInfo f1) {
        if(!f1.getFieldStaticClassName().equals(f.getFieldStaticClassName()) ||
                !f1.fieldName.equals(f.fieldName) ||
                (f1.isStaticField != f.isStaticField))return false;
        return true;
    }

    public static int getObjRef(ThreadInfo ti, StackFrame stackFrame, HoleExpression hole,
                                LinkedHashMap<Expression, Expression> methodHoles,
                                LinkedHashMap<Expression, Expression> retHoleHashMap,
                                boolean isMethodSummary,
                                InvokeInfo callSiteInfo) throws StaticRegionException {
        int objRef = -1;
        //get the object reference from fieldInputInfo.use's local stack slot if not from the call site stack slot
        HoleExpression.FieldInfo fieldInputInfo = hole.getFieldInfo();
        boolean isStatic = fieldInputInfo.isStaticField;
        String className = ti.getTopFrame().getClassInfo().getName();
        String methodName = ti.getTopFrame().getMethodInfo().getName();
        int stackSlot = -1;
        if(hole.getLocalStackSlot() != -1) {
            if(updateStackSlot(ti, callSiteInfo, hole, isMethodSummary))
                throw new StaticRegionException("failed to update stack slot for hole: " + hole.toString());
            stackSlot = hole.getGlobalOrLocalStackSlot(className, methodName);
        }
        //this field is being loaded from an object reference that is itself a hole
        // this object reference hole should be filled already because holes are stored in a LinkedHashMap
        // that keeps holes in the order they were created while traversing the WALA IR
        if(stackSlot == -1 && !fieldInputInfo.isStaticField) {
            Expression e = fieldInputInfo.useHole;
            gov.nasa.jpf.symbc.numeric.Expression objRefExpression;
            assert(e != null);
            while(!(e instanceof IntConstant)) {
                HoleExpression h = (HoleExpression) e;
                if(h.getHoleType() == FIELD_INPUT) {
                    FillFieldInputHole fillFieldInputHole = new FillFieldInputHole(h, methodHoles,
                            isMethodSummary, callSiteInfo, ti, stackFrame, retHoleHashMap);
                    fillFieldInputHole.invoke();
                    retHoleHashMap = fillFieldInputHole.getRetHoleHashMap();
                    assert(retHoleHashMap.containsKey(h));
                    e = retHoleHashMap.get(h);
                } else
                    throw new StaticRegionException("cannot fill a non-field-input hole in getObjRef, hole: " + h.toString());
            }
            objRefExpression = ExpressionUtil.GreenToSPFExpression(e);
            assert(objRefExpression instanceof IntegerConstant);
            objRef = ((IntegerConstant) objRefExpression).value();
        }
        if (!isStatic && (stackSlot != -1)) {
            objRef = stackFrame.getLocalVariable(stackSlot);
            assert(objRef != 0);
        }
        return objRef;
    }

    public static boolean isFieldHole(HoleExpression.HoleType holeType) {
        return (holeType == FIELD_INPUT) || (holeType == FIELD_OUTPUT) || (holeType == FIELD_PHI);
    }

    public static boolean isField(HoleExpression hole) {
        return hole.getHoleType() == FIELD_INPUT || hole.getHoleType() == FIELD_OUTPUT || hole.getHoleType() == FIELD_PHI;
    }

    /*
    Returns an expression which lets the field output be one of the output variables in outputVars or be the value of
    the field before the region. The assignments are predicated on the pathlabel assignment being satisfied, where the
    pathlabel assignment comes from each output variable. This method will return a phi expression over all fields that
    are the same field from the same aliased object. This method needs to be called only once per field output.
    holeExpression = one of the output variables in outputVars
    outputVars = output variables of the region
    finalValue = intermediate symbolic variable that will be written into the field
    prevValue = value of the field before the region
    retHoleHashMap = hashmap that maps holes to instantiated green expressions
     */
    public static Expression findCommonFieldOutputs(ThreadInfo ti, StackFrame sf,
                                              HoleExpression holeExpression, HashSet<Expression> outputVars,
                                              Expression finalValue, Expression prevValue,
                                              LinkedHashMap<Expression, Expression> methodHoles,
                                              LinkedHashMap<Expression, Expression> retHoleHashMap,
                                              boolean isMethodSummary, InvokeInfo callSiteInfo) throws StaticRegionException {
        Expression ret = null;
        Iterator iterator = outputVars.iterator();
        Expression disjAllPLAssign = null;
        // FYI: outputVars doesn't contain FIELD_PHI holes
        while(iterator.hasNext()) {
            HoleExpression outputVar = (HoleExpression) iterator.next();
            if(outputVar.getHoleType() != FIELD_OUTPUT) continue;
            if(!outputVar.isLatestWrite()) continue;
            if(FieldUtil.isSameField(ti, sf, holeExpression, outputVar, methodHoles, retHoleHashMap, isMethodSummary, callSiteInfo, false)) {
                if(outputVar.PLAssign == null) {
                    outputVar.setIsLatestWrite(false);
                    continue;
                }
                assert(outputVar.getFieldInfo().writeValue != null);
                Expression thisOutputVar = new Operation(Operation.Operator.AND,
                        outputVar.PLAssign,
                        new Operation(Operation.Operator.EQ, finalValue, outputVar.getFieldInfo().writeValue));
                if(ret == null) {
                    ret = thisOutputVar;
                    assert(disjAllPLAssign == null);
                    disjAllPLAssign = outputVar.PLAssign;
                }
                else {
                    assert(disjAllPLAssign != null);
                    ret = new Operation(Operation.Operator.OR, ret, thisOutputVar);
                    disjAllPLAssign = new Operation(Operation.Operator.OR, disjAllPLAssign, outputVar.PLAssign);
                }
                outputVar.setIsLatestWrite(false);
            }
        }
        Expression prevAssign = new Operation(Operation.Operator.AND,
                new Operation(Operation.Operator.NOT, disjAllPLAssign),
                new Operation(Operation.Operator.EQ, finalValue, prevValue));
        assert(ret != null);
        return new Operation(Operation.Operator.OR, ret, prevAssign);
    }

}
