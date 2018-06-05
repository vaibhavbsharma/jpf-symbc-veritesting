package gov.nasa.jpf.symbc.veritesting;


import za.ac.sun.cs.green.expr.Expression;

public class FillArrayOutput {
    boolean allOk;
    Expression additionalAST;

    public Expression getAdditionalAST() {
        return additionalAST;
    }

    public FillArrayOutput(boolean allOk, Expression additionalAST) {
        this.allOk = allOk;
        this.additionalAST = additionalAST;
    }

    public boolean isOk() {
        return allOk;
    }
}
