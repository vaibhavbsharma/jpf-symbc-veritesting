package gov.nasa.jpf.symbc.veritesting.SPFCase;

import za.ac.sun.cs.green.expr.Expression;
import gov.nasa.jpf.symbc.veritesting.StaticRegionException;
import za.ac.sun.cs.green.expr.Operation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SPFCaseList {
    List<SPFCase> cases = null;

    public SPFCaseList() {
        cases = new ArrayList<>();
    }

    // List manipulation
    public List<SPFCase> getCases() { return cases; }
    public void addCase(SPFCase c) { cases.add(c); }

    // behavior
    public void instantiate(HashMap<Expression, Expression> holeHashMap) throws StaticRegionException {
        for (SPFCase c : cases) {  c.instantiate(holeHashMap); }
    }

    public void simplify() throws StaticRegionException {
        for (SPFCase c: cases) { c.simplify(); }
    }

    public void embedPathConstraint(Expression e) throws StaticRegionException {
        for (SPFCase c: cases) {c.embedPathConstraint(e);}
    }

    public Expression spfPredicate() throws StaticRegionException {
        Expression result = Operation.FALSE;
        for (SPFCase c: cases) {
            result = new Operation(Operation.Operator.OR, result, c.spfPredicate());
        }
        return result;
    }

    public Expression staticNominalPredicate() throws StaticRegionException {
        Expression result = new Operation(Operation.Operator.NOT, spfPredicate());
        return result;
    }

}
