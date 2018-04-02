package gov.nasa.jpf.symbc;//
// Copyright (C) 2006 United States Government as represented by the
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

import java.util.Collections;
import java.util.LinkedList;
import java.util.Stack;

import gov.nasa.jpf.ListenerAdapter;
import gov.nasa.jpf.jvm.bytecode.JVMInvokeInstruction;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.symbc.numeric.PCChoiceGenerator;
import gov.nasa.jpf.symbc.numeric.PathCondition;
import gov.nasa.jpf.symbc.numeric.Comparator;
import gov.nasa.jpf.symbc.numeric.VeriPCChoiceGenerator;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.ClassInfo;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.IntChoiceGenerator;
import gov.nasa.jpf.vm.MJIEnv;
import gov.nasa.jpf.vm.MethodInfo;
import gov.nasa.jpf.vm.StackFrame;
import gov.nasa.jpf.vm.SystemState;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.VM;
import gov.nasa.jpf.vm.choice.IntIntervalGenerator;

public class SymbolicChoiceChangingListener extends ListenerAdapter {


    public void instructionExecuted(VM vm, ThreadInfo ti, Instruction nextInstruction, Instruction executedInstruction) {

            StackFrame sf = ti.getTopFrame();

            // 21 is ifeq, 24 is getstatic
            // If we run starting fom instruction 21, the choiceGenerators are "stacked"
            // and it appears that either ours or the ifeq cg is ignored.
            // looking at the output, the then and else branches only occur once.
            // If we start from 24, the 'then' branch runs twice, like we would expect.
            if (executedInstruction.getMethodInfo().getName().equals("testMe2") &&
                    executedInstruction.getPosition() == 20) {
                StackFrame sframe = ti.getTopFrame();

                if (!ti.isFirstStepInsn()) { // first time around
                    VeriPCChoiceGenerator newPCChoice = new VeriPCChoiceGenerator(2);
                    newPCChoice.setOffset(executedInstruction.getPosition());
                    newPCChoice.setMethodName(executedInstruction.getMethodInfo().getFullName());
                    SystemState ss = vm.getSystemState();
                    ss.setNextChoiceGenerator(newPCChoice);
                    ti.setNextPC(executedInstruction);
                    System.out.println("Initial run through ifeq ");
                    return;
                } else {
                    VeriPCChoiceGenerator cg = (VeriPCChoiceGenerator) ti.getVM().getSystemState().getChoiceGenerator();
                    int value = (Integer) cg.getNextChoice();
                    PathCondition pc = cg.getCurrentPC();
                    if (value == 0) { // default SPF running
                        //pc._addDet(Comparator.EQ, 0, 0); // add !nominal PC
                        System.out.println("First choice run through ifeq ");
                        return;
                    } else {
                        System.out.println("Second choice run through ifeq");
                        //ti.setNextPC(...); // this needs to be set to after the verittesting region
                        return;
                    }
                }
        }

    }

}
