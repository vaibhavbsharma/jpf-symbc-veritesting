package gov.nasa.jpf.symbc.veritesting;

import gov.nasa.jpf.vm.StackFrame;
import za.ac.sun.cs.green.expr.Expression;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static gov.nasa.jpf.symbc.veritesting.HoleExpression.HoleType.FIELD_INPUT;
import static gov.nasa.jpf.symbc.veritesting.HoleExpression.HoleType.FIELD_OUTPUT;

public class FieldUtil {

    /*
    Checks if a method's holeHashMap has a read-write interference with the outer region's holeHashmap.
    The only kind of interference allowed failure a both the outer region and the method reading the same field.
     */
    public static boolean hasRWInterference(LinkedHashMap<Expression, Expression> holeHashMap,
                                            LinkedHashMap<Expression, Expression> methodHoles, InvokeInfo callSiteInfo,
                                            StackFrame stackFrame) {
        for(Map.Entry<Expression, Expression> entry: methodHoles.entrySet()) {
            HoleExpression holeExpression = (HoleExpression) entry.getKey();
            if(!(holeExpression.getHoleType() == HoleExpression.HoleType.FIELD_INPUT ||
                    holeExpression.getHoleType() == FIELD_OUTPUT)) continue;
            if(holeExpression.getHoleType() == FIELD_OUTPUT) {
                if(FieldUtil.isFieldHasRWWithMap(holeExpression, FIELD_OUTPUT, holeHashMap,
                        callSiteInfo, stackFrame) ||
                        FieldUtil.isFieldHasRWWithMap(holeExpression, HoleExpression.HoleType.FIELD_INPUT, holeHashMap,
                                callSiteInfo, stackFrame))
                    return true;
            }
            if(holeExpression.getHoleType() == HoleExpression.HoleType.FIELD_INPUT) {
                if(FieldUtil.isFieldHasRWWithMap(holeExpression, FIELD_OUTPUT, holeHashMap,
                        callSiteInfo, stackFrame))
                    return true;
            }
        }
        return false;
    }

    /*
    Checks if holeExpression has a write to the same field before holeExpression occurs in holeHashMap
     */
    public static boolean hasWriteBefore(HoleExpression holeExpression,
                                         LinkedHashMap<Expression, Expression> holeHashMap,
                                         InvokeInfo callSiteInfo,
                                         StackFrame stackFrame) {
        if(holeExpression.getHoleType() != FIELD_INPUT && holeExpression.getHoleType() != FIELD_OUTPUT) {
            System.out.println("Warning: Did you really mean to check FieldUtil.hasWriteBefore on holeType = " +
                    holeExpression.getHoleType() + " ?");
            return false;
        }
        for (Map.Entry<Expression, Expression> entry : holeHashMap.entrySet()) {
            Expression key = entry.getKey();
            assert(key instanceof HoleExpression);
            if(holeExpression == key || holeExpression.equals(key)) return false;
            if(isTwoFieldsRW(holeExpression, (HoleExpression)key, FIELD_OUTPUT, callSiteInfo, stackFrame))
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
                                              HashMap<Expression, Expression> holeHashMap, InvokeInfo callSiteInfo,
                                              StackFrame stackFrame) {
        assert(holeExpression.getHoleType() == HoleExpression.HoleType.FIELD_INPUT ||
                holeExpression.getHoleType() == FIELD_OUTPUT);
        HoleExpression.FieldInfo f = holeExpression.getFieldInfo();
        for(HashMap.Entry<Expression, Expression> entry: holeHashMap.entrySet()) {
            Boolean x = isTwoFieldsRW(holeExpression, (HoleExpression)entry.getKey(), holeType, callSiteInfo, stackFrame);
            if (x != false) return x;
        }
        return false;
    }

    // holeType used as a filter on h1
    private static Boolean isTwoFieldsRW(HoleExpression h, HoleExpression h1, HoleExpression.HoleType holeType,
                                         InvokeInfo callSiteInfo, StackFrame stackFrame) {
        // if we aren't checking field interference between holes, return false
        if(!(h.getHoleType() == FIELD_INPUT || h.getHoleType() == FIELD_OUTPUT) ||
                !(h1.getHoleType() == FIELD_INPUT || h1.getHoleType() == FIELD_OUTPUT))
            return false;
        if(h.getHoleType() == FIELD_INPUT && holeType == FIELD_INPUT) {
            System.out.println("Warning: did you really mean to check read-read interference ?");
        }
        HoleExpression.FieldInfo f = h.getFieldInfo();
        HoleExpression.FieldInfo f1 = h1.getFieldInfo();
        if(h1.getHoleType() != holeType) return false;
        //One of the field accesses is non-static, so there cannot be a r/w operation to the same field
        if(f1.isStaticField && !f.isStaticField) return false;
        if(!f1.isStaticField && f.isStaticField) return false;
        if(!f.fieldName.equals(f1.fieldName)) return false;
        if(!f.fieldClassName.equals(f1.fieldClassName)) return false;
        //Both field accesses are static and access the same field
        if(f1.isStaticField && f.isStaticField)
            if(f.equals(f1))
                return true;
        //Were both fields created on the same side of a branch (if there was a branch)
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
        else return false;
    }

    public static HoleExpression findPreviousWrite(HoleExpression holeExpression,
                                                   HashMap<Expression, Expression> holeHashMap,
                                                   InvokeInfo callSiteInfo,
                                                   StackFrame stackFrame) {
        assert(FieldUtil.isFieldHasRWWithMap(holeExpression,
                FIELD_OUTPUT, holeHashMap, callSiteInfo, stackFrame) == true);
        assert(holeExpression.getHoleType() == HoleExpression.HoleType.FIELD_INPUT ||
                holeExpression.getHoleType() == FIELD_OUTPUT);
        HoleExpression prevWrite = null;
        for(HashMap.Entry<Expression, Expression> entry: holeHashMap.entrySet()) {
            HoleExpression holeExpression1 = (HoleExpression) entry.getKey();
            if(isTwoFieldsRW(holeExpression, holeExpression1, FIELD_OUTPUT, callSiteInfo, stackFrame)) {
                prevWrite = holeExpression1;
            }
            if(holeExpression == holeExpression1 || holeExpression1.equals(holeExpression))
                break;
        }
        assert(prevWrite != null);
        return prevWrite;
    }

    /*
    Sets previous writes to the same field as not-the-latest-write and this write to is-latest-write
     */
    public static void setLatestWrite(HoleExpression methodKeyHole, LinkedHashMap<Expression, Expression> methodHoles,
                                      InvokeInfo callSiteInfo, StackFrame stackFrame) {
        if(hasWriteBefore(methodKeyHole, methodHoles, callSiteInfo, stackFrame) &&
                methodKeyHole.getHoleType() == FIELD_OUTPUT) {
            HoleExpression prevWrite = findPreviousWrite(methodKeyHole, methodHoles, callSiteInfo, stackFrame);
            prevWrite.setIsLatestWrite(false);
        }
        if(methodKeyHole.getHoleType() == FIELD_OUTPUT)
            methodKeyHole.setIsLatestWrite(true);
    }
}
