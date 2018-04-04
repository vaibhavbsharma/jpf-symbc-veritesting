package gov.nasa.jpf.symbc.veritesting.SPFCase;

import gov.nasa.jpf.symbc.veritesting.StaticRegionException;
import gov.nasa.jpf.symbc.veritesting.Visitors.FillAstHoleVisitor;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;

import java.util.HashMap;

public class ArrayBoundsReason implements SPFCaseReason {

    Expression arrayExpr;
    Expression indexExpr;

    // Note: these are transient (per instantiation).
    // The instantiate(), simplify() and spfPredicate()
    // methods should be called in succession before
    // a subsequent call to instantiate(), or incorrect
    // results may occur.

    Expression instantiatedArrayExpr = null;
    Expression instantiatedIndexExpr = null;
    Expression predicate = null;

    public ArrayBoundsReason(Expression arrayExpr, Expression indexExpr) {
        this.arrayExpr = arrayExpr;
        this.indexExpr = indexExpr;
    }

    public SPFCaseReason copy() {
        // we do not copy transient data...
        return new ArrayBoundsReason(arrayExpr, indexExpr);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public String toString() {
        return super.toString();
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }

    @Override
    public void instantiate(HashMap<Expression, Expression> holeHashMap) throws StaticRegionException {
        FillAstHoleVisitor visitor  = new FillAstHoleVisitor(holeHashMap);

        instantiatedArrayExpr = visitor.visit(arrayExpr);
        instantiatedIndexExpr = visitor.visit(indexExpr);
    }

    @Override
    public void simplify() throws StaticRegionException {
        assert(instantiatedArrayExpr != null);
        assert(instantiatedIndexExpr != null);

        // MWW: This relies on some information that is not yet stored in the holeHashMap: we need to
        // store the array size information in order to be able to access it here.

        // What should happen:
        //   Check to see if index is concrete.  If within bounds, set predicate to false.
    }

    @Override
    public Expression getInstantiatedSPFPredicate() throws StaticRegionException {
        return Operation.TRUE;
    }
}
