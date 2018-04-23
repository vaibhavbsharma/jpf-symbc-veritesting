package gov.nasa.jpf.symbc.veritesting;

import gov.nasa.jpf.symbc.VeritestingListener;
import gov.nasa.jpf.vm.StackFrame;
import gov.nasa.jpf.vm.ThreadInfo;
import za.ac.sun.cs.green.expr.Expression;

import java.util.LinkedHashMap;

import static gov.nasa.jpf.symbc.VeritestingListener.fillFieldHole;
import static gov.nasa.jpf.symbc.veritesting.HoleExpression.HoleType.FIELD_OUTPUT;
import static gov.nasa.jpf.symbc.veritesting.HoleExpression.HoleType.FIELD_PHI;

public class FillFieldInputHole {
    private final boolean isMethodSummary;
    private final InvokeInfo callSiteInfo;
    private final ThreadInfo ti;
    private final StackFrame stackFrame;
    private final LinkedHashMap<Expression, Expression> methodHoles;

    public LinkedHashMap<Expression, Expression> getRetHoleHashMap() {
        return retHoleHashMap;
    }

    private final LinkedHashMap<Expression, Expression> retHoleHashMap;
    private boolean myResult;
    private HoleExpression hole;

    public FillFieldInputHole(HoleExpression hole, LinkedHashMap<Expression, Expression> methodHoles,
                              boolean isMethodSummary, InvokeInfo callSiteInfo,
                              ThreadInfo ti, StackFrame stackFrame,
                              LinkedHashMap<Expression, Expression> retHoleHashMap) {
        this.hole = hole;
        this.methodHoles = methodHoles;
        this.isMethodSummary = isMethodSummary;
        this.callSiteInfo = callSiteInfo;
        this.ti = ti;
        this.stackFrame = stackFrame;
        this.retHoleHashMap = retHoleHashMap;
    }

    boolean is() {
        return myResult;
    }

    public boolean invoke() {
        gov.nasa.jpf.symbc.numeric.Expression spfExpr;
        Expression greenExpr;
        if (LocalUtil.updateStackSlot(ti, callSiteInfo, hole, isMethodSummary)) return true;
        if(FieldUtil.hasWriteBefore(hole, ti, stackFrame, methodHoles, retHoleHashMap, isMethodSummary, callSiteInfo)) {
            VeritestingListener.fieldReadAfterWrite += 1;
            if((!VeritestingListener.allowFieldReadAfterWrite)) {
                return true;
            }
            //get the latest value written into this field, not the value in the field at the beginning of
            //this region. Look in retHoleHashmap because non-input holes get populated before input holes
            HoleExpression holeExpression = FieldUtil.findPreviousRW(hole,
                    FIELD_OUTPUT, ti, stackFrame, methodHoles, retHoleHashMap, isMethodSummary, callSiteInfo);
            assert(holeExpression.getHoleType() == FIELD_OUTPUT || holeExpression.getHoleType() == FIELD_PHI);
            if(holeExpression.getHoleType() == FIELD_OUTPUT) {
                assert(retHoleHashMap.containsKey(holeExpression.getFieldInfo().writeValue));
                retHoleHashMap.put(hole, retHoleHashMap.get(holeExpression.getFieldInfo().writeValue));
            }
            if(holeExpression.getHoleType() == FIELD_PHI) {
                // FIELD_PHI holes are mapped to intermediate variables whereas FIELD_OUTPUT holes have a write value
                // saved within them in the FieldInfo.writeValue field
                retHoleHashMap.put(hole, retHoleHashMap.get(holeExpression));
            }
        } else {
            spfExpr = fillFieldHole(ti, stackFrame, hole, methodHoles, retHoleHashMap, isMethodSummary, callSiteInfo, true, null);
            if (spfExpr == null) {
                return true;
            }
            greenExpr = ExpressionUtil.SPFToGreenExpr(spfExpr);
            retHoleHashMap.put(hole, greenExpr);
        }
        return false;
    }

}
