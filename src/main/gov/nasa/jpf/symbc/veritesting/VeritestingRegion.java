package gov.nasa.jpf.symbc.veritesting;


import gov.nasa.jpf.symbc.veritesting.SPFCase.SPFCase;
import gov.nasa.jpf.symbc.veritesting.SPFCase.SPFCaseList;
import za.ac.sun.cs.green.expr.Expression;
import java.util.HashMap;
import java.util.HashSet;

/**
 *  MWW: This could be much better managed through a good encapsulated interface.
 *    As it is, it is no better than a struct and has very poor encapsulation.
 *    To be fixed after ASE.
 *
 *  In addition, some of the fields are public, some are private, and some are
 *  package protected.
 *
 *
 */

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;


public class VeritestingRegion {

    private int startInsnPosition;
    private int endInsnPosition;
    private Expression summaryExpression;
    private LinkedHashSet<Expression> outputVars;
    private boolean isMethodSummary = false;
    public Expression retVal;
    private String methodSignature;
    public HashSet<Integer> summarizedRegionStartBB = null;
    public int ranIntoCount = 0, usedCount = 0;
    public int endBBNum;
    public int startBBNum;

    private SPFCaseList spfCases = new SPFCaseList();
    private String currentPackageName;

    // Should not be necessary, if we have a good interface!
    public SPFCaseList getSpfCases() { return spfCases; }
    public void setSpfCases(SPFCaseList list) { spfCases = list; }

    public void addSPFCase(SPFCase c) { spfCases.addCase(c); }

    // Behavior
    // add more here for other aspects of region: segments with return, e.g.

    public Expression spfPathPredicate() throws StaticRegionException {
        return spfCases.spfPredicate();
    }

    public Expression staticNominalPredicate() throws StaticRegionException {
        return spfCases.staticNominalPredicate();
    }

    /*
    private HashSet<ExitTransition> exitTransitionHashMap;

    public void putExitTransition(ExitTransition exitTransition) {
        if (exitTransitionHashMap == null)
            exitTransitionHashMap = new HashSet<ExitTransition>();
        exitTransitionHashMap.add(exitTransition);
    }

    // consolidates all nominalConstraints for the region into one giant constraint.
    public Expression getAllNominalsConstraints() {
        if (exitTransitionHashMap != null) {
            Iterator<ExitTransition> exitTransitionIterator = exitTransitionHashMap.iterator();
            Expression allNominalConstraints = null;
            while (exitTransitionIterator.hasNext()) {
                Expression nominalConstraint = exitTransitionIterator.next().getNominalConstraint();
                if (allNominalConstraints == null)
                    allNominalConstraints = nominalConstraint;
                else
                    allNominalConstraints = new Operation(Operation.Operator.AND, allNominalConstraints, nominalConstraint);
            }
            return allNominalConstraints;
        }
        else
            return null;
    }

    public HashSet<ExitTransition> getExitTransitionHashMap() {
        return exitTransitionHashMap;
    }
    */

    public int getStartInsnPosition() {
        return startInsnPosition;
    }
    public void setStartInsnPosition(int startInsnPosition) {
        this.startInsnPosition = startInsnPosition;
    }

    public int getEndInsnPosition() {
        return endInsnPosition;
    }
    public void setEndInsnPosition(int endInsnPosition) {
        this.endInsnPosition = endInsnPosition;
    }

    public Expression getSummaryExpression() {
        return summaryExpression;
    }
    public void setSummaryExpression(Expression CNLIE) {
        this.summaryExpression = CNLIE;
    }

    public LinkedHashSet<Expression> getOutputVars() {
        return outputVars;
    }
    public void setOutputVars(LinkedHashSet outputVars) {
        this.outputVars = outputVars;
    }

    public String getClassName() {
        return className;
    }
    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    private String className, methodName;

    public void setHoleHashMap(LinkedHashMap<Expression,Expression> holeHashMap) {
        this.holeHashMap = holeHashMap;
    }
    public LinkedHashMap<Expression, Expression> getHoleHashMap() {
        return holeHashMap;
    }
    private LinkedHashMap<Expression, Expression> holeHashMap;

    public void setIsMethodSummary(boolean isMethodSummary) {
        this.isMethodSummary = isMethodSummary;
    }
    public boolean isMethodSummary() {
        return isMethodSummary;
    }

    public void setRetValVars(Expression retVal) {
        this.retVal = retVal;
    }
    public Expression getRetValVars() { return retVal; }

    public String toString() {
        return "(" + className + ", " + methodName + ", " + startInsnPosition + ", " + endInsnPosition +
                ", BB" + startBBNum + ", BB" + endBBNum + ", " + getNumBranchesSummarized() + ")";
    }

    public void setMethodSignature(String methodSignature) {
        this.methodSignature = methodSignature;
    }
    public String getMethodSignature() {
        return methodSignature;
    }

    public void setSummarizedRegionStartBB(HashSet<Integer> summarizedRegionStartBB) {
        this.summarizedRegionStartBB = new HashSet<>();
        this.summarizedRegionStartBB.addAll(summarizedRegionStartBB);
    }

    public int getNumBranchesSummarized() {
        if (summarizedRegionStartBB == null) {
            return 0;
        }
        return summarizedRegionStartBB.size();
    }

    public void setEndBBNum(int endBBNum) {
        this.endBBNum = endBBNum;
    }

    public void setStartBBNum(int startBBNum) {
        this.startBBNum = startBBNum;
    }

    public void setPackageName(String currentPackageName) {
        this.currentPackageName = currentPackageName;
    }
}

