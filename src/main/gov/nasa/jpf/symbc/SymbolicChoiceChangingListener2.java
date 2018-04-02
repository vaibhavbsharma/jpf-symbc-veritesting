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

import gov.nasa.jpf.ListenerAdapter;
import gov.nasa.jpf.symbc.numeric.PCChoiceGenerator;
import gov.nasa.jpf.symbc.numeric.PathCondition;
import gov.nasa.jpf.symbc.numeric.VeriPCChoiceGenerator;
import gov.nasa.jpf.vm.*;

public class SymbolicChoiceChangingListener2 extends ListenerAdapter {

    @Override
    public void executeInstruction(VM vm, ThreadInfo ti, Instruction instr) {

            StackFrame sf = ti.getTopFrame();

            // 21 is ifeq, 24 is getstatic
            // If we run starting fom instruction 21, the choiceGenerators are "stacked"
            // and it appears that either ours or the ifeq cg is ignored.
            // looking at the output, the then and else branches only occur once.
            // If we start from 24, the 'then' branch runs twice, like we would expect.
            if (instr.getMethodInfo().getName().equals("testMe2") &&
                    instr.getPosition() == 24) {
                StackFrame sframe = ti.getTopFrame();

                if (!ti.isFirstStepInsn()) { // first time around
                    VeriPCChoiceGenerator newPCChoice = new VeriPCChoiceGenerator(2);
                    newPCChoice.setOffset(instr.getPosition());
                    newPCChoice.setMethodName(instr.getMethodInfo().getFullName());
                    SystemState ss = vm.getSystemState();
                    ss.setNextChoiceGenerator(newPCChoice);
                    ti.setNextPC(instr);
                    System.out.println("Initial run through ifeq ");
                    return;
                } else {
                    PCChoiceGenerator cg = (PCChoiceGenerator) ti.getVM().getSystemState().getChoiceGenerator();
                    if(cg instanceof VeriPCChoiceGenerator) {
                        int value = (Integer) cg.getNextChoice();
                        PathCondition pc = cg.getCurrentPC();
                        if (value == 0) { // default SPF running
                            //pc._addDet(Comparator.EQ, 0, 0); // add !nominal PC
                            System.out.println("First choice run through ifeq ");
                            //ti.setNextPC(instr.getNext());
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

}
