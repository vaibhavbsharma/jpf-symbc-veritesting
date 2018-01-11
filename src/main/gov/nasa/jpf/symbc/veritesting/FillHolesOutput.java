package gov.nasa.jpf.symbc.veritesting;

import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;

import java.util.HashMap;

public class FillHolesOutput extends HashMap<Expression, Expression> {
    public HashMap<Expression, Expression> holeHashMap;
    public Operation additionalAST;
    public FillHolesOutput(HashMap<Expression, Expression> h, Operation additionalAST) {
        this.holeHashMap = h;
        this.additionalAST = additionalAST;
    }
}
