package gov.nasa.jpf.symbc.veritesting;

import gov.nasa.jpf.symbc.VeritestingListener;
import gov.nasa.jpf.symbc.numeric.SymbolicInteger;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.Operation;


public class ExitTransition {
    private Expression nominalConstraint;
    private Expression negNominalConstraint;
    private Expression nominalRHS;
    private Expression negNominalRHS;
    private Expression pathConstraint;
    private String instructionName;

// pathConstraint is (pathLabelString = pathLabel)
// nominalConstraint is  pathConstraint => nominalRHS
// negNominalConstraint is pathconstraint => negNominalRHS

    public ExitTransition(Expression nominalRHS, String instructionName, Expression pathLabel){
        this.nominalRHS = nominalRHS;
        //this.negNominalRHS = new Operation(Operation.Operator.NOT, nominalRHS);
        this.negNominalRHS = new Operation(Operation.Operator.IMPLIES, nominalRHS, Operation.FALSE);
        this.pathConstraint = pathLabel;
        this.nominalConstraint = new Operation(Operation.Operator.IMPLIES, pathConstraint, nominalRHS);
        this.negNominalConstraint = new Operation(Operation.Operator.AND, pathConstraint, negNominalRHS);
        this.instructionName = instructionName;
    }

    public void setPathConstraint(Expression pathConstraint){
        this.pathConstraint = pathConstraint;
    }

    public Expression getNegNominalConstraint() {
        return negNominalConstraint;
    }


    public String getInstructionName() {
        return instructionName;
    }

    public Expression getNominalConstraint() {
        return nominalConstraint;
    }

    public Expression getNegNominalRHS() {
        return negNominalRHS;
    }


    public Expression getNominalRHS() {
        return nominalRHS;
    }


    public Expression getPathConstraint() {
        return pathConstraint;
    }

    @Override
    public String toString() {
        return("instruction = " + instructionName + ", pathConstraint = "+ pathConstraint + " = " +", nominalConstraint = " + nominalConstraint.toString() );
    }

    public void setNegNominalConstraint(Expression newConstraint) {
        this.negNominalConstraint = newConstraint;
    }

    public void setNominalConstraint(Expression nominalConstraint) {
        this.nominalConstraint = nominalConstraint;
    }
}
