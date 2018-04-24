package gov.nasa.jpf.symbc.veritesting;

import za.ac.sun.cs.green.expr.Expression;

public class FillNonInputHolesOutput {
    public boolean result;
    public Expression fieldOutputExpression;

    public boolean result() {
        return result;
    }

    public Expression getFieldOutputExpression() {
        return fieldOutputExpression;
    }

    public FillNonInputHolesOutput(boolean result, Expression f) {
        this.result = result;
        this.fieldOutputExpression = f;
    }

}
