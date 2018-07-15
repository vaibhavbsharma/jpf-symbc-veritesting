package gov.nasa.jpf.symbc.veritesting.CFGConversion;

import com.ibm.wala.cfg.Util;
import com.ibm.wala.ssa.*;
import gov.nasa.jpf.symbc.veritesting.StaticRegionException;

import java.util.HashMap;
import java.util.HashSet;

/*
    This class creates our structure IR from the WALA SSA form for further transformation.
    Right now it emits a string because the IR is not yet finished.

    Important question: what is the scope of this class?  Is it supposed to be maintained
    throughout the creation process or is it constructed / destructed for each visited method?

    In examining the debug output, it appears that the same classes and methods are visited
    multiple times.

 */

public class CreateStaticRegions {

    public CreateStaticRegions() {
        visitedBlocks = new HashSet<>();
        veritestingRegions = new HashMap<>();
    }

    // String will be replaced by IR
    private HashMap<String, String> veritestingRegions;
    HashMap<String, String> getVeritestingRegions() { return this.veritestingRegions; }

    public static String constructRegionIdentifier(String className, String methodName, String methodSignature, int offset) {
        return className + "." + methodName + methodSignature + "#" + offset;
    }

    public static String constructRegionIdentifier(ISSABasicBlock blk) {
        return constructRegionIdentifier(blk.getClass().getCanonicalName(),
                blk.getMethod().getName().toString(),
                blk.getMethod().getSignature(),
                blk.getFirstInstructionIndex());
    }

    public static String constructMethodIdentifier(String className, String methodName, String methodSignature) {
        return className + "." + methodName + methodSignature;
    }

    public static String constructMethodIdentifier(ISSABasicBlock blk) {
        return constructMethodIdentifier(blk.getClass().getCanonicalName(),
                blk.getMethod().getName().toString(),
                blk.getMethod().getSignature());
    }

    public boolean isBranch(SSACFG cfg, ISSABasicBlock block) {
        return cfg.getNormalSuccessors(block).size() == 2;
    }

    // for memoization.
    HashSet<ISSABasicBlock> visitedBlocks;

    enum TranslationMode {InitialBlock, MiddleBlock, FinalBlock};

    static String skip = "skip; " + System.lineSeparator();

    public String translateTruncatedInitialBlock(ISSABasicBlock currentBlock) {
        return skip;
    }

    public String appendSkipToEmpty(String s) {
        if (s.equals("")) {
            return skip;
        }
        return s;
    }

    public String translateTruncatedFinalBlock(ISSABasicBlock currentBlock) {
        String s = "";
        for (SSAInstruction ins: currentBlock) {
            if (!(ins instanceof SSAPhiInstruction))
                return s;
            s += ins.toString() + System.lineSeparator();
        }
        return appendSkipToEmpty(s);
    }

    public String translateInternalBlock(ISSABasicBlock currentBlock) {
        String s = "";
        boolean prefix = true;

        for (SSAInstruction ins: currentBlock) {
            if ((ins instanceof SSAConditionalBranchInstruction) ||
                    (ins instanceof SSAGotoInstruction)) {
                // properly formed blocks will only have branches and gotos as the last instruction.
                // We will handle branches in attemptSubregion.
            } else {
                s += ins.toString() + System.lineSeparator();
            }
        }
        return appendSkipToEmpty(s);
    }

    // precondiion: terminus is the loop join.
    public String conditionalBranch(SSACFG cfg, ISSABasicBlock currentBlock, ISSABasicBlock terminus) throws StaticRegionException {

        SSAInstruction ins = currentBlock.getLastInstruction();
        if (!(ins instanceof SSAConditionalBranchInstruction)) {
            throw new StaticRegionException("Expect conditional branch at end of 2-path attemptSubregion");
        }
        // Handle case where terminus is either 'if' or 'else' branch;
        String s ;
        s = "if (" + ins.toString() + ") { " + System.lineSeparator();
        ISSABasicBlock thenBlock = Util.getTakenSuccessor(cfg, currentBlock);
        if (thenBlock.getNumber() < terminus.getNumber()) {
            s += attemptSubregionRec(cfg, thenBlock, terminus);
        } else {
            s += skip;
        }
        s += "else {" + System.lineSeparator();

        ISSABasicBlock elseBlock = Util.getNotTakenSuccessor(cfg, currentBlock);
        if (elseBlock.getNumber() < terminus.getNumber()) {
            s += attemptSubregionRec(cfg, elseBlock, terminus);
        } else {
            s += skip;
        }
        s += "}" + System.lineSeparator();

        return s;
    }

    /*
        This method translates from currentBlock up to but not including endingBlock.
        Doing it this way makes it much simpler to deal with nested if/then/elses that land in the same spot.

        It also makes it simpler to tailor the end of the translation: for methods, we want to grab the
        remaining code within the method, while for conditional blocks we only want to grab the subsequent \phi
        functions.

     */

    public String attemptSubregionRec(SSACFG cfg, ISSABasicBlock currentBlock, ISSABasicBlock endingBlock) throws StaticRegionException {

        if (currentBlock == endingBlock) {
            return skip;
        }

        String s = translateInternalBlock(currentBlock);

        if (cfg.getNormalSuccessors(currentBlock).size() == 2) {
            FindStructuredBlockEndNode finder = new FindStructuredBlockEndNode(cfg, currentBlock, endingBlock);
            ISSABasicBlock terminus = finder.findMinConvergingNode();
            s += conditionalBranch(cfg, currentBlock, terminus);
            s += attemptSubregionRec(cfg, terminus, endingBlock);
        }
        else if (cfg.getNormalSuccessors(currentBlock).size() == 1){
            SSAInstruction last = (currentBlock.iterator().hasNext()) ? currentBlock.getLastInstruction() : null;

            // gets rid of a few extra 'skips'
            ISSABasicBlock nextBlock = cfg.getNormalSuccessors(currentBlock).iterator().next();
            if (nextBlock.getNumber() < endingBlock.getNumber()) {
                s += attemptSubregionRec(cfg, nextBlock, endingBlock);
            }
        }
        return s;
    }

    // precondition: endingBlock is the terminus of the loop
    public String attemptConditionalSubregion(SSACFG cfg, ISSABasicBlock startingBlock, ISSABasicBlock terminus) throws StaticRegionException {

        assert(isBranch(cfg, startingBlock));
        String s = conditionalBranch(cfg, startingBlock, terminus);
        s += translateTruncatedFinalBlock(terminus);
        return s;
    }

    public String attemptMethodSubregion(SSACFG cfg, ISSABasicBlock startingBlock, ISSABasicBlock endingBlock) throws StaticRegionException {
        String s = attemptSubregionRec(cfg, startingBlock, endingBlock);
        s += translateInternalBlock(endingBlock);
        return s;
    }

    public void createStructuredConditionalRegions(SSACFG cfg, ISSABasicBlock startingBlock, ISSABasicBlock endingBlock) throws StaticRegionException {
        ISSABasicBlock currentBlock = startingBlock;

        // terminating conditions
        if (visitedBlocks.contains(currentBlock))
            return;
        if (currentBlock == endingBlock) { return; }

        visitedBlocks.add(currentBlock);

        if (isBranch(cfg, currentBlock)) {
            try {
                FindStructuredBlockEndNode finder = new FindStructuredBlockEndNode(cfg, currentBlock, endingBlock);
                ISSABasicBlock terminus = finder.findMinConvergingNode();
                String s = attemptConditionalSubregion(cfg, currentBlock, terminus);
                this.veritestingRegions.put(CreateStaticRegions.constructRegionIdentifier(currentBlock), s);
                System.out.println("Subregion: " + System.lineSeparator() + s);
                createStructuredConditionalRegions(cfg, terminus, endingBlock);
                return;
            } catch (StaticRegionException sre) {
                System.out.println("Unable to create subregion");
            }
        }
        for (ISSABasicBlock nextBlock: cfg.getNormalSuccessors(currentBlock)) {
            createStructuredConditionalRegions(cfg, nextBlock, endingBlock);
        }
    }

    public void createStructuredMethodRegion(SSACFG cfg, ISSABasicBlock startingBlock, ISSABasicBlock endingBlock) throws StaticRegionException {
        try {
            String s = attemptMethodSubregion(cfg, startingBlock, endingBlock);
            System.out.println("Method" + System.lineSeparator() + s);
            this.veritestingRegions.put(CreateStaticRegions.constructMethodIdentifier(startingBlock), s);
        } catch (StaticRegionException sre) {
            System.out.println("Unable to create a method summary subregion for: " + cfg.getMethod().getName().toString());
        }
    }
}
