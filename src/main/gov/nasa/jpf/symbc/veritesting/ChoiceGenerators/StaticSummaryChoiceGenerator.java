package gov.nasa.jpf.symbc.veritesting.ChoiceGenerators;

import gov.nasa.jpf.symbc.numeric.GreenConstraint;
import gov.nasa.jpf.symbc.numeric.PCChoiceGenerator;
import gov.nasa.jpf.symbc.numeric.PathCondition;
import gov.nasa.jpf.symbc.veritesting.StaticRegionException;
import gov.nasa.jpf.symbc.veritesting.VeritestingRegion;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.ThreadInfo;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;

public class StaticSummaryChoiceGenerator extends StaticPCChoiceGenerator {

    public static final int STATIC_CHOICE = 0;
    public static final int SPF_CHOICE = 1;
    public static final int RETURN_CHOICE = 2;

    public StaticSummaryChoiceGenerator(VeritestingRegion region, Instruction instruction) {
        super(1, region, instruction);
        assert(getKind(instruction) == Kind.OTHER);
    }

    @Override
    public Instruction execute(ThreadInfo ti, Instruction instruction, int choice) {
        assert(choice == STATIC_CHOICE || choice == SPF_CHOICE);
        Instruction nextInstruction = null;
        if (choice == STATIC_CHOICE) {
//            System.out.println("Executing static region");
            nextInstruction = setupSPF(ti, instruction, getRegion());
        } else if (choice == SPF_CHOICE) {
            PathCondition pc;
            pc = this.getCurrentPC();
            nextInstruction = instruction;
            if(!pc.simplify()) {// not satisfiable
                // System.out.println("SPF Summary choice unsat!  Instruction: " + instruction.toString());
                ti.getVM().getSystemState().setIgnored(true);
            }
            else {
                // System.out.println("SPF summary choice sat!  Instruction: " + instruction.toString());
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
        PathCondition pc = ((PCChoiceGenerator)(ti.getVM().getSystemState().getChoiceGenerator())).getCurrentPC();

        setPC(createPC(pc, regionSummary, getRegion().staticNominalPredicate()), STATIC_CHOICE);
        setPC(createPC(pc, regionSummary, getRegion().spfPathPredicate()), SPF_CHOICE);
        // TODO: create the path predicate for the 'return' case.
    }
}
