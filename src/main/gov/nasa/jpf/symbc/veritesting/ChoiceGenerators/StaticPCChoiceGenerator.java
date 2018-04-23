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
import gov.nasa.jpf.symbc.veritesting.StaticRegionException;
import gov.nasa.jpf.symbc.veritesting.VeritestingRegion;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.StackFrame;
import gov.nasa.jpf.vm.ThreadInfo;
import za.ac.sun.cs.green.expr.Expression;

// MWW: general comment: many uses of nulls as 'signal' values.
// This usually leads to brittle and hard to debug code with lots
// of null pointer exceptions.  Be explicit!  Use exceptions
// to handle unexpected circumstances.

public abstract class StaticPCChoiceGenerator extends PCChoiceGenerator {

    public enum Kind {UNARYIF, BINARYIF, NULLIF, OTHER}

    private VeritestingRegion region;

    public StaticPCChoiceGenerator(int count, VeritestingRegion region, Instruction instruction) {
        super(0, count);
        this.region = region;
        setOffset(instruction.getPosition());
        setMethodName(instruction.getMethodInfo().getFullName());
    }

    protected VeritestingRegion getRegion() { return region; }

    // MWW: make choice 0 and choice 4 also the responsibility of the CG
    abstract public Instruction execute(ThreadInfo ti, Instruction instructionToExecute, int choice);

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


    public static Instruction setupSPF(ThreadInfo ti, Instruction instructionToExecute, VeritestingRegion region) {
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
        StackFrame sf = ti.getTopFrame();
        InstructionInfo instructionInfo = new InstructionInfo().invoke(sf);
        if (instructionInfo != null && !region.isMethodSummary())
            numOperands = instructionInfo.getNumOperands();

        while (numOperands > 0) {
            modifiableTopFrame.pop();
            numOperands--;
        }

        region.usedCount++;
        return insn;
    }

    public abstract void makeVeritestingCG(Expression regionSummary, ThreadInfo ti) throws StaticRegionException;


}
