package gov.nasa.jpf.symbc.veritesting.SPFCase;

import gov.nasa.jpf.symbc.veritesting.HoleExpression;
import gov.nasa.jpf.symbc.veritesting.StaticRegionException;
import gov.nasa.jpf.symbc.veritesting.Visitors.FillAstHoleVisitor;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.Operation;

import java.util.HashMap;

public class ArrayBoundsReason implements SPFCaseReason {

    Expression arrayExpr;
    Expression indexExpr;
    Expression arrayLengthExpr;

    // Note: these are transient (per instantiation).
    // The instantiate(), simplify() and spfPredicate()
    // methods should be called in succession before
    // a subsequent call to instantiate(), or incorrect
    // results may occur.

    Expression instantiatedArrayExpr = null;
    Expression instantiatedIndexExpr = null;
    Expression instantiatedLengthExpr = null;
    Expression predicate = null;

    public ArrayBoundsReason(Expression arrayExpr, Expression indexExpr, Expression arrayLengthExpr) {
        this.arrayExpr = arrayExpr;
        this.indexExpr = indexExpr;
        this.arrayLengthExpr = arrayLengthExpr;
    }

    public SPFCaseReason copy() {
        // we do not copy transient data...
        return new ArrayBoundsReason(arrayExpr, indexExpr, arrayLengthExpr);
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
        FillAstHoleVisitor visitor  = new FillAstHoleVisitor(holeHashMap);

        instantiatedArrayExpr = visitor.visit(arrayExpr);
        instantiatedIndexExpr = visitor.visit(indexExpr);
        instantiatedLengthExpr = new IntConstant(((HoleExpression)arrayLengthExpr).getArrayInfo().length());
    }

    @Override
    public void simplify() throws StaticRegionException {
        assert(instantiatedArrayExpr != null);
        assert(instantiatedIndexExpr != null);
        assert(instantiatedLengthExpr != null);

        // optimized case for concrete index/length
        if (instantiatedIndexExpr instanceof IntConstant &&
                instantiatedLengthExpr instanceof IntConstant) {
            int index = ((IntConstant)instantiatedIndexExpr).getValue();
            int length = ((IntConstant)instantiatedLengthExpr).getValue();

            if (index < 0 && index >= length) {
                predicate = Operation.TRUE;
            } else {
                predicate = Operation.FALSE;
            }
        } else {
            predicate = new Operation(Operation.Operator.OR,
                    new Operation(Operation.Operator.LT, instantiatedIndexExpr, new IntConstant(0)),
                    new Operation(Operation.Operator.GE, instantiatedIndexExpr, instantiatedLengthExpr));
        }

        // MWW: This relies on some information that is not yet stored in the holeHashMap: we need to
        // store the array size information in order to be able to access it here.

        // What should happen:
        //   Check to see if index is concrete.  If within bounds, set predicate to false.
    }

    @Override
    public Expression getInstantiatedSPFPredicate() throws StaticRegionException {
        assert(predicate != null);
        return predicate;
    }
}
