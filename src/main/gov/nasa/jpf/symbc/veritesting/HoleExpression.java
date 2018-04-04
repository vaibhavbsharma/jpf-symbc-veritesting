package gov.nasa.jpf.symbc.veritesting;

import com.ibm.wala.types.TypeReference;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Visitor;
import za.ac.sun.cs.green.expr.VisitorException;

import java.util.List;

// MWW - arrayInfoHole is only used for one kind of hole.
// Why are we not subclassing here for the extra information?

public class HoleExpression extends za.ac.sun.cs.green.expr.Expression{
    public boolean isLatestWrite = true;
    final long holeID;
    String holeVarName = "";
    InvokeInfo invokeInfo = null;
    public Expression dependsOn = null;
    FieldInfo fieldInfo = null;
    HoleType holeType = HoleType.NONE;
    ArrayInfoHole arrayInfoHole = null;

    protected boolean isHole = false;
    protected int localStackSlot = -1;

    // MWW: Why is this not public?  Is it supposed to be instantiated only within package?
    HoleExpression(long _holeID) { holeID = _holeID; }

    @Override
    public void accept(Visitor visitor) throws VisitorException {
        visitor.preVisit(this);
        visitor.postVisit(this);
    }

    @Override
    public boolean equals(Object object) {
        if(object instanceof HoleExpression) {
            HoleExpression holeExpression = (HoleExpression) object;
            if(holeExpression.getHoleType() != holeType) return false;
            if(!holeExpression.getHoleVarName().equals(holeVarName))
                return false;
            switch(holeExpression.getHoleType()) {
                case LOCAL_INPUT:
                case LOCAL_OUTPUT:
                    if(holeExpression.getLocalStackSlot() != localStackSlot)
                        return false;
                    else return true;
                case INTERMEDIATE:
                    return true;
                case NONE:
                    return true;
                case CONDITION:
                    return true;
                case NEGCONDITION:
                    return true;
                case FIELD_INPUT:
                case FIELD_OUTPUT:
                    FieldInfo fieldInfo1 = holeExpression.getFieldInfo();
                    if(!fieldInfo1.className.equals(fieldInfo.className) ||
                            !fieldInfo1.fieldName.equals(fieldInfo.fieldName) ||
                            fieldInfo1.localStackSlot != fieldInfo.localStackSlot ||
                            fieldInfo1.callSiteStackSlot != fieldInfo.callSiteStackSlot ||
                            (fieldInfo1.writeValue != null && fieldInfo.writeValue != null && !fieldInfo1.writeValue.equals(fieldInfo.writeValue)))
                        return false;
                    else return true;
                case INVOKE:
                    InvokeInfo otherInvokeInfo = holeExpression.invokeInfo;
                    return (!otherInvokeInfo.equals(invokeInfo));
            }
            return true;
        }
        else return false;
    }

    @Override
    public String toString() {
        String ret = new String();
        ret += "(type = " + holeType + ", name = " + holeVarName;
        switch (holeType) {
            case LOCAL_INPUT:
            case LOCAL_OUTPUT:
                ret += ", stackSlot = " + localStackSlot;
                break;
            case INTERMEDIATE:
                break;
            case NONE:
                break;
            case CONDITION:
                break;
            case NEGCONDITION:
                break;
            case FIELD_INPUT:
            case FIELD_OUTPUT:
                ret += ", fieldInfo = " + fieldInfo.toString();
                break;
            case INVOKE:
                ret += ", invokeInfo = " + invokeInfo.toString();
                break;
            case ARRAYLOAD:
                ret += ", arrayInfo = " + arrayInfoHole.toString();
                break;
            default:
                System.out.println("undefined toString for holeType (" + holeType + ")");
                assert(false);
        }
        ret += ")";
        return ret;
    }

    @Override
    public int getLength() {
        return 1;
    }

    @Override
    public int getLeftLength() {
        return 1;
    }

    @Override
    public int numVar() {
        return 1;
    }

    @Override
    public int numVarLeft() {
        return 1;
    }

    @Override
    public List<String> getOperationVector() {
        return null;
    }

    public void setHoleVarName(String holeVarName) {
        this.holeVarName = holeVarName;
    }
    public String getHoleVarName() {
        return holeVarName;
    }

    public enum HoleType {
        LOCAL_INPUT("local_input"),
        LOCAL_OUTPUT("local_output"),
        INTERMEDIATE("lhsExpr"),
        NONE ("none"),
        CONDITION("condition"),
        NEGCONDITION("negcondition"),
        FIELD_INPUT("field_input"),
        FIELD_OUTPUT("field_output"),
        INVOKE("invoke"),
        ARRAYLOAD("array_load");
        private final String string;

        HoleType(String string) {
            this.string = string;
        }
    }

    public HoleType getHoleType() {
        return holeType;
    }

    public boolean isHole() {
        return isHole;
    }

    public void setHole(boolean hole, HoleType h) {
        isHole = hole; holeType = h;
        assert((isHole && holeType == HoleType.LOCAL_INPUT) ||
                (isHole && holeType == HoleType.LOCAL_OUTPUT) ||
                (isHole && holeType == HoleType.INTERMEDIATE) ||
                (isHole && holeType == HoleType.CONDITION) ||
                (isHole && holeType == HoleType.NEGCONDITION) ||
                (isHole && holeType == HoleType.FIELD_INPUT) ||
                (isHole && holeType == HoleType.FIELD_OUTPUT) ||
                (isHole && holeType == HoleType.INVOKE) ||
                (isHole && holeType == HoleType.ARRAYLOAD) ||
                (!isHole && holeType == HoleType.NONE));
    }


    public void setLocalStackSlot(int localStackSlot) {
        assert(holeType == HoleType.LOCAL_INPUT || holeType == HoleType.LOCAL_OUTPUT);
        if(this.localStackSlot != -1) {
            System.out.println("Hole " + toString() + " cannot be given new stack slot (" + localStackSlot + ")");
            assert(false);
        }
        this.localStackSlot = localStackSlot;
    }
    public int getLocalStackSlot() {
        assert(holeType == HoleType.LOCAL_INPUT || holeType == HoleType.LOCAL_OUTPUT);
        return localStackSlot;
    }

    public void setFieldInfo(String className, String fieldName, int localStackSlot, int callSiteStackSlot,
                             Expression writeExpr, boolean isStaticField) {
        assert(holeType == HoleType.FIELD_INPUT || holeType == HoleType.FIELD_OUTPUT);
        if(holeType == HoleType.FIELD_OUTPUT) {
            assert (writeExpr != null);
            assert (writeExpr instanceof HoleExpression);
            assert (((HoleExpression)writeExpr).getHoleType() == HoleType.INTERMEDIATE);
        }
        if(holeType == HoleType.FIELD_INPUT) assert(writeExpr == null);
        fieldInfo = new FieldInfo(className, fieldName, localStackSlot, callSiteStackSlot, writeExpr, isStaticField);
    }

    public FieldInfo getFieldInfo() {
        return fieldInfo;
    }

    public ArrayInfoHole getArrayInfo() {return  arrayInfoHole;}
    public void setArrayInfo(Expression arrayRef, Expression arrayIndex, Expression lhsExpr,  TypeReference arrayType, Expression pathLabelHole){
        assert(this.isHole && this.holeType== HoleType.ARRAYLOAD);
        arrayInfoHole = new ArrayInfoHole(arrayRef, arrayIndex, lhsExpr, arrayType, pathLabelHole);
    }

    public class ArrayInfoHole{
        public Expression arrayRefHole, arrayIndexHole, lhsExpr;
        public TypeReference arrayType;
        Expression pathLabelHole;
        int length;

        public ArrayInfoHole(Expression arrayRef, Expression arrayIndex, Expression lhsExpr, TypeReference arrayType, Expression pathLabelHole){
            this.arrayRefHole = arrayRef;
            this.lhsExpr = lhsExpr;
            this.arrayIndexHole = arrayIndex;
            this.arrayType = arrayType;
            this.pathLabelHole = pathLabelHole;
        }

        public Expression getPathLabelHole() {
            return pathLabelHole;
        }

        // instantiation information; transient.
        public int length() { return length; }
        public void setLength(int length) { this.length = length; }

        @Override
        public String toString() {
            return arrayRefHole + "[" + arrayType +":" +arrayIndexHole + "]";
        }

        public boolean equals(Object o) {
            if(!(o instanceof ArrayInfoHole)) return false;
            ArrayInfoHole arrayInfoHole = (ArrayInfoHole) o;
            if(arrayInfoHole.arrayIndexHole != (this.arrayIndexHole) ||
                    arrayInfoHole.arrayType != (this.arrayType) ||
                    arrayInfoHole.arrayRefHole != (this.arrayRefHole) )
                return false;
            else return true;
        }
    }

    public class FieldInfo {
        public String className, fieldName;
        public int localStackSlot = -1, callSiteStackSlot = -1;
        public Expression writeValue = null;
        public boolean isStaticField = false;

        public FieldInfo(String className, String fieldName, int localStackSlot, int callSiteStackSlot,
                         Expression writeValue, boolean isStaticField) {
            this.localStackSlot = localStackSlot;
            this.callSiteStackSlot = callSiteStackSlot;
            this.className = className;
            this.fieldName = fieldName;
            this.writeValue = writeValue;
            this.isStaticField = isStaticField;
        }

        public String toString() {
            String ret = "currentClassName = " + className + ", fieldName = " + fieldName +
                    ", stackSlots (local = " + localStackSlot + ", callSite = " + callSiteStackSlot;
            if(writeValue != null) ret += ", writeValue (" + writeValue.toString() + ")";
            else ret += ")";
            ret += ", isStaticField = " + isStaticField;
            return ret;
        }

        public boolean equals(Object o) {
            if(!(o instanceof FieldInfo)) return false;
            FieldInfo fieldInfo1 = (FieldInfo) o;
            if(!fieldInfo1.className.equals(this.className) ||
                    !fieldInfo1.fieldName.equals(this.fieldName) ||
                    fieldInfo1.localStackSlot != this.localStackSlot ||
                    fieldInfo1.callSiteStackSlot != this.callSiteStackSlot ||
                    (fieldInfo1.writeValue != null && this.writeValue != null && !fieldInfo1.writeValue.equals(this.writeValue)))
                return false;
            else return true;
        }
    }

    public void setInvokeInfo(InvokeInfo invokeInfo) {
        this.invokeInfo = invokeInfo;
    }
    public InvokeInfo getInvokeInfo() {
        return invokeInfo;
    }
}
