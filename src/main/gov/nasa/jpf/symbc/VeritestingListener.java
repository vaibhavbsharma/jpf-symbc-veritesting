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

import com.ibm.wala.types.TypeReference;
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
import static gov.nasa.jpf.symbc.veritesting.LocalUtil.updateStackSlot;
import static gov.nasa.jpf.symbc.veritesting.HoleExpression.HoleType.*;

public class VeritestingListener extends PropertyListenerAdapter implements PublisherExtension {

    public static HashMap<String, VeritestingRegion> veritestingRegions;


    //TODO: make these into configuration options
    public static boolean boostPerf = false;
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
    public static int debug = 0;

    public static long totalSolverTime = 0, z3Time = 0;
    public static long parseTime = 0;
    public static long solverAllocTime = 0;
    public static long cleanupTime = 0;
    public static int solverCount = 0;
    public static int pathLabelCount = 1;
    private long staticAnalysisTime = 0;
    //These counts are incremented during static analysis once per operation encountered across all the region summaries
    public static int fieldReadAfterWrite = 0;
    public static int fieldWriteAfterWrite = 0;
    public static int fieldWriteAfterRead = 0;
    public static final boolean allowFieldReadAfterWrite = false;
    public static final boolean allowFieldWriteAfterRead = true;
    public static final boolean allowFieldWriteAfterWrite = true;
    private static int methodSummaryRWInterference = 0;

    public VeritestingListener(Config conf, JPF jpf) {
        if (conf.hasValue("veritestingMode")) {
            veritestingMode = conf.getInt("veritestingMode");
            if (veritestingMode < 0 || veritestingMode > 4) {
                System.out.println("Warning: veritestingMode should be between 0 and 4 (both 0 and 4 included)");
                System.out.println("Warning: resetting veritestingMode to 0 (aka use vanilla SPF)");
                veritestingMode = 0;
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

        // MWW: Essentially, this acts as a singleton to construct an element.
        if (veritestingRegions == null) {
            discoverRegions(ti); // static analysis to discover regions
        }

        // Here is the real code
        String key = generateRegionKey(ti, instructionToExecute);

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
                        regionSummary = instantiateRegion(ti, region); // fill holes in region
                        if (regionSummary == null)
                            return;
                        //System.out.println(ASTToString(regionSummary));
                        newCG.makeVeritestingCG(regionSummary, ti);
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
                        StaticPCChoiceGenerator vcg = (StaticPCChoiceGenerator) cg;
                        int choice = (Integer) cg.getNextChoice();
                        Instruction nextInstruction = vcg.execute(ti, instructionToExecute, choice);
                        ti.setNextPC(nextInstruction);
                    }
                }
            } else if (veritestingMode >= 1 && veritestingMode <= 3) {
                // MWW: hopefully this code all goes away sometime soon!
                try {
                    Expression regionSummary = instantiateRegion(ti, region); // fill holes in region
                    if (regionSummary == null)
                        return;

                    // MWW: added code back in!
                    PathCondition pc = ((PCChoiceGenerator) ti.getVM().getSystemState().getChoiceGenerator()).getCurrentPC();
                    pc._addDet(new GreenConstraint(regionSummary));
                    // MWW: for debugging.
                    System.out.println("pc: " + pc);
                    ((PCChoiceGenerator) ti.getVM().getSystemState().getChoiceGenerator()).setCurrentPC(pc);
                    // MWW: end of add.

                    Instruction nextInstruction = StaticPCChoiceGenerator.setupSPF(ti, instructionToExecute, region);
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
        return ti.getTopFrame().getClassInfo().getName() + "." + ti.getTopFrame().getMethodInfo().getName() +
                ti.getTopFrame().getMethodInfo().getSignature() +
                "#" + instructionToExecute.getPosition();
    }

    private Expression instantiateRegion(ThreadInfo ti, VeritestingRegion region) throws StaticRegionException {
        // increment path labels for current region.
        pathLabelCount += 1;
        // MWW: Why is this code not in the region class?
        region.ranIntoCount++;
        StackFrame sf = ti.getTopFrame();

        // MWW: make emitting this stuff keyed off of a verbosity level.
        //System.out.println("Starting region (" + region.toString()+") at instruction " + instructionToExecute
        //+ " (pos = " + instructionToExecute.getPosition() + ")");

        // What is this?  InstructionInfo tracks information related to a condition.
        // Why is this here?  It has nothing to do with holes.
        // If we can't figure out the condition and we are not reasoning about a
        // method summary, return null.
        InstructionInfo instructionInfo = new InstructionInfo().invoke(sf);
        if (instructionInfo == null && !region.isMethodSummary()) return null;


        PathCondition pc;
        //We've intercepted execution before any symbolic state was reached, so return
        if (!(ti.getVM().getSystemState().getChoiceGenerator() instanceof PCChoiceGenerator)) return null;

        pc = ((PCChoiceGenerator) ti.getVM().getSystemState().getChoiceGenerator()).getCurrentPC();
        // this code checks if SPF has reached a branch with both sides being infeasible
        if (boostPerf == false && instructionInfo != null) {
            PathCondition eqPC = pc.make_copy();
            eqPC._addDet(new GreenConstraint(instructionInfo.getCondition()));
            boolean eqSat = eqPC.simplify();
            //if (!eqSat) return null;
            PathCondition nePC = pc.make_copy();
            nePC._addDet(new GreenConstraint(instructionInfo.getNegCondition()));
            boolean neSat = nePC.simplify();
            //if (!neSat) return null;
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
        region.getSpfCases().instantiate(fillHolesOutput.holeHashMap);
        region.getSpfCases().simplify();
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
        populateOutputs(ti,sf, region, fillHolesOutput);

        return finalSummaryExpression;
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
        veritestingMain.analyzeForVeritesting(classPath, className);
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1000000; //milliseconds
        staticAnalysisTime = (endTime - startTime);
        System.out.println("veritesting analysis took " + duration + " milliseconds");
        System.out.println("Number of veritesting regions = " + veritestingRegions.size());
    }

    public static String ASTToString(Expression expression) {
        if (expression instanceof Operation) {
            Operation operation = (Operation) expression;
            String str = new String();
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
    write all outputs of the veritesting region, FIELD_OUTPUT have the value to be written in a field named writeValue
    LOCAL_OUTPUT and FIELD_PHIs have the final value mapped in fillHolesOutput.holeHashMap
     */
    //TODO make this method write the outputs atomically, either all of them get written or none of them do and then SPF takes over
    private void populateOutputs(ThreadInfo ti, StackFrame stackFrame, VeritestingRegion region,
                                 FillHolesOutput fillHolesOutput) throws StaticRegionException {
        LinkedHashMap<Expression, Expression> retHoleHashMap = fillHolesOutput.holeHashMap;
        HashSet<Expression> allOutputVars = new HashSet<>();
        allOutputVars.addAll(region.getOutputVars());
        allOutputVars.addAll(fillHolesOutput.additionalOutputVars);
        LinkedHashMap<Expression, Expression> methodHoles = region.getHoleHashMap();
        Iterator iterator = allOutputVars.iterator();
        while (iterator.hasNext()) {
            Expression expression = (Expression) iterator.next(), value;
            assert (expression instanceof HoleExpression);
            HoleExpression holeExpression = (HoleExpression) expression;
            assert (retHoleHashMap.containsKey(holeExpression));
            switch (holeExpression.getHoleType()) {
                case LOCAL_OUTPUT:
                    value = retHoleHashMap.get(holeExpression);
                    stackFrame.setSlotAttr(holeExpression.getLocalStackSlot(), ExpressionUtil.GreenToSPFExpression(value));
                    break;
                case FIELD_OUTPUT:
                    if(holeExpression.isLatestWrite()) {
                        value = holeExpression.getFieldInfo().writeValue;
                        if(value instanceof HoleExpression) {
                            assert (retHoleHashMap.containsKey(value));
                            value = retHoleHashMap.get(value);
                        }
                        fillFieldHole(ti, stackFrame, holeExpression, methodHoles, retHoleHashMap, false, null,
                                false,
                                ExpressionUtil.GreenToSPFExpression(value));
                    }
                    break;
                case FIELD_PHI:
                    if(holeExpression.isLatestWrite()) {
                        value = retHoleHashMap.get(holeExpression);
                        fillFieldHole(ti, stackFrame, holeExpression, methodHoles, retHoleHashMap, false, null,
                                false,
                                ExpressionUtil.GreenToSPFExpression(value));
                    }
                    break;
            }
        }
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
        for(int i=0; i < completedFieldOutputs.size(); i++) {
            if(FieldUtil.isSameField(ti, sf, holeExpression, completedFieldOutputs.get(i), methodHoles, retHoleHashMap,
                    isMethodSummary, callSiteInfo, false)) {
                return completedFieldOutputs.get(i);
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
        HashSet<HoleExpression> additionalOutputVars = new HashSet<>();
        Expression additionalAST = null;
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
        FillInvokeHole fillInvokeHole = new FillInvokeHole(stackFrame, ti, holeHashMap, retHoleHashMap, additionalAST).invoke();
        if (fillInvokeHole.is()) return null;
        retHoleHashMap = fillInvokeHole.getRetHoleHashMap();
        additionalAST = fillInvokeHole.getAdditionalAST();
        additionalOutputVars = fillInvokeHole.getAdditionalOutputVars();
        /* populate the return value of methodSummary regions that have a non-null return value */
        if(region.isMethodSummary() && region.retVal != null) {
            ti.getModifiableTopFrame().push(0);
            if(region.retVal instanceof HoleExpression)
                ti.getModifiableTopFrame().setOperandAttr(ExpressionUtil.GreenToSPFExpression(retHoleHashMap.get(region.retVal)));
            else ti.getModifiableTopFrame().setOperandAttr(ExpressionUtil.GreenToSPFExpression(region.retVal));
        }


        return new FillHolesOutput(retHoleHashMap, additionalAST, additionalOutputVars);
    }

    // This needs to store sufficient information in the holeHashMap so that I can discover it for the exception computation.
    private boolean fillArrayLoadHoles(VeritestingRegion region, HashMap<Expression, Expression> holeHashMap, InstructionInfo instructionInfo,
                                       StackFrame stackFrame, ThreadInfo ti, HashMap<Expression, Expression> retHoleHashMap) {
        for (HashMap.Entry<Expression, Expression> entry : holeHashMap.entrySet()) {
            Expression key = entry.getKey(), finalValueGreen;
            gov.nasa.jpf.symbc.numeric.Expression indexAttr;
            assert (key instanceof HoleExpression);
            HoleExpression keyHoleExpression = (HoleExpression) key;
            switch (keyHoleExpression.getHoleType()) {
                case ARRAYLOAD:
                    HoleExpression.ArrayInfoHole arrayInfoHole = keyHoleExpression.getArrayInfo();
                    indexAttr =
                            (gov.nasa.jpf.symbc.numeric.Expression) stackFrame.getLocalAttr(((HoleExpression) (arrayInfoHole.arrayIndexHole)).getLocalStackSlot());
                    HoleExpression indexHole = (HoleExpression) arrayInfoHole.arrayIndexHole;
                    HoleExpression arrayRefHole = ((HoleExpression) arrayInfoHole.arrayRefHole);

                    switch (indexHole.getHoleType()) {
                        // what happens for field inputs?
                        // This code appears to be similar between both kinds of holes
                        case LOCAL_INPUT: //case array index is local input
                            int arrayRef = stackFrame.getLocalVariable(arrayRefHole.getLocalStackSlot());
                            ElementInfo ei = ti.getElementInfo(arrayRef);
                            int arrayLength = ((ArrayFields) ei.getFields()).arrayLength();

                            // MWW: modified to be able to extract array length from spfCase.
                            arrayInfoHole.setLength(arrayLength);
                            // MWW: end of modification.

                            TypeReference arrayType = arrayInfoHole.arrayType;
                            Expression pathLabelConstraint = arrayInfoHole.getPathLabelHole();
                            Expression arrayConstraint;
                            if (indexAttr == null) //attribute is null so index is concrete
                            {
                                int indexVal = stackFrame.getLocalVariable(((HoleExpression) arrayInfoHole.arrayIndexHole).getLocalStackSlot());
                                if (indexVal < 0 || indexVal >= arrayLength) //checking concrete index is out of bound
                                    return true;
                                int value = ei.getIntElement(indexVal);
                                finalValueGreen = SPFToGreenExpr(new IntegerConstant(value));
                            } else { //index is symbolic - fun starts here :)
                                finalValueGreen = SPFToGreenExpr(indexAttr);
                                Expression lhsExpr = retHoleHashMap.get(arrayInfoHole.lhsExpr);
                                Expression[] arraySymbConstraint = new Expression[arrayLength];
                                //Expression arrayLoadResult = new IntVariable("arrayLoadResult", Integer.MIN_VALUE, Integer.MAX_VALUE);
                                for (int i = 0; i < arrayLength; i++) {//constructing the symbolic index constraint
                                    Expression exp1 = new Operation(Operation.Operator.EQ, finalValueGreen, new IntConstant(i));
                                    int value = ei.getIntElement(i);
                                    Expression exp2 = new Operation(Operation.Operator.EQ, lhsExpr, new IntConstant(value)); //loadArrayElement(ei, arrayType)
                                    arraySymbConstraint[i] = new Operation(Operation.Operator.IMPLIES, exp1, exp2);
                                }
                                arrayConstraint = unrollGreenOrConstraint(arraySymbConstraint);
                                arrayConstraint = new Operation(Operation.Operator.IMPLIES, pathLabelConstraint, arrayConstraint);
                                retHoleHashMap.put(keyHoleExpression, arrayConstraint);

                                // MWW: TODO: move this code.
                                /*
                                if (outOfBound(arraySymbConstraint, finalValueGreen, ti)) {//outOfBoundException is possible
                                    Expression lowerBoundConstraint = new Operation(Operation.Operator.GE, finalValueGreen, new IntConstant(0));
                                    Expression upperBoundConstraint = new Operation(Operation.Operator.LT, finalValueGreen, new IntConstant(arraySymbConstraint.length));
                                    Expression inBoundConstraint = new Operation(Operation.Operator.AND, lowerBoundConstraint, upperBoundConstraint);
                                    ExitTransition outOfBoundExit = new ExitTransition(inBoundConstraint, ((HoleExpression) key).getHoleVarName(), pathLabelConstraint);
                                    region.putExitTransition(outOfBoundExit);
                                }
                                */
                            }
                            break;
                        case FIELD_INPUT:
                            break;
                        default:
                            System.out.println("Array type not supported");
                            break;
                    }

                    break;
                default:
                    break;
            }
        }
        return false;
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

    private Expression unrollGreenOrConstraint(Expression[] arraySymbConstraint) {
        assert (arraySymbConstraint != null);
        Expression unrolledConstraint = arraySymbConstraint[0];
        for (int i = 1; i < arraySymbConstraint.length; i++) {
            unrolledConstraint = new Operation(Operation.Operator.AND, arraySymbConstraint[i], unrolledConstraint);
        }
        return unrolledConstraint;
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


    private class InstructionInfo {
        private int numOperands;
        private Operation.Operator trueComparator, falseComparator;
        private Expression condition, negCondition;

        public Expression getCondition() {
            return condition;
        }

        public Expression getNegCondition() {
            return negCondition;
        }

        public int getNumOperands() {
            return numOperands;
        }

        public Operation.Operator getTrueComparator() {
            return trueComparator;
        }

        public Operation.Operator getFalseComparator() {
            return falseComparator;
        }

        public InstructionInfo invoke(StackFrame stackFrame) {
            String mnemonic = stackFrame.getPC().getMnemonic();
            //System.out.println("mne = " + mnemonic);
            switch (mnemonic) {
                case "ifeq":
                    numOperands = 1;
                    trueComparator = Operation.Operator.EQ;
                    falseComparator = Operation.Operator.NE;
                    break;
                case "ifne":
                    trueComparator = Operation.Operator.NE;
                    falseComparator = Operation.Operator.EQ;
                    numOperands = 1;
                    break;
                case "iflt":
                    trueComparator = Operation.Operator.LT;
                    falseComparator = Operation.Operator.GE;
                    numOperands = 1;
                    break;
                case "ifle":
                    trueComparator = Operation.Operator.LE;
                    falseComparator = Operation.Operator.GT;
                    numOperands = 1;
                    break;
                case "ifgt":
                    trueComparator = Operation.Operator.GT;
                    falseComparator = Operation.Operator.LE;
                    numOperands = 1;
                    break;
                case "ifge":
                    trueComparator = Operation.Operator.GE;
                    falseComparator = Operation.Operator.LT;
                    numOperands = 1;
                    break;
                case "ifnull":
                    trueComparator = Operation.Operator.EQ;
                    falseComparator = Operation.Operator.NE;
                    numOperands = 1;
                    break;
                case "ifnonnull":
                    trueComparator = Operation.Operator.EQ;
                    falseComparator = Operation.Operator.NE;
                    numOperands = 1;
                    break;
                case "if_icmpeq":
                    trueComparator = Operation.Operator.EQ;
                    falseComparator = Operation.Operator.NE;
                    numOperands = 2;
                    break;
                case "if_icmpne":
                    trueComparator = Operation.Operator.NE;
                    falseComparator = Operation.Operator.EQ;
                    numOperands = 2;
                    break;
                case "if_icmpgt":
                    trueComparator = Operation.Operator.GT;
                    falseComparator = Operation.Operator.LE;
                    numOperands = 2;
                    break;
                case "if_icmpge":
                    trueComparator = Operation.Operator.GE;
                    falseComparator = Operation.Operator.LT;
                    numOperands = 2;
                    break;
                case "if_icmple":
                    trueComparator = Operation.Operator.LE;
                    falseComparator = Operation.Operator.GT;
                    numOperands = 2;
                    break;
                case "if_icmplt":
                    trueComparator = Operation.Operator.LT;
                    falseComparator = Operation.Operator.GE;
                    numOperands = 2;
                    break;
                default:
                    return null;
            }
            assert (numOperands == 1 || numOperands == 2);
            IntegerExpression operand1 = null, operand2 = null;
            boolean isConcreteCondition = true;
            if (numOperands == 1) {
                gov.nasa.jpf.symbc.numeric.Expression operand1_expr = (gov.nasa.jpf.symbc.numeric.Expression)
                        stackFrame.getOperandAttr();
                operand1 = (IntegerExpression) operand1_expr;
                if (operand1 == null) operand1 = new IntegerConstant(stackFrame.peek());
                else isConcreteCondition = false;
                operand2 = new IntegerConstant(0);
            }
            if (numOperands == 2) {
                operand1 = (IntegerExpression) stackFrame.getOperandAttr(1);
                if (operand1 == null) operand1 = new IntegerConstant(stackFrame.peek(1));
                else isConcreteCondition = false;
                operand2 = (IntegerExpression) stackFrame.getOperandAttr(0);
                if (operand2 == null) operand2 = new IntegerConstant(stackFrame.peek(0));
                else isConcreteCondition = false;
            }
            if (isConcreteCondition) {
                return null;
            } else {
                condition = new Operation(trueComparator, SPFToGreenExpr(operand1), SPFToGreenExpr(operand2));
                negCondition = new Operation(falseComparator, SPFToGreenExpr(operand1), SPFToGreenExpr(operand2));
            }
            return this;
        }

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
                    case NONE:
                        System.out.println("expression marked as hole with NONE hole type: " +
                                methodKeyHole.toString());
                        assert (false);
                        break;
                    case INVOKE:
                    case FIELD_INPUT:
                    case LOCAL_INPUT:
                    default:
                        break;
                }
            }
            Expression fieldOutputExpression = getFieldOutputExpression();
            return new FillNonInputHolesOutput(false, fieldOutputExpression);
        }

        private Expression getFieldOutputExpression() throws StaticRegionException {
            ArrayList<HoleExpression> completedFieldOutputs = new ArrayList<>();
            Expression fieldOutputExpression = null;
            for(Map.Entry<Expression, Expression> entry1 : methodHoles.entrySet()) {
                HoleExpression holeExpression = (HoleExpression) entry1.getKey();
                switch(holeExpression.getHoleType()) {
                    case FIELD_PHI:
                        HoleExpression.FieldInfo fieldInfo = holeExpression.getFieldInfo();
                        HoleExpression prevFilledHole = isFieldOutputComplete(
                                ti, stackFrame, methodHoles, retHoleHashMap, isMethodSummary, callSiteInfo,
                                completedFieldOutputs, holeExpression);
                        if (prevFilledHole != null) {
                            retHoleHashMap.put(holeExpression, retHoleHashMap.get(prevFilledHole));
                            continue;
                        }
                        assert (fieldInfo != null);
                        HoleExpression prevValueHole = FieldUtil.findPreviousRW(holeExpression,
                                FIELD_INPUT, ti, stackFrame, methodHoles, retHoleHashMap, isMethodSummary, callSiteInfo);
                        if(!retHoleHashMap.containsKey(prevValueHole)) {
                            FillFieldInputHole fillFieldInputHole = new FillFieldInputHole(prevValueHole, methodHoles,
                                    isMethodSummary, callSiteInfo, ti, stackFrame, retHoleHashMap);
                            fillFieldInputHole.invoke();
                            retHoleHashMap = fillFieldInputHole.getRetHoleHashMap();

                        }
                        Expression prevValue = retHoleHashMap.get(prevValueHole);
                        Expression finalValueSymVar =
                                SPFToGreenExpr(makeSymbolicInteger(holeExpression.getHoleVarName() + ".final_value" + pathLabelCount));
                        //returns an predicate that is a conjunction of (predicate IMPLIES (finalValueSymVar EQ outputVar)) expressions
                        Expression fieldOutputPredicate =
                                findCommonFieldOutputs(ti, stackFrame, holeExpression, region.getOutputVars(),
                                        finalValueSymVar, prevValue, methodHoles, retHoleHashMap, isMethodSummary, callSiteInfo);
                        if (fieldOutputExpression == null) fieldOutputExpression = fieldOutputPredicate;
                        else fieldOutputExpression =
                                new Operation(Operation.Operator.AND, fieldOutputExpression, fieldOutputPredicate);
                        completedFieldOutputs.add(holeExpression);
                        retHoleHashMap.put(holeExpression, finalValueSymVar);
                    break;
                }

            }
            return fieldOutputExpression;
        }
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
        private HashSet<HoleExpression> additionalOutputVars;


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
            this.additionalOutputVars = new HashSet<>();
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
            findAdditionalOutputVars(methodHoles);

            FillInputHoles fillInputHolesMS =
                    new FillInputHoles(stackFrame, ti, retHoleHashMap, callSiteInfo, methodHoles, true).invoke();
            if (fillInputHolesMS.failure()) {
                myResult = true;
                return this;
            }
            retHoleHashMap = fillInputHolesMS.retHoleHashMap;

            FillInvokeHole fillInvokeHole = new FillInvokeHole(stackFrame, ti, methodHoles, retHoleHashMap, additionalAST).invoke();
            if (fillInvokeHole.is()) {
                myResult = true;
                return this;
            }
            retHoleHashMap = fillInvokeHole.getRetHoleHashMap();
            additionalAST = fillInvokeHole.getAdditionalAST();
            additionalOutputVars.addAll(fillInvokeHole.getAdditionalOutputVars());

            Expression retValEq = null;
            if (methodSummary.retVal != null)
                retValEq = new Operation(Operation.Operator.EQ, methodSummary.retVal, keyHoleExpression);
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

        public HashSet<HoleExpression> getAdditionalOutputVars() {
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

        public HashSet<HoleExpression> getAdditionalOutputVars() {
            return additionalOutputVars;
        }

        private HashSet<HoleExpression> additionalOutputVars;

        public FillInvokeHole(StackFrame stackFrame, ThreadInfo ti, LinkedHashMap<Expression, Expression> holeHashMap,
                              LinkedHashMap<Expression, Expression> retHoleHashMap, Expression additionalAST) {
            this.stackFrame = stackFrame;
            this.ti = ti;
            this.holeHashMap = holeHashMap;
            this.retHoleHashMap = retHoleHashMap;
            this.additionalAST = additionalAST;
            additionalOutputVars = new HashSet<>();
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
