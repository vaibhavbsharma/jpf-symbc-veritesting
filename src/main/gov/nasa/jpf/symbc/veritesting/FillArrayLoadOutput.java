package gov.nasa.jpf.symbc.veritesting;


import za.ac.sun.cs.green.expr.Expression;

public class FillArrayLoadOutput {
    boolean allOk;
    Expression additionalAST;

    public Expression getAdditionalAST() {
        return additionalAST;
    }

    public FillArrayLoadOutput(boolean allOk, Expression additionalAST) {
        this.allOk = allOk;
        this.additionalAST = additionalAST;
    }

    public boolean isOk() {
        return allOk;
    }
}
