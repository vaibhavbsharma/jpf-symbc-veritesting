package gov.nasa.jpf.symbc.veritesting;

import com.ibm.wala.dalvik.dex.instructions.Invoke;
import gov.nasa.jpf.symbc.veritesting.FillFieldInputHole;
import gov.nasa.jpf.symbc.numeric.IntegerConstant;
import gov.nasa.jpf.vm.StackFrame;
import gov.nasa.jpf.vm.ThreadInfo;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

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
                                            InvokeInfo callSiteInfo) {
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
            }
            if(holeExpression.getHoleType() == HoleExpression.HoleType.FIELD_INPUT) {
                if(FieldUtil.isFieldHasRWWithMap(holeExpression, FIELD_OUTPUT, ti, stackFrame, holeHashMap, retHoleHashMap,
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
                                         boolean isMethodSummary, InvokeInfo callSiteInfo) {
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
                                              boolean isMethodSummary, InvokeInfo callSiteInfo) {
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
                                         boolean isMethodSummary, InvokeInfo callSiteInfo) {
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
                                                boolean isMethodSummary, InvokeInfo callSiteInfo) {
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
                                      boolean isMethodSummary, InvokeInfo callSiteInfo) {
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
                                      boolean isMethodSummary, InvokeInfo callSiteInfo, boolean checkPLAssign) {
        HoleExpression.FieldInfo f1 = holeExpression1.getFieldInfo();
        HoleExpression.FieldInfo f = holeExpression.getFieldInfo();
        if(!f1.fieldName.equals(f.fieldName) ||
                (f1.isStaticField != f.isStaticField)) return false;
        if(f1.isStaticField && f.isStaticField)
            if(!f.equals(f1))
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

    public static int getObjRef(ThreadInfo ti, StackFrame stackFrame, HoleExpression holeExpression,
                                LinkedHashMap<Expression, Expression> methodHoles,
                                LinkedHashMap<Expression, Expression> retHoleHashMap,
                                boolean isMethodSummary,
                                InvokeInfo callSiteInfo) {
        int objRef = -1;
        //get the object reference from fieldInputInfo.use's local stack slot if not from the call site stack slot
        int stackSlot = -1;
        HoleExpression.FieldInfo fieldInputInfo = holeExpression.getFieldInfo();
        boolean isStatic = fieldInputInfo.isStaticField;
        if(ti.getTopFrame().getClassInfo().getName().equals(holeExpression.getClassName()) &&
                ti.getTopFrame().getMethodInfo().getName().equals(holeExpression.getMethodName()))
            stackSlot = fieldInputInfo.localStackSlot;
        else {
            stackSlot = fieldInputInfo.callSiteStackSlot;
            if(stackSlot == -1 && !fieldInputInfo.isStaticField)
                assert(false);
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
                    if (fillFieldInputHole.invoke()) {
                        assert(false); // throw a StaticRegionException later
                    }
                    retHoleHashMap = fillFieldInputHole.getRetHoleHashMap();
                    assert(retHoleHashMap.containsKey(h));
                    e = retHoleHashMap.get(h);
                } else assert(false); // throw a StaticRegionException in the future because we don't know how to fill
                // up a non-field-input hole at this point
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
}
