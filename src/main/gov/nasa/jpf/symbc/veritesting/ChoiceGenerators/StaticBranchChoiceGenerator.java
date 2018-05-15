package gov.nasa.jpf.symbc.veritesting.ChoiceGenerators;

import gov.nasa.jpf.jvm.bytecode.IfInstruction;
import gov.nasa.jpf.symbc.bytecode.IFNONNULL;
import gov.nasa.jpf.symbc.numeric.*;
import gov.nasa.jpf.symbc.veritesting.StaticRegionException;
import gov.nasa.jpf.symbc.veritesting.VeritestingRegion;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.StackFrame;
import gov.nasa.jpf.vm.ThreadInfo;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;


public class StaticBranchChoiceGenerator extends StaticPCChoiceGenerator {

    public static final int STATIC_CHOICE = 0;
    public static final int THEN_CHOICE = 1;
    public static final int ELSE_CHOICE = 2;
    public static final int RETURN_CHOICE = 3;

    public StaticBranchChoiceGenerator(VeritestingRegion region, Instruction instruction) {
        super(2, region, instruction);
        Kind kind = getKind(instruction);

        assert(kind == Kind.BINARYIF ||
            kind == Kind.NULLIF ||
            kind == Kind.UNARYIF);
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
            case "if_icmpne":
                return Comparator.NE;
            default:
                System.out.println("Unknown comparator: " + instruction.getMnemonic());
                assert(false);
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
            case "if_icmpne":
                return Comparator.EQ;
            default:
                System.out.println("Unknown comparator: " + instruction.getMnemonic());
                assert(false);
                return null;
        }
    }

    // MWW: make choice 0 and choice 4 also the responsibility of the CG
    public Instruction execute(ThreadInfo ti, Instruction instructionToExecute, int choice) {
        // if/else conditions.
        assert(choice == STATIC_CHOICE || choice == THEN_CHOICE || choice == ELSE_CHOICE);

        Instruction nextInstruction = null;
        if (choice == STATIC_CHOICE) {
            //System.out.println("Executing static region");
            nextInstruction = setupSPF(ti, instructionToExecute, getRegion());
        } else if (choice == THEN_CHOICE || choice == ELSE_CHOICE) {
            //System.out.println("Executing then/else choice.  Instruction: " + instructionToExecute);
            switch (getKind(instructionToExecute)) {
                case UNARYIF:
                    nextInstruction = executeUnaryIf(instructionToExecute, choice);
                    break;
                case BINARYIF:
                    nextInstruction = executeBinaryIf(instructionToExecute, choice);
                    break;
                case NULLIF:
                    nextInstruction = executeNullIf(instructionToExecute);
                    break;
                case OTHER:
                    System.out.println("Error: Branch choice generator instantiated on non-branch instruction!");
                    assert(false);
            }
        } else {
            // should never get here (until we make early returns)
            assert(false);
        }
        return nextInstruction;
    }

    /*
        So: here is what should happen.
        We have the PC constructed for choices 0, 1, and 2.
        In this case, we are in choice 1 or 2.

        We unpack the instruction, add it to the PC, and execute.
     */
    private Instruction executeBinaryIf(Instruction instruction, int choice) {
        StackFrame sf = ti.getModifiableTopFrame();

        IntegerExpression sym_v1 = (IntegerExpression) sf.getOperandAttr(1);
        IntegerExpression sym_v2 = (IntegerExpression) sf.getOperandAttr(0);

        if ((sym_v1 == null) && (sym_v2 == null)) { // both conditions are concrete
            //System.out.println("Execute IF_ICMPEQ: The conditions are concrete");
            return instruction.execute(ti);
        } else {
            int	v2 = sf.pop();
            int	v1 = sf.pop();
            PathCondition pc;
            pc = this.getCurrentPC();

            assert pc != null;
            assert(choice == THEN_CHOICE || choice == ELSE_CHOICE);

            if (choice == THEN_CHOICE) {
                Comparator byteCodeOp = this.getComparator(instruction);
                if (sym_v1 != null){
                    if (sym_v2 != null){ //both are symbolic values
                        pc._addDet(byteCodeOp,sym_v1,sym_v2);
                    }else
                        pc._addDet(byteCodeOp,sym_v1,v2);
                }else
                    pc._addDet(byteCodeOp, v1, sym_v2);

                if(!pc.simplify())  {// not satisfiable
                    // System.out.println("Then choice unsat!  Instruction: " + instruction.toString());
                    ti.getVM().getSystemState().setIgnored(true);
                }else{
                    this.setCurrentPC(pc);
                    // System.out.println("Then choice sat!  Instruction: " + instruction.toString());
                    // System.out.println(this.getCurrentPC());
                }
                return ((IfInstruction) instruction).getTarget();
            } else {
                Comparator byteCodeNegOp = this.getNegComparator(instruction);
                if (sym_v1 != null){
                    if (sym_v2 != null){ //both are symbolic values
                        pc._addDet(byteCodeNegOp,sym_v1,sym_v2);
                    }else
                        pc._addDet(byteCodeNegOp,sym_v1,v2);
                }else
                    pc._addDet(byteCodeNegOp, v1, sym_v2);
                if(!pc.simplify())  {// not satisfiable
                    // System.out.println("Else choice unsat!  Instruction: " + instruction.toString());
                    ti.getVM().getSystemState().setIgnored(true);
                }else {
                    this.setCurrentPC(pc);
                    // System.out.println("Else choice sat!  Instruction: " + instruction.toString());
                    // System.out.println(this.getCurrentPC());
                }
                return instruction.getNext(ti);
            }

        }
    }

    public Instruction executeNullIf(Instruction instruction) {
        StackFrame sf = ti.getModifiableTopFrame();
        Expression sym_v = (Expression) sf.getOperandAttr();
        if (sym_v == null) { // the condition is concrete
            //System.out.println("Execute IFEQ: The condition is concrete");
            return ((IFNONNULL) instruction).execute(ti);
        } else {
            // MWW: I do not understand this code, I am asserting false!
            // MWW: I think SPF code may be wrong.
            sf.pop();
            assert(false);
            return ((IfInstruction) instruction).getTarget();
        }
    }




    public Instruction executeUnaryIf(Instruction instruction, int choice) {
        StackFrame sf = ti.getModifiableTopFrame();
        IntegerExpression sym_v = (IntegerExpression) sf.getOperandAttr();

        if(sym_v == null) { // the condition is concrete
            return instruction.execute(ti);
        }

        sf.pop();
        PathCondition pc = this.getCurrentPC();

        if (choice == THEN_CHOICE) {
            pc._addDet(this.getComparator(instruction), sym_v, 0);
            if (!pc.simplify()) {// not satisfiable
                // System.out.println("Then choice unsat!  Instruction: " + instruction.toString());
                ti.getVM().getSystemState().setIgnored(true);
            } else {
                this.setCurrentPC(pc);
                // System.out.println("Then choice sat!  Instruction: " + instruction.toString());
                // System.out.println(this.getCurrentPC());
            }
            return ((IfInstruction) instruction).getTarget();
        } else {
            pc._addDet(this.getNegComparator(instruction), sym_v, 0);
            if (!pc.simplify()) {// not satisfiable
                // System.out.println("Else choice unsat!  Instruction: " + instruction.toString());
                ti.getVM().getSystemState().setIgnored(true);
            } else {
                this.setCurrentPC(pc);
                // System.out.println("Else choice sat!  Instruction: " + instruction.toString());
                // System.out.println(this.getCurrentPC());
            }
            return instruction.getNext(ti);
        }
    }

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

    public void makeVeritestingCG(Expression regionSummary, ThreadInfo ti) throws StaticRegionException {
        assert(regionSummary != null);
        PathCondition pc = ((PCChoiceGenerator)(ti.getVM().getSystemState().getChoiceGenerator())).getCurrentPC();

        setPC(createPC(pc, regionSummary, getRegion().staticNominalPredicate()), STATIC_CHOICE);
        setPC(createPC(pc, regionSummary, getRegion().spfPathPredicate()), THEN_CHOICE);
        setPC(createPC(pc, regionSummary, getRegion().spfPathPredicate()), ELSE_CHOICE);
        // TODO: create the path predicate for the 'return' case.
    }

}
