package gov.nasa.jpf.symbc.veritesting;

import gov.nasa.jpf.symbc.numeric.GreenToSPFTranslator;
import gov.nasa.jpf.symbc.numeric.solvers.SolverTranslator;
import za.ac.sun.cs.green.expr.Expression;

public class ExpressionUtil {

    public static gov.nasa.jpf.symbc.numeric.Expression GreenToSPFExpression(Expression greenExpression) {
        GreenToSPFTranslator toSPFTranslator = new GreenToSPFTranslator();
        return toSPFTranslator.translate(greenExpression);
    }

    public static Expression SPFToGreenExpr(gov.nasa.jpf.symbc.numeric.Expression spfExp) {
        SolverTranslator.Translator toGreenTranslator = new SolverTranslator.Translator();
        spfExp.accept(toGreenTranslator);
        return toGreenTranslator.getExpression();
    }
}
