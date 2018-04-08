package gov.nasa.jpf.symbc.veritesting.SPFCase;

import gov.nasa.jpf.symbc.veritesting.HoleExpression;
import gov.nasa.jpf.symbc.veritesting.StaticRegionException;
import gov.nasa.jpf.symbc.veritesting.Visitors.FillAstHoleVisitor;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.Operation;

import java.util.HashMap;

public class TrueReason implements SPFCaseReason {

    Expression predicate = null;

    public TrueReason() {
    }

    public SPFCaseReason copy() {
        return new TrueReason();
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
        return copy();
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
    }

    @Override
    public void simplify() throws StaticRegionException {
        predicate = Operation.TRUE;
    }

    @Override
    public Expression getInstantiatedSPFPredicate() throws StaticRegionException {
        assert(predicate != null);
        return predicate;
    }
}
