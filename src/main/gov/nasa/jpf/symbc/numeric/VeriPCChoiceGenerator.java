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
package gov.nasa.jpf.symbc.numeric;

import gov.nasa.jpf.jvm.bytecode.IfInstruction;
import gov.nasa.jpf.symbc.VeritestingListener;
import gov.nasa.jpf.symbc.bytecode.IFNONNULL;
import gov.nasa.jpf.symbc.bytecode.IF_ICMPEQ;
import gov.nasa.jpf.symbc.numeric.PCChoiceGenerator;
import gov.nasa.jpf.symbc.numeric.PathCondition;
import gov.nasa.jpf.symbc.veritesting.StaticRegionException;
import gov.nasa.jpf.symbc.veritesting.VeritestingRegion;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.StackFrame;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.symbc.numeric.Comparator;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Expression;

import java.util.HashMap;

// MWW: general comment: many uses of nulls as 'signal' values.
// This usually leads to brittle and hard to debug code with lots
// of null pointer exceptions.  Be explicit!  Use exceptions
// to handle unexpected circumstances.

public class VeriPCChoiceGenerator extends PCChoiceGenerator {
    private enum kind {UNARYIF, BINARYIF, NULLIF}

    @SuppressWarnings("deprecation")
    public VeriPCChoiceGenerator(int size) {
        super(0, size - 1);
        PC = new HashMap<Integer, PathCondition>();
        for (int i = 0; i < size; i++)
            PC.put(i, new PathCondition());
        isReverseOrder = false;
    }

    public VeriPCChoiceGenerator(int min, int max) {
        this(min, max, 1);
    }

    @SuppressWarnings("deprecation")
    public VeriPCChoiceGenerator(int min, int max, int delta) {
        super(min, max, delta);
        PC = new HashMap<Integer, PathCondition>();
        for (int i = min; i <= max; i += delta)
            PC.put(i, new PathCondition());
        isReverseOrder = false;
    }

    /*
     * If reverseOrder is true, the PCChoiceGenerator
     * explores paths in the opposite order used by
     * the default constructor. If reverseOrder is false
     * the usual behavior is used.
     */
    @SuppressWarnings("deprecation")
    public VeriPCChoiceGenerator(int size, boolean reverseOrder) {
        super(0, size - 1, reverseOrder ? -1 : 1);
        PC = new HashMap<Integer, PathCondition>();
        for (int i = 0; i < size; i++)
            PC.put(i, new PathCondition());
        isReverseOrder = reverseOrder;
    }

    public PathCondition getCurrentPC() {
        PathCondition pc;

        pc = PC.get(getNextChoice());
        if (pc != null) {
            return pc.make_copy();
        } else {
            return null;
        }
    }


    private kind getKind(Instruction instruction) {
        switch (instruction.getMnemonic()) {
            case "ifeq":
            case "ifge":
            case "ifle":
            case "ifgt":
            case "iflt":
            case "ifne":
                return kind.UNARYIF;
            case "if_icmpeq":
            case "if_icmpge":
            case "if_icmpgt":
            case "if_icmple":
            case "if_icmplt":
            case "if_icmpne":
                return kind.BINARYIF;
            case "ifnull":
                return kind.NULLIF;
            default:
                return null;
        }
    }

    // MWW: I see vey similar code in InstuctionInfo.  Why?
    //TODO: Fix that after talking with Vaibhav
    public Comparator getComparator(Instruction instruction) {
        switch (instruction.getMnemonic()) {
            case "ifeq":
            case "if_icmpeq":
                return Comparator.EQ;
            case "ifge":
            case "if_icmpge":
                return Comparator.GE;
            case "ifle":
            case "if_icmple":
                return Comparator.LE;
            case "ifgt":
            case "if_icmpgt":
                return Comparator.GT;
            case "iflt":
            case "if_icmplt":
                return Comparator.LT;
            case "ifne":
                return Comparator.NE;
            default:
                return null;
        }
    }

    // MWW: I see vey similar code in InstuctionInfo.  Why?
    //TODO: Fix that after talking with Vaibhav
    public Comparator getNegComparator(Instruction instruction) {
        switch (instruction.getMnemonic()) {
            case "ifeq":
            case "if_icmpeq":
                return Comparator.NE;
            case "ifge":
            case "if_icmpge":
                return Comparator.LT;
            case "ifle":
            case "if_icmple":
                return Comparator.GT;
            case "ifgt":
            case "if_icmpgt":
                return Comparator.LE;
            case "iflt":
            case "if_icmplt":
                return Comparator.GE;
            case "ifne":
                return Comparator.EQ;
            default:
                return null;
        }
    }

    public Instruction execute(Instruction instructionToExecute, int choice) {
        Instruction nextInstruction = null;
        switch (getKind(instructionToExecute)) {
            case UNARYIF:
                Comparator byteCodeOp = this.getComparator(instructionToExecute);
                Comparator byteCodeNegOp = this.getNegComparator(instructionToExecute);
                nextInstruction = executeUnaryIf(byteCodeOp, byteCodeNegOp, instructionToExecute, choice);
                break;
            case BINARYIF:
                byteCodeOp = this.getComparator(instructionToExecute);
                byteCodeNegOp = this.getNegComparator(instructionToExecute);
                nextInstruction = executeBinaryIf(byteCodeOp, byteCodeNegOp, instructionToExecute, choice);
                break;
            case NULLIF:
                nextInstruction = executeNullIf(instructionToExecute);
                break;
        }
        return nextInstruction;
    }

    // MWW: Why are we looking up the choice generator here?   We are *in* the choice generator.
    // Soha: You are right, I fixed that.
    private Instruction executeBinaryIf(Comparator byteCodeOp, Comparator byteCodeNegOp, Instruction instructionToExecute, int choice) {
        StackFrame sf = ti.getModifiableTopFrame();

        IntegerExpression sym_v1 = (IntegerExpression) sf.getOperandAttr(1);
        IntegerExpression sym_v2 = (IntegerExpression) sf.getOperandAttr(0);

        if ((sym_v1 == null) && (sym_v2 == null)) { // both conditions are concrete
            //System.out.println("Execute IF_ICMPEQ: The conditions are concrete");
            return instructionToExecute.execute(ti);
        } else {
            //ChoiceGenerator<?> cg;
            //cg = ti.getVM().getSystemState().getChoiceGenerator();
            //assert (cg instanceof PCChoiceGenerator) : "expected PCChoiceGenerator, got: " + cg;
            //boolean conditionValue = (Integer) cg.getNextChoice() == 1 ? false : true;
            boolean conditionValue = (Integer) this.getNextChoice() == 1 ? false : true;
            int	v2 = sf.pop();
            int	v1 = sf.pop();
            //System.out.println("Execute IF_ICMPEQ: "+ conditionValue);
            PathCondition pc;
            // pc = ((VeriPCChoiceGenerator) ti.getVM().getChoiceGenerator()).getCurrentPC();
            pc = this.getCurrentPC();
            assert pc != null;

            if (conditionValue) {
                if (sym_v1 != null){
                    if (sym_v2 != null){ //both are symbolic values
                        pc._addDet(byteCodeOp,sym_v1,sym_v2);
                    }else
                        pc._addDet(byteCodeOp,sym_v1,v2);
                }else
                    pc._addDet(byteCodeOp, v1, sym_v2);
                if(!pc.simplify())  {// not satisfiable
                    ti.getVM().getSystemState().setIgnored(true);
                }else{
                    //pc.solve();
                    //((PCChoiceGenerator) cg).setCurrentPC(pc);
                    this.setCurrentPC(pc);
                    //	System.out.println(((PCChoiceGenerator) cg).getCurrentPC());
                }
                return ((IfInstruction) instructionToExecute).getTarget();
            } else {
                if (sym_v1 != null){
                    if (sym_v2 != null){ //both are symbolic values
                        pc._addDet(byteCodeNegOp,sym_v1,sym_v2);
                    }else
                        pc._addDet(byteCodeNegOp,sym_v1,v2);
                }else
                    pc._addDet(byteCodeNegOp, v1, sym_v2);
                if(!pc.simplify())  {// not satisfiable
                    ti.getVM().getSystemState().setIgnored(true);
                }else {
                    //pc.solve();
                    //((PCChoiceGenerator) cg).setCurrentPC(pc);
                    this.setCurrentPC(pc);
                    //System.out.println(((PCChoiceGenerator) cg).getCurrentPC());
                }
                return instructionToExecute.getNext(ti);
            }

        }
    }

    // MWW - Why are we calling these method execute functions directly
    // rather than adding their conditions to the path?

    public Instruction executeNullIf(Instruction instructionToExecute) {
        StackFrame sf = ti.getModifiableTopFrame();
        Expression sym_v = (Expression) sf.getOperandAttr();
        if (sym_v == null) { // the condition is concrete
            //System.out.println("Execute IFEQ: The condition is concrete");
            return ((IFNONNULL) instructionToExecute).execute(ti);
        } else {
            sf.pop();
            return ((IfInstruction) instructionToExecute).getTarget();
        }
    }

    // MWW: you are *in* the choice generator - why are you looking it up?!?
    // Soha: You are right, I fixed that.

    public Instruction executeUnaryIf(Comparator byteCodeOp, Comparator byteCodeNegOp, Instruction instruction, int choice) {
        StackFrame sf = ti.getModifiableTopFrame();
        IntegerExpression sym_v = (IntegerExpression) sf.getOperandAttr();
//        ChoiceGenerator<?> cg;

        boolean conditionValue;
//        cg = ti.getVM().getSystemState().getChoiceGenerator();
//        assert (cg instanceof PCChoiceGenerator) : "expected PCChoiceGenerator, got: " + cg;
        conditionValue = choice == 1 ? false : true;

        sf.pop();
        //System.out.println("Execute IFGE: "+ conditionValue);
        PathCondition pc;

        // pc is updated with the pc stored in the choice generator above
        // get the path condition from the
        // previous choice generator of the same type
/*
        ChoiceGenerator<?> prev_cg = cg.getPreviousChoiceGenerator();
        while (!((prev_cg == null) || (prev_cg instanceof PCChoiceGenerator))) {
            prev_cg = prev_cg.getPreviousChoiceGenerator();
        }

        if (prev_cg == null)
            pc = new PathCondition();
        else
            pc = ((PCChoiceGenerator) prev_cg).getCurrentPC();

        assert pc != null;

        PathCondition veritestingPc = ((VeriPCChoiceGenerator)ti.getVM().getChoiceGenerator()).getCurrentPC();
        pc.appendPathcondition(veritestingPc);
*/
 //       pc = ((VeriPCChoiceGenerator) ti.getVM().getChoiceGenerator()).getCurrentPC();

        pc = this.getCurrentPC();

        if (conditionValue) {
            pc._addDet(byteCodeOp, sym_v, 0);
            if (!pc.simplify()) {// not satisfiable
                ti.getVM().getSystemState().setIgnored(true);
            } else {
                //pc.solve();
//                ((PCChoiceGenerator) cg).setCurrentPC(pc);
                this.setCurrentPC(pc);
                //System.out.println(((PCChoiceGenerator) cg).getCurrentPC());
            }
            return ((IfInstruction) instruction).getTarget();
        } else {
            pc._addDet(byteCodeNegOp, sym_v, 0);
            if (!pc.simplify()) {// not satisfiable
                ti.getVM().getSystemState().setIgnored(true);
            } else {
                //pc.solve();
                //((PCChoiceGenerator) cg).setCurrentPC(pc);
                this.setCurrentPC(pc);
                //System.out.println(((PCChoiceGenerator) cg).getCurrentPC());
            }
            return instruction.getNext(ti);
        }
    }

    // MWW: It would probably be better if this code migrated to the VeriPCChoiceGenerator.
    // As it is the CG is 1/2 responsible for creating its choices, and the
    // VeritestingListener is 1/2 responsible (below).

    // 4 cases (they may be UNSAT, but that's ok):
    // 0: staticNominalNoReturn
    // 1: thenException
    // 2: elseException
    // 3: staticNominalReturn
    // NB: then and else constraints are the same (here).  We will tack on the additional
    // constraint for the 'then' and 'else' branches when we execute the choice generator.
    private PathCondition createPC(PathCondition pc, Expression regionSummary, Expression constraint) {
        PathCondition pcCopy = pc.make_copy();
        za.ac.sun.cs.green.expr.Expression copyConstraint = new Operation(Operation.Operator.AND, regionSummary, constraint);
        pcCopy._addDet(new GreenConstraint(copyConstraint));
        return pcCopy;
    }

    public void makeVeritestingCG(VeritestingRegion region, Expression regionSummary, ThreadInfo ti) throws StaticRegionException {
        assert(regionSummary != null);
        PathCondition pc = ((PCChoiceGenerator)(ti.getVM().getSystemState().getChoiceGenerator())).getCurrentPC();

        setPC(createPC(pc, regionSummary, region.staticNominalPredicate()), 0);
        setPC(createPC(pc, regionSummary, region.spfPathPredicate()), 1);
        setPC(createPC(pc, regionSummary, region.spfPathPredicate()), 2);
        // TODO: create the path preicate for the 'return' case.
    }
}
