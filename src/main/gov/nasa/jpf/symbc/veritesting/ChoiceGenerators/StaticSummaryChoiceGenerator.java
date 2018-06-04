package gov.nasa.jpf.symbc.veritesting.ChoiceGenerators;

import gov.nasa.jpf.symbc.VeritestingListener;
import gov.nasa.jpf.symbc.numeric.GreenConstraint;
import gov.nasa.jpf.symbc.numeric.PCChoiceGenerator;
import gov.nasa.jpf.symbc.numeric.PathCondition;
import gov.nasa.jpf.symbc.veritesting.FillHolesOutput;
import gov.nasa.jpf.symbc.veritesting.LogUtil;
import gov.nasa.jpf.symbc.veritesting.StaticRegionException;
import gov.nasa.jpf.symbc.veritesting.VeritestingRegion;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.StackFrame;
import gov.nasa.jpf.vm.ThreadInfo;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;

import java.util.ArrayList;
import java.util.List;

import static gov.nasa.jpf.symbc.VeritestingListener.DEBUG_LIGHT;

public class StaticSummaryChoiceGenerator extends StaticPCChoiceGenerator {
    public static List<Instruction> spfCasesIgnoreInstList = new ArrayList<>();

    //TODO: SOHA to restore that order again
    public static final int STATIC_CHOICE = 0;
    public static final int SPF_CHOICE = 1;
    public static final int RETURN_CHOICE = 2;

    public StaticSummaryChoiceGenerator(VeritestingRegion region, Instruction instruction) {
        super(1, region, instruction);
        assert(getKind(instruction) == Kind.OTHER);
    }

    @Override
    public Instruction execute(ThreadInfo ti, Instruction instruction, int choice, FillHolesOutput fillHolesOutput) throws StaticRegionException {
        assert(choice == STATIC_CHOICE || choice == SPF_CHOICE);
        Instruction nextInstruction = null;
        if (choice == STATIC_CHOICE) {
            LogUtil.log(DEBUG_LIGHT, "Executing static region choice in SummaryCG");
            if(this.getCurrentPC().simplify())
                nextInstruction = setupSPF(ti, instruction, getRegion(), fillHolesOutput);
            else { //ignore choice if it is unsat
                ti.getVM().getSystemState().setIgnored(true);
                ++VeritestingListener.unsatSPFCaseCount;
            }

        } else if (choice == SPF_CHOICE) {
            LogUtil.log(DEBUG_LIGHT, "Executing SPF choice in SummaryCG");
            PathCondition pc;
            pc = this.getCurrentPC();
            nextInstruction = instruction;
            if(!pc.simplify()) {// not satisfiable
                // System.out.println("SPF Summary choice unsat!  Instruction: " + instruction.toString());
                ti.getVM().getSystemState().setIgnored(true);
            }
            else {
                // System.out.println("SPF summary choice sat!  Instruction: " + instruction.toString());
                /*if(currentTopFrame != null)
                    ti.setTopFrame(currentTopFrame);*/
                spfCasesIgnoreInstList.add(instruction);
            }
        }
        return nextInstruction;
    }

    private PathCondition createPC(PathCondition pc, Expression regionSummary, Expression constraint) {
        PathCondition pcCopy = pc.make_copy();
        za.ac.sun.cs.green.expr.Expression copyConstraint = new Operation(Operation.Operator.AND, regionSummary, constraint);
        pcCopy._addDet(new GreenConstraint(copyConstraint));
        return pcCopy;
    }

    public void makeVeritestingCG(Expression regionSummary, ThreadInfo ti) throws StaticRegionException {
        assert(regionSummary != null);
        PathCondition pc;
        if (ti.getVM().getSystemState().getChoiceGenerator() instanceof PCChoiceGenerator)
            pc = ((PCChoiceGenerator)(ti.getVM().getSystemState().getChoiceGenerator())).getCurrentPC();
        else{
            pc = new PathCondition();
            pc._addDet(new GreenConstraint(Operation.TRUE));
        }

        setPC(createPC(pc, regionSummary, getRegion().staticNominalPredicate()), STATIC_CHOICE);
        setPC(createPC(pc, regionSummary, getRegion().spfPathPredicate()), SPF_CHOICE);
       // setPC(createPC(pc, regionSummary, getRegion().spfPathPredicate()), SPF_CHOICE);
        // TODO: create the path predicate for the 'return' case.
    }
}
