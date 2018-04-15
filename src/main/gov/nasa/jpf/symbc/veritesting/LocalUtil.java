package gov.nasa.jpf.symbc.veritesting;

import gov.nasa.jpf.vm.StackFrame;
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
            if(h1.getHoleType() != HoleExpression.HoleType.LOCAL_OUTPUT)
                continue;
            if(h.getLocalStackSlot() == h1.getLocalStackSlot()) return true;
        }
        return false;
    }

    public static HoleExpression findPreviousWrite(HoleExpression h,
                                                   LinkedHashMap<Expression, Expression> holeHashMap) {
        if(!hasWriteBefore(h, holeHashMap)) {
            System.out.println("Warning: LocalUtil.findPreviousWrite called where no previous write was found");
            return null;
        }
        if(h.getHoleType() != HoleExpression.HoleType.LOCAL_INPUT &&
                h.getHoleType() != HoleExpression.HoleType.LOCAL_OUTPUT) {
            System.out.println("Warning: LocalUtil.findPreviousWrite called on non-local hole");
            return null;
        }
        HoleExpression prevWrite = null;
        for (Map.Entry<Expression, Expression> entry : holeHashMap.entrySet()) {
            HoleExpression h1 = (HoleExpression) entry.getKey();
            if(h1.getHoleType() != HoleExpression.HoleType.LOCAL_OUTPUT)
                continue;
            if(h.getLocalStackSlot() == h1.getLocalStackSlot())
                prevWrite = h1;
        }
        assert(prevWrite != null);
        return prevWrite;
    }
}
