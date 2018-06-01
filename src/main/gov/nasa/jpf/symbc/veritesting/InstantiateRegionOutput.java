package gov.nasa.jpf.symbc.veritesting;

import za.ac.sun.cs.green.expr.Expression;

public class InstantiateRegionOutput {

    public FillHolesOutput fillHolesOutput;
    public Expression summaryExpression;

    public InstantiateRegionOutput(Expression finalSummaryExpression, FillHolesOutput fillHolesOutput) {
        this.fillHolesOutput = fillHolesOutput;
        this.summaryExpression = finalSummaryExpression;
    }
}
