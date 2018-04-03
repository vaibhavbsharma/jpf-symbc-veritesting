package gov.nasa.jpf.symbc.veritesting.SPFCase;

import gov.nasa.jpf.symbc.veritesting.StaticRegionException;
import gov.nasa.jpf.symbc.veritesting.Visitors.FillAstHoleVisitor;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;

import java.util.HashMap;

public class SPFCase {
    private Expression pathConstraint;
    private Expression instantiatedPathConstraint = null;
    private SPFCaseReason reason;

    public SPFCase(Expression pathConstraint, SPFCaseReason reason) {
        this.pathConstraint = pathConstraint;
        this.reason = reason;
    }

    void instantiate(HashMap<Expression, Expression> holeHashMap) throws StaticRegionException {
        reason.instantiate(holeHashMap);
        FillAstHoleVisitor visitor = new FillAstHoleVisitor(holeHashMap);
        instantiatedPathConstraint = visitor.visit(pathConstraint);
    }

    void simplify() throws StaticRegionException {
        reason.simplify();
    }

    void embedPathConstraint(Expression e) throws StaticRegionException {
        pathConstraint = new Operation(Operation.Operator.AND, e, pathConstraint);
    }

    Expression spfPredicate() throws StaticRegionException {
        Expression spfConstraint =
                new Operation(Operation.Operator.AND, instantiatedPathConstraint, reason.getInstantiatedSPFPredicate());
        return spfConstraint;
    }

}
