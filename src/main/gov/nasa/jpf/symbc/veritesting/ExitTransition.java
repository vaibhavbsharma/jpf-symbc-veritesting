package gov.nasa.jpf.symbc.veritesting;

import za.ac.sun.cs.green.expr.Expression;

public class NominalTransition {
    public int pathLabel;
    public String pathLabelString;
    public Expression nominalConstraint;
    public Expression negNominalConstraint;
    private Expression nominalRHS;
    private Expression negNominalRHS;
    public String instructionName;


    public NominalTransition(Expression Constraint, String instructionName, String pathLabelString, int pathLabel){
        this.pathLabelString = pathLabelString;
        this.pathLabel = pathLabel;
        this.nominalConstraint = nominalConstraint;
        this.instructionName = instructionName;
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

    public int getPathLabel() {
        return pathLabel;
    }

    public String getPathLabelString() {
        return pathLabelString;
    }

    public void setNominalConstraint(Expression nominalConstraint) {
        this.nominalConstraint = nominalConstraint;
    }

    public void setPathLabel(int exceptionPathLabel) {
        this.pathLabel = exceptionPathLabel;
    }

    public void setInstructionName(String instructionName) {
        this.instructionName = instructionName;
    }

    public void setPathLabelString(String pathLabelString) {
        this.pathLabelString = pathLabelString;
    }

    @Override
    public String toString() {
        return("instruction = " + instructionName + ", "+ pathLabelString + " = " + pathLabel +", nominalConstraint = " + nominalConstraint.toString() );
    }
}
