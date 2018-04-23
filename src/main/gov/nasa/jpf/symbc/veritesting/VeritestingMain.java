package gov.nasa.jpf.symbc.veritesting;


/*
  Command used to copy WALA jar files to jpf-symbc/lib
  for file in `ls -l ~/git_repos/MyWALA/ | grep ^d | tr -s ' ' | cut -d ' ' -f 9 | grep -v jars | grep -v target`; do
    cp ~/git_repos/MyWALA/$file/target/*.jar ~/IdeaProjects/jpf-symbc/lib/;
  done
*/
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import com.ibm.wala.cfg.Util;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.NumberedGraph;
import com.ibm.wala.util.graph.dominators.Dominators;
import com.ibm.wala.util.graph.dominators.NumberedDominators;
import com.ibm.wala.util.graph.impl.GraphInverter;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.StringStuff;
import gov.nasa.jpf.symbc.VeritestingListener;
import gov.nasa.jpf.symbc.veritesting.SPFCase.SPFCaseList;
import gov.nasa.jpf.symbc.veritesting.Visitors.MyIVisitor;
import x10.wala.util.NatLoop;
import x10.wala.util.NatLoopSolver;

import static gov.nasa.jpf.symbc.veritesting.ReflectUtil.getSignature;

import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.Operation;


public class VeritestingMain {

    public int pathLabelVarNum = 0;
    public HashSet endingInsnsHash;
    ClassHierarchy cha;
    HashSet<String> methodSummaryClassNames, methodSummarySubClassNames;
    private boolean methodAnalysis = false;

    // Relevant only if this region is a method summary
    // Used to point to the object on which the method will be called
    // Useful to get fields of the object in this method summary
    int objectReference = -1;
    SSACFG cfg;
    HashSet startingPointsHistory;
    String currentClassName, currentMethodName, methodSig;
    VarUtil varUtil;
    HashSet<NatLoop> loops;
    IR ir;

    public VeritestingMain(String appJar) {
        try {
            appJar = System.getenv("TARGET_CLASSPATH_WALA");// + appJar;
            System.out.println("appJar = " + appJar);
            AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(appJar,
                    (new FileProvider()).getFile(CallGraphTestUtil.REGRESSION_EXCLUSIONS));
            cha = ClassHierarchyFactory.make(scope);
            methodSummaryClassNames = new HashSet<String>();
            VeritestingListener.veritestingRegions = new HashMap<String, VeritestingRegion>();
        } catch (WalaException | IOException e) {
            e.printStackTrace();
        }
    }
    /*public static void main(String[] args) {
        endingInsnsHash = new HashSet();
        new MyAnalysis(args[1], args[3]);
    }*/
    public int getObjectReference() {
        return objectReference;
    }
    public void setObjectReference(int objectReference) {
        this.objectReference = objectReference;
    }

    public void analyzeForVeritesting(String classPath, String _className) {
        // causes java.lang.IllegalArgumentException: ill-formed sig testMe4(int[],int)
        endingInsnsHash = new HashSet();
        methodAnalysis = false;
        findClasses(classPath, _className);

        try {
            File f = new File(classPath);
            URL[] cp = new URL[]{f.toURI().toURL()};
            URLClassLoader urlcl = new URLClassLoader(cp);
            Class c = urlcl.loadClass(_className);
            Method[] allMethods = c.getDeclaredMethods();
            for (Method m : allMethods) {
                String signature = getSignature(m);
                startAnalysis(_className,signature);
            }
            methodSummarySubClassNames = new HashSet<String>();
            for(Iterator it = methodSummaryClassNames.iterator(); it.hasNext();) {
                String methodSummaryClassName = (String) it.next();
                Class cAdditional;
                try {
                    cAdditional = urlcl.loadClass(methodSummaryClassName);
                } catch (ClassNotFoundException e) { continue; }
                Method[] allMethodsAdditional = cAdditional.getDeclaredMethods();
                for (Method m: allMethodsAdditional) {
                    String signature = getSignature(m);
                    MethodReference mr = StringStuff.makeMethodReference(methodSummaryClassName + "." + signature);
                    IMethod iMethod = cha.resolveMethod(mr);
                    if (iMethod == null) {
                        System.out.println("could not resolve " + mr);
                        continue;
                    }
                    IClass iClass = iMethod.getDeclaringClass();
                    for(IClass subClass: cha.computeSubClasses(iClass.getReference())) {
                        if(iClass.equals(subClass)) continue;
                        methodSummarySubClassNames.add(subClass.getReference().getName().getClassName().toString());
                    }
                    //Only need to add subclass once for all the methods in the class
                    break;
                }
            }
            //find veritesting regions inside all the methods discovered so far
            methodSummaryClassNames.addAll(methodSummarySubClassNames);
            for(Iterator it = methodSummaryClassNames.iterator(); it.hasNext();) {
                String methodSummaryClassName = (String) it.next();
                Class cAdditional;
                try { cAdditional = urlcl.loadClass(methodSummaryClassName); }
                catch (ClassNotFoundException e) { continue; }
                Method[] allMethodsAdditional = cAdditional.getDeclaredMethods();
                for (Method m : allMethodsAdditional) {
                    String signature = getSignature(m);
                    startAnalysis(methodSummaryClassName, signature);
                }
            }
            //summarize methods inside all methods discovered so far
            methodAnalysis = true;
            for(Iterator it = methodSummaryClassNames.iterator(); it.hasNext();) {
                String methodSummaryClassName = (String) it.next();
                Class cAdditional;
                try { cAdditional = urlcl.loadClass(methodSummaryClassName); }
                catch (ClassNotFoundException e) { continue; }
                Method[] allMethodsAdditional = cAdditional.getDeclaredMethods();
                for (Method m : allMethodsAdditional) {
                    String signature = getSignature(m);
                    startAnalysis(methodSummaryClassName, signature);
                }
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void findClasses(String classPath, String startingClassName) {

        methodSummaryClassNames.add(startingClassName);
        HashSet<String> newClassNames;
        do {
            newClassNames = new HashSet<>();
            for (String className : methodSummaryClassNames) {
                File f = new File(classPath);
                URL[] cp = new URL[0];
                try {
                    cp = new URL[]{f.toURI().toURL()};
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
                URLClassLoader urlcl = new URLClassLoader(cp);
                Class c = null;
                try {
                    c = urlcl.loadClass(className);
                } catch (ClassNotFoundException e) {
                    continue;
                }
                if(c == null) continue;
                Method[] allMethods = c.getDeclaredMethods();
                for (Method method : allMethods) {
                    String signature = getSignature(method);
                    MethodReference mr = StringStuff.makeMethodReference(className + "." + signature);
                    IMethod iMethod = cha.resolveMethod(mr);
                    if(iMethod == null)
                        continue;
                    AnalysisOptions options = new AnalysisOptions();
                    options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes());
                    IAnalysisCacheView cache = new AnalysisCacheImpl(options.getSSAOptions());
                    ir = cache.getIR(iMethod, Everywhere.EVERYWHERE);
                    Iterator<CallSiteReference> iterator = ir.iterateCallSites();
                    while (iterator.hasNext()) {
                        CallSiteReference reference = iterator.next();
                        MethodReference methodReference = reference.getDeclaredTarget();
                        String declaringClass = methodReference.getDeclaringClass().getName().getClassName().toString();
                        if (!methodSummaryClassNames.contains(declaringClass)) {
                            newClassNames.add(declaringClass);
                        }
                    }
                }
            }
            methodSummaryClassNames.addAll(newClassNames);
        } while(newClassNames.size() != 0);
    }

    public void startAnalysis(String className, String methodSig) {
        try {
            startingPointsHistory = new HashSet();
            MethodReference mr = StringStuff.makeMethodReference(className + "." + methodSig);
            IMethod m = cha.resolveMethod(mr);
            if (m == null) {
                System.out.println("could not resolve " + mr);
                return;
                //Assertions.UNREACHABLE("could not resolve " + mr);
            }
            AnalysisOptions options = new AnalysisOptions();
            options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes());
            IAnalysisCacheView cache = new AnalysisCacheImpl(options.getSSAOptions());
            ir = cache.getIR(m, Everywhere.EVERYWHERE);
            if (ir == null) {
                Assertions.UNREACHABLE("Null IR for " + m);
            }
            cfg = ir.getControlFlowGraph();
            currentClassName = m.getDeclaringClass().getName().getClassName().toString();
            currentMethodName = m.getName().toString();
            this.methodSig = methodSig.substring(methodSig.indexOf('('));
            System.out.println("Starting analysis for " + currentMethodName + "(" + currentClassName + "." + methodSig + ")");
            varUtil = new VarUtil(ir, currentClassName, currentMethodName);
            NumberedDominators<ISSABasicBlock> uninverteddom =
                    (NumberedDominators<ISSABasicBlock>) Dominators.make(cfg, cfg.entry());
            loops = new HashSet<>();
            HashSet<Integer> visited = new HashSet<>();
            NatLoopSolver.findAllLoops(cfg, uninverteddom, loops, visited, cfg.getNode(0));
            // Here is where the magic happens.
            if(!methodAnalysis)
                doAnalysis(cfg.entry(), null);
            else doMethodAnalysis(cfg.entry(), cfg.exit());
        } catch (InvalidClassFileException e) {
            e.printStackTrace();
        }
    }

    public boolean isLoopStart(ISSABasicBlock b) {
        Iterator var1 = loops.iterator();

        while (var1.hasNext()) {
            NatLoop var3 = (NatLoop) var1.next();
            if (b == var3.getStart()) return true;
        }
        return false;
    }

    // MWW TODO: Add support for static exceptions into veritesting region.
    // MWW: This method takes 9 parameters!
    public VeritestingRegion constructVeritestingRegion(
            Expression thenExpr,
            Expression elseExpr,
            final Expression thenPLAssignSPF,
            final Expression elsePLAssignSPF,
            ISSABasicBlock currUnit, ISSABasicBlock commonSucc,
            int thenUseNum, int elseUseNum,
            HashSet<Integer> summarizedRegionStartBB) throws InvalidClassFileException {
        /****SH: Begin of handling skipping of both branches, thus we create no region*****/
//        if((thenUseNum == -1)&&(elseUseNum == -1))
//            return null;
        /****SH: End of handling skipping of both branches, thus we create no region *****/

        if (thenExpr != null)
            thenExpr = new Operation(Operation.Operator.AND, thenExpr, thenPLAssignSPF);
        else thenExpr = thenPLAssignSPF;
        if (elseExpr != null)
            elseExpr = new Operation(Operation.Operator.AND, elseExpr, elsePLAssignSPF);
        else elseExpr = elsePLAssignSPF;

        // (If && thenExpr) || (ifNot && elseExpr)
        HoleExpression condition = new HoleExpression(varUtil.nextInt(), currentClassName, currentMethodName);
        condition.setHole(true, HoleExpression.HoleType.CONDITION);
        HoleExpression negCondition = new HoleExpression(varUtil.nextInt(), currentClassName, currentMethodName);
        negCondition.setHole(true, HoleExpression.HoleType.NEGCONDITION);
        varUtil.holeHashMap.put(condition, condition);
        varUtil.holeHashMap.put(negCondition, negCondition);
        Expression pathExpr1 =
                new Operation(Operation.Operator.OR,
                        new Operation(Operation.Operator.AND, condition, thenExpr),
                        new Operation(Operation.Operator.AND, negCondition, elseExpr));

        MyIVisitor myIVisitor = new MyIVisitor(varUtil, thenUseNum, elseUseNum, true);
        Expression phiExprSPF, finalPathExpr = pathExpr1;
        Iterator<SSAInstruction> iterator = commonSucc.iterator();
        while(iterator.hasNext()) {
            // visit instructions one at a time, break on the first non-phi statement because a region summary starts at
            // the condition and ends at the meet point. The meet point ends at the first non-phi statement. Any outer
            // region that encapsulates this inner region will have to summarize statements that appear after the first
            // non-phi statement in this basic block.
            iterator.next().visit(myIVisitor);
            if (myIVisitor.hasPhiExpr()) {
                phiExprSPF = myIVisitor.getPhiExprSPF(thenPLAssignSPF, elsePLAssignSPF);
                finalPathExpr =
                        new Operation(Operation.Operator.AND, finalPathExpr, phiExprSPF);
            } else break;
        }

        int startingBC = ((IBytecodeMethod) (ir.getMethod())).getBytecodeIndex(currUnit.getLastInstructionIndex());
        int endingBC = ((IBytecodeMethod) (ir.getMethod())).getBytecodeIndex(commonSucc.getFirstInstructionIndex());

        VeritestingRegion veritestingRegion = new VeritestingRegion();
        veritestingRegion.setSummaryExpression(finalPathExpr);
        veritestingRegion.setStartInsnPosition(startingBC);
        veritestingRegion.setEndInsnPosition(endingBC);
        HashSet<Expression> hashSet = new HashSet<>();
        for(Expression e: varUtil.defLocalVars) {
            hashSet.add(e);
        }
        veritestingRegion.setOutputVars(hashSet);
        veritestingRegion.setClassName(currentClassName);
        veritestingRegion.setMethodName(currentMethodName);
        veritestingRegion.setMethodSignature(methodSig);
        veritestingRegion.setStartBBNum(currUnit.getNumber());
        veritestingRegion.setEndBBNum(commonSucc.getNumber());
        LinkedHashMap<Expression, Expression> hashMap = new LinkedHashMap<>();
        for(Map.Entry<Expression, Expression> entry: varUtil.holeHashMap.entrySet()) {
            hashMap.put(entry.getKey(), entry.getValue());
        }
        veritestingRegion.setHoleHashMap(hashMap);
        veritestingRegion.setSummarizedRegionStartBB(summarizedRegionStartBB);

        // MWW: adding the spfCases
        veritestingRegion.setSpfCases(varUtil.getSpfCases());
        // MWW: end addition

        pathLabelVarNum++;
        return veritestingRegion;
    }

    // MWW: WTF?!?
    // MWW: A two-hundred-line method?
    // MWW: I feel a great disturbance in the force...

    // What can we tease apart here?
    //
    // For one thing, we should simply construct a new varUtil each time this method is called; when
    // we recursively invoke it, we just save off the entire structure rather than do it piecemeal.

    public void doAnalysis(ISSABasicBlock startingUnit, ISSABasicBlock endingUnit) throws InvalidClassFileException {
        //System.out.println("Starting doAnalysis");
        boolean thenCreateThrow = false;
        boolean elseCreateThrow = false;

        ISSABasicBlock currUnit = startingUnit;
        if (startingPointsHistory.contains(startingUnit)) return;
        Expression methodExpression = null;
        HashSet<Integer> methodSummarizedRegionStartBB = new HashSet<>();
        while (true) {
            if (currUnit == null || currUnit == endingUnit) break;

            /*
                Get the list of normal successors (excluding JVM-generated exceptions) and find find their
                common successor.  If there is no successors, exit, and if only one successor, immediately
                continue exploration.  The interesting case involves 2 successors.
                endingUnit can be null.

                When we rewrite this function, I would make varUtil an argument, which I would generate fresh
                for each iteration and copy into the parent context as needed.  Rather than having while(true),
                I would use recursion for continuations uniformly.


                I would have a co-recursive function that actually built the veritesting region
             */
            List<ISSABasicBlock> succs = new ArrayList<>(cfg.getNormalSuccessors(currUnit));
            ISSABasicBlock commonSucc = cfg.getIPdom(currUnit.getNumber());
            if (succs.size() == 1) {
                currUnit = succs.get(0);
                continue;
            } else if (succs.size() == 0) break;
            else if (succs.size() == 2 && startingPointsHistory.contains(currUnit)) {
                currUnit = commonSucc;
                break;
            } else if (succs.size() == 2 && !startingPointsHistory.contains(currUnit)) {
                startingPointsHistory.add(currUnit);
                //fix this varUtil reset because it screws up varUtil.holeHashMap

                // MWW: why is this reset here?  Why does it not occur prior to the
                // other recursive call for doAnalysis?
                varUtil.reset();

                ISSABasicBlock thenUnit = Util.getTakenSuccessor(cfg, currUnit);
                ISSABasicBlock elseUnit = Util.getNotTakenSuccessor(cfg, currUnit);
                if (isLoopStart(currUnit)) {
                    doAnalysis(thenUnit, null);
                    doAnalysis(elseUnit, null);
                    return;
                }

                // constructing path labels.
                Expression thenExpr = null, elseExpr = null;
                String pathLabelString = "pathLabel" + varUtil.getPathCounter();
                final int thenPathLabel = varUtil.getPathCounter();
                final int elsePathLabel = varUtil.getPathCounter();
                ISSABasicBlock thenPred = thenUnit, elsePred = elseUnit;
                int thenUseNum = -1, elseUseNum = -1;
                Expression pathLabel = varUtil.makeIntermediateVar(pathLabelString, true);
                final Expression thenPLAssignSPF =
                        new Operation(Operation.Operator.EQ, pathLabel,
                                new IntConstant(thenPathLabel));
                final Expression elsePLAssignSPF =
                        new Operation(Operation.Operator.EQ, pathLabel,
                                new IntConstant(elsePathLabel));
                boolean canVeritest = true;
                HashSet<Integer> summarizedRegionStartBB = new HashSet<>();
                summarizedRegionStartBB.add(currUnit.getNumber());

                // Create thenExpr
                while (thenUnit != commonSucc) { // the meet point not reached yet
                    while(cfg.getNormalSuccessors(thenUnit).size() > 1 && thenUnit != commonSucc && canVeritest) {  //TODO: Support exceptionalSuccessors in the future
                        if (VeritestingListener.veritestingMode < 2) {
                            canVeritest = false;
                            break;
                        }
                        //instead of giving up, try to compute a summary of everything from thenUnit up to commonSucc
                        //to allow complex regions

                        HashMap<Expression, Expression> savedHoleHashMap = saveHoleHashMap();
                        HashMap<String, Expression> savedVarCache = saveVarCache();

                        // MWW : recursive call
                        doAnalysis(thenUnit, commonSucc);
                        for(Map.Entry<Expression, Expression> entry: savedHoleHashMap.entrySet()) { //restore holeHshMap
                            varUtil.holeHashMap.put(entry.getKey(), entry.getValue());
                        }
                        for(Map.Entry<String, Expression> entry: savedVarCache.entrySet()) { //restore varCache
                            varUtil.varCache.put(entry.getKey(), entry.getValue());
                        }
                        int offset = ((IBytecodeMethod) (ir.getMethod())).getBytecodeIndex(thenUnit.getLastInstructionIndex());
                        String key = currentClassName + "." + currentMethodName + methodSig + "#" + offset;

                        // MWW: working with child region here
                        if(VeritestingListener.veritestingRegions.containsKey(key)) { // we are able to summarize the inner region, try to sallow it
                            System.out.println("Veritested inner region with key = " + key);
                            //visit all instructions up to and including the condition
                            BlockSummary blockSummary = new BlockSummary(thenUnit, thenExpr, canVeritest).invoke();
                            canVeritest = blockSummary.isCanVeritest();
                            thenExpr = blockSummary.getExpression(); // outer region thenExpr
                            Expression conditionExpression = blockSummary.getIfExpression();
                            //cannot handle returns inside a if-then-else
                            if(blockSummary.getIsExitNode()) canVeritest = false;
                            if(!canVeritest) break;
                            ISSABasicBlock commonSuccthenUnit = cfg.getIPdom(thenUnit.getNumber());

                            //invariant: outer region meetpoint postdominate inner region meet point
                            NumberedGraph<ISSABasicBlock> invertedCFG = GraphInverter.invert(cfg);
                            NumberedDominators<ISSABasicBlock> postDom = (NumberedDominators<ISSABasicBlock>)
                                    Dominators.make(invertedCFG, cfg.exit());
                            boolean bPostDom = (postDom.isDominatedBy(commonSuccthenUnit, commonSucc));
                            assert(bPostDom);
                            //start swallow holes from inner region to the outer region by taking a copy of the inner holes to the outer region
                            VeritestingRegion innerRegion = VeritestingListener.veritestingRegions.get(key);

                            // MWW: new code.  Note: really exception should never occur, so perhaps this is *too*
                            // defensive.
                            try {
                                assert(conditionExpression != null);
                                SPFCaseList innerCases = innerRegion.getSpfCases().cloneEmbedPathConstraint(conditionExpression);
                                varUtil.getSpfCases().addAll(innerCases);
                            } catch (StaticRegionException sre) {
                                System.out.println("Unable to instantiate spfCases: " + sre.toString());
                                canVeritest = false;
                                break;
                            }
                            // MWW: end of new code

                            for(Expression e: innerRegion.getOutputVars()) {
                                varUtil.defLocalVars.add(e);
                            }
                            for(Map.Entry<Expression, Expression> entry: innerRegion.getHoleHashMap().entrySet()) {
                                varUtil.holeHashMap.put(entry.getKey(), entry.getValue());
                                if(((HoleExpression)entry.getKey()).getHoleType() == HoleExpression.HoleType.CONDITION ||
                                        ((HoleExpression)entry.getKey()).getHoleType() == HoleExpression.HoleType.NEGCONDITION)
                                    varUtil.holeHashMap.remove(entry.getKey());
                            }
                            Expression thenExpr1 = innerRegion.getSummaryExpression();
                            thenExpr1 = replaceCondition(thenExpr1, conditionExpression);
                            if (thenExpr1 != null) {
                                if (thenExpr != null)
                                    thenExpr =
                                            new Operation(Operation.Operator.AND,
                                                    thenExpr, thenExpr1);
                                else thenExpr = thenExpr1;
                            }
                            thenPred = null;
                            thenUnit = commonSuccthenUnit;
                            summarizedRegionStartBB.addAll(innerRegion.summarizedRegionStartBB);
                        } else canVeritest = false;
                    }
                    if (!canVeritest || thenUnit == commonSucc) break;
                    BlockSummary blockSummary = new BlockSummary(thenUnit, thenExpr, canVeritest, thenPLAssignSPF).invoke();
                    canVeritest = blockSummary.isCanVeritest();
                    thenExpr = blockSummary.getExpression();
                    //we should not encounter a BB with more than one successor at this point
                    assert(blockSummary.getIfExpression() == null);
                    //cannot handle returns inside a if-then-else
                    if(blockSummary.getIsExitNode()) canVeritest = false;
                    //SH: supporting new object or throw instructions
                    if (!canVeritest) break;
                    if (blockSummary.hasNewOrThrow){ //SH: skip to the end of the region when a new Object or throw instruction encountered
                        thenUnit = commonSucc;
                        thenPred = null;
                        break;
                    }
                    thenPred = thenUnit;
                    thenUnit = cfg.getNormalSuccessors(thenUnit).iterator().next();
                    if (thenUnit == endingUnit) break;
                }
                // if there is no "then" side, then set then's predecessor to currUnit
                if (canVeritest && (thenPred == commonSucc)) thenPred = currUnit;

                //move static analysis deeper into the then-side searching for veritesting regions
                if (!canVeritest) {
                    while(thenUnit != commonSucc) {
                        if(cfg.getNormalSuccessors(thenUnit).size() > 1) {
                            doAnalysis(thenUnit, commonSucc);
                            break;
                        }
                        if(cfg.getNormalSuccessors(thenUnit).size() == 0) break;
                        thenUnit = cfg.getNormalSuccessors(thenUnit).iterator().next();
                    }
                }

                // Create elseExpr
                while (canVeritest && elseUnit != commonSucc) {
                    while(cfg.getNormalSuccessors(elseUnit).size() > 1 && elseUnit != commonSucc && canVeritest) {
                        if (VeritestingListener.veritestingMode < 2) {
                            canVeritest = false;
                            break;
                        }
                        //instead of giving up, try to compute a summary of everything from elseUnit up to commonSucc
                        //to allow complex regions
                        HashMap<Expression, Expression> savedHoleHashMap = saveHoleHashMap();
                        HashMap<String, Expression> savedVarCache = saveVarCache();
                        SPFCaseList savedCaseList = varUtil.getSpfCases();

                        doAnalysis(elseUnit, commonSucc);
                        // MWW: Note: you can merge maps in one go with putAll as in e.g., the following:
                        // varUtil.holeHashMap.putAll(savedHoleHashMap);
                        // so these loops are not necessary.

                        // Also: doesn't this information get added *again* when we take it from the
                        // inner veritestingRegion?
                        for(Map.Entry<Expression, Expression> entry: savedHoleHashMap.entrySet()) {
                            varUtil.holeHashMap.put(entry.getKey(), entry.getValue());
                        }
                        for(Map.Entry<String, Expression> entry: savedVarCache.entrySet()) {
                            varUtil.varCache.put(entry.getKey(), entry.getValue());
                        }

                        int offset = ((IBytecodeMethod) (ir.getMethod())).getBytecodeIndex(elseUnit.getLastInstructionIndex());
                        String key = currentClassName + "." + currentMethodName + methodSig + "#" + offset;
                        if(VeritestingListener.veritestingRegions.containsKey(key)) {
                            System.out.println("Veritested inner region with key = " + key);
                            //visit all instructions up to and including the condition
                            BlockSummary blockSummary = new BlockSummary(elseUnit, elseExpr, canVeritest).invoke();
                            canVeritest = blockSummary.isCanVeritest();
                            elseExpr = blockSummary.getExpression();
                            Expression conditionExpression = blockSummary.getIfExpression();
                            //cannot handle returns inside a if-then-else
                            if(blockSummary.getIsExitNode()) canVeritest = false;
                            if(!canVeritest) break;
                            ISSABasicBlock commonSuccelseUnit = cfg.getIPdom(elseUnit.getNumber());

                            if(!VeritestingListener.boostPerf) {
                                NumberedGraph<ISSABasicBlock> invertedCFG = GraphInverter.invert(cfg);
                                NumberedDominators<ISSABasicBlock> postDom = (NumberedDominators<ISSABasicBlock>)
                                        Dominators.make(invertedCFG, cfg.exit());
                                boolean bPostDom = (postDom.isDominatedBy(commonSuccelseUnit, commonSucc));
                                assert (bPostDom);
                            }


                            VeritestingRegion innerRegion = VeritestingListener.veritestingRegions.get(key);
                            // MWW: new code.  Note: really exception should never occur, so perhaps this is *too*
                            // defensive.
                            try {
                                // need the negation of the condition expression here.
                                Expression negIfExpr = new Operation(Operation.Operator.EQ, conditionExpression, Operation.FALSE);
                                SPFCaseList innerCases = innerRegion.getSpfCases().cloneEmbedPathConstraint(
                                        negIfExpr);
                                varUtil.getSpfCases().addAll(innerCases);
                            } catch (StaticRegionException sre) {
                                System.out.println("Unable to instantiate spfCases: " + sre.toString());
                                canVeritest = false;
                                break;
                            }
                            // MWW: end of new code

                            for(Expression e: innerRegion.getOutputVars()) {
                                varUtil.defLocalVars.add(e);
                            }
                            for(Map.Entry<Expression, Expression> entry: innerRegion.getHoleHashMap().entrySet()) {
                                varUtil.holeHashMap.put(entry.getKey(), entry.getValue());
                                if(((HoleExpression)entry.getKey()).getHoleType() == HoleExpression.HoleType.CONDITION ||
                                        ((HoleExpression)entry.getKey()).getHoleType() == HoleExpression.HoleType.NEGCONDITION)
                                    varUtil.holeHashMap.remove(entry.getKey());
                            }
                            Expression elseExpr1 = innerRegion.getSummaryExpression();
                            elseExpr1 = replaceCondition(elseExpr1, conditionExpression);

                            // MWW: what is this business?
                            if (elseExpr1 != null) {
                                if (elseExpr != null)
                                    elseExpr =
                                            new Operation(Operation.Operator.AND,
                                                    elseExpr, elseExpr1);
                                else elseExpr = elseExpr1;
                            }
                            elsePred = null;
                            elseUnit = commonSuccelseUnit;
                            summarizedRegionStartBB.addAll(innerRegion.summarizedRegionStartBB);
                        } else canVeritest = false;
                    }
                    if (!canVeritest || elseUnit == commonSucc) break;
                    BlockSummary blockSummary = new BlockSummary(elseUnit, elseExpr, canVeritest, elsePLAssignSPF).invoke();
                    canVeritest = blockSummary.isCanVeritest();
                    elseExpr = blockSummary.getExpression();
                    //we should not encounter a BB with more than one successor at this point
                    assert(blockSummary.getIfExpression() == null);
                    //cannot handle returns inside a if-then-else
                    if(blockSummary.getIsExitNode()) canVeritest = false;
                    if (!canVeritest) break;
                    if (blockSummary.hasNewOrThrow){ //SH: skip to the end of the region when a new Object or throw instruction encountered
                        elseUnit = commonSucc;
                        elsePred = null;
                        break;
                    }
                    elsePred = elseUnit;
                    elseUnit = cfg.getNormalSuccessors(elseUnit).iterator().next();
                    if (elseUnit == endingUnit) break;
                }
                // if there is no "else" side, else set else's predecessor to currUnit
                if (canVeritest && (elsePred == commonSucc)) elsePred = currUnit;

                //move static analysis deeper into the else-side searching for veritesting regions
                if (!canVeritest) {
                    while(elseUnit != commonSucc) {
                        if(cfg.getNormalSuccessors(elseUnit).size() > 1) {
                            doAnalysis(elseUnit, commonSucc);
                            break;
                        }
                        if(cfg.getNormalSuccessors(elseUnit).size() == 0) break;
                        elseUnit = cfg.getNormalSuccessors(elseUnit).iterator().next();
                    }
                }

                // Assign pathLabel a value in the elseExpr
                if ((canVeritest)) {
                    if(thenPred != null)
                        thenUseNum = Util.whichPred(cfg, thenPred, commonSucc);
                    if(elsePred != null)
                        elseUseNum = Util.whichPred(cfg, elsePred, commonSucc);
                    VeritestingRegion veritestingRegion =   constructVeritestingRegion(thenExpr, elseExpr,
                            thenPLAssignSPF, elsePLAssignSPF,
                            currUnit, commonSucc,
                            thenUseNum, elseUseNum, summarizedRegionStartBB);
                    if (veritestingRegion != null) {
                        String key = veritestingRegion.getClassName() + "." + veritestingRegion.getMethodName() +
                                veritestingRegion.getMethodSignature() + "#" +
                                veritestingRegion.getStartInsnPosition();
                        //FNV1 fnv = new FNV1a64();
                        //fnv.init(key);
                        //long hash = fnv.getHash();
                        VeritestingListener.veritestingRegions.put(key, veritestingRegion);
                    }
                }
                currUnit = commonSucc;
            } else {
                System.out.println("more than 2 successors unhandled in stmt = " + currUnit);
                return;
            }
            System.out.println();
        } // end while(true)
        if (currUnit != null && currUnit != startingUnit && currUnit != endingUnit &&
                cfg.getNormalSuccessors(currUnit).size() > 0)
            doAnalysis(currUnit, endingUnit);
    } // end doAnalysis

    private HashMap<String, Expression> saveVarCache() {
        HashMap<String, Expression> ret = new HashMap<>();
        for (Map.Entry<String, Expression> entry : varUtil.varCache.entrySet()) {
            ret.put(entry.getKey(), entry.getValue());
        }
        return ret;
    }

    private HashMap<Expression,Expression> saveHoleHashMap() {
        HashMap<Expression, Expression> ret = new HashMap<>();
        for (Map.Entry<Expression, Expression> entry : varUtil.holeHashMap.entrySet()) {
            ret.put(entry.getKey(), entry.getValue());
        }
        return ret;
    }

    // Replace all holes of type CONDITION with conditionExpression
    // Replace all holes of type NEGCONDITION with !(conditionExpression)
    private Expression replaceCondition(Expression holeExpression, Expression conditionExpression) {
        if(holeExpression instanceof HoleExpression && ((HoleExpression)holeExpression).isHole()) {
            Expression ret = holeExpression;
            if(((HoleExpression)holeExpression).getHoleType() == HoleExpression.HoleType.CONDITION)
                ret = conditionExpression;
            if(((HoleExpression)holeExpression).getHoleType() == HoleExpression.HoleType.NEGCONDITION)
                ret = new Operation(
                        negateOperator(((Operation)conditionExpression).getOperator()),
                        ((Operation) conditionExpression).getOperand(0),
                        ((Operation) conditionExpression).getOperand(1));
            return ret;
        }
        if(holeExpression instanceof Operation) {
            Operation oldOperation = (Operation) holeExpression;
            Operation newOperation = new Operation(oldOperation.getOperator(),
                    replaceCondition(oldOperation.getOperand(0), conditionExpression),
                    replaceCondition(oldOperation.getOperand(1), conditionExpression));
            return newOperation;
        }
        return holeExpression;

    }

    private Operation.Operator negateOperator(Operation.Operator operator) {
        switch(operator) {
            case NE: return Operation.Operator.EQ;
            case EQ: return Operation.Operator.NE;
            case GT: return Operation.Operator.LE;
            case GE: return Operation.Operator.LT;
            case LT: return Operation.Operator.GE;
            case LE: return Operation.Operator.GT;
            default:
                System.out.println("Don't know how to negate Green operator (" + operator + ")");
                return null;
        }
    }

    public void doMethodAnalysis(ISSABasicBlock startingUnit, ISSABasicBlock endingUnit) throws InvalidClassFileException {
        assert(methodAnalysis);
        if(VeritestingListener.veritestingMode < 3) {
            return;
        }
        //System.out.println("Starting doMethodAnalysis");
        //currUnit represents the next BB to be summarized
        ISSABasicBlock currUnit = startingUnit;
        Expression methodExpression = null;
        HashSet<Integer> methodSummarizedRegionStartBB = new HashSet<>();
        boolean canVeritestMethod = true;
        int endingBC = 0;
        while (true) {
            if(((SSACFG.BasicBlock) currUnit).getAllInstructions().size() > 0)
                endingBC = ((IBytecodeMethod) (ir.getMethod())).getBytecodeIndex(currUnit.getLastInstructionIndex());
            List<ISSABasicBlock> succs = new ArrayList<>(cfg.getNormalSuccessors(currUnit));
            ISSABasicBlock commonSucc = cfg.getIPdom(currUnit.getNumber());
            if (succs.size() == 1 || succs.size() == 0) {
                //Assuming that it would be ok to visit a BB that starts with a phi expression
                BlockSummary blockSummary = new BlockSummary(currUnit, methodExpression, canVeritestMethod).invoke();
                canVeritestMethod = blockSummary.isCanVeritest();
                methodExpression = blockSummary.getExpression();
                assert(blockSummary.getIfExpression() == null);
                if(!canVeritestMethod) return;
                if(blockSummary.getIsExitNode() || succs.size() == 0) {
                    VeritestingRegion veritestingRegion =
                            constructMethodRegion(methodExpression, cfg.entry().getNumber(),
                                    cfg.entry().getNumber(), methodSummarizedRegionStartBB, endingBC);
                    String key = veritestingRegion.getClassName() + "." + veritestingRegion.getMethodName() +
                            veritestingRegion.getMethodSignature() + "#" +
                            veritestingRegion.getStartInsnPosition();
                    FNV1 fnv = new FNV1a64();
                    fnv.init(key);
                    long hash = fnv.getHash();
                    VeritestingListener.veritestingRegions.put(key, veritestingRegion);
                    return;
                }
                if (!canVeritestMethod) return;
                if(succs.size() == 0) return;
                currUnit = succs.get(0);
                continue;
            }
            else if (succs.size() == 2) {
                //Summarize instructions before the condition
                BlockSummary blockSummary = new BlockSummary(currUnit, methodExpression, canVeritestMethod).invoke();
                canVeritestMethod = blockSummary.isCanVeritest();
                methodExpression = blockSummary.getExpression();
                Expression conditionExpression = blockSummary.getIfExpression();
                if(!canVeritestMethod) return;
                //cannot handle returns inside a if-then-else
                if(blockSummary.getIsExitNode()) return;
                int startingBC = ((IBytecodeMethod) (ir.getMethod())).getBytecodeIndex(currUnit.getLastInstructionIndex());
                String key = currentClassName + "." + currentMethodName + methodSig + "#" + startingBC;
                if(!VeritestingListener.veritestingRegions.containsKey(key)) return;
                VeritestingRegion veritestingRegion = VeritestingListener.veritestingRegions.get(key);
                Expression summaryExpression = veritestingRegion.getSummaryExpression();
                summaryExpression = replaceCondition(summaryExpression, conditionExpression);
                assert(veritestingRegion != null);
                if(methodExpression != null) {
                    if(veritestingRegion.getSummaryExpression() != null) {
                        methodExpression = new Operation(Operation.Operator.AND, methodExpression,
                                summaryExpression);
                    }
                }
                else methodExpression = summaryExpression;
                methodSummarizedRegionStartBB.addAll(veritestingRegion.summarizedRegionStartBB);
                varUtil.defLocalVars.addAll(veritestingRegion.getOutputVars());
                for(Map.Entry<Expression, Expression> entry: veritestingRegion.getHoleHashMap().entrySet()) {
                    if(((HoleExpression)entry.getKey()).getHoleType() == HoleExpression.HoleType.CONDITION ||
                            ((HoleExpression)entry.getKey()).getHoleType() == HoleExpression.HoleType.NEGCONDITION)
                        continue;
                    varUtil.holeHashMap.put(entry.getKey(), entry.getValue());
                }
                for(HashMap.Entry<Expression, Expression> entry: veritestingRegion.getHoleHashMap().entrySet()) {
                    if(((HoleExpression)entry.getKey()).getHoleType() == HoleExpression.HoleType.CONDITION ||
                            ((HoleExpression)entry.getKey()).getHoleType() == HoleExpression.HoleType.NEGCONDITION)
                        continue;
                    HoleExpression holeExpression = (HoleExpression) entry.getKey();
                    varUtil.varCache.put(holeExpression.getHoleVarName(), holeExpression);
                }
                currUnit = commonSucc;
            } else {
                System.out.println("doMethodAnalysis: cannot summarize more than 2 successors in BB = " + currUnit);
                return;
            }
        } // end while(true)
    } // end doMethodAnalysis

    public VeritestingRegion constructMethodRegion(
            Expression summaryExp, int startBBNum, int endBBNum, HashSet<Integer> summarizedRegionStartBB,
            int endingBC) throws InvalidClassFileException {
        VeritestingRegion veritestingRegion = new VeritestingRegion();
        veritestingRegion.setSummaryExpression(summaryExp);
        veritestingRegion.setStartInsnPosition(0);
        // assuming ending instruction position is not needed for using a method summary
        veritestingRegion.setEndInsnPosition(endingBC);
        veritestingRegion.setOutputVars(varUtil.defLocalVars);
        veritestingRegion.setRetValVars(varUtil.retValVar);
        veritestingRegion.setClassName(currentClassName);
        veritestingRegion.setMethodName(currentMethodName);
        veritestingRegion.setMethodSignature(methodSig);
        veritestingRegion.setHoleHashMap(varUtil.holeHashMap);
        veritestingRegion.setStartBBNum(startBBNum);
        veritestingRegion.setEndBBNum(endBBNum);
        veritestingRegion.setIsMethodSummary(true);
        veritestingRegion.setSummarizedRegionStartBB(summarizedRegionStartBB);

        // MWW: adding the spfCases
        veritestingRegion.setSpfCases(varUtil.getSpfCases());
        // MWW: end addition

        return veritestingRegion;
    }

    private class BlockSummary {
        private Expression pathLabelHole;
        private ISSABasicBlock unit;
        private Expression expression;
        private Expression lastExpression;
        private boolean isExitNode = false;
        private boolean hasNewOrThrow = false;

        public boolean isHasNewOrThrow() {
            return hasNewOrThrow;
        }

        public Expression getIfExpression() {
            return ifExpression;
        }
        private Expression ifExpression = null;

        private boolean canVeritest;

        public BlockSummary(ISSABasicBlock thenUnit, Expression thenExpr, boolean canVeritest) {
            this.unit = thenUnit;
            this.expression = thenExpr;
            this.canVeritest = canVeritest;
        }

        public BlockSummary(ISSABasicBlock thenUnit, Expression thenExpr, boolean canVeritest,
                            Expression pathLabelHole ) {
            this.unit = thenUnit;
            this.expression = thenExpr;
            this.canVeritest = canVeritest;
            this.pathLabelHole = pathLabelHole;
        }

        public Expression getExpression() {
            return expression;
        }

        public boolean isCanVeritest() {
            return canVeritest;
        }

        public Expression getLastExpression() {
            return lastExpression;
        }

        public BlockSummary invoke() {
            MyIVisitor myIVisitor;
            Iterator<SSAInstruction> ssaInstructionIterator = unit.iterator();
            while (ssaInstructionIterator.hasNext()) {
                //phi expressions are summarized in the constructVeritestingRegion method, dont try to summarize them here
                myIVisitor = new MyIVisitor(varUtil, -1, -1, false, pathLabelHole);
                SSAInstruction instruction = ssaInstructionIterator.next();
                instruction.visit(myIVisitor);

                if (!myIVisitor.canVeritest()) {
                    canVeritest = false;
                    break;
                }
                if(myIVisitor.isInvoke()) {
                    methodSummaryClassNames.add(myIVisitor.getInvokeClassName());
                }
                Expression expression1 = myIVisitor.getSPFExpr();
                lastExpression = expression1;
                ifExpression = myIVisitor.getIfExpr();
                if (expression1 != null) {
                    if (expression != null)
                        expression =
                                new Operation(Operation.Operator.AND,
                                        expression, expression1);
                    else expression = expression1;
                }
                if(myIVisitor.isExitNode()) {
                    isExitNode = true;
                    break;
                }
                if(myIVisitor.isHasNewOrThrow()) {
                    hasNewOrThrow = true;
                    break;
                }
            }
            return this;
        }

        public boolean getIsExitNode() {
            return isExitNode;
        }
    }
}







