package gov.nasa.jpf.symbc.veritesting.SPFCase;

/**
 * The SPFCase interface
 *
 * There are various situations that our static regions cannot handle; for example,
 * paths that lead to object creation or exceptions.  The SPFCase interface
 * provides an abstract description of the reason for using SPF rather than handling
 * it in the static region.  The interface contains three methods:
 *  instantiate: which instantiates any 'holes' in the Expression associated with the reason
 *  simplify: which performs any simplifications possible after instantiation
 *  getInstantiatedSPFPredicate: which returns the instantiated predicate associated with
 *    the reason, or throws an exception if not instantiated.
 *
 * @author  Zara Ali
 * @version 1.0
 * @since   2014-03-31
 */

import gov.nasa.jpf.symbc.veritesting.StaticRegionException;
import za.ac.sun.cs.green.expr.Expression;
import java.util.HashMap;

public interface SPFCaseReason {

    // Instantiate should fill in the holes for the predicate associated with the reason
    void instantiate(HashMap<Expression, Expression> holeHashMap) throws StaticRegionException;
    void simplify() throws StaticRegionException;
    Expression getInstantiatedSPFPredicate() throws StaticRegionException;

}
