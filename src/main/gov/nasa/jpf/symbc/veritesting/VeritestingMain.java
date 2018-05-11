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

import com.ibm.wala.cfg.CFGSanitizer;
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
import com.ibm.wala.shrikeBT.IInstruction;
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
import gov.nasa.jpf.vm.ThreadInfo;
import x10.wala.util.NatLoop;
import x10.wala.util.NatLoopSolver;

import static gov.nasa.jpf.symbc.veritesting.ClassUtils.findClasses;
import static gov.nasa.jpf.symbc.veritesting.ExpressionUtil.*;
import static gov.nasa.jpf.symbc.veritesting.HoleExpression.HoleType.FIELD_OUTPUT;
import static gov.nasa.jpf.symbc.veritesting.ReflectUtil.getSignature;

import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.Operation;


public class VeritestingMain {

    public int pathLabelVarNum = 0;
    public HashSet endingInsnsHash;
    ClassHierarchy cha;
    HashSet<String> methodSummaryClassNames;
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
    private String currentPackageName;

    public VeritestingMain(String appJar) {
        try {
            appJar = System.getenv("TARGET_CLASSPATH_WALA");// + appJar;
            System.out.println("appJar = " + appJar);
            AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(appJar,
                    (new FileProvider()).getFile("../MyJava60RegressionExclusions.txt"));
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

    public void analyzeForVeritesting(ThreadInfo ti, String classPath, String _className) {
        // causes java.lang.IllegalArgumentException: ill-formed sig testMe4(int[],int)
        endingInsnsHash = new HashSet();
        findClasses(ti, cha, classPath, _className, methodSummaryClassNames);
        startingPointsHistory = new HashSet();

        try {
            File f = new File(classPath);
            URL[] cp = new URL[]{f.toURI().toURL()};
            URLClassLoader urlcl = new URLClassLoader(cp);
            Class c = urlcl.loadClass(_className);
            Method[] allMethods = c.getDeclaredMethods();
            for (Method m : allMethods) {
                String signature = getSignature(m);
                startAnalysis(getPackageName(_className),_className,signature);
            }
            if(VeritestingListener.veritestingMode <= 2) return;
            for(Iterator it = methodSummaryClassNames.iterator(); it.hasNext();) {
                String methodSummaryClassName = (String) it.next();
                Class cAdditional;
                try { cAdditional = urlcl.loadClass(methodSummaryClassName); }
                catch (ClassNotFoundException e) { continue; }
                Method[] allMethodsAdditional = cAdditional.getDeclaredMethods();
                for (Method m : allMethodsAdditional) {
                    String signature = getSignature(m);
                    startAnalysis(getPackageName(methodSummaryClassName), methodSummaryClassName, signature);
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
                    startAnalysis(getPackageName(methodSummaryClassName), methodSummaryClassName, signature);
                }
            }
        } catch (MalformedURLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private String getPackageName(String c) {
        if (c.contains(".")) return c.substring(0, c.lastIndexOf("."));
        else return null;
    }

    public void startAnalysis(String packageName, String className, String methodSig) {
        try {

            MethodReference mr = StringStuff.makeMethodReference(className + "." + methodSig);
            IMethod m = cha.resolveMethod(mr);
            if (m == null) {
                System.out.println("could not resolve " + className + "." + methodSig);
                return;
                //Assertions.UNREACHABLE("could not resolve " + mr);
            }
            AnalysisOptions options = new AnalysisOptions();
            options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes());
            IAnalysisCacheView cache = new AnalysisCacheImpl(options.getSSAOptions());
            ir = cache.getIR(m, Everywhere.EVERYWHERE);
            if (ir == null) {
                System.out.println("Null IR for " + className + "." + methodSig);
                return;
            }
            cfg = ir.getControlFlowGraph();
            currentPackageName = packageName;
            currentClassName = className;
            currentMethodName = m.getName().toString();
            this.methodSig = methodSig.substring(methodSig.indexOf('('));
            System.out.println("Starting " + (methodAnalysis ? "method " : "region ") + "analysis for " + currentMethodName + "(" + currentClassName + "." + methodSig + ")");
            varUtil = new VarUtil(ir, currentClassName, currentMethodName);
            NumberedDominators<ISSABasicBlock> uninverteddom =
                    (NumberedDominators<ISSABasicBlock>) Dominators.make(cfg, cfg.entry());
            loops = new HashSet<>();
            HashSet<Integer> visited = new HashSet<>();
            NatLoopSolver.findAllLoops(cfg, uninverteddom, loops, visited, cfg.getNode(0));
            // Here is where the magic happens.
            if(!methodAnalysis) {
                doAnalysis(cfg.entry(), null);
            }
            else doMethodAnalysis(cfg.entry(), cfg.exit());
        } catch (InvalidClassFileException e) {
            e.printStackTrace();
        } catch (StaticRegionException e) {
            System.out.println(e.getMessage());
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
            HashSet<Integer> summarizedRegionStartBB) throws InvalidClassFileException, StaticRegionException {
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
        assert(ExpressionUtil.isNoHoleLeftBehind(thenExpr, varUtil.holeHashMap));
        assert(ExpressionUtil.isNoHoleLeftBehind(elseExpr, varUtil.holeHashMap));

        // (If && thenExpr) || (ifNot && elseExpr)
        HoleExpression condition = new HoleExpression(VarUtil.nextInt(), currentClassName, currentMethodName,
                HoleExpression.HoleType.CONDITION, thenPLAssignSPF, -1, -1);
        HoleExpression negCondition = new HoleExpression(VarUtil.nextInt(), currentClassName, currentMethodName,
                HoleExpression.HoleType.NEGCONDITION, elsePLAssignSPF, -1, -1);
        varUtil.holeHashMap.put(condition, condition);
        varUtil.holeHashMap.put(negCondition, negCondition);
        // make a FIELD_PHI hole for every FIELD_OUTPUT hole so that common fields will get phi'd in
        // VeritestingListener.FillNonInputHoles. Don't add the FIELD_PHI hole into region.outputVars aka
        // varUtil.defLocalVars because every FIELD_OUTPUT hole is already in region.outputVars.
        Iterator itr = varUtil.defLocalVars.iterator();
        HashSet<HoleExpression> extraHoles = new HashSet<>();
        while(itr.hasNext()) {
            HoleExpression h = (HoleExpression) itr.next();
            if(h.getHoleType() == FIELD_OUTPUT) {
                HoleExpression h1 = new HoleExpression(VarUtil.nextInt(), h.getClassName(), h.getMethodName(),
                        HoleExpression.HoleType.FIELD_PHI, null, h.getLocalStackSlot(), h.getGlobalStackSlot());
                h1.setFieldInfo(h.getFieldInfo());
                h1.getFieldInfo().writeValue = null;
                h1.setHoleVarName(h.getHoleVarName().replaceAll("put","PHI"));

                HoleExpression fieldInput = new HoleExpression(VarUtil.nextInt(), h.getClassName(), h.getMethodName(),
                        HoleExpression.HoleType.FIELD_INPUT, null, h.getLocalStackSlot(), h.getGlobalStackSlot());
                HoleExpression.FieldInfo f = h.getFieldInfo();
                fieldInput.setFieldInfo(f.getFieldStaticClassName(), f.fieldName, f.methodName, null, f.isStaticField,
                        f.useHole);
                fieldInput.getFieldInfo().writeValue = null;
                fieldInput.setHoleVarName(h.getHoleVarName().replaceAll("put","PHI_INPUT"));
                MapUtil.add(varUtil.holeHashMap, 0, fieldInput, fieldInput);
                h1.getFieldInfo().setFieldInputHole(fieldInput);
                extraHoles.add(h1);
                extraHoles.add(fieldInput);
            }
        }
        for(HoleExpression e: extraHoles) {
            varUtil.varCache.put(e.getHoleVarName(), e);
        }
        Expression pathExpr1 =
                new Operation(Operation.Operator.OR,
                        new Operation(Operation.Operator.AND, condition, thenExpr),
                        new Operation(Operation.Operator.AND, negCondition, elseExpr));

        MyIVisitor myIVisitor;
        Expression phiExprSPF, finalPathExpr = pathExpr1;
        Iterator<SSAInstruction> iterator = commonSucc.iterator();
        while(iterator.hasNext()) {
            // visit instructions one at a time, break on the first non-phi statement because a region summary starts at
            // the condition and ends at the meet point. The meet point ends at the first non-phi statement. Any outer
            // region that encapsulates this inner region will have to summarize statements that appear after the first
            // non-phi statement in this basic block.
            myIVisitor = new MyIVisitor(varUtil, thenUseNum, elseUseNum, true, null);
            iterator.next().visit(myIVisitor);
            if (myIVisitor.hasPhiExpr()) {
                phiExprSPF = myIVisitor.getPhiExprSPF(thenPLAssignSPF, elsePLAssignSPF);
                finalPathExpr =
                        new Operation(Operation.Operator.AND, finalPathExpr, phiExprSPF);
            } else break;
        }

        int startingBC = ((IBytecodeMethod) (ir.getMethod())).getBytecodeIndex(currUnit.getLastInstructionIndex());
        int endingBC = -1;
        if (ir.getControlFlowGraph().exit() == commonSucc) {
            endingBC = -1; // for debugging situations where the common successor is the exit node of the CFG
        }
        if(commonSucc.getFirstInstructionIndex() == -1) {
            if (ir.getControlFlowGraph().exit() == commonSucc) {
                SSAInstruction[] insns = ir.getInstructions();
                endingBC = ((IBytecodeMethod) (ir.getMethod())).getBytecodeIndex(insns[insns.length-1].iindex);
            } else
                throw new StaticRegionException("failed to get ending bytecode offset from non-exit common successor: " +
                        commonSucc.toString());
        }
        else endingBC = ((IBytecodeMethod) (ir.getMethod())).getBytecodeIndex(commonSucc.getFirstInstructionIndex());


        VeritestingRegion veritestingRegion = new VeritestingRegion();
        veritestingRegion.setSummaryExpression(finalPathExpr);
        veritestingRegion.setStartInsnPosition(startingBC);
        veritestingRegion.setEndInsnPosition(endingBC);
        veritestingRegion.setClassName(currentClassName);
        veritestingRegion.setMethodName(currentMethodName);
        veritestingRegion.setMethodSignature(methodSig);
        veritestingRegion.setStartBBNum(currUnit.getNumber());
        veritestingRegion.setEndBBNum(commonSucc.getNumber());
        LinkedHashSet<Expression> hashSet = new LinkedHashSet<>();
        hashSet.addAll(varUtil.defLocalVars);
        veritestingRegion.setOutputVars(hashSet);
        LinkedHashMap<Expression, Expression> hashMap = new LinkedHashMap<>();
        hashMap.putAll(varUtil.holeHashMap);
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

    public void doAnalysis(ISSABasicBlock startingUnit, ISSABasicBlock endingUnit) throws StaticRegionException {
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
            if(currentClassName.contains("VeritestingPerf") && currentMethodName.contains("nestedRegion"))
                System.out.println("");
            ISSABasicBlock commonSucc = null;
            try {
                commonSucc = cfg.getIPdom(currUnit.getNumber(), true, false, ir, cha);
            } catch (WalaException e) {
                System.out.println(e.getMessage() + "\nran into WalaException in cfg.getIPdom(currUnit = " + currUnit.toString() + ")");
                return;
            }
            if (commonSucc == null)
                throw new StaticRegionException("`compute immediate post-dominator of "
                        + currUnit.toString() + ", isExit = " + currUnit.isExitBlock());
            if (succs.size() == 1) {
                currUnit = succs.get(0);
                continue;
            } else if (succs.size() == 0) break;
            else if (succs.size() == 2 && startingPointsHistory.contains(currUnit)) {
                currUnit = commonSucc;
                break;
            } else if (succs.size() == 2 && !startingPointsHistory.contains(currUnit)) {
                startingPointsHistory.add(currUnit);
                if (!(currUnit.getLastInstruction() instanceof SSAConditionalBranchInstruction)) {
                    currUnit = commonSucc;
                    continue;
                }
                //fix this varUtil reset because it screws up varUtil.holeHashMap

                // MWW: why is this reset here?  Why does it not occur prior to the
                // other recursive call for doAnalysis?
                varUtil.reset();

                ISSABasicBlock thenUnit = Util.getTakenSuccessor(cfg, currUnit);
                ISSABasicBlock elseUnit = Util.getNotTakenSuccessor(cfg, currUnit);
                if (isLoopStart(currUnit)) {
                    try {
                        doAnalysis(thenUnit, null);
                    } catch (StaticRegionException s) {
                        System.out.println(s.getMessage() + "\nfailed to summarize body of loop");
                    }
                    try {
                        doAnalysis(elseUnit, null);
                    } catch (StaticRegionException s) {
                        System.out.println(s.getMessage() + "\nfailed to summarize code after loop");
                    }
                    return;
                }

                // constructing path labels.
                Expression thenExpr = null, elseExpr = null;
                String pathLabelString = "pathLabel" + varUtil.getPathCounter();
                final int thenPathLabel = varUtil.getPathCounter();
                final int elsePathLabel = varUtil.getPathCounter();
                ISSABasicBlock thenPred = thenUnit, elsePred = elseUnit;
                int thenUseNum = -1, elseUseNum = -1;
                Expression pathLabel = varUtil.makeIntermediateVar(pathLabelString, true, null);
                final Expression thenPLAssign =
                        new Operation(Operation.Operator.EQ, pathLabel,
                                new IntConstant(thenPathLabel));
                final Expression elsePLAssign =
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

                        //save varUtil.holeHashMap because it contains ordering information for all the holes and varCache because restoring varCache will also restore defLocalVars
                        // recursive doAnalysis call
                        LinkedHashMap<HoleExpression, HoleExpression> savedHoleHashMap = saveHoleHashMap();
                        LinkedHashMap<String, Expression> savedVarCache = saveVarCache();
                        SPFCaseList savedCaseList = varUtil.getSpfCases();

                        // recursive call to doAnalysis to try to summarize the inner region
                        try {
                            doAnalysis(thenUnit, commonSucc);
                        } catch (StaticRegionException s) {
                            System.out.println(s.getMessage() + "\nfailed to summarize inner region in then-side");
                            return;
                        }

                        varUtil.holeHashMap.clear();
                        varUtil.defLocalVars.clear();
                        varUtil.holeHashMap.putAll(savedHoleHashMap);
                        varUtil.varCache.putAll(savedVarCache);//this will also populate varUtil.defLocalVars and
                        // varUtil.holeHashmap because VarUtil.varCache.put and VarUtil.varCache.putAll method are overridden
                        varUtil.getSpfCases().addAll(savedCaseList);

                        int offset;
                        try {
                            offset = ((IBytecodeMethod) (ir.getMethod())).getBytecodeIndex(thenUnit.getLastInstructionIndex());
                        } catch (InvalidClassFileException e) {
                            System.out.println(e.getMessage() + "\nran into InvalidClassFileException on getByteCodeIndex(thenUnit = " + thenUnit.toString() + ")");
                            return;
                        }
                        String key = getCurrentKey(offset);

                        // working with inner region here
                        if(VeritestingListener.veritestingRegions.containsKey(key)) {
                            // we are able to summarize the inner region, try to include it
                            System.out.println("Veritested inner region with key = " + key);
                            //visit all instructions up to and including the condition
                            BlockSummary blockSummary = new BlockSummary(thenUnit, thenExpr, canVeritest,
                                    thenPLAssign).invoke();
                            canVeritest = blockSummary.isCanVeritest();
                            thenExpr = blockSummary.getExpression(); // outer region thenExpr
                            Expression conditionExpression = blockSummary.getIfExpression();
                            //cannot handle returns inside a if-then-else
                            if(blockSummary.getIsExitNode()) canVeritest = false;
                            if(!canVeritest) break;
                            ISSABasicBlock commonSuccthenUnit;
                            try {
                                commonSuccthenUnit = cfg.getIPdom(thenUnit.getNumber(), true, false, ir, cha);
                            } catch (WalaException e) {
                                System.out.println(e.getMessage() + "\nran into WalaException on cfg.getIPdom(thenUnit = " + thenUnit.toString() + ")");
                                return;
                            }
                            if (commonSuccthenUnit == null)
                                throw new StaticRegionException("failed to compute immediate post-dominator of "
                                        + thenUnit.toString() + ", isExit = " + thenUnit.isExitBlock());
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
                            Expression thenExpr1 = innerRegion.getSummaryExpression();
                            // this needs to happen before replaceHolesInExpression(thenExpr1,...)
                            thenExpr1 = replaceCondition(thenExpr1, conditionExpression);
                            // maps each inner region hole to its copy to be used here but
                            // CONDITION, NEGCONDITION holes are skipped and
                            // every hole's PLAssign is conjuncted with thenPLAssign
                            LinkedHashMap<HoleExpression, HoleExpression> innerHolesCopyMap =
                                    copyHoleHashMap(innerRegion.getHoleHashMap(), thenPLAssign,
                                            currentClassName, currentMethodName);
                            replaceHolesInPLAssign(innerHolesCopyMap);
                            thenExpr1 = replaceHolesInExpression(thenExpr1, innerHolesCopyMap);
                            insertIntoVarUtil(innerHolesCopyMap, varUtil);
                            assert(ExpressionUtil.isNoHoleLeftBehind(thenExpr1, varUtil.holeHashMap));

                            thenExpr = ExpressionUtil.nonNullOp(Operation.Operator.AND, thenExpr, thenExpr1);
                            thenPred = null;
                            thenUnit = commonSuccthenUnit;
                            summarizedRegionStartBB.addAll(innerRegion.summarizedRegionStartBB);
                        } else canVeritest = false;
                    }
                    if (!canVeritest || thenUnit == commonSucc) break;
                    BlockSummary blockSummary = new BlockSummary(thenUnit, thenExpr, canVeritest,
                            thenPLAssign).invoke();
                    canVeritest = blockSummary.isCanVeritest();
                    thenExpr = blockSummary.getExpression();
                    //we should not encounter a BB with more than one successor at this point
                    assert(blockSummary.getIfExpression() == null);
                    //cannot handle returns inside a if-then-else
                    if(blockSummary.getIsExitNode()) canVeritest = false;
                    //SH: supporting new object or throw instructions
                    if (!canVeritest) break;
                    if (blockSummary.isHasNewOrThrow()){ //SH: skip to the end of the region when a new Object or throw instruction encountered
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
                            try {
                                doAnalysis(thenUnit, commonSucc);
                            } catch (StaticRegionException s) {
                                System.out.println(s.getMessage() + "\nfailed to summarize inner region in then-side");
                                return;
                            }
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

                        //save varUtil.holeHashMap because it contains ordering information for all the holes and varCache because restoring varCache will also restore defLocalVars
                        // recursive doAnalysis call
                        LinkedHashMap<HoleExpression, HoleExpression> savedHoleHashMap = saveHoleHashMap();
                        LinkedHashMap<String, Expression> savedVarCache = saveVarCache();
                        SPFCaseList savedCaseList = varUtil.getSpfCases();

                        // recursive call to doAnalysis to try to summarize the inner region
                        try {
                            doAnalysis(elseUnit, commonSucc);
                        } catch (StaticRegionException s) {
                            System.out.println(s.getMessage() + "\nfailed to summarize inner region in else-side");
                            return;
                        }

                        varUtil.holeHashMap.clear();
                        varUtil.defLocalVars.clear();
                        varUtil.holeHashMap.putAll(savedHoleHashMap);
                        varUtil.varCache.putAll(savedVarCache);//this will also populate varUtil.defLocalVars and
                        // varUtil.holeHashmap because VarUtil.varCache.put and VarUtil.varCache.putAll method are overridden
                        varUtil.getSpfCases().addAll(savedCaseList);
                        int offset;
                        try {
                            offset = ((IBytecodeMethod) (ir.getMethod())).getBytecodeIndex(elseUnit.getLastInstructionIndex());
                        } catch (InvalidClassFileException e) {
                            System.out.println(e.getMessage() + "\nran into InvalidClassFileException on getByteCodeIndex(elseUnit = " + elseUnit.toString() + ")");
                            return;
                        }
                        String key = getCurrentKey(offset);
                        if(VeritestingListener.veritestingRegions.containsKey(key)) {
                            System.out.println("Veritested inner region with key = " + key);
                            //visit all instructions up to and including the condition
                            BlockSummary blockSummary = new BlockSummary(elseUnit, elseExpr, canVeritest,
                                    elsePLAssign).invoke();
                            canVeritest = blockSummary.isCanVeritest();
                            elseExpr = blockSummary.getExpression();
                            Expression conditionExpression = blockSummary.getIfExpression();
                            //cannot handle returns inside a if-else-else
                            if(blockSummary.getIsExitNode()) canVeritest = false;
                            if(!canVeritest) break;
                            ISSABasicBlock commonSuccelseUnit ;
                            try {
                                commonSuccelseUnit = cfg.getIPdom(elseUnit.getNumber(), true, false, ir, cha);
                            } catch (WalaException e) {
                                System.out.println(e.getMessage() + "\nran into WalaException on cfg.getIPdom(elseUnit = " + elseUnit.toString() + ")");
                                return;
                            }
                            if (commonSuccelseUnit == null)
                                throw new StaticRegionException("failed to compute immediate post-dominator of "
                                        + elseUnit.toString() + ", isExit = " + elseUnit.isExitBlock());
                            NumberedGraph<ISSABasicBlock> invertedCFG = GraphInverter.invert(cfg);
                            NumberedDominators<ISSABasicBlock> postDom = (NumberedDominators<ISSABasicBlock>)
                                    Dominators.make(invertedCFG, cfg.exit());
                            boolean bPostDom = (postDom.isDominatedBy(commonSuccelseUnit, commonSucc));
                            assert(bPostDom);

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
                            Expression elseExpr1 = innerRegion.getSummaryExpression();
                            // this needs to happen before replaceHolesInExpression(thenExpr1,...)
                            elseExpr1 = replaceCondition(elseExpr1, conditionExpression);
                            // maps each inner region hole to its copy to be used here but
                            // CONDITION, NEGCONDITION holes are skipped and
                            // every hole's PLAssign is conjuncted with elsePLAssign
                            LinkedHashMap<HoleExpression, HoleExpression> innerHolesCopyMap =
                                    copyHoleHashMap(innerRegion.getHoleHashMap(), elsePLAssign,
                                            currentClassName, currentMethodName);
                            replaceHolesInPLAssign(innerHolesCopyMap);
                            elseExpr1 = replaceHolesInExpression(elseExpr1, innerHolesCopyMap);
                            insertIntoVarUtil(innerHolesCopyMap, varUtil);
                            assert(ExpressionUtil.isNoHoleLeftBehind(elseExpr1, varUtil.holeHashMap));

                            elseExpr = ExpressionUtil.nonNullOp(Operation.Operator.AND, elseExpr, elseExpr1);
                            elsePred = null;
                            elseUnit = commonSuccelseUnit;
                            summarizedRegionStartBB.addAll(innerRegion.summarizedRegionStartBB);
                        } else canVeritest = false;
                    }
                    if (!canVeritest || elseUnit == commonSucc) break;
                    BlockSummary blockSummary = new BlockSummary(elseUnit, elseExpr, canVeritest,
                            elsePLAssign).invoke();
                    canVeritest = blockSummary.isCanVeritest();
                    elseExpr = blockSummary.getExpression();
                    //we should not encounter a BB with more than one successor at this point
                    assert(blockSummary.getIfExpression() == null);
                    //cannot handle returns inside a if-else-else
                    if(blockSummary.getIsExitNode()) canVeritest = false;
                    if (!canVeritest) break;
                    if (blockSummary.isHasNewOrThrow()){ //SH: skip to the end of the region when a new Object or throw instruction encountered
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
                            try {
                                doAnalysis(elseUnit, commonSucc);
                            } catch (StaticRegionException s) {
                                System.out.println(s.getMessage() + "\nfailed to summarize inner region in else-side");
                                return;
                            }
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
                    VeritestingRegion veritestingRegion = null;
                    try {
                        veritestingRegion = constructVeritestingRegion(thenExpr, elseExpr,
                                thenPLAssign, elsePLAssign,
                                currUnit, commonSucc,
                                thenUseNum, elseUseNum, summarizedRegionStartBB);
                    } catch (InvalidClassFileException e) {
                        System.out.println(e.getMessage() + "\nran into InvalidClassFileException in constructVeritestingRegion(commonSucc = " + commonSucc.toString() + ")");
                        return;
                    }
                    if (veritestingRegion != null) {
                        String key = getKey(veritestingRegion);
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
                cfg.getNormalSuccessors(currUnit).size() > 0) {
            try {
                doAnalysis(currUnit, endingUnit);
            } catch (StaticRegionException s) {
                System.out.println(s.getMessage() + "\nfailed to summarize region starting from currUnit = " + currUnit.toString());
                return;
            }
        }
    } // end doAnalysis

    private String getKey(VeritestingRegion veritestingRegion) {
        return veritestingRegion.getClassName() + "." + veritestingRegion.getMethodName() +
                veritestingRegion.getMethodSignature() + "#" +
                veritestingRegion.getStartInsnPosition();
    }

    private LinkedHashMap<String, Expression> saveVarCache() {
        LinkedHashMap<String, Expression> ret = new LinkedHashMap<>();
        for (Map.Entry<String, Expression> entry : varUtil.varCache.entrySet()) {
            ret.put(entry.getKey(), entry.getValue());
        }
        return ret;
    }

    private LinkedHashMap<HoleExpression,HoleExpression> saveHoleHashMap() {
        LinkedHashMap<HoleExpression, HoleExpression> ret = new LinkedHashMap<>();
        for (Map.Entry<Expression, Expression> entry : varUtil.holeHashMap.entrySet()) {
            ret.put((HoleExpression)entry.getKey(), (HoleExpression)entry.getValue());
        }
        return ret;
    }

    public void doMethodAnalysis(ISSABasicBlock startingUnit, ISSABasicBlock endingUnit) throws InvalidClassFileException, StaticRegionException {
        assert(methodAnalysis);
        if(VeritestingListener.veritestingMode < 3) {
            return;
        }
        if(currentClassName.contains("TempClassDerived") && currentMethodName.contains("nestedRegion"))
            System.out.println("");
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
            ISSABasicBlock commonSucc = null;
            try {
                if(currUnit.isExitBlock()) commonSucc = currUnit;
                else commonSucc = cfg.getIPdom(currUnit.getNumber(), true, false, ir, cha);
            } catch (WalaException e) {
            }
            if (commonSucc == null)
                throw new StaticRegionException("failed to compute immediate post-dominator of "
                        + currUnit.toString() + ", isExit = " + currUnit.isExitBlock());
            if (succs.size() == 1 || succs.size() == 0) {
                //Assuming that it would be ok to visit a BB that starts with a phi expression
                BlockSummary blockSummary = new BlockSummary(currUnit, methodExpression, canVeritestMethod, null).invoke();
                canVeritestMethod = blockSummary.isCanVeritest();
                methodExpression = blockSummary.getExpression();
                assert(blockSummary.getIfExpression() == null);
                if(!canVeritestMethod) return;
                if(blockSummary.getIsExitNode() || succs.size() == 0) {
                    VeritestingRegion veritestingRegion =
                            constructMethodRegion(methodExpression, cfg.entry().getNumber(),
                                    cfg.entry().getNumber(), methodSummarizedRegionStartBB, endingBC);
                    String key = getKey(veritestingRegion);
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
                BlockSummary blockSummary = new BlockSummary(currUnit, methodExpression, canVeritestMethod, null).invoke();
                canVeritestMethod = blockSummary.isCanVeritest();
                methodExpression = blockSummary.getExpression();
                Expression conditionExpression = blockSummary.getIfExpression();
                if(!canVeritestMethod) return;
                //cannot handle returns inside a if-then-else
                if(blockSummary.getIsExitNode()) return;
                int startingBC = ((IBytecodeMethod) (ir.getMethod())).getBytecodeIndex(currUnit.getLastInstructionIndex());
                String key = getCurrentKey(startingBC);
                if(!VeritestingListener.veritestingRegions.containsKey(key)) return;
                VeritestingRegion veritestingRegion = VeritestingListener.veritestingRegions.get(key);
                Expression summaryExpression = veritestingRegion.getSummaryExpression();
                summaryExpression = replaceCondition(summaryExpression, conditionExpression);
                methodExpression = ExpressionUtil.nonNullOp(Operation.Operator.AND, methodExpression, summaryExpression);
                methodSummarizedRegionStartBB.addAll(veritestingRegion.summarizedRegionStartBB);
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

    private String getCurrentKey(int startingBC) {
        return currentClassName + "." + currentMethodName + methodSig + "#" + startingBC;
    }

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
        veritestingRegion.setPackageName(currentPackageName);
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
        private final Expression PLAssign;
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

        public BlockSummary(ISSABasicBlock thenUnit, Expression thenExpr, boolean canVeritest, Expression PLAssign) {
            this.unit = thenUnit;
            this.expression = thenExpr;
            this.canVeritest = canVeritest;
            this.PLAssign = PLAssign;
        }

        public BlockSummary(ISSABasicBlock thenUnit, Expression thenExpr, boolean canVeritest,
                            Expression pathLabelHole, Expression PLAssign) {
            this.unit = thenUnit;
            this.expression = thenExpr;
            this.canVeritest = canVeritest;
            this.pathLabelHole = pathLabelHole;
            this.PLAssign = PLAssign;
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

                myIVisitor = new MyIVisitor(varUtil, -1, -1, false, PLAssign, pathLabelHole);
                SSAInstruction instruction = ssaInstructionIterator.next();
                // can this be a Potentially Exception-raising Instruction ?
                if (instruction.isPEI()) {
                    //TODO: add this instruction's potential exception as an SPFCase
                }
                instruction.visit(myIVisitor);

                if (!myIVisitor.canVeritest()) {
                    canVeritest = false;
                    break;
                }
                if(myIVisitor.isInvoke()) {
                    if(!methodSummaryClassNames.contains(myIVisitor.getInvokeClassName())) {
                        // we wont be able to summarize the method invocation later
                        System.out.println("methodSummaryClassNames does not contain " + myIVisitor.getInvokeClassName());
                        canVeritest = false;
                        break;
                    }
                }
                lastExpression = myIVisitor.getSPFExpr();
                ifExpression = myIVisitor.getIfExpr();
                expression = ExpressionUtil.nonNullOp(Operation.Operator.AND, expression, lastExpression);
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







