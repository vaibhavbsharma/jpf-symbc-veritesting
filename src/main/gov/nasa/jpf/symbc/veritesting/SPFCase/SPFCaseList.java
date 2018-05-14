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
    public void addAll(SPFCaseList cl) {cases.addAll(cl.cases); }

    // behavior
    public void instantiate(HashMap<Expression, Expression> holeHashMap) throws StaticRegionException {
        for (SPFCase c : cases) {  c.instantiate(holeHashMap); }
    }

    public void simplify() throws StaticRegionException {
        for (SPFCase c: cases) { c.simplify(); }
    }

    public SPFCaseList cloneEmbedPathConstraint(Expression e) throws StaticRegionException {
        SPFCaseList cl = new SPFCaseList();
        for (SPFCase c: cases) {cl.cases.add(c.cloneEmbedPathConstraint(e)); }
        return cl;
    }

    public Expression spfPredicate() throws StaticRegionException {
        Expression result = Operation.FALSE;
        for (SPFCase c: cases) {
            result = new Operation(Operation.Operator.OR, result, c.spfPredicate());
        }
        return result;
    }

    // MWW: Negation is flaky in Green for some reason, so I am simulating it with
    // <pred> == false.
    public Expression staticNominalPredicate() throws StaticRegionException {
        Expression result = new Operation(Operation.Operator.EQ, spfPredicate(), Operation.FALSE);
        return result;
    }
}