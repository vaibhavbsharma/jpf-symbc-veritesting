package gov.nasa.jpf.symbc.veritesting;

import com.ibm.wala.types.TypeReference;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Visitor;
import za.ac.sun.cs.green.expr.VisitorException;

import java.util.List;

public class HoleExpression extends za.ac.sun.cs.green.expr.Expression{
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
        if(object == null) return false;
        if(object instanceof HoleExpression) {
            HoleExpression holeExpression = (HoleExpression) object;
            if(holeExpression.getHoleType() != holeType) return false;
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

    @Override
    public String toString() {
        String ret = new String();
        ret += "(type = " + holeType + ", name = " + holeVarName;
        ret += ", className = " + className + ", methodName = " + methodName;
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

    private final long holeID;
    HoleExpression(long _holeID, String className, String methodName, HoleType holeType, Expression PLAssign) {
        holeID = _holeID;
        setClassName(className);
        setMethodName(methodName);
        setHoleType(holeType);
        this.PLAssign = PLAssign;
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
        assert(localStackSlot != -1);
        assert(holeType == HoleType.LOCAL_INPUT || holeType == HoleType.LOCAL_OUTPUT);
        this.globalStackSlot = globalStackSlot;
    }

    public int getGlobalOrLocalStackSlot(String className, String methodName) {
        assert(localStackSlot != -1);
        assert(holeType == HoleType.LOCAL_INPUT || holeType == HoleType.LOCAL_OUTPUT);
        if(this.className.equals(className) && this.methodName.equals(methodName)) return getLocalStackSlot();
        else return globalStackSlot;
    }

    public static boolean isLocal(Expression expression) {
        if(!(expression instanceof HoleExpression)) return false;
        HoleExpression h = (HoleExpression) expression;
        return (h.holeType == HoleType.LOCAL_OUTPUT || h.holeType == HoleType.LOCAL_INPUT);
    }

    public void setIsLatestWrite(boolean isLatestWrite) {
        this.isLatestWrite = isLatestWrite;
    }
    public boolean isLatestWrite() { return this.isLatestWrite; }
    private boolean isLatestWrite = true;

    public enum HoleType {
        LOCAL_INPUT("local_input"),
        LOCAL_OUTPUT("local_output"),
        INTERMEDIATE("intermediate"),
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
                (h == HoleType.ARRAYLOAD));
        holeType = h;
    }

    public void setLocalStackSlot(int localStackSlot) {
        assert(holeType == HoleType.LOCAL_INPUT || holeType == HoleType.LOCAL_OUTPUT);
        if(localStackSlot == -1) {
            System.out.println("Hole " + toString() + " cannot be given new stack slot (" + localStackSlot + ")");
            assert(false);
        }
        this.localStackSlot = localStackSlot;
    }
    public int getLocalStackSlot() {
        assert(holeType == HoleType.LOCAL_INPUT || holeType == HoleType.LOCAL_OUTPUT);
        return localStackSlot;
    }
    protected int localStackSlot = -1;

    public void setFieldInfo(FieldInfo f) {
        assert(holeType == HoleType.FIELD_INPUT || holeType == HoleType.FIELD_OUTPUT);
        //the object reference should either be local, come from another hole, or this field should be static
        assert((f.localStackSlot != -1 || f.callSiteStackSlot != -1) || (f.useHole != null) || f.isStaticField);
        if(holeType == HoleType.FIELD_OUTPUT) {
            assert (f.writeValue != null);
            assert (f.writeValue instanceof HoleExpression);
            assert (((HoleExpression)f.writeValue).getHoleType() == HoleType.INTERMEDIATE);
        }
        if(holeType == HoleType.FIELD_INPUT) assert(f.writeValue == null);
        fieldInfo = new FieldInfo(f.fieldStaticClassName, f.fieldName, methodName, localStackSlot, f.callSiteStackSlot, f.writeValue,
                f.isStaticField, f.useHole);
    }

    public void setFieldInfo(String staticFieldClassName, String fieldName, String methodName, int localStackSlot,
                             int callSiteStackSlot,
                             Expression writeExpr, boolean isStaticField, HoleExpression useHole) {
        assert(holeType == HoleType.FIELD_INPUT || holeType == HoleType.FIELD_OUTPUT);
        //the object reference should either be local, come from another hole, or this field should be static
        assert((localStackSlot != -1 || callSiteStackSlot != -1) || (useHole != null) || isStaticField);
        if(holeType == HoleType.FIELD_OUTPUT) {
            assert (writeExpr != null);
            assert (writeExpr instanceof HoleExpression);
            assert (((HoleExpression)writeExpr).getHoleType() == HoleType.INTERMEDIATE);
        }
        if(holeType == HoleType.FIELD_INPUT) assert(writeExpr == null);
        fieldInfo = new FieldInfo(staticFieldClassName, fieldName, methodName, localStackSlot, callSiteStackSlot, writeExpr,
                isStaticField, useHole);
    }

    public FieldInfo getFieldInfo() {
        return fieldInfo;
    }

    public ArrayInfoHole getArrayInfo() { return arrayInfoHole; }
    public void setArrayInfo(Expression arrayRef, Expression arrayIndex, TypeReference arrayType, String pathLabelString, int pathLabel) {
        assert(this.holeType == HoleType.ARRAYLOAD);
        arrayInfoHole = new ArrayInfoHole(arrayRef, arrayIndex, arrayType, pathLabelString, pathLabel);
    }
    ArrayInfoHole arrayInfoHole = null;

    public class ArrayInfoHole{
        public Expression arrayRefHole,arrayIndexHole;
        public TypeReference arrayType;
        String pathLabelString;
        int pathLabel;

        public ArrayInfoHole(Expression arrayRef, Expression arrayIndex, TypeReference arrayType, String pathLabelString, int pathLabel){
            this.arrayRefHole = arrayRef;
            this.arrayIndexHole = arrayIndex;
            this.arrayType = arrayType;
            this.pathLabelString = pathLabelString;
            this.pathLabel = pathLabel;
        }

        public String getPathLabelString() {
            return pathLabelString;
        }

        public int getPathLabel() {
            return pathLabel;
        }

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
        public int localStackSlot = -1, callSiteStackSlot = -1;
        public Expression writeValue = null;
        public boolean isStaticField = false;
        public HoleExpression useHole = null;
        public FieldInfo(String fieldStaticClassName, String fieldName, String methodName, int localStackSlot,
                         int callSiteStackSlot,
                         Expression writeValue, boolean isStaticField, HoleExpression useHole) {
            this.localStackSlot = localStackSlot;
            this.callSiteStackSlot = callSiteStackSlot;
            this.fieldName = fieldName;
            this.fieldStaticClassName = fieldStaticClassName;
            this.methodName = methodName;
            this.writeValue = writeValue;
            this.isStaticField = isStaticField;
            this.useHole = useHole;
        }

        public String toString() {
            String ret = "fieldDynClassName = " + fieldDynClassName + ", fieldName = " + fieldName +
                    ", stackSlots (local = " + localStackSlot + ", callSite = " + callSiteStackSlot;
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
                    fieldInfo1.localStackSlot != this.localStackSlot ||
                    fieldInfo1.callSiteStackSlot != this.callSiteStackSlot ||
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

    }

    FieldInfo fieldInfo = null;

    public void setInvokeInfo(InvokeInfo invokeInfo) {
        this.invokeInfo = invokeInfo;
    }
    public InvokeInfo getInvokeInfo() {
        return invokeInfo;
    }
    InvokeInfo invokeInfo = null;

}
