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

//
// Copyright (C) 2007 United States Government as represented by the
// Administrator of the National Aeronautics and Space Administration
// (NASA).  All Rights Reserved.
//
// This software is distributed under the NASA Open Source Agreement
// (NOSA), version 1.3.  The NOSA has been approved by the Open Source
// Initiative.  See the file NOSA-1.3-JPF at the top of the distribution
// directory tree for the complete NOSA document.
//
// THE SUBJECT SOFTWARE IS PROVIDED "AS IS" WITHOUT ANY WARRANTY OF ANY
// KIND, EITHER EXPRESSED, IMPLIED, OR STATUTORY, INCLUDING, BUT NOT
// LIMITED TO, ANY WARRANTY THAT THE SUBJECT SOFTWARE WILL CONFORM TO
// SPECIFICATIONS, ANY IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR
// A PARTICULAR PURPOSE, OR FREEDOM FROM INFRINGEMENT, ANY WARRANTY THAT
// THE SUBJECT SOFTWARE WILL BE ERROR FREE, OR ANY WARRANTY THAT
// DOCUMENTATION, IF PROVIDED, WILL CONFORM TO THE SUBJECT SOFTWARE.
//
package gov.nasa.jpf.symbc.veritesting.ChoiceGenerators;

import gov.nasa.jpf.jvm.bytecode.GOTO;
import gov.nasa.jpf.symbc.InstructionInfo;
import gov.nasa.jpf.symbc.numeric.*;
import gov.nasa.jpf.symbc.veritesting.*;
import gov.nasa.jpf.vm.*;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.IntVariable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import static gov.nasa.jpf.symbc.VeritestingListener.DEBUG_LIGHT;
import static gov.nasa.jpf.symbc.VeritestingListener.debug;
import static gov.nasa.jpf.symbc.VeritestingListener.fillFieldHole;

// MWW: general comment: many uses of nulls as 'signal' values.
// This usually leads to brittle and hard to debug code with lots
// of null pointer exceptions.  Be explicit!  Use exceptions
// to handle unexpected circumstances.

public abstract class StaticPCChoiceGenerator extends PCChoiceGenerator {

    public enum Kind {UNARYIF, BINARYIF, NULLIF, OTHER}

    private VeritestingRegion region;
    protected StackFrame currentTopFrame = null;

    public StaticPCChoiceGenerator(int count, VeritestingRegion region, Instruction instruction) {
        super(0, count);
        this.region = region;
        setOffset(instruction.getPosition());
        setMethodName(instruction.getMethodInfo().getFullName());
        if(ti != null && ti.getTopFrame() != null)
            this.currentTopFrame = ti.getTopFrame();; //backup the frame for the SPFCase

    }

    protected VeritestingRegion getRegion() { return region; }

    // MWW: make choice 0 and choice 4 also the responsibility of the CG
    abstract public Instruction execute(ThreadInfo ti, Instruction instructionToExecute, int choice, FillHolesOutput fillHolesOutput) throws StaticRegionException;

    public static StaticSummaryChoiceGenerator.Kind getKind(Instruction instruction) {
        switch (instruction.getMnemonic()) {
            case "ifeq":
            case "ifge":
            case "ifle":
            case "ifgt":
            case "iflt":
            case "ifne":
                return Kind.UNARYIF;
            case "if_icmpeq":
            case "if_icmpge":
            case "if_icmpgt":
            case "if_icmple":
            case "if_icmplt":
            case "if_icmpne":
                return Kind.BINARYIF;
            case "ifnull":
                return Kind.NULLIF;
            default:
                return Kind.OTHER;
        }
    }


    public static Instruction setupSPF(ThreadInfo ti, Instruction instructionToExecute, VeritestingRegion region, FillHolesOutput fillHolesOutput) throws StaticRegionException {
        StackFrame sf = ti.getTopFrame();

        populateOutputs(ti,sf, region, fillHolesOutput);
        Instruction insn = instructionToExecute;
        while (insn.getPosition() != region.getEndInsnPosition()) {
            if (insn instanceof GOTO && (((GOTO) insn).getTarget().getPosition() <= region.getEndInsnPosition()))
                insn = ((GOTO) insn).getTarget();
            else insn = insn.getNext();
        }

        // MWW: this looks like a hack!
        if (insn.getMnemonic().contains("store")) insn = insn.getNext();
        // MWW: end comment.

        StackFrame modifiableTopFrame = ti.getModifiableTopFrame();
        int numOperands = 0;
        InstructionInfo instructionInfo = new InstructionInfo().invoke(sf);
        if (instructionInfo != null && !region.isMethodSummary())
            numOperands = instructionInfo.getNumOperands();

        while (numOperands > 0) {
            modifiableTopFrame.pop();
            numOperands--;
        }

        /* methodSummary regions with a non-null return value should finish at a return statement.
        This invariant is checked here but the return value is populated inside fillHoles. */
        if(region.isMethodSummary() && region.retVal != null) {
            assert(insn.getMnemonic().contains("return"));
        }
        //((PCChoiceGenerator) ti.getVM().getSystemState().getChoiceGenerator()).setCurrentPC(pc);
        ti.setNextPC(insn);

        region.usedCount++;
        if (debug == DEBUG_LIGHT)
            System.out.println("used region: " + region.toString() +", topStackFrame = " + ti.getTopFrame().toString());
        return insn;

    }


    /*
write all outputs of the veritesting region, FIELD_OUTPUT have the value to be written in a field named writeValue
LOCAL_OUTPUT and FIELD_PHIs have the final value mapped in fillHolesOutput.holeHashMap
 */
    //TODO make this method write the outputs atomically, either all of them get written or none of them do and then SPF takes over
    private static void populateOutputs(ThreadInfo ti, StackFrame stackFrame, VeritestingRegion region,
                                 FillHolesOutput fillHolesOutput) throws StaticRegionException {
        LinkedHashMap<Expression, Expression> retHoleHashMap = fillHolesOutput.holeHashMap;
        LinkedHashSet<Expression> allOutputVars = new LinkedHashSet<>();
        allOutputVars.addAll(region.getOutputVars());
        allOutputVars.addAll(fillHolesOutput.additionalOutputVars);
        LinkedHashMap<Expression, Expression> methodHoles = region.getHoleHashMap();
        for (Expression allOutputVar : allOutputVars) {
            Expression value;
            assert (allOutputVar instanceof HoleExpression);
            HoleExpression holeExpression = (HoleExpression) allOutputVar;
            assert (retHoleHashMap.containsKey(holeExpression));
            switch (holeExpression.getHoleType()) {
                case LOCAL_OUTPUT:
                    value = retHoleHashMap.get(holeExpression);
                    stackFrame.setSlotAttr(holeExpression.getLocalStackSlot(), ExpressionUtil.GreenToSPFExpression(value));
                    break;
                case FIELD_OUTPUT:
                    if (holeExpression.isLatestWrite()) {
                        value = holeExpression.getFieldInfo().writeValue;
                        if (value instanceof HoleExpression) {
                            assert (retHoleHashMap.containsKey(value));
                            value = retHoleHashMap.get(value);
                        }
                        fillFieldHole(ti, stackFrame, holeExpression, methodHoles, retHoleHashMap, false, null,
                                false,
                                ExpressionUtil.GreenToSPFExpression(value));
                    }
                    break;
                case FIELD_PHI:
                    if (holeExpression.isLatestWrite()) {
                        value = retHoleHashMap.get(holeExpression);
                        fillFieldHole(ti, stackFrame, holeExpression, methodHoles, retHoleHashMap, false, null,
                                false,
                                ExpressionUtil.GreenToSPFExpression(value));
                    }
                    break;
                case ARRAYSTORE:
                    populateArrayStoreOutput(ti, holeExpression.getArrayInfo(), retHoleHashMap);
                    break;
            }
        }
         /* populate the return value of methodSummary regions that have a non-null return value */
        if(region.isMethodSummary() && region.retVal != null) {
            ti.getModifiableTopFrame().push(0);
            if(region.retVal instanceof HoleExpression)
                ti.getModifiableTopFrame().setOperandAttr(ExpressionUtil.GreenToSPFExpression(retHoleHashMap.get(region.retVal)));
            else ti.getModifiableTopFrame().setOperandAttr(ExpressionUtil.GreenToSPFExpression(region.retVal));
        }
    }

    private static void populateArrayStoreOutput(ThreadInfo ti, HoleExpression.ArrayInfo arrayInfo, HashMap<Expression, Expression> retHoleHashMap) {

        HoleExpression arrayRefHole = ((HoleExpression) arrayInfo.arrayRefHole);
        Expression arrayRefExpression = retHoleHashMap.get(arrayRefHole);
        int arrayRef = ((IntConstant) arrayRefExpression).getValue();

        FillArrayStoreHoles.Where indexWhere, operandWhere = null;

        indexWhere = (arrayInfo.arrayIndexHole instanceof  IntConstant) ? FillArrayStoreHoles.Where.CONCRETE : FillArrayStoreHoles.Where.SYM;
        operandWhere = (arrayInfo.val instanceof IntConstant) ? FillArrayStoreHoles.Where.CONCRETE : FillArrayStoreHoles.Where.SYM;

        ElementInfo ei = ti.getElementInfo(arrayRef);
        int arrayLength = arrayInfo.length();

        StackFrame frame = ti.getModifiableTopFrame();
        ElementInfo eiArray = ei.getModifiableInstance();
        switch (indexWhere) {
            case CONCRETE:
                int concreteIndex = ((IntConstant) arrayInfo.arrayIndexHole).getValue();
                switch (operandWhere) {
                    case CONCRETE: //index and rhs both concrete
                        int concreteValue = ((IntConstant) arrayInfo.val).getValue();
                        eiArray.setIntElement(concreteIndex, concreteValue);
                        break;
                    case SYM: // index concrete but operand is sym
                        Expression operandAttr = retHoleHashMap.get(arrayInfo.val);
                        //eiArray.setIntElement(concreteIndex, concreteValue); //even though value is SYM, as per the SPF bytecode, concrete value is also copied.
                        eiArray.setElementAttrNoClone(concreteIndex,operandAttr);
                        break;
                }
                break;
            case SYM: // index symbolic and rhs either concrete or sym
                setElementsAttributes(ti, arrayInfo, arrayRef);
                break;
        }
    }

    private static void setElementsAttributes(ThreadInfo ti, HoleExpression.ArrayInfo arrayInfo, int arrayRef) {
        HoleExpression arrayRefHole = ((HoleExpression) arrayInfo.arrayRefHole);

        ElementInfo ei = ti.getElementInfo(arrayRef);
        ElementInfo eiArray = ei.getModifiableInstance();

        for(int index = 0; index < arrayInfo.getLength(); index++){
            String newVarName = arrayRefHole.getClass()+ "." + arrayRefHole.getMethodName() + ".v_i" + index;
            Expression newVar = new IntVariable(newVarName, Integer.MIN_VALUE, Integer.MAX_VALUE);
            eiArray.setElementAttrNoClone(index,ExpressionUtil.GreenToSPFExpression(newVar));
        }
    }
    public abstract void makeVeritestingCG(Expression regionSummary, ThreadInfo ti) throws StaticRegionException;


}
