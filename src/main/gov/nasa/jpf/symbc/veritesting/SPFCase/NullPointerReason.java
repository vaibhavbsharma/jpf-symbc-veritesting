package gov.nasa.jpf.symbc.veritesting.SPFCase;

import gov.nasa.jpf.symbc.veritesting.FieldUtil;
import gov.nasa.jpf.symbc.veritesting.HoleExpression;
import gov.nasa.jpf.symbc.veritesting.StaticRegionException;
import gov.nasa.jpf.symbc.veritesting.Visitors.FillAstHoleVisitor;
import ia_parser.Exp;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.Operation;

import java.util.HashMap;

public class NullPointerReason implements SPFCaseReason {

    Expression objectRef;

    // Note: these are transient (per instantiation).
    // The instantiate(), simplify() and spfPredicate()
    // methods should be called in succession before
    // a subsequent call to instantiate(), or incorrect
    // results may occur.

    int instantiatedobjRef;
    Expression predicate = null;

    public NullPointerReason(Expression objectRef) {
        this.objectRef = objectRef;
    }

    public SPFCaseReason copy() {
        // we do not copy transient data...
        return new NullPointerReason(objectRef);
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


    public void instantiate(HoleExpression holeExpression, int instantiated) throws StaticRegionException {
        if(this.objectRef.equals(holeExpression))
            instantiatedobjRef = 0;
    }


    @Override
    public void simplify() throws StaticRegionException {
        if (instantiatedobjRef == 0)
            predicate = Operation.TRUE;
        else
            predicate = Operation.FALSE;

        // MWW: This relies on some information that is not yet stored in the holeHashMap: we need to
        // store the array size information in order to be able to access it here.
    }

    @Override
    public Expression getInstantiatedSPFPredicate() throws StaticRegionException {
        assert (predicate != null);
        return predicate;
    }
}
