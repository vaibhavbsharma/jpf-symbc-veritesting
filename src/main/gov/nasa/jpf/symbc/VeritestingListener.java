/*
 * Copyright (C) 2014, United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 *
 * Symbolic Pathfinder (jpf-symbc) is licensed under the Apache License, 
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 *        http://www.apache.org/licenses/LICENSE-2.0. 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */


package gov.nasa.jpf.symbc;


// MWW: general comment: many uses of nulls as 'signal' values.
// This usually leads to brittle and hard to debug code with lots
// of null pointer exceptions.  Be explicit!  Use exceptions
// to handle unexpected circumstances.

// MWW general comment: there is very little OO in this code.
// Poor encapsulation and little thought to behavioral interfaces.

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.PropertyListenerAdapter;
import gov.nasa.jpf.report.ConsolePublisher;
import gov.nasa.jpf.report.Publisher;
import gov.nasa.jpf.report.PublisherExtension;
import gov.nasa.jpf.symbc.numeric.*;
import gov.nasa.jpf.symbc.veritesting.*;
import gov.nasa.jpf.symbc.veritesting.ChoiceGenerators.StaticBranchChoiceGenerator;
import gov.nasa.jpf.symbc.veritesting.ChoiceGenerators.StaticPCChoiceGenerator;
import gov.nasa.jpf.symbc.veritesting.ChoiceGenerators.StaticSummaryChoiceGenerator;
import gov.nasa.jpf.symbc.veritesting.Visitors.FillAstHoleVisitor;
import gov.nasa.jpf.vm.*;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.Operation;

import java.io.PrintWriter;
import java.util.*;
import gov.nasa.jpf.symbc.veritesting.StaticRegionException;

import gov.nasa.jpf.symbc.veritesting.ExpressionUtil;

import static gov.nasa.jpf.symbc.veritesting.ExpressionUtil.SPFToGreenExpr;
import static gov.nasa.jpf.symbc.veritesting.FieldUtil.findCommonFieldOutputs;
import static gov.nasa.jpf.symbc.veritesting.FieldUtil.setLatestWrite;
import static gov.nasa.jpf.symbc.veritesting.LocalUtil.updateStackSlot;
import static gov.nasa.jpf.symbc.veritesting.HoleExpression.HoleType.*;

public class VeritestingListener extends PropertyListenerAdapter implements PublisherExtension {

    public static HashMap<String, VeritestingRegion> veritestingRegions;


    //TODO: make these into configuration options
    private static boolean boostPerf = true;
    /* In the jpf file, if
    veritestingMode = 1, then only regions that don't contain other regions are summarized and instantiated,
    veritestingMode = 2, then regions that contain other regions will also be summarized if they don't contain method calls
    veritestingMode = 3, then regions that contain other regions and method calls will be summarized but their
    exceptional behavior will not be explored
    veritestingMode = 4, includes all of the above + explores exceptional paths in regions
    //TODO make veritestingMode = 4 be sound, if a region has exceptional behavior, we shouldn't summarize it
    assuming that the exception will never be triggered
     */

    public static final int DEBUG_OFF = 0;
    public static final int DEBUG_LIGHT = 1;
    public static final int DEBUG_MEDIUM = 2;
    public static final int DEBUG_VERBOSE = 3;
    public static int veritestingMode = 0;
    public static int debug = DEBUG_VERBOSE;

    public static long totalSolverTime = 0, z3Time = 0;
    public static long parseTime = 0;
    public static long solverAllocTime = 0;
    public static long cleanupTime = 0;
    public static int solverCount = 0;
    private static int pathLabelCount = 1;
    public static int unsatSPFCaseCount = 0;
    private long staticAnalysisTime = 0;
    //These counts are incremented during static analysis once per operation encountered across all the region summaries
    public static int fieldReadAfterWrite = 0;
    static int fieldWriteAfterWrite = 0;
    static int fieldWriteAfterRead = 0;
    public static final boolean allowFieldReadAfterWrite = true;
    public static final boolean allowFieldWriteAfterRead = true;
    static final boolean allowFieldWriteAfterWrite = true;
    public static final int maxStaticExplorationDepth = 2;
    private static int methodSummaryRWInterference = 0;
    private static int phiCount;
    public static boolean filterUnsat = false;
    public static int filterUnsatTimeout = 2;
    public static String pathToZ3Binary = "z3";
    private Stack<InstantiateRegionOutput> instantiateRegionOutputStack = null;


    public VeritestingListener(Config conf, JPF jpf) {
        if (conf.hasValue("veritestingMode")) {
            veritestingMode = conf.getInt("veritestingMode");
            if (veritestingMode < 0 || veritestingMode > 4) {
                System.out.println("Warning: veritestingMode should be between 0 and 4 (both 0 and 4 included)");
                System.out.println("Warning: resetting veritestingMode to 0 (aka use vanilla SPF)");
                veritestingMode = 0;
            } else {
                if (conf.hasValue("filterUnsat"))
                    filterUnsat = conf.getBoolean("filterUnsat");
                if(filterUnsat) {
                    if (conf.hasValue("filterUnsatTimeout"))
                        filterUnsatTimeout = conf.getInt("filterUnsatTimeout");
                    if (conf.hasValue("pathToZ3Binary"))
                        pathToZ3Binary = conf.getString("pathToZ3Binary");
                }
                if (conf.hasValue("veritestingDebug"))
                    debug = conf.getInt("veritestingDebug");
                instantiateRegionOutputStack = new Stack<>();
            }
        } else {
            System.out.println("* Warning: no veritestingMode specified");
            System.out.println("* Warning: set veritestingMode to 0 to use vanilla SPF with VeritestingListener");
            System.out.println("* Warning: set veritestingMode to 1 to use veritesting with simple regions");
            System.out.println("* Warning: set veritestingMode to 2 to use veritesting with complex regions");
            System.out.println("* Warning: set veritestingMode to 3 to use veritesting with complex regions and method summaries");
            System.out.println("* Warning: set veritestingMode to 4 to use veritesting with complex regions, method summaries, and exceptional behavior");
            System.out.println("* Warning: resetting veritestingMode to 0 (aka use vanilla SPF)");
            veritestingMode = 0;
        }
        jpf.addPublisherExtension(ConsolePublisher.class, this);
    }

    public SymbolicInteger makeSymbolicInteger(String name) {
        //return new SymbolicInteger(name, MinMax.getVarMinInt(name), MinMax.getVarMaxInt(name));
        return new SymbolicInteger(name, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public void executeInstruction(VM vm, ThreadInfo ti, Instruction instructionToExecute) {
        if (veritestingMode == 0) return;

        if((veritestingMode == 4) && (StaticSummaryChoiceGenerator.spfCasesIgnoreInstList.contains(instructionToExecute))){
            assert(StaticSummaryChoiceGenerator.spfCasesIgnoreInstList.size() <= 1);
            StaticSummaryChoiceGenerator.spfCasesIgnoreInstList.remove(instructionToExecute);
            return;
        }

        // MWW: Essentially, this acts as a singleton to construct an element.
        if (veritestingRegions == null) {
            discoverRegions(ti); // static analysis to discover regions
        }

        // Here is the real code
        String key = generateRegionKey(ti, instructionToExecute);
        if (key.contains("Own_Below_Threat"))
            System.out.print("");

        if (veritestingRegions.containsKey(key)) {
            VeritestingRegion region = veritestingRegions.get(key);
            if(veritestingMode == 4) {
                if (!ti.isFirstStepInsn()) { // first time around
                    Expression regionSummary;
                    StaticPCChoiceGenerator newCG;
                    if (StaticPCChoiceGenerator.getKind(instructionToExecute) == StaticPCChoiceGenerator.Kind.OTHER) {
                        newCG = new StaticSummaryChoiceGenerator(region, instructionToExecute);
                    } else {
                        newCG = new StaticBranchChoiceGenerator(region, instructionToExecute);
                    }

                    try {
                        InstantiateRegionOutput instantiateRegionOutput = instantiateRegion(ti, region); // fill holes in region
                        if (instantiateRegionOutput == null) return;

                        regionSummary = instantiateRegionOutput.summaryExpression;
                        if (regionSummary == null)
                            return;
                        //System.out.println(ASTToString(regionSummary));
                        newCG.makeVeritestingCG(regionSummary, ti);
                        instantiateRegionOutputStack.push(instantiateRegionOutput);
                    } catch (StaticRegionException sre) {
                        System.out.println(sre.toString());
                        return; //problem filling holes, abort veritesting
                    }

                    SystemState systemState = vm.getSystemState();
                    systemState.setNextChoiceGenerator(newCG);
                    ti.setNextPC(instructionToExecute);
                    // System.out.println("resume program after creating veritesting region");
                } else {
                    ChoiceGenerator<?> cg = ti.getVM().getSystemState().getChoiceGenerator();
                    if (cg instanceof StaticPCChoiceGenerator) {
                        InstantiateRegionOutput instantiateRegionOutput = instantiateRegionOutputStack.peek();
                        StaticPCChoiceGenerator vcg = (StaticPCChoiceGenerator) cg;
                        int choice = (Integer) cg.getNextChoice();
                        Instruction nextInstruction = null;
                        try {
                            nextInstruction = vcg.execute(ti, instructionToExecute, choice, instantiateRegionOutput.fillHolesOutput);
                        } catch (StaticRegionException sre) {
                            System.out.println(sre.toString());
                            return;
                        }
                        ti.setNextPC(nextInstruction);
                        if (!cg.hasMoreChoices()) instantiateRegionOutputStack.pop();
                    }
                }
            } else if (veritestingMode >= 1 && veritestingMode <= 3) {
                // MWW: hopefully this code all goes away sometime soon!
                try {
                    InstantiateRegionOutput instantiateRegionOutput = instantiateRegion(ti, region); // fill holes in region
                    if (instantiateRegionOutput == null) return;

                    Expression regionSummary = instantiateRegionOutput.summaryExpression;
                    if (regionSummary == null)
                        return;

                    PathCondition pc;
                    if (ti.getVM().getSystemState().getChoiceGenerator() instanceof PCChoiceGenerator)
                        pc = ((PCChoiceGenerator)(ti.getVM().getSystemState().getChoiceGenerator())).getCurrentPC();
                    else{
                        pc = new PathCondition();
                        pc._addDet(new GreenConstraint(Operation.TRUE));
                    }
                    pc._addDet(new GreenConstraint(regionSummary));

                    // MWW: for debugging.
                    //System.out.println("pc: " + pc);
                    ((PCChoiceGenerator) ti.getVM().getSystemState().getChoiceGenerator()).setCurrentPC(pc);
                    // MWW: end of add.

                    Instruction nextInstruction = StaticPCChoiceGenerator.setupSPF(ti, instructionToExecute, region, instantiateRegionOutput.fillHolesOutput);
                    ti.setNextPC(nextInstruction);
                } catch (StaticRegionException sre) {
                    System.out.println(sre.toString());
                }
            }
        }
    }

   /*
    public long generateHashCode(String key) {
        FNV1 fnv = new FNV1a64();
        fnv.init(key);
        long hash = fnv.getHash();
        return hash;
    }
    */

    private String generateRegionKey(ThreadInfo ti, Instruction instructionToExecute) {
        return ti.getTopFrame().getClassInfo().getName()
                + "." + ti.getTopFrame().getMethodInfo().getName()
                + ti.getTopFrame().getMethodInfo().getSignature()
                + "#" + instructionToExecute.getPosition();
    }

    private InstantiateRegionOutput instantiateRegion(ThreadInfo ti, VeritestingRegion region) throws StaticRegionException {
        // increment path labels for current region.
        pathLabelCount += 1;
        // MWW: Why is this code not in the region class?
        region.ranIntoCount++;
        StackFrame sf = ti.getTopFrame();

        LogUtil.log(DEBUG_MEDIUM,"Starting region (" + region.toString()+")");

        // If we can't figure out the condition and we are not reasoning about a
        // method summary, return null.
        InstructionInfo instructionInfo = new InstructionInfo().invoke(sf);
        if (instructionInfo == null && !region.isMethodSummary()) return null;


        PathCondition pc;
        if(!(ti.getVM().getSystemState().getChoiceGenerator() instanceof PCChoiceGenerator)){
            //We've intercepted execution before any symbolic state was reached, so return
            pc = new PathCondition();
            pc._addDet(new GreenConstraint(Operation.TRUE));
        }
        else pc = ((PCChoiceGenerator) ti.getVM().getSystemState().getChoiceGenerator()).getCurrentPC();
        // this code checks if SPF has reached a branch with both sides being infeasible
        if (!boostPerf && instructionInfo != null) {
            PathCondition eqPC = pc.make_copy();
            eqPC._addDet(new GreenConstraint(instructionInfo.getCondition()));
            boolean eqSat = eqPC.simplify();
            PathCondition nePC = pc.make_copy();
            nePC._addDet(new GreenConstraint(instructionInfo.getNegCondition()));
            boolean neSat = nePC.simplify();
            if (!eqSat && !neSat) {
                System.out.println("both sides of branch at offset " + ti.getTopFrame().getPC().getPosition() + " are unsat");
                assert (false);
            }
        }


        FillHolesOutput fillHolesOutput =
                fillHoles(region, instructionInfo, sf, ti);
        if (fillHolesOutput == null || fillHolesOutput.holeHashMap == null) return null;
        Expression finalSummaryExpression = ExpressionUtil.nonNullOp(Operation.Operator.AND,
                region.getSummaryExpression(), fillHolesOutput.additionalAST);
        FillAstHoleVisitor visitor = new FillAstHoleVisitor(fillHolesOutput.holeHashMap);
        finalSummaryExpression = visitor.visit(finalSummaryExpression); //not constant-folding for now
        if ((VeritestingListener.veritestingMode >= 4)) {
            region.getSpfCases().instantiate(fillHolesOutput.holeHashMap);
            region.getSpfCases().simplify();
        }
        // exitTransitionsFillASTHoles(fillHolesOutput.holeHashMap);

        // pc._addDet(new GreenConstraint(finalSummaryExpression));
        if (!boostPerf) {
            //String finalSummaryExpressionString = ASTToString(finalSummaryExpression);
            if (!pc.simplify()) {
                System.out.println("veritesting region added unsat summary");
                System.out.println(ASTToString(((GreenConstraint)pc.header).getExp()));
                assert (false);
            }
        }

        return new InstantiateRegionOutput(finalSummaryExpression, fillHolesOutput);
    }

/*
    private void exitTransitionsFillASTHoles(HashMap<Expression, Expression> holeHashMap) throws StaticRegionException {
        //go through all regions and find existTransitions in them.
        //call fillASTHoles on the its pathConstraint
        assert (veritestingRegions != null);
        Collection<VeritestingRegion> regions = veritestingRegions.values();
        for (VeritestingRegion region : regions) {
            Collection<ExitTransition> exitTransitions = region.getExitTransitionHashMap();
            if (exitTransitions != null)
                for (ExitTransition exitTransition : exitTransitions) {
                    FillAstHoleVisitor visitor = new FillAstHoleVisitor(holeHashMap);
                    Expression newPathConstraint = visitor.visit(exitTransition.getPathConstraint());
                    exitTransition.setPathConstraint(newPathConstraint);
                    Expression newNegConstraint = visitor.visit(exitTransition.getNegNominalConstraint());
                    exitTransition.setNegNominalConstraint(newNegConstraint);
                    Expression newNominalConstraint = visitor.visit(exitTransition.getNominalConstraint());
                    exitTransition.setNominalConstraint(newNominalConstraint);
                }
        }
    }
*/

    private void discoverRegions(ThreadInfo ti) {
        Config conf = ti.getVM().getConfig();
        String classPath = conf.getStringArray("classpath")[0] + "/";
        String className = conf.getString("target");
        // should be like VeritestingPerf.testMe4([II)V aka jvm internal format
        VeritestingMain veritestingMain = new VeritestingMain(className + ".class");
        long startTime = System.nanoTime();
        veritestingMain.analyzeForVeritesting(ti, classPath, className);
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1000000; //milliseconds
        staticAnalysisTime = (endTime - startTime);
        System.out.println("veritesting analysis took " + duration + " milliseconds");
        System.out.println("Number of veritesting regions = " + veritestingRegions.size());
    }

    private static String ASTToString(Expression expression) {
        if (expression instanceof Operation) {
            Operation operation = (Operation) expression;
            String str = "";
            if (operation.getOperator().getArity() == 2)
                str = "(" + ASTToString(operation.getOperand(0)) + " " + operation.getOperator().toString() + " " +
                        ASTToString(operation.getOperand(1)) + ")";
            else if (operation.getOperator().getArity() == 1)
                str = "(" + operation.getOperator().toString() + ASTToString(operation.getOperand(0)) + ")";
            return str;
        } else
            return expression.toString();
    }


    public void publishFinished(Publisher publisher) {
        if (veritestingMode == 0) return;

        PrintWriter pw = publisher.getOut();
        publisher.publishTopicStart("VeritestingListener report (boostPerf = " + boostPerf + ", veritestingMode = " + veritestingMode + ")");
        pw.println("static analysis time = " + staticAnalysisTime / 1000000);
        pw.println("totalSolverTime = " + totalSolverTime / 1000000);
        pw.println("z3Time = " + z3Time / 1000000);
        pw.println("parsingTime = " + parseTime / 1000000);
        pw.println("solverAllocTime = " + solverAllocTime / 1000000);
        pw.println("cleanupTime = " + cleanupTime / 1000000);
        pw.println("solverCount = " + solverCount);
        pw.println("(fieldReadAfterWrite, fieldWriteAfterRead, fieldWriteAfterWrite = (" + fieldReadAfterWrite + ", " +
                VeritestingListener.fieldWriteAfterRead + ", " + fieldWriteAfterWrite + "), (these are dynamic counts aka once per region instantiation attempt)");
        pw.println("methodSummaryRWInterference = " + methodSummaryRWInterference);
        if (veritestingMode > 0) {
            pw.println("# regions = " + VeritestingListener.veritestingRegions.size());
            int maxSummarizedBranches = getMaxSummarizedBranch(false);
            ArrayList<Integer> ranIntoByBranch = new ArrayList<>();
            ArrayList<Integer> usedByBranch = new ArrayList<>();
            ranIntoByBranch.add(0);
            usedByBranch.add(0);
            for (int i = 0; i <= maxSummarizedBranches; i++) {
                ranIntoByBranch.add(0);
                usedByBranch.add(0);
                ArrayList<VeritestingRegion> regions = getRegionsForSummarizedBranchNum(i, false);
                for (VeritestingRegion region: regions) {
                    ranIntoByBranch.set(i, ranIntoByBranch.get(i) + (region.ranIntoCount != 0 ? 1 : 0));
                    usedByBranch.set(i, usedByBranch.get(i) + (region.usedCount != 0 ? 1 : 0));
                }
            }
            pw.println("# summarized branches: # regions (#run into, #used)");
            for (int i = 0; i <= maxSummarizedBranches; i++) {
                if (getRegionsForSummarizedBranchNum(i, false).size() != 0) {
                    pw.println(i + " branches: " + getRegionsForSummarizedBranchNum(i, false).size() + " (" +
                            ranIntoByBranch.get(i) + ", " + usedByBranch.get(i) + ") ");
                }
            }
            maxSummarizedBranches = getMaxSummarizedBranch(true);
            ranIntoByBranch = new ArrayList<>();
            usedByBranch = new ArrayList<>();
            ranIntoByBranch.add(0);
            usedByBranch.add(0);
            for (int i = 0; i <= maxSummarizedBranches; i++) {
                ranIntoByBranch.add(0);
                usedByBranch.add(0);
                ArrayList<VeritestingRegion> regions = getRegionsForSummarizedBranchNum(i, true);
                for (VeritestingRegion region: regions) {
                    ranIntoByBranch.set(i, ranIntoByBranch.get(i) + (region.ranIntoCount != 0 ? 1 : 0));
                    usedByBranch.set(i, usedByBranch.get(i) + (region.usedCount != 0 ? 1 : 0));
                }
            }
            pw.println("# summarized methods: # regions (#run into, #used)");
            for (int i = 0; i <= maxSummarizedBranches; i++) {
                if (getRegionsForSummarizedBranchNum(i, true).size() != 0) {
                    pw.println(i + " branches: " + getRegionsForSummarizedBranchNum(i, true).size() + " (" +
                            ranIntoByBranch.get(i) + ", " + usedByBranch.get(i) + ") ");
                }
            }
            ArrayList<String> regions = new ArrayList<>();
            for (HashMap.Entry<String, VeritestingRegion> entry : veritestingRegions.entrySet()) {
                regions.add(entry.getKey());
            }
        }
        ArrayList<String> regions = new ArrayList<>();
        for (Map.Entry<String, VeritestingRegion> entry : veritestingRegions.entrySet()) {
            regions.add(entry.getKey());
        }
        System.out.println("Sorted regions:");
        regions.sort(String::compareTo);
        for (String region: regions) {
            System.out.println(region);
        }
        System.out.println("VeritestingListener.unsatSPFCaseCount = " + unsatSPFCaseCount);

    }

    private ArrayList<VeritestingRegion> getRegionsForSummarizedBranchNum(int numBranch, boolean methodSummary) {
        ArrayList<VeritestingRegion> ret = new ArrayList<>();
        for (Map.Entry<String, VeritestingRegion> entry : veritestingRegions.entrySet()) {
            VeritestingRegion region = entry.getValue();
            if (region.getNumBranchesSummarized() == numBranch) {
                if (!methodSummary || region.isMethodSummary())
                    ret.add(region);
            }
        }
        return ret;
    }

    private int getMaxSummarizedBranch(boolean methodSummary) {
        int maxSummarizedBranch = 0;
        for (Map.Entry<String, VeritestingRegion> entry : veritestingRegions.entrySet()) {
            VeritestingRegion region = entry.getValue();
            if (region.getNumBranchesSummarized() > maxSummarizedBranch) {
                if (!methodSummary || (region.isMethodSummary()))
                    maxSummarizedBranch = region.getNumBranchesSummarized();
            }
        }
        return maxSummarizedBranch;
    }


    /*
    Have we previously resolved field outputs to the field output hole in holeExpression ?
     */
    private HoleExpression isFieldOutputComplete(ThreadInfo ti, StackFrame sf,
                                                 LinkedHashMap<Expression, Expression> methodHoles,
                                                 LinkedHashMap<Expression, Expression> retHoleHashMap,
                                                 boolean isMethodSummary, InvokeInfo callSiteInfo,
                                                 ArrayList<HoleExpression> completedFieldOutputs,
                                                 HoleExpression holeExpression) throws StaticRegionException {
        assert(holeExpression.getHoleType() == FIELD_PHI);
        for (HoleExpression completedFieldOutput : completedFieldOutputs) {
            if (FieldUtil.isSameField(ti, sf, holeExpression, completedFieldOutput, methodHoles, retHoleHashMap,
                    isMethodSummary, callSiteInfo, true)) {
                return completedFieldOutput;
            }
        }
        return null;
    }

    /*
    Load from local variable stack slots IntegerExpression objects and store them into holeHashMap
     */
    //if a read after write happens on a class field, the read operation should return the latest value written to
    //the field
    private FillHolesOutput fillHoles(VeritestingRegion region,
                                      InstructionInfo instructionInfo,
                                      final StackFrame stackFrame,
                                      final ThreadInfo ti) throws StaticRegionException {
        LinkedHashMap<Expression, Expression> holeHashMap = region.getHoleHashMap();
        LinkedHashMap<Expression, Expression> retHoleHashMap = new LinkedHashMap<>();
        LinkedHashSet<HoleExpression> additionalOutputVars = new LinkedHashSet<>();
        Expression additionalAST;
        FillNonInputHoles fillNonInputHoles =
                new FillNonInputHoles(retHoleHashMap, null, holeHashMap, instructionInfo, false, ti, stackFrame, region);
        FillNonInputHolesOutput fillNonInputHolesOutput = fillNonInputHoles.invoke();
        if (fillNonInputHolesOutput.result()) return null;
        additionalAST = fillNonInputHolesOutput.getFieldOutputExpression();
        retHoleHashMap = fillNonInputHoles.retHoleHashMap;
        FillInputHoles fillInputHoles =
                new FillInputHoles(stackFrame, ti, retHoleHashMap, null, holeHashMap, false).invoke();
        if (fillInputHoles.failure()) return null;
        retHoleHashMap = fillInputHoles.retHoleHashMap;
        FillArrayLoadOutput fillArrayLoadOutput = new FillArrayLoadHoles(ti, region.getHoleHashMap(), retHoleHashMap, additionalAST).invoke();
        if (!fillArrayLoadOutput.isOk()) return null;
        additionalAST = fillArrayLoadOutput.getAdditionalAST();


        FillArrayStoreOutput fillArrayStoreOutput = new FillArrayStoreHoles(ti, region.getHoleHashMap(), retHoleHashMap, additionalAST).invoke();
        if (!fillArrayStoreOutput.isOk()) return null;
        additionalAST = fillArrayStoreOutput.getAdditionalAST();
        additionalOutputVars = fillArrayStoreOutput.getAdditionalOutputVars();


        FillInvokeHole fillInvokeHole = new FillInvokeHole(stackFrame, ti, holeHashMap, retHoleHashMap, additionalAST).invoke();
        if (fillInvokeHole.is()) return null;
        retHoleHashMap = fillInvokeHole.getRetHoleHashMap();
        additionalAST = fillInvokeHole.getAdditionalAST();
        additionalOutputVars.addAll(fillInvokeHole.getAdditionalOutputVars());

        return new FillHolesOutput(retHoleHashMap, additionalAST, additionalOutputVars);
    }

    private boolean outOfBound(Expression[] arraySymbConstraint, Expression finalValueGreen, ThreadInfo ti) {
        PCChoiceGenerator lastCG = ti.getVM().getSystemState().getLastChoiceGeneratorOfType(PCChoiceGenerator.class);
        PathCondition pcCopy;
        if (lastCG == null)
            pcCopy = new PathCondition();
        else
            pcCopy = ((PCChoiceGenerator) lastCG).getCurrentPC().make_copy();
        Expression lowerOutOfBoundConstraint = new Operation(Operation.Operator.GE, finalValueGreen, new IntConstant(arraySymbConstraint.length));
        Expression upperOutOfBoundConstraint = new Operation(Operation.Operator.LT, finalValueGreen, new IntConstant(0));
        Expression outOfBoundConstraint = new Operation(Operation.Operator.OR, lowerOutOfBoundConstraint, upperOutOfBoundConstraint);
        pcCopy._addDet(new GreenConstraint(outOfBoundConstraint));
        return pcCopy.simplify();
    }

    public static gov.nasa.jpf.symbc.numeric.Expression fillFieldHole(ThreadInfo ti, StackFrame stackFrame,
                                                                      HoleExpression holeExpression,
                                                                      LinkedHashMap<Expression, Expression> methodHoles,
                                                                      LinkedHashMap<Expression, Expression> retHoleHashMap,
                                                                      boolean isMethodSummary, InvokeInfo callSiteInfo,
                                                                      boolean isRead,
                                                                      gov.nasa.jpf.symbc.numeric.Expression finalValue)
            throws StaticRegionException {
        HoleExpression.FieldInfo fieldInputInfo = holeExpression.getFieldInfo();
        final boolean isStatic = fieldInputInfo.isStaticField;
        int objRef = FieldUtil.getObjRef(ti, stackFrame, holeExpression, methodHoles, retHoleHashMap, isMethodSummary, callSiteInfo);
        //load the class name dynamically based on the object reference
        if(objRef != -1) fieldInputInfo.setFieldDynClassName(ti.getClassInfo(objRef).getName());
        if (objRef == 0) {
            System.out.println("java.lang.NullPointerException" + "referencing field '" +
                    fieldInputInfo.fieldName + "' on null object");
            assert (false);
        } else {
            ClassInfo ci;
            try {
                ci = ClassLoaderInfo.getCurrentResolvedClassInfo(fieldInputInfo.getDynOrStClassName());
            } catch (ClassInfoException e) {
                throw new StaticRegionException("fillFieldInputHole: class loader failed to resolve class name " + fieldInputInfo.getDynOrStClassName());
            }
            ElementInfo eiFieldOwner;
            if (!isStatic) {
                if (isRead) eiFieldOwner = ti.getElementInfo(objRef);
                else eiFieldOwner = ti.getModifiableElementInfo(objRef);
            }
            else {
                if(isRead) eiFieldOwner = ci.getStaticElementInfo();
                else eiFieldOwner = ci.getModifiableStaticElementInfo();
            }
            FieldInfo fieldInfo = null;
            if (ci != null && !isStatic)
                fieldInfo = ci.getInstanceField(fieldInputInfo.fieldName);
            if (ci != null && isStatic)
                fieldInfo = ci.getStaticField(fieldInputInfo.fieldName);
            if (fieldInfo == null) {
                System.out.println("java.lang.NoSuchFieldError" + "referencing field '" + fieldInputInfo.fieldName
                        + "' in " + eiFieldOwner);
                assert (false);
            } else {
                if(isRead) {
                    Object fieldAttr = eiFieldOwner.getFieldAttr(fieldInfo);
                    if (fieldAttr != null) {
                        return (gov.nasa.jpf.symbc.numeric.Expression) fieldAttr;
                    } else {
                        if (fieldInfo.getStorageSize() == 1) {
                            return new IntegerConstant(eiFieldOwner.get1SlotField(fieldInfo));
                        } else {
                            return new IntegerConstant(eiFieldOwner.get2SlotField(fieldInfo));
                        }
                    }
                } else {
                    int fieldSize = fieldInfo.getStorageSize();
                    if (fieldSize == 1) {
                        eiFieldOwner.set1SlotField(fieldInfo, 0); // field value should not matter (I, Vaibhav, think)
                        eiFieldOwner.setFieldAttr(fieldInfo, finalValue);
                    } else {
                        eiFieldOwner.set2SlotField(fieldInfo, 0); // field value should not matter (I, Vaibhav, think)
                        eiFieldOwner.setFieldAttr(fieldInfo, finalValue);
                    }
                }
            }
        }
        return null;
    }


    private class FillNonInputHoles {
        private final InstructionInfo instructionInfo;
        private final boolean isMethodSummary;
        private final StackFrame stackFrame;
        private final ThreadInfo ti;
        private final VeritestingRegion region;
        private LinkedHashMap<Expression, Expression> retHoleHashMap;
        private InvokeInfo callSiteInfo;
        private LinkedHashMap<Expression, Expression> methodHoles;

        public FillNonInputHoles(LinkedHashMap<Expression, Expression> retHoleHashMap, InvokeInfo callSiteInfo,
                                 LinkedHashMap<Expression, Expression> methodHoles, InstructionInfo instructionInfo,
                                 boolean isMethodSummary, ThreadInfo ti, StackFrame stackFrame,
                                 VeritestingRegion region) {
            this.retHoleHashMap = retHoleHashMap;
            this.callSiteInfo = callSiteInfo;
            this.methodHoles = methodHoles;
            this.instructionInfo = instructionInfo;
            this.isMethodSummary = isMethodSummary;
            this.stackFrame = stackFrame;
            this.ti = ti;
            this.region = region;
        }

        /*
        this method returns a true to indicate failure, returns false to indicate success
         */
        public FillNonInputHolesOutput invoke() throws StaticRegionException {
            gov.nasa.jpf.symbc.numeric.Expression spfExpr;
            Expression greenExpr;//fill all holes inside the method summary
            Expression fieldOutputExpression = null;
            ArrayList<HoleExpression> completedFieldOutputs = new ArrayList<>();
            for(Map.Entry<Expression, Expression> entry1 : methodHoles.entrySet()) {
                Expression methodKeyExpr = entry1.getKey();
                assert (methodKeyExpr instanceof HoleExpression);
                HoleExpression methodKeyHole = (HoleExpression) methodKeyExpr;

                switch(methodKeyHole.getHoleType()) {
                    case CONDITION:
                        if(isMethodSummary) {
                            System.out.println("unsupported condition hole in method summary");
                            return new FillNonInputHolesOutput(true, null);
                        } else {
                            assert(instructionInfo != null);
                            greenExpr = instructionInfo.getCondition();
                            assert (greenExpr != null);
                            retHoleHashMap.put(methodKeyExpr, greenExpr);
                        }
                        break;
                    case NEGCONDITION:
                        if(isMethodSummary) {
                            System.out.println("unsupported negCondition hole in method summary");
                            return new FillNonInputHolesOutput(true, null);
                        } else {
                            assert (instructionInfo != null);
                            greenExpr = instructionInfo.getNegCondition();
                            assert (greenExpr != null);
                            retHoleHashMap.put(methodKeyExpr, greenExpr);
                        }
                        break;
                    case LOCAL_OUTPUT:
                    case INTERMEDIATE:
                        spfExpr = makeSymbolicInteger(methodKeyHole.getHoleVarName() + pathLabelCount);
                        greenExpr = SPFToGreenExpr(spfExpr);
                        retHoleHashMap.put(methodKeyHole, greenExpr);
                        break;
                    case FIELD_OUTPUT:
                        updateStackSlot(ti, callSiteInfo, methodKeyHole, isMethodSummary);
                        FieldUtil.setLatestWrite(methodKeyHole, ti, stackFrame, methodHoles, retHoleHashMap, isMethodSummary, callSiteInfo);
                        if(FieldUtil.hasWriteBefore(methodKeyHole, ti, stackFrame, methodHoles, retHoleHashMap,
                                isMethodSummary, callSiteInfo)) {
                            VeritestingListener.fieldWriteAfterWrite += 1;
                            if ((VeritestingListener.allowFieldWriteAfterWrite == false))
                                return new FillNonInputHolesOutput(true, null);
                        }
                        retHoleHashMap.put(methodKeyHole, null);
                        break;
                    case FIELD_PHI:
                        fieldOutputExpression = ExpressionUtil.nonNullOp(Operation.Operator.AND, fieldOutputExpression,
                                getFieldOutputExpression(completedFieldOutputs, methodKeyHole));
                        break;
                    case NONE:
                        throw new StaticRegionException("expression marked as hole with NONE hole type: " +
                                methodKeyHole.toString());
                    case INVOKE:
                    case FIELD_INPUT:
                    case LOCAL_INPUT:
                    default:
                        break;
                }
            }
            return new FillNonInputHolesOutput(false, fieldOutputExpression);
        }

        private Expression getFieldOutputExpression(ArrayList<HoleExpression> completedFieldOutputs,
                                                    HoleExpression holeExpression) throws StaticRegionException {
            assert(holeExpression.getHoleType() == FIELD_PHI);
            Expression fieldOutputExpression = null;
            HoleExpression.FieldInfo fieldInfo = holeExpression.getFieldInfo();
            HoleExpression prevFilledHole = isFieldOutputComplete(
                    ti, stackFrame, methodHoles, retHoleHashMap, isMethodSummary, callSiteInfo,
                    completedFieldOutputs, holeExpression);
            if (prevFilledHole != null) {
                retHoleHashMap.put(holeExpression, retHoleHashMap.get(prevFilledHole));
                return null;
            }
            assert (fieldInfo != null);
            HoleExpression prevValueHole = holeExpression.getFieldInfo().getFieldInputHole();
            if(!retHoleHashMap.containsKey(prevValueHole)) {
                FillFieldInputHole fillFieldInputHole = new FillFieldInputHole(prevValueHole, methodHoles,
                        isMethodSummary, callSiteInfo, ti, stackFrame, retHoleHashMap);
                fillFieldInputHole.invoke();
                retHoleHashMap = fillFieldInputHole.getRetHoleHashMap();
            }
            Expression prevValue = retHoleHashMap.get(prevValueHole);
            Expression finalValueSymVar =
                    SPFToGreenExpr(makeSymbolicInteger(holeExpression.getHoleVarName() + ".final_value" + nextPhiCount()));
            //returns an predicate that is a conjunction of (predicate IMPLIES (finalValueSymVar EQ outputVar)) expressions
            fieldOutputExpression =
                    findCommonFieldOutputs(ti, stackFrame, holeExpression, region.getOutputVars(),
                            finalValueSymVar, prevValue, methodHoles, retHoleHashMap, isMethodSummary, callSiteInfo);
            completedFieldOutputs.add(holeExpression);
            setLatestWrite(holeExpression, ti, stackFrame, methodHoles, retHoleHashMap, isMethodSummary, callSiteInfo);
            retHoleHashMap.put(holeExpression, finalValueSymVar);
            return fieldOutputExpression;
        }
    }

    private static int nextPhiCount() {
        phiCount++;
        return phiCount;
    }

    private class FillInputHoles {
        private final boolean isMethodSummary;
        private boolean failure;
        private final StackFrame stackFrame;
        private final ThreadInfo ti;
        private LinkedHashMap<Expression, Expression> retHoleHashMap;
        private InvokeInfo callSiteInfo;
        private LinkedHashMap<Expression, Expression> methodHoles;

        public FillInputHoles(StackFrame stackFrame, ThreadInfo ti, LinkedHashMap<Expression, Expression> retHoleHashMap,
                              InvokeInfo callSiteInfo, LinkedHashMap<Expression, Expression> methodHoles,
                              boolean isMethodSummary) {
            this.stackFrame = stackFrame;
            this.ti = ti;
            this.retHoleHashMap = retHoleHashMap;
            this.callSiteInfo = callSiteInfo;
            this.methodHoles = methodHoles;
            this.isMethodSummary = isMethodSummary;
        }

        boolean failure() {
            return failure;
        }

        public FillInputHoles invoke() throws StaticRegionException {
            gov.nasa.jpf.symbc.numeric.Expression spfExpr;
            Expression greenExpr;
            for(Map.Entry<Expression, Expression> entry1 : methodHoles.entrySet()) {

                Expression methodKeyExpr = entry1.getKey();
                assert (methodKeyExpr instanceof HoleExpression);
                HoleExpression methodKeyHole = (HoleExpression) methodKeyExpr;
                switch (methodKeyHole.getHoleType()) {
                    //LOCAL_INPUTs can be mapped to parameters at the call site, non-parameter local inputs
                    // need to be mapped to lhsExpr variables since we cannot create a stack for the summarized method
                    case LOCAL_INPUT:
                        //get the latest value written into this field, not the value in the field at the beginning of
                        //this region. Look in retHoleHashmap because non-input holes get populated before input holes
                        if(LocalUtil.hasWriteBefore(methodKeyHole, methodHoles)) {
                            HoleExpression holeExpression = LocalUtil.findPreviousWrite(methodKeyHole, methodHoles);
                            assert(holeExpression.getHoleType() == HoleExpression.HoleType.LOCAL_OUTPUT);
                            assert(retHoleHashMap.containsKey(holeExpression));
                            retHoleHashMap.put(methodKeyHole, retHoleHashMap.get(holeExpression));
                        } else {
                            if(isMethodSummary) {
                                //local inputs used in method summary have to come from the filled-up holes in paramList
                                if (methodKeyHole.getLocalStackSlot() < callSiteInfo.paramList.size()) {
                                    int methodLocalStackSlot = methodKeyHole.getLocalStackSlot();
                                    if (callSiteInfo.paramList.get(methodLocalStackSlot) instanceof HoleExpression) {
                                        //int callSiteLocalStackSlot = ((HoleExpression)callSiteInfo.paramList.get(methodLocalStackSlot)).getLocalStackSlot();
                                        //methodKeyHole.setLocalStackSlot(callSiteLocalStackSlot);
                                        retHoleHashMap.put(methodKeyHole, retHoleHashMap.get(callSiteInfo.paramList.get(methodLocalStackSlot)));
                                    }
                                    else //a constant could have been passed as an argument instead of a variable
                                        retHoleHashMap.put(methodKeyHole, callSiteInfo.paramList.get(methodLocalStackSlot));
                                } else {
                                    //Local variables in the method summary should just become intermediate variables
                                    gov.nasa.jpf.symbc.numeric.Expression finalValueSPF =
                                            makeSymbolicInteger(methodKeyHole.getHoleVarName() + pathLabelCount);
                                    Expression finalValueGreen = SPFToGreenExpr(finalValueSPF);
                                    retHoleHashMap.put(methodKeyHole, finalValueGreen);
                                }
                            } else {
                                gov.nasa.jpf.symbc.numeric.Expression finalValueSPF =
                                        (gov.nasa.jpf.symbc.numeric.Expression) stackFrame.getLocalAttr(methodKeyHole.getLocalStackSlot());
                                if (finalValueSPF == null)
                                    finalValueSPF = new IntegerConstant(stackFrame.getLocalVariable(methodKeyHole.getLocalStackSlot()));
                                Expression finalValueGreen = SPFToGreenExpr(finalValueSPF);
                                retHoleHashMap.put(methodKeyHole, finalValueGreen);
                            }
                        }
                        break;
                    case FIELD_INPUT:
                        FillFieldInputHole fillFieldInputHole = new FillFieldInputHole(methodKeyHole, methodHoles,
                                isMethodSummary, callSiteInfo, ti, stackFrame, retHoleHashMap);
                        fillFieldInputHole.invoke();
                        retHoleHashMap = fillFieldInputHole.getRetHoleHashMap();
                        break;
                    case INVOKE:
                        if(isMethodSummary) {
                            //Update the global stack slot of holes used in the invoke-call of method summary
                            // based on the caller's paramList for all local holes
                            InvokeInfo methodCallSiteInfo = methodKeyHole.getInvokeInfo();
                            for(int i=0; i<methodCallSiteInfo.paramList.size(); i++) {
                                if(HoleExpression.isLocal(methodCallSiteInfo.paramList.get(i))) {
                                    assert(methodCallSiteInfo.paramList.get(i) instanceof HoleExpression);
                                    HoleExpression h = (HoleExpression) methodCallSiteInfo.paramList.get(i);
                                    int methodCallSiteStackSlot = h.getLocalStackSlot();
                                    if(methodCallSiteStackSlot < callSiteInfo.paramList.size()) {
                                        if(HoleExpression.isLocal(callSiteInfo.paramList.get(methodCallSiteStackSlot))) {
                                            assert(callSiteInfo.paramList.get(methodCallSiteStackSlot) instanceof HoleExpression);
                                            HoleExpression callSiteHole = (HoleExpression) callSiteInfo.paramList.get(methodCallSiteStackSlot);
                                            //It is important to use getGlobalOrLocalStackSlot here, not getLocalStackSlot
                                            // because we would like the caller's globalStackSlot to be used if possible
                                            h.setGlobalStackSlot(callSiteHole.getGlobalOrLocalStackSlot(ti.getTopFrame().getClassInfo().getName(),
                                                    ti.getTopFrame().getMethodInfo().getName()));
                                            methodCallSiteInfo.paramList.set(i, h);
                                        }
                                        else {
                                            Expression callSiteExpression = callSiteInfo.paramList.get(methodCallSiteStackSlot);
                                            methodCallSiteInfo.paramList.set(i, callSiteExpression);
                                            if(callSiteExpression instanceof HoleExpression) {
                                                if (methodHoles.containsKey(callSiteInfo.paramList.get(methodCallSiteStackSlot)) == false)
                                                    methodHoles.put(callSiteExpression, callSiteExpression);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        break;
                    default: break;
                }
            }
            failure = false;
            return this;
        }


    }

    private class FillMethodSummary {
        private boolean myResult;
        private StackFrame stackFrame;
        private ThreadInfo ti;
        private LinkedHashMap<Expression, Expression> holeHashMap;
        private LinkedHashMap<Expression, Expression> retHoleHashMap;
        private Expression additionalAST;
        private HoleExpression keyHoleExpression;
        private InvokeInfo callSiteInfo;
        private VeritestingRegion methodSummary;
        private LinkedHashSet<HoleExpression> additionalOutputVars;


        public FillMethodSummary(StackFrame stackFrame, ThreadInfo ti, LinkedHashMap<Expression, Expression> holeHashMap,
                                 LinkedHashMap<Expression, Expression> retHoleHashMap, Expression additionalAST,
                                 HoleExpression keyHoleExpression, InvokeInfo callSiteInfo, VeritestingRegion methodSummary) {
            this.stackFrame = stackFrame;
            this.ti = ti;
            this.holeHashMap = holeHashMap;
            this.retHoleHashMap = retHoleHashMap;
            this.additionalAST = additionalAST;
            this.keyHoleExpression = keyHoleExpression;
            this.callSiteInfo = callSiteInfo;
            this.methodSummary = methodSummary;
            this.additionalOutputVars = new LinkedHashSet<>();
        }

        boolean is() {
            return myResult;
        }

        public LinkedHashMap<Expression, Expression> getRetHoleHashMap() {
            return retHoleHashMap;
        }

        public Expression getAdditionalAST() {
            return additionalAST;
        }

        public FillMethodSummary invoke() throws StaticRegionException {
            FillNonInputHoles fillNonInputHoles;
            LinkedHashMap<Expression, Expression> methodHoles = methodSummary.getHoleHashMap();
            if(FieldUtil.hasRWInterference(holeHashMap, methodHoles, retHoleHashMap, ti, stackFrame, true, callSiteInfo)) {
                methodSummaryRWInterference++;
                System.out.println("method summary hole interferes with outer region for method ("
                        + methodSummary.getClassName() + ", " + methodSummary.getMethodName() + ")");
                myResult = true;
                return this;
            }
            fillNonInputHoles =
                    new FillNonInputHoles(retHoleHashMap, callSiteInfo, methodHoles, null, true, this.ti, this.stackFrame, methodSummary);
            FillNonInputHolesOutput fillNonInputHolesOutput = fillNonInputHoles.invoke();
            if (fillNonInputHolesOutput.result()) {
                myResult = true;
                return this;
            }

            additionalAST = ExpressionUtil.nonNullOp(Operation.Operator.AND, additionalAST, fillNonInputHolesOutput.getFieldOutputExpression());
            retHoleHashMap = fillNonInputHoles.retHoleHashMap;
            findAdditionalOutputVars(methodHoles);// reports if the method summary writes to any fields

            FillInputHoles fillInputHolesMS =
                    new FillInputHoles(stackFrame, ti, retHoleHashMap, callSiteInfo, methodHoles, true).invoke();
            if (fillInputHolesMS.failure()) {
                myResult = true;
                return this;
            }
            retHoleHashMap = fillInputHolesMS.retHoleHashMap;

            FillArrayLoadOutput fillArrayLoadOutput = new FillArrayLoadHoles(ti, methodHoles, retHoleHashMap, additionalAST).invoke();
            if (!fillArrayLoadOutput.isOk()) return null;
            additionalAST = fillArrayLoadOutput.getAdditionalAST();

            FillInvokeHole fillInvokeHole = new FillInvokeHole(stackFrame, ti, methodHoles, retHoleHashMap, additionalAST).invoke();
            if (fillInvokeHole.is()) {
                myResult = true;
                return this;
            }
            retHoleHashMap = fillInvokeHole.getRetHoleHashMap();
            additionalAST = fillInvokeHole.getAdditionalAST();
            additionalOutputVars.addAll(fillInvokeHole.getAdditionalOutputVars());

            Expression retValEq = null;
            if (methodSummary.retValList != null)
                retValEq = new Operation(Operation.Operator.EQ, methodSummary.retValList.entrySet().iterator().next().getValue(), keyHoleExpression);
            Expression mappingOperation = retValEq;
            if (methodSummary.getSummaryExpression() != null)
                mappingOperation = new Operation(Operation.Operator.AND, mappingOperation, methodSummary.getSummaryExpression());
            additionalAST = ExpressionUtil.nonNullOp(Operation.Operator.AND, additionalAST, mappingOperation);
            Expression finalValueGreen = SPFToGreenExpr(makeSymbolicInteger(keyHoleExpression.getHoleVarName() + pathLabelCount));
            retHoleHashMap.put(keyHoleExpression, finalValueGreen);
            methodSummary.ranIntoCount++;
            methodSummary.usedCount++;
            myResult = false;
            return this;
        }

        private void findAdditionalOutputVars(LinkedHashMap<Expression, Expression> methodHoles) {
            for(Map.Entry<Expression, Expression> entry: methodHoles.entrySet()) {
                HoleExpression h = (HoleExpression) entry.getKey();
                if((h.getHoleType() == FIELD_OUTPUT || h.getHoleType() == FIELD_PHI) &&
                        (h.getGlobalStackSlot() != -1 || h.getFieldInfo().isStaticField)) {
                    assert(retHoleHashMap.containsKey(h));
                    additionalOutputVars.add(h);
                }
            }
        }

        public LinkedHashSet<HoleExpression> getAdditionalOutputVars() {
            return additionalOutputVars;
        }
    }

    private class FillInvokeHole {
        private boolean myResult;
        private StackFrame stackFrame;
        private ThreadInfo ti;
        private LinkedHashMap<Expression, Expression> holeHashMap;
        private LinkedHashMap<Expression, Expression> retHoleHashMap;
        private Expression additionalAST;

        public LinkedHashSet<HoleExpression> getAdditionalOutputVars() {
            return additionalOutputVars;
        }

        private LinkedHashSet<HoleExpression> additionalOutputVars;

        public FillInvokeHole(StackFrame stackFrame, ThreadInfo ti, LinkedHashMap<Expression, Expression> holeHashMap,
                              LinkedHashMap<Expression, Expression> retHoleHashMap, Expression additionalAST) {
            this.stackFrame = stackFrame;
            this.ti = ti;
            this.holeHashMap = holeHashMap;
            this.retHoleHashMap = retHoleHashMap;
            this.additionalAST = additionalAST;
            additionalOutputVars = new LinkedHashSet<>();
        }

        boolean is() {
            return myResult;
        }

        public LinkedHashMap<Expression, Expression> getRetHoleHashMap() {
            return retHoleHashMap;
        }

        public Expression getAdditionalAST() {
            return additionalAST;
        }

        public FillInvokeHole invoke() throws StaticRegionException {
            // resolve all invoke holes in the current region's summary expression
            for(Map.Entry<Expression, Expression> entry : holeHashMap.entrySet()) {
                Expression key = entry.getKey();
                assert (key instanceof HoleExpression);
                HoleExpression keyHoleExpression = (HoleExpression) key;
                switch (keyHoleExpression.getHoleType()) {
                    case INVOKE:
                        InvokeInfo callSiteInfo = keyHoleExpression.getInvokeInfo();
                        ClassInfo ci = null;
                        if (callSiteInfo.isVirtualInvoke) {
                            Expression callingObject = retHoleHashMap.get(callSiteInfo.paramList.get(0));
                            ci = ti.getClassInfo(((IntConstant) callingObject).getValue());
                        }
                        if (callSiteInfo.isStaticInvoke) {
                            ci = ClassLoaderInfo.getCurrentResolvedClassInfo(callSiteInfo.className);
                        }

                        // if ci failure null, that means either MyIVisitor.visitInvoke has a bug or we failed to load the class
                        assert(ci != null);
                        //Change the class name based on the call site object reference
                        callSiteInfo.className = ci.getName();
                        //If there exists a invokeVirtual for a method that we weren't able to summarize, skip veritesting
                        String key1 = callSiteInfo.className + "." + callSiteInfo.methodName + callSiteInfo.methodSignature + "#0";
                        FNV1 fnv = new FNV1a64();
                        fnv.init(key1);
                        long hash = fnv.getHash();
                        if(!veritestingRegions.containsKey(key1)) {
                            System.out.println("Could not find method summary for " +
                                    callSiteInfo.className+"."+callSiteInfo.methodName+callSiteInfo.methodSignature+"#0");
                            myResult = true;
                            return this;
                        }
                        //All holes in callSiteInfo.paramList will also be present in holeHashmap and will be filled up here
                        for (Expression h : callSiteInfo.paramList) {
                            if (h instanceof HoleExpression) {
                                if (holeHashMap.containsKey(h) == false) {
                                    System.out.println("invokeHole's holeHashmap does not contain hole: " + h.toString());
                                    assert(false);
                                }
                            }
                        }
                        VeritestingRegion methodSummary = veritestingRegions.get(key1);
                        FillMethodSummary fillMethodSummary = new FillMethodSummary(stackFrame, ti, holeHashMap,
                                retHoleHashMap, additionalAST, keyHoleExpression, callSiteInfo, methodSummary).invoke();
                        if (fillMethodSummary.is()) {
                            myResult = true;
                            return this;
                        }
                        retHoleHashMap = fillMethodSummary.getRetHoleHashMap();
                        additionalAST = fillMethodSummary.getAdditionalAST();
                        additionalOutputVars = fillMethodSummary.getAdditionalOutputVars();
                        break;
                }
            }
            myResult = false;
            return this;
        }
    }

  /*public IntegerExpression constantFold(IntegerExpression integerExpression) {
    if(integerExpression instanceof IntegerConstant) return integerExpression;
    if(integerExpression instanceof ComplexNonLinearIntegerExpression) {
      ComplexNonLinearIntegerExpression cnlie = (ComplexNonLinearIntegerExpression) integerExpression;
      if (cnlie.getLeft() instanceof IntegerConstant && cnlie.getRight() instanceof IntegerConstant) {
        int left = ((IntegerConstant) cnlie.getLeft()).value();
        int right = ((IntegerConstant) cnlie.getRight()).value();
        int failure = 0;
        switch (cnlie.getOperator()) {
          case DIV:
            failure = left / right;
            break;
          case MUL:
            failure = left * right;
            break;
          case MINUS:
            failure = left - right;
            break;
          case PLUS:
            failure = left + right;
            break;
          case CMP:
            failure = Integer.compare(left, right);
            break;
          case AND:
            failure = left & right;
            break;
          case OR:
            failure = left | right;
            break;
          case XOR:
            failure = left ^ right;
            break;
          case SHIFTL:
            failure = left << right;
            break;
          case SHIFTR:
            failure = left >> right;
            break;
          case SHIFTUR:
            failure = left >>> right;
            break;
          case REM:
            failure = left % right;
            break;
          case NONE_OP:
            switch (cnlie.getComparator()) {
              case EQ:
                failure = (left == right) ? 1 : 0;
                break;
              case NE:
                failure = (left != right) ? 1 : 0;
                break;
              case LT:
                failure = (left < right) ? 1 : 0;
                break;
              case LE:
                failure = (left <= right) ? 1 : 0;
                break;
              case GT:
                failure = (left > right) ? 1 : 0;
                break;
              case GE:
                failure = (left >= right) ? 1 : 0;
                break;
              case LOGICAL_AND:
                failure = ((left != 0) && (right != 0)) ? 1 : 0;
                break;
              case LOGICAL_OR:
                failure = ((left != 0) || (right != 0)) ? 1 : 0;
                break;
              case NONE_CMP:
                System.out.println("constantFold found NONE_OP and NONE_CMP");
                assert(false);
                break;
              default:
                System.out.println("constantFold found NONE_OP and undefined comparator (" + cnlie.getComparator() + ")");
                assert(false);
                break;
            }
            break;
          default:
            System.out.println("constantFold found undefined operator (" + cnlie.getOperator() + ")");
            assert (false);
        }
        return new IntegerConstant(failure);
      }
      cnlie.setLeft(constantFold(cnlie.getLeft()));
      cnlie.setRight(constantFold(cnlie.getRight()));
      if(cnlie.getLeft() instanceof IntegerConstant) {
        if(cnlie.getRight() instanceof IntegerConstant) {
          if(cnlie.getOperator() == NONE_OP) {
            return constantFold(new ComplexNonLinearIntegerExpression(cnlie.getLeft(), cnlie.getComparator(), cnlie.getRight()));
          }
          if(cnlie.getComparator() == NONE_CMP) {
            return constantFold(new ComplexNonLinearIntegerExpression(cnlie.getLeft(), cnlie.getOperator(), cnlie.getRight()));
          }
        }
      }
    }
    return integerExpression;
}*/

}
