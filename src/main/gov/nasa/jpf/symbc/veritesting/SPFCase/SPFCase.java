package gov.nasa.jpf.symbc.veritesting.SPFCase;

import gov.nasa.jpf.symbc.veritesting.StaticRegionException;
import gov.nasa.jpf.symbc.veritesting.Visitors.FillAstHoleVisitor;
import ia_parser.Exp;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;

import java.util.HashMap;

import static gov.nasa.jpf.symbc.veritesting.ExpressionUtil.replaceCondition;

public class SPFCase {
    private Expression pathConstraint;
    private Expression instantiatedPathConstraint = null;
    private SPFCaseReason reason;
    private boolean isMethodCase = false;

     public void setIsMethodCase (boolean isMethodCase){
         this.isMethodCase = isMethodCase;
     }

     public boolean getIsMethodCase(){
         return isMethodCase;
     }

    public SPFCase(Expression pathConstraint, SPFCaseReason reason) {
        this.pathConstraint = pathConstraint;
        this.reason = reason;
    }

    public boolean isInstantiated(){
        return (instantiatedPathConstraint!=null);
    }

    void instantiate(HashMap<Expression, Expression> holeHashMap) throws StaticRegionException {
        reason.instantiate(holeHashMap);
        if(isMethodCase)
            instantiatedPathConstraint = Operation.TRUE;
        else {
            FillAstHoleVisitor visitor = new FillAstHoleVisitor(holeHashMap);
            instantiatedPathConstraint = visitor.visit(pathConstraint);
        }
    }

    void simplify() throws StaticRegionException {
        reason.simplify();
    }

    SPFCase cloneEmbedPathConstraint(Expression e, Expression cond) throws StaticRegionException {
        Expression exp = new Operation(Operation.Operator.AND, e, replaceCondition(pathConstraint, cond));
        return new SPFCase(exp, reason.copy());
    }




    Expression spfPredicate() throws StaticRegionException {
        Expression spfConstraint =
                new Operation(Operation.Operator.AND, instantiatedPathConstraint, reason.getInstantiatedSPFPredicate());
        return spfConstraint;
    }

    public void spfReplaceCondition(Expression cond) {
        pathConstraint = replaceCondition(pathConstraint, cond);
    }

    public SPFCase cloneSPFCase() {
        SPFCase newSPFCase = new SPFCase(this.pathConstraint, this.reason);
        return  newSPFCase;
    }
}
