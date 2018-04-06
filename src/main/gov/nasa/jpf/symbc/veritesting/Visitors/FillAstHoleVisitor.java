package gov.nasa.jpf.symbc.veritesting.Visitors;

import gov.nasa.jpf.symbc.veritesting.HoleExpression;
import gov.nasa.jpf.symbc.veritesting.StaticRegionException;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;

import java.util.HashMap;

/** MWW: FillAstHoleVisitor
 *   Performs a traversal of AST "hole" expressions filling them with
 *   instantiated expressions corresponding to the holes.
 */
public class FillAstHoleVisitor {

    HashMap<Expression, Expression> holeHashMap;

    public FillAstHoleVisitor(HashMap<Expression, Expression> holeHashMap) {
        this.holeHashMap = holeHashMap;
    }

    public Expression visit(Expression holeExpression) throws StaticRegionException {
        if (holeExpression instanceof HoleExpression && ((HoleExpression) holeExpression).isHole()) {
            //assert(holeHashMap.containsKey(holeExpression));
            if (!holeHashMap.containsKey(holeExpression)) {
                String exp = "visit does not know how to fill hole " + holeExpression.toString();
                System.out.println(exp);
                throw new StaticRegionException(exp);
            }
            Expression ret = holeHashMap.get(holeExpression);
            if (ret instanceof Operation) {
                Operation oldOperation = (Operation) ret;
                Operation newOperation = new Operation(oldOperation.getOperator(),
                        visit(oldOperation.getOperand(0)),
                        visit(oldOperation.getOperand(1)));
                return newOperation;
            }
            return ret;
        }
        if (holeExpression instanceof Operation) {
            Operation oldOperation = (Operation) holeExpression;
            Operation newOperation = new Operation(oldOperation.getOperator(),
                    visit(oldOperation.getOperand(0)),
                    visit(oldOperation.getOperand(1)));
            return newOperation;
        }
        return holeExpression;
    }
}