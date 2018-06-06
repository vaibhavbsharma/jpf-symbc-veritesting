package gov.nasa.jpf.symbc.veritesting;


import za.ac.sun.cs.green.expr.Expression;

import java.util.LinkedHashSet;

public class FillArrayStoreOutput {
    boolean allOk;
    Expression additionalAST;
    private LinkedHashSet<HoleExpression> additionalOutputVars;

    public LinkedHashSet<HoleExpression> getAdditionalOutputVars() {
        return additionalOutputVars;
    }



    public Expression getAdditionalAST() {
        return additionalAST;
    }

    public FillArrayStoreOutput(boolean allOk, Expression additionalAST, LinkedHashSet<HoleExpression> additionalOutputVars) {
        this.additionalOutputVars = additionalOutputVars;
        this.allOk = allOk;
        this.additionalAST = additionalAST;
    }

    public boolean isOk() {
        return allOk;
    }
}
