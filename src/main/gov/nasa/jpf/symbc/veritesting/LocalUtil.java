package gov.nasa.jpf.symbc.veritesting;

import gov.nasa.jpf.vm.ThreadInfo;
import za.ac.sun.cs.green.expr.Expression;

import java.util.LinkedHashMap;
import java.util.Map;

public class LocalUtil {
    public static boolean hasWriteBefore(HoleExpression h, LinkedHashMap<Expression, Expression> methodHoles) {
        if(h.getHoleType() != HoleExpression.HoleType.LOCAL_INPUT &&
                h.getHoleType() != HoleExpression.HoleType.LOCAL_OUTPUT)
            return false;
        for (Map.Entry<Expression, Expression> entry : methodHoles.entrySet()) {
            HoleExpression h1 = (HoleExpression) entry.getKey();
            if(h1 == h) return false;
            if(h1.getHoleType() != HoleExpression.HoleType.LOCAL_OUTPUT)
                continue;
            if(h.getLocalStackSlot() == h1.getLocalStackSlot()) return true;
        }
        return false;
    }

    public static HoleExpression findPreviousWrite(HoleExpression h,
                                                   LinkedHashMap<Expression, Expression> holeHashMap) throws StaticRegionException {
        if(!hasWriteBefore(h, holeHashMap)) {
            throw new StaticRegionException("Warning: LocalUtil.findPreviousRW called where no previous write was found");
        }
        if(h.getHoleType() != HoleExpression.HoleType.LOCAL_INPUT &&
                h.getHoleType() != HoleExpression.HoleType.LOCAL_OUTPUT) {
            System.out.println("Warning: LocalUtil.findPreviousRW called on non-local hole");
            return null;
        }
        HoleExpression prevWrite = null;
        for (Map.Entry<Expression, Expression> entry : holeHashMap.entrySet()) {
            HoleExpression h1 = (HoleExpression) entry.getKey();
            if ((h == h1) && (prevWrite == null)) {
                throw new StaticRegionException("LocalUtil.findPreviousWrite failed to find a previous write but hasWriteBefore was satisfied on hole: " + h.toString());
            }
            if (h == h1) return prevWrite;
            if(h1.getHoleType() != HoleExpression.HoleType.LOCAL_OUTPUT)
                continue;
            if(h.getLocalStackSlot() == h1.getLocalStackSlot())
                prevWrite = h1;
        }
        assert(prevWrite != null);
        return prevWrite;
    }

    public static boolean updateStackSlot(ThreadInfo ti, InvokeInfo callSiteInfo,
                                          HoleExpression hole, boolean isMethodSummary) throws StaticRegionException {
        HoleExpression.FieldInfo fieldInfo = hole.getFieldInfo();
        assert (fieldInfo != null);
        if(isMethodSummary) {
            if (!fieldInfo.isStaticField) {
                if(hole.getLocalStackSlot() == -1) return false;
                String className = ti.getTopFrame().getClassInfo().getName();
                String methodName = ti.getTopFrame().getMethodInfo().getName();
                if(hole.getClassName().equals(className) && hole.getMethodName().equals(methodName)) return false;
                if (hole.localStackSlot < callSiteInfo.paramList.size()) {
                    assert (callSiteInfo.paramList.size() > 0);
                    assert(HoleExpression.isLocal(callSiteInfo.paramList.get(hole.getLocalStackSlot())));
                    hole.setGlobalStackSlot(((HoleExpression)
                            callSiteInfo.paramList.get(hole.getLocalStackSlot())).getGlobalOrLocalStackSlot(
                            className, methodName));
                } else {
                    // method summary uses a field from an object that is a local inside the method
                    // this cannot be handled during veritesting because we cannot create an object
                    // when using a method summary
                    throw new StaticRegionException("method summary uses a field from an object that is a non-parameter local, hole: " + hole.toString());
                }
            }
        }
        return false;
    }
}
