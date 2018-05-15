package gov.nasa.jpf.symbc.veritesting;

import com.ibm.wala.types.TypeReference;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Visitor;
import za.ac.sun.cs.green.expr.VisitorException;

import java.util.List;
import static gov.nasa.jpf.symbc.veritesting.FieldUtil.isField;

// MWW - arrayInfo is only used for one kind of hole.
// Why are we not subclassing here for the extra information?

public class HoleExpression extends za.ac.sun.cs.green.expr.Expression{
    FieldInfo fieldInfo = null;
    ArrayInfo arrayInfo = null;

    protected int localStackSlot = -1;

    // MWW: Why is this not public?  Is it supposed to be instantiated only within package?
    private HoleExpression(long _holeID) { holeID = _holeID; }

    private int globalStackSlot = -1;

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }
    //these fields are important to know the context in which the hole is being used
    //if a local variable field is used to fill in a method summary being used then the globalStackSlot will be used
    // and needs to be set to a value other than -1
    //className and methodName will point to the className and methodName of the region for which this hole was created
    private String className=null, methodName=null;

    @Override
    public void accept(Visitor visitor) throws VisitorException {
        visitor.preVisit(this);
        visitor.postVisit(this);
    }

    @Override
    public boolean equals(Object object) {
        //this doesn't check PLAssign for equality on purpose. Use the isSamePLAssign() method to do that explicitly
        if(object == null) return false;
        if(object instanceof HoleExpression) {
            HoleExpression holeExpression = (HoleExpression) object;
            if(holeExpression.getHoleType() != holeType) return false;
            if (holeExpression.holeID != this.holeID) return false;
            if(!holeExpression.getHoleVarName().equals(holeVarName) || !holeExpression.getClassName().equals(className)
                    || !holeExpression.getMethodName().equals(methodName))
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
                    if(!fieldInfo1.equals(fieldInfo))
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

    public int getGlobalStackSlot() {
        return globalStackSlot;
    }

    public HoleExpression clone(String currentClassName, String currentMethodName,
                                Expression plAssign) {
        HoleExpression copy = new HoleExpression(VarUtil.nextInt(), currentClassName,
                currentMethodName, this.getHoleType(), plAssign, this.getLocalStackSlot(), this.getGlobalStackSlot());
        copy.invokeInfo = this.getInvokeInfo();
        copy.setHoleVarName(this.getHoleVarName());
        if (FieldUtil.isFieldHole(copy.getHoleType()))
            copy.setFieldInfo(this.getFieldInfo());
        return copy;
    }

    @Override
    public String toString() {
        String ret = new String();
        ret += "(holeID = " + holeID + ", type = " + holeType + ", name = " + holeVarName;
        ret += ", className = " + className + ", methodName = " + methodName;
        if(holeType == HoleType.FIELD_OUTPUT || holeType == HoleType.FIELD_PHI)
            ret += ", isLatestWrite = " + isLatestWrite;
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
            case FIELD_PHI:
                ret += ", fieldInfo = " + fieldInfo.toString();
                break;
            case INVOKE:
                ret += ", invokeInfo = " + invokeInfo.toString();
                break;
            case ARRAYLOAD:
                ret += ", arrayInfo = " + arrayInfo.toString();
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

    private final long holeID;
    public HoleExpression(long _holeID, String className, String methodName, HoleType holeType, Expression PLAssign,
                          int localStackSlot, int globalStackSlot) {
        holeID = _holeID;
        setClassName(className);
        setMethodName(methodName);
        setHoleType(holeType);
        this.PLAssign = PLAssign;
        this.setLocalStackSlot(localStackSlot);
        this.setGlobalStackSlot(globalStackSlot);
        if(this.getLocalStackSlot() != -1 || this.getGlobalStackSlot() != -1)
            assert(isLocal(this));
        if(isLocal(this) && !isField(this))
            assert(this.getLocalStackSlot() != -1 || this.getGlobalStackSlot() != -1);
    }

    public void setHoleVarName(String holeVarName) {
        this.holeVarName = holeVarName;
    }
    public String getHoleVarName() {
        return holeVarName;
    }

    private String holeVarName = "";

    public void setGlobalStackSlot(int globalStackSlot) {
        assert(className != null);
        assert(methodName != null);
        this.globalStackSlot = globalStackSlot;
    }

    public int getGlobalOrLocalStackSlot(String className, String methodName) {
        assert(localStackSlot != -1);
        assert(holeType == HoleType.LOCAL_INPUT || holeType == HoleType.LOCAL_OUTPUT ||
                holeType == HoleType.FIELD_INPUT || holeType == HoleType.FIELD_OUTPUT ||
                holeType == HoleType.FIELD_PHI);
        if(this.className.equals(className) && this.methodName.equals(methodName)) return getLocalStackSlot();
        else return globalStackSlot;
    }

    public static boolean isLocal(Expression expression) {
        if(!(expression instanceof HoleExpression)) return false;
        HoleExpression h = (HoleExpression) expression;
        HoleType holeType = h.getHoleType();
        if (holeType == HoleType.LOCAL_INPUT || holeType == HoleType.LOCAL_OUTPUT) return true;
        return ((holeType == HoleType.FIELD_INPUT || holeType == HoleType.FIELD_OUTPUT || holeType == HoleType.FIELD_PHI)
                && (h.getFieldInfo() == null || !h.getFieldInfo().isStaticField)
                && (h.getFieldInfo() == null || h.getFieldInfo().useHole == null));
    }

    public void setIsLatestWrite(boolean isLatestWrite) {
        this.isLatestWrite = isLatestWrite;
    }
    public boolean isLatestWrite() { return this.isLatestWrite; }
    private boolean isLatestWrite = true;

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
        ARRAYLOAD("array_load"),
        FIELD_PHI("field_phi");
        private final String string;

        HoleType(String string) {
            this.string = string;
        }
    }

    public HoleType getHoleType() {
        return holeType;
    }

    private HoleType holeType = HoleType.NONE;

    private void setHoleType(HoleType h) {
        assert(h != HoleType.NONE);
        assert((h == HoleType.LOCAL_INPUT) ||
                (h == HoleType.LOCAL_OUTPUT) ||
                (h == HoleType.INTERMEDIATE) ||
                (h == HoleType.CONDITION) ||
                (h == HoleType.NEGCONDITION) ||
                (h == HoleType.FIELD_INPUT) ||
                (h == HoleType.FIELD_OUTPUT) ||
                (h == HoleType.INVOKE) ||
                (h == HoleType.ARRAYLOAD) ||
                (h == HoleType.FIELD_PHI));
        holeType = h;
    }

    public void setLocalStackSlot(int localStackSlot) {
        this.localStackSlot = localStackSlot;
    }
    public int getLocalStackSlot() {
        return localStackSlot;
    }

    public void setFieldInfo(FieldInfo f) {
        assert(holeType == HoleType.FIELD_INPUT || holeType == HoleType.FIELD_OUTPUT || holeType == HoleType.FIELD_PHI);
        //the object reference should either be local, come from another hole, or this field should be static
        assert((f.useHole != null) || f.isStaticField || isLocal(this));

        if(holeType == HoleType.FIELD_OUTPUT) {
            assert (f.writeValue != null);
        }
        if(holeType == HoleType.FIELD_INPUT) assert(f.writeValue == null);
        fieldInfo = new FieldInfo(f.fieldStaticClassName, f.fieldName, f.methodName, f.writeValue,
                f.isStaticField, f.useHole, f.fieldInputHole);
        // this needs to be done after setting the fieldInfo so that isLocal can use this fieldInfo as part of its check
        if(isLocal(this))
            assert(this.getLocalStackSlot() != -1 || this.getGlobalStackSlot() != -1);
    }

    public void setFieldInfo(String staticFieldClassName, String fieldName, String methodName,
                             Expression writeExpr, boolean isStaticField, HoleExpression useHole) {
        assert(holeType == HoleType.FIELD_INPUT || holeType == HoleType.FIELD_OUTPUT);
        //the object reference should either be local, come from another hole, or this field should be static
        assert((useHole != null) || isStaticField || isLocal(this));

        if(holeType == HoleType.FIELD_OUTPUT) {
            assert (writeExpr != null);
        }
        if(holeType == HoleType.FIELD_INPUT) assert(writeExpr == null);
        fieldInfo = new FieldInfo(staticFieldClassName, fieldName, methodName, writeExpr,
                isStaticField, useHole, null);
        // this needs to be done after setting the fieldInfo so that isLocal can use this fieldInfo as part of its check
        if(isLocal(this))
            assert(this.getLocalStackSlot() != -1 || this.getGlobalStackSlot() != -1);
    }

    public FieldInfo getFieldInfo() {
        return fieldInfo;
    }

    public ArrayInfo getArrayInfo() {return arrayInfo;}
    public void setArrayInfo(Expression arrayRef, Expression arrayIndex, HoleExpression lhsExpr,
                             TypeReference arrayType){
        assert(this.holeType== HoleType.ARRAYLOAD);
        arrayInfo = new ArrayInfo(arrayRef, arrayIndex, lhsExpr, arrayType);
    }

    public class ArrayInfo {
        public Expression arrayRefHole, arrayIndexHole, lhsExpr;
        public TypeReference arrayType;
        int length;

        public ArrayInfo(Expression arrayRef, Expression arrayIndex, Expression lhsExpr, TypeReference arrayType){
            this.arrayRefHole = arrayRef;
            this.lhsExpr = lhsExpr;
            this.arrayIndexHole = arrayIndex;
            this.arrayType = arrayType;
        }

        // instantiation information; transient.
        public int length() { return length; }
        public void setLength(int length) { this.length = length; }

        @Override
        public String toString() {
            return arrayRefHole + "[" + arrayType +":" +arrayIndexHole + "]";
        }

        public boolean equals(Object o) {
            if(!(o instanceof ArrayInfo)) return false;
            ArrayInfo arrayInfo = (ArrayInfo) o;
            if(arrayInfo.arrayIndexHole != (this.arrayIndexHole) ||
                    arrayInfo.arrayType != (this.arrayType) ||
                    arrayInfo.arrayRefHole != (this.arrayRefHole) )
                return false;
            else return true;
        }
    }

    /*
        This method assumes that other attributes in HoleExpression.FieldInfo such as fieldClassName, fieldName,
        staticField would have already been checked before the pathlabel assignment is checked for equality.
        That is why we return true if one of the two pathlabel assignments is null.
         */
    public boolean isSamePLAssign(Expression plAssign1) {
        if(this.PLAssign == null && plAssign1 == null) return true;
        if(this.PLAssign == null && plAssign1 != null) return false;
        if(this.PLAssign != null && plAssign1 == null) return false;
        return (this.PLAssign.equals(plAssign1));
    }
    public Expression PLAssign;

    public class FieldInfo {
        public final String methodName;

        public String getFieldStaticClassName() {
            return fieldStaticClassName;
        }

        private final String fieldStaticClassName;

        public String getFieldDynClassName() {
            return fieldDynClassName;
        }

        public void setFieldDynClassName(String fieldDynClassName) {
            this.fieldDynClassName = fieldDynClassName;
        }

        public String getDynOrStClassName() {
            if(fieldDynClassName != null) return fieldDynClassName;
            else return fieldStaticClassName;
        }
        // this fieldDynClassName is the dynamic class type of the object from which this field is accessed
        private String fieldDynClassName;
        public final String fieldName;
        public Expression writeValue = null;
        public boolean isStaticField = false;
        public HoleExpression useHole = null;
        public FieldInfo(String fieldStaticClassName, String fieldName, String methodName,
                         Expression writeValue, boolean isStaticField, HoleExpression useHole, HoleExpression fieldInputHole) {
            this.fieldName = fieldName;
            this.fieldStaticClassName = fieldStaticClassName;
            this.methodName = methodName;
            this.writeValue = writeValue;
            this.isStaticField = isStaticField;
            this.useHole = useHole;
            this.fieldInputHole = fieldInputHole;
        }

        public String toString() {
            String ret = "fieldClassName (dyn,st) = (" + fieldDynClassName + ", " + fieldStaticClassName +
                    "), fieldName = " + fieldName;
            if(writeValue != null) ret += ", writeValue (" + writeValue.toString() + ")";
            ret += ", isStaticField = " + isStaticField;
            if(useHole!= null) ret += ", useHole = " + useHole.toString() + ")";
            else ret+= ")";
            return ret;
        }

        public boolean equals(Object o) {
            if(!(o instanceof FieldInfo) || o == null) return false;
            FieldInfo fieldInfo1 = (FieldInfo) o;
            return equalsDetailed(fieldInfo1);
        }

        public boolean equalsDetailed(FieldInfo fieldInfo1) {
            if(!fieldInfo1.getFieldStaticClassName().equals(this.fieldStaticClassName) ||
                    !fieldInfo1.fieldName.equals(this.fieldName) ||
                    !fieldInfo1.methodName.equals(this.methodName) ||
                    (fieldInfo1.writeValue != null && this.writeValue != null && !fieldInfo1.writeValue.equals(this.writeValue)) ||
                    (fieldInfo1.isStaticField != this.isStaticField) ||
                    (fieldInfo1.useHole!= null && !fieldInfo1.useHole.equals(this.useHole))) return false;
            String fDCN1 = fieldDynClassName;
            String fDCN2 = fieldInfo1.getFieldDynClassName();
            if(fDCN1 == null && fDCN2 == null)
                return true;
            if(fDCN1 != null && fDCN2 != null)
                return fDCN1.equals(fDCN2);
            if((fDCN1 != null && fDCN2 == null) || (fDCN1 == null && fDCN2 != null))
                return false;
            return true;


        }

        public void setFieldInputHole(HoleExpression fieldInputHole) {
            assert(getHoleType() == HoleType.FIELD_PHI);
            this.fieldInputHole = fieldInputHole;
        }
        HoleExpression fieldInputHole = null;

        public HoleExpression getFieldInputHole() {
            return fieldInputHole;
        }
    }

    public void setInvokeInfo(InvokeInfo invokeInfo) {
        this.invokeInfo = invokeInfo;
    }
    public InvokeInfo getInvokeInfo() {
        return invokeInfo;
    }
    InvokeInfo invokeInfo = null;

}
