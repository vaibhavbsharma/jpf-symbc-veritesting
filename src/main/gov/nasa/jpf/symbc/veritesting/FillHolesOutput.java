package gov.nasa.jpf.symbc.veritesting;

import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

public class FillHolesOutput {
    public LinkedHashMap<Expression, Expression> holeHashMap;
    public Expression additionalAST;
    public LinkedHashSet<HoleExpression> additionalOutputVars;
    public FillHolesOutput(LinkedHashMap<Expression, Expression> h, Expression additionalAST,
                           LinkedHashSet<HoleExpression> additionalOutputVars) {
        this.holeHashMap = h;
        this.additionalAST = additionalAST;
        this.additionalOutputVars = additionalOutputVars;
    }
}
