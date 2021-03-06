/*
Copyright (c) 2010-2011, Advanced Micro Devices, Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
following conditions are met:

Redistributions of source code must retain the above copyright notice, this list of conditions and the following
disclaimer.

Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
disclaimer in the documentation and/or other materials provided with the distribution.

Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products
derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

If you use the software (in whole or in part), you shall adhere to all applicable U.S., European, and other export
laws, including but not limited to the U.S. Export Administration Regulations ("EAR"), (15 C.F.R. Sections 730 through
774), and E.U. Council Regulation (EC) No 1334/2000 of 22 June 2000.  Further, pursuant to Section 740.6 of the EAR,
you hereby certify that, except pursuant to a license granted by the United States Department of Commerce Bureau of
Industry and Security or as otherwise permitted pursuant to a License Exception under the U.S. Export Administration
Regulations ("EAR"), you will not (1) export, re-export or release to a national of a country in Country Groups D:1,
E:1 or E:2 any restricted technology, software, or source code you receive hereunder, or (2) export to Country Groups
D:1, E:1 or E:2 the direct product of such technology or software, if such foreign produced direct product is subject
to national security controls as identified on the Commerce Control List (currently found in Supplement 1 to Part 774
of EAR).  For the most current Country Group listings, or for additional information about the EAR or your obligations
under those regulations, please refer to the U.S. Bureau of Industry and Security's website at http://www.bis.doc.gov/.

*/
package com.amd.aparapi.internal.writer;

import com.amd.aparapi.*;
import com.amd.aparapi.internal.util.*;
import com.amd.aparapi.internal.exception.*;
import com.amd.aparapi.internal.instruction.*;
import com.amd.aparapi.internal.instruction.BranchSet.LogicalExpressionNode;
import com.amd.aparapi.internal.instruction.InstructionSet.AccessInstanceField;
import com.amd.aparapi.internal.instruction.BranchSet.*;
import com.amd.aparapi.internal.instruction.InstructionSet.*;
import com.amd.aparapi.internal.model.ClassModel.ConstantPool.*;
import com.amd.aparapi.internal.model.ClassModel.*;
import com.amd.aparapi.internal.model.*;
import com.amd.aparapi.internal.model.ClassModel.ConstantPool.NameAndTypeEntry;
import com.amd.aparapi.internal.model.FullMethodSignature.TypeSignature;
import com.amd.aparapi.internal.model.MethodModel.METHODTYPE;
import com.amd.aparapi.Config;

import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Base abstract class for converting <code>Aparapi</code> IR to text.<br/>
 *
 *
 * @author gfrost
 *
 */

public abstract class BlockWriter {

    protected static Logger logger = Logger.getLogger(Config.getLoggerName());

    protected Entrypoint entryPoint = null;

    public Entrypoint getEntryPoint() {
        return entryPoint;
    }

    protected final static boolean useFPGAStyle = true;

    public final static String iteratorIndexSuffix = "_iterIdx";

    public final static String arrayLengthMangleSuffix = "__javaArrayLength";

    public final static String arrayDimMangleSuffix = "__javaArrayDimension";

//	public final static String arrayItemLengthMangleSuffix = "__javaItemLength";

    public abstract void write(String _string);

    public abstract void writeBeforeCurrentLine(String _string);

    public void deleteCurrentLine() {
        ;
    }

    public void writeln(String _string) {
        write(_string);
        newLine();
    }

    public int indent = 0;

    public void in() {
        indent++;
    }

    public void out() {
        indent--;
    }

    public void newLine() {
        write("\n");
        for (int i = 0; i < indent; i++)
            write("  ");
    }

    // FIXME
    public String [] findTypeHintForLocalVariable(MethodModel mm,
            LocalVariableInfo lvi) {
        String [] params = null;
        String curType = null;
        String des = lvi.getVariableDescriptor();
        TypeSignature sig = new TypeSignature(des);
        int numParam = sig.getTypeParameters().size();

        // Skip the local variable without type parameters
        if (numParam == 0)
            return null;
        else if (numParam > 1) {
            if (!entryPoint.getCustomizedClassModels().hasClass(sig.getBaseType())) {
                throw new RuntimeException("Customized class model " + sig.getBaseType() +
                                           " is necessary for multiple type parameter classes.");
            }
            // Add method to type mapping. i.e. scala.Tuple2<_1, _2>
            curType = entryPoint.getCustomizedClassModels().getSample(sig.getBaseType())
                      .getMethod2ParamMapping();
            logger.fine("Map: " + curType);
        }

        for (Instruction inst = mm.getPCHead(); inst != null; inst = inst.getNextPC()) {
            // We're only interested in local variables
            if (!(inst instanceof AccessLocalVariable))
                continue;

            // We only focus on the specific local variable
            int curIdx = ((AccessLocalVariable) inst).getLocalVariableTableIndex();
            if (curIdx != lvi.getVariableIndex())
                continue;


        }
        return params;
    }

    public void writeConditionalBranch16(ConditionalBranch16 _branch16,
                                         boolean _invert) throws CodeGenException {

        if (_branch16 instanceof If) {
            final If iff = (If) _branch16;

            writeInstruction(iff.getLhs());
            write(_branch16.getOperator().getText(_invert));
            writeInstruction(iff.getRhs());
        } else if (_branch16 instanceof I_IFNULL) {
            final I_IFNULL iff = (I_IFNULL) _branch16;
            writeInstruction(iff.getFirstChild());

            if (_invert)
                write(" != NULL");
            else
                write(" == NULL");

        } else if (_branch16 instanceof I_IFNONNULL) {
            final I_IFNONNULL iff = (I_IFNONNULL) _branch16;
            writeInstruction(iff.getFirstChild());

            if (_invert)
                write(" == NULL");
            else
                write(" != NULL");
        } else if (_branch16 instanceof IfUnary) {
            final IfUnary branch16 = (IfUnary) _branch16;
            final Instruction comparison = branch16.getUnary();
            final ByteCode comparisonByteCode = comparison.getByteCode();
            final String comparisonOperator = _branch16.getOperator().getText(_invert);

            switch (comparisonByteCode) {
                case FCMPG:
                case DCMPG:
                case FCMPL:
                case DCMPL:
                    if (Config.verboseComparitor)
                        write("/* bytecode=" + comparisonByteCode.getName() + " invert=" + _invert +
                              "*/");
                    writeInstruction(comparison.getFirstChild());
                    write(comparisonOperator);
                    writeInstruction(comparison.getLastChild());
                    break;
                default:
                    if (Config.verboseComparitor)
                        write("/* default bytecode=" + comparisonByteCode.getName() + " invert=" +
                              _invert + "*/");
                    writeInstruction(comparison);
                    write(comparisonOperator);
                    write("0");
            }
        }
    }

    public void writeComposite(CompositeInstruction instruction) throws
        CodeGenException {
        if (instruction instanceof CompositeArbitraryScopeInstruction) {
            newLine();

            writeBlock(instruction.getFirstChild(), null);
        } else if (instruction instanceof CompositeIfInstruction) {
            newLine();
            write("if (");
            final Instruction blockStart = writeConditional(instruction.getBranchSet());

            write(")");
            writeBlock(blockStart, null);
        } else if (instruction instanceof CompositeIfElseInstruction) {
            newLine();
            write("if (");
            final Instruction blockStart = writeConditional(instruction.getBranchSet());
            write(")");
            Instruction elseGoto = blockStart;
            while (!(elseGoto.isBranch() && elseGoto.asBranch().isUnconditional()))
                elseGoto = elseGoto.getNextExpr();
            writeBlock(blockStart, elseGoto);
            write(" else ");
            writeBlock(elseGoto.getNextExpr(), null);
        } else if (instruction instanceof CompositeForSunInstruction) {
            newLine();
            write("for (");
            Instruction topBranch = instruction.getFirstChild();
            if (topBranch instanceof AssignToLocalVariable) {
                writeInstruction(topBranch);
                topBranch = topBranch.getNextExpr();
            } else {
                // The loop has no initialization. Copy the nearest statement.
                // 1. Find the loop variable
                Instruction tempInst = instruction.getLastChild().getPrevExpr();
                LocalVariableInfo loopVariableInfo = ((AssignToLocalVariable)
                                                      tempInst).getLocalVariableInfo();

                // 2. Find the nearest assignment.
                tempInst = instruction;
                while (tempInst != null) {
                    if (tempInst instanceof AssignToLocalVariable) {
                        LocalVariableInfo localVariableInfo = ((AssignToLocalVariable)
                                                               tempInst).getLocalVariableInfo();
                        if (localVariableInfo.getVariableIndex() ==
                                loopVariableInfo.getVariableIndex()) {
                            // Check if the statement RHS has other variables.
                            if (!ExpressionList.hasOtherVariables(localVariableInfo, tempInst)) {

                                // Write the start expression without variable declaration
                                final String varName = loopVariableInfo.getVariableName();
                                write(varName + " = ");
                                for (Instruction operand = tempInst.getFirstChild(); operand != null;
                                        operand = operand.getNextExpr())
                                    writeInstruction(operand);
                            } else {
                                if (logger.isLoggable(Level.FINEST))
                                    System.out.println("Find other local variables are referred by loop variable initialization");
                            }
                            break;
                        }
                    }
                    tempInst = tempInst.getPrevExpr();
                }
            }
            write("; ");
            final BranchSet branchSet = instruction.getBranchSet();
            final Instruction blockStart = writeConditional(branchSet);

            final Instruction lastGoto = instruction.getLastChild();

            if (branchSet.getFallThrough() == lastGoto) {
                // empty body no delta!
                write(";){}");
            } else {
                final Instruction delta = lastGoto.getPrevExpr();
                write("; ");
                if (!(delta instanceof CompositeInstruction)) {
                    writeInstruction(delta);
                    write(")");
                    writeBlock(blockStart, delta);
                } else {
                    write("){");
                    in();
                    writeSequence(blockStart, delta);

                    newLine();
                    writeSequence(delta, delta.getNextExpr());
                    out();
                    newLine();
                    write("}");

                }
            }

        } else if (instruction instanceof CompositeWhileInstruction) {
            newLine();
            write("while (");
            final BranchSet branchSet = instruction.getBranchSet();
            final Instruction blockStart = writeConditional(branchSet);
            write(")");
            final Instruction lastGoto = instruction.getLastChild();
            writeBlock(blockStart, lastGoto);

        } else if (instruction instanceof CompositeEmptyLoopInstruction) {
            newLine();
            write("for (");
            Instruction topBranch = instruction.getFirstChild();
            if (topBranch instanceof AssignToLocalVariable) {
                writeInstruction(topBranch);
                topBranch = topBranch.getNextExpr();
            }
            write("; ");
            writeConditional(instruction.getBranchSet());
            write(";){}");

        } else if (instruction instanceof CompositeForEclipseInstruction) {
            newLine();
            write("for (");
            Instruction topGoto = instruction.getFirstChild();
            if (topGoto instanceof AssignToLocalVariable) {
                writeInstruction(topGoto);
                topGoto = topGoto.getNextExpr();
            }
            write("; ");
            Instruction last = instruction.getLastChild();
            while (last.getPrevExpr().isBranch())
                last = last.getPrevExpr();
            writeConditional(instruction.getBranchSet(), true);
            write("; ");
            final Instruction delta = last.getPrevExpr();
            if (!(delta instanceof CompositeInstruction)) {
                writeInstruction(delta);
                write(")");
                writeBlock(topGoto.getNextExpr(), delta);
            } else {
                write("){");
                in();
                writeSequence(topGoto.getNextExpr(), delta);

                newLine();
                writeSequence(delta, delta.getNextExpr());
                out();
                newLine();
                write("}");

            }

        } else if (instruction instanceof CompositeDoWhileInstruction) {
            newLine();
            write("do");
            Instruction blockStart = instruction.getFirstChild();
            Instruction blockEnd = instruction.getLastChild();
            writeBlock(blockStart, blockEnd);
            write("while(");
            writeConditional(instruction.getBranchSet(), true);
            write(");");
            newLine();
        }
    }

    public void writeSequence(Instruction _first,
                              Instruction _last) throws CodeGenException {

        for (Instruction instruction = _first; instruction != _last;
                instruction = instruction.getNextExpr()) {
            if (instruction instanceof CompositeInstruction)
                writeComposite((CompositeInstruction) instruction);
            else if (!instruction.getByteCode().equals(ByteCode.NONE)) {
                newLine();
                writeInstruction(instruction);
                write(";");
            }
        }
    }

    protected void writeGetterBlock(FieldEntry accessorVariableFieldEntry) {
        write("{");
        in();
        newLine();
        write("return this->");
        write(accessorVariableFieldEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8());
        write(";");
        out();
        newLine();

        write("}");
    }

    public void writeBlock(Instruction _first,
                           Instruction _last) throws CodeGenException {
        write("{");
        in();
        writeSequence(_first, _last);
        out();
        newLine();

        write("}");
    }

    public Instruction writeConditional(BranchSet _branchSet) throws
        CodeGenException {
        return (writeConditional(_branchSet, false));
    }

    public Instruction writeConditional(BranchSet _branchSet,
                                        boolean _invert) throws CodeGenException {

        final LogicalExpressionNode logicalExpression =
            _branchSet.getLogicalExpression();
        write(_invert ? logicalExpression : logicalExpression.cloneInverted());
        return (_branchSet.getLast().getNextExpr());
    }

    public void writeNewFixedSizeArray(Instruction _instruction) throws
        CodeGenException {

        // Get type from newarray (this instruction)
        int typeCode = ((I_NEWARRAY) _instruction).getType();
        String typeName = null;
        switch (typeCode) {
            case 5:
                typeName = "char";
                break;
            case 6:
                typeName = "float";
                break;
            case 7:
                typeName = "double";
                break;
            case 8: // Byte
                typeName = "char";
                break;
            case 9:
                typeName = "short";
                break;
            case 10:
                typeName = "int";
                break;
            case 11:
                typeName = "long";
                break;
            default:
                typeName = "/* Unsupported type " + typeCode + "*/";
        }

        // Get variable name from astore (parent instruction)
        Instruction parent = _instruction.getParentExpr();
        assert(parent instanceof LocalVariableTableIndexAccessor);
        LocalVariableTableIndexAccessor var = (LocalVariableTableIndexAccessor) parent;
        String varName = var.getLocalVariableInfo().getVariableName();

        // Get array length from i_const/push (child instruction)
        Instruction child = _instruction.getFirstChild();
        int length = 0;
        if (child instanceof BytecodeEncodedConstant) {
            @SuppressWarnings("unchecked")
            BytecodeEncodedConstant<Integer> inst = (BytecodeEncodedConstant<Integer>)
                                                    child;
            length = inst.getValue();
        } else if (child instanceof ImmediateConstant) {
            @SuppressWarnings("unchecked")
            ImmediateConstant<Integer> inst = (ImmediateConstant<Integer>) child;
            length = inst.getValue();
        }

        deleteCurrentLine();
        write(typeName + " " + varName + "[" + length + "]");
        return ;
    }

    public void write(LogicalExpressionNode _node) throws CodeGenException {
        if (_node instanceof SimpleLogicalExpressionNode) {
            final SimpleLogicalExpressionNode sn = (SimpleLogicalExpressionNode) _node;

            writeConditionalBranch16((ConditionalBranch16) sn.getBranch(), sn.isInvert());
        } else {
            final CompoundLogicalExpressionNode ln = (CompoundLogicalExpressionNode) _node;
            boolean needParenthesis = false;
            final CompoundLogicalExpressionNode parent = (CompoundLogicalExpressionNode)
                    ln.getParent();
            if (parent != null) {
                if (!ln.isAnd() && parent.isAnd())
                    needParenthesis = true;
            }
            if (needParenthesis)

                write("(");
            write(ln.getLhs());
            write(ln.isAnd() ? " && " : " || ");
            write(ln.getRhs());
            if (needParenthesis)

                write(")");
        }
    }

    public String convertType(String _typeDesc, boolean useClassModel) {
        return (_typeDesc);
    }

    public String convertCast(String _cast) {
        // Strip parens off cast
        //System.out.println("cast = " + _cast);
        final String raw = convertType(_cast.substring(1, _cast.length() - 1), false);
        return ("(" + raw + ")");
    }

    public boolean writeInstruction(Instruction _instruction) throws
        CodeGenException {
        boolean writeCheck = false;
//			write("/*" + _instruction.toString() + "*/");

        if (_instruction.getByteCode().equals(ByteCode.NONE)) {
            // do nothing
        } else if (_instruction instanceof CompositeIfElseInstruction) {
            write("(");
            final Instruction lhs = writeConditional(((CompositeInstruction)
                                    _instruction).getBranchSet());
            write(")?");
            writeInstruction(lhs);
            write(":");
            writeInstruction(lhs.getNextExpr().getNextExpr());
        } else if (_instruction instanceof CompositeInstruction) {
//					write("/* composite */");
            writeComposite((CompositeInstruction) _instruction);

        } else if (_instruction instanceof AssignToLocalVariable) {
//				 write("/* assign to local var */");
            final AssignToLocalVariable assignToLocalVariable = (AssignToLocalVariable)
                    _instruction;

            final LocalVariableInfo localVariableInfo =
                assignToLocalVariable.getLocalVariableInfo();
            String varName = null;

            // FIXME: This problem now only happens on loop iterator
            // so it must be an integer. In general, we should evaluate
            // the type of RHS expression to decide the right type,
            // or even remove this statement.
            if (localVariableInfo == null) {
                //throw new CodeGenException("outOfScope" + _instruction.getThisPC() + " = ");
                varName = "j2faDummyVar" + assignToLocalVariable.getLocalVariableTableIndex();

                // It should be assigned only once
                write("int ");
            } else
                varName = localVariableInfo.getVariableName();


            if (assignToLocalVariable.isDeclaration()) {
                final String descriptor = localVariableInfo.getVariableDescriptor();
                boolean isArray = false;
                boolean isObject = false;

                String localType = convertType(descriptor, true);
                if (descriptor.startsWith("["))
                    isArray = true;
                if (descriptor.startsWith("L") || (isArray && descriptor.charAt(1) == 'L'))
                    isObject = true;

                if (isObject)
                    localType = localType.replace('.', '_');

                write(localType);

                if (isObject && !isArray)
                    write("*");

                write(varName + " = ");
                for (Instruction operand = _instruction.getFirstChild(); operand != null;
                        operand = operand.getNextExpr())
                    writeInstruction(operand);
            } else {
                write(varName + " = ");
                for (Instruction operand = _instruction.getFirstChild(); operand != null;
                        operand = operand.getNextExpr())
                    writeInstruction(operand);
            }
        } else if (_instruction instanceof AssignToArrayElement) {
//					write("/* assign to array ele */");
            final AssignToArrayElement arrayAssignmentInstruction =
                (AssignToArrayElement) _instruction;
            writeInstruction(arrayAssignmentInstruction.getArrayRef());
            write("[");
            writeInstruction(arrayAssignmentInstruction.getArrayIndex());
            write("]");
            write(" ");
            write(" = ");
            writeInstruction(arrayAssignmentInstruction.getValue());
        } else if (_instruction instanceof AccessArrayElement) {
//					write("/* access array ele */");
            //we're getting an element from an array
            //if the array is a primitive then we just return the value
            //so the generated code looks like
            //arrayName[arrayIndex];
            //but if the array is an object, or multidimensional array, then we want to return
            //a pointer to our index our position in the array.  The code will look like
            //&(arrayName[arrayIndex * this->arrayNameLen_dimension]
            //
            final AccessArrayElement arrayLoadInstruction = (AccessArrayElement)
                    _instruction;

            //object array, get address
            boolean isMultiDimensional = arrayLoadInstruction instanceof I_AALOAD &&
                                         isMultiDimensionalArray(arrayLoadInstruction);
            if (isMultiDimensional)
                write("(&");
            writeInstruction(arrayLoadInstruction.getArrayRef());
            write("[");
            writeInstruction(arrayLoadInstruction.getArrayIndex());

            //object array, find the size of each object in the array
            //for 2D arrays, this size is the size of a row.
            if (isMultiDimensional) {
                int dim = 0;
                Instruction load = arrayLoadInstruction.getArrayRef();
                while (load instanceof I_AALOAD) {
                    load = load.getFirstChild();
                    dim++;
                }

                NameAndTypeEntry nameAndTypeEntry = ((AccessInstanceField)
                                                     load).getConstantPoolFieldEntry().getNameAndTypeEntry();
                if (isMultiDimensionalArray(nameAndTypeEntry)) {
                    String arrayName = nameAndTypeEntry.getNameUTF8Entry().getUTF8();
                    write(" * " + arrayName + arrayDimMangleSuffix + dim);
                }
            }

            write("]");

            //object array, close parentheses
            if (isMultiDimensional)
                write(")");
        } else if (_instruction instanceof AccessField) {
//			write("/* access field */");
            final AccessField accessField = (AccessField) _instruction;
            if (accessField instanceof AccessInstanceField) {
                Instruction accessInstanceField = ((AccessInstanceField)
                                                   accessField).getInstance();
                if (accessInstanceField instanceof CloneInstruction)
                    accessInstanceField = ((CloneInstruction) accessInstanceField).getReal();
                if (!(accessInstanceField instanceof I_ALOAD_0)) {
                    writeInstruction(accessInstanceField);
                    write(".");
                }
                else {
                    String type =
                        accessField.getConstantPoolFieldEntry().getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8();
                }
            }
            write(accessField.getConstantPoolFieldEntry().getNameAndTypeEntry().getNameUTF8Entry().getUTF8());

        } else if (_instruction instanceof I_ARRAYLENGTH) {
//					write("/* array len */");
            //getting the length of an array.
            //if this is a primitive array, then this is trivial
            //if we're getting an object array, then we need to find what dimension
            //we're looking at
            int dim = 0;
            boolean aryInObj = false;
            Instruction load = _instruction.getFirstChild();

            // Issue #33: Sometimes we may have invokevirtual or checkcast after getField.
            // Example: obj.getMyArray().length();
            while (!(load instanceof AccessInstanceField)) {
                if (load instanceof I_AALOAD)
                    dim++;
                else if (load instanceof MethodCall)
                    aryInObj = true;
                load = load.getFirstChild();
            }
            NameAndTypeEntry nameAndTypeEntry = ((AccessInstanceField)
                                                 load).getConstantPoolFieldEntry().getNameAndTypeEntry();
            final String arrayName = nameAndTypeEntry.getNameUTF8Entry().getUTF8();
            String dimSuffix = isMultiDimensionalArray(nameAndTypeEntry) ? Integer.toString(
                                   dim) : "";

            write(arrayName); // Variable name

            if (!aryInObj) // Array field, get length directly
                write(arrayLengthMangleSuffix + dimSuffix);
            else { // Array within an object field, get length from the object
                Instruction methodCall = _instruction.getFirstChild();

                // Find method name
                while (!(methodCall instanceof MethodCall))
                    methodCall = methodCall.getFirstChild();
                String methodName = ((MethodCall) methodCall).getConstantPoolMethodEntry()
                                    .getNameAndTypeEntry().getNameUTF8Entry().getUTF8();

                // Write array length: obj->methodName__javaArrayLength
                write("->" + methodName + arrayLengthMangleSuffix + dimSuffix);
            }

        } else if (_instruction instanceof AssignToField) {
//					write("/* assign to field */");
            final AssignToField assignedField = (AssignToField) _instruction;

            if (assignedField instanceof AssignToInstanceField) {
                final Instruction accessInstanceField = ((AssignToInstanceField)
                                                        assignedField).getInstance().getReal();

                if (!(accessInstanceField instanceof I_ALOAD_0)) {
                    writeInstruction(accessInstanceField);
                    write(".");
                } else
                    write("this->");
            }
            write(assignedField.getConstantPoolFieldEntry().getNameAndTypeEntry().getNameUTF8Entry().getUTF8());
            write("=");
            writeInstruction(assignedField.getValueToAssign());
        } else if (_instruction instanceof Constant<?>) {
//					write("/* const */");
            final Constant<?> constantInstruction = (Constant<?>) _instruction;
            final Object value = constantInstruction.getValue();

            if (value instanceof Float) {

                final Float f = (Float) value;
                if (f.isNaN())
                    write("NAN");
                else if (f.isInfinite()) {
                    if (f < 0)
                        write("-");
                    write("INFINITY");
                } else {
                    write(value.toString());
                    write("f");
                }
            } else if (value instanceof Double) {

                final Double d = (Double) value;
                if (d.isNaN())
                    write("NAN");
                else if (d.isInfinite()) {
                    if (d < 0)
                        write("-");
                    write("INFINITY");
                } else
                    write(value.toString());
            } else {
                write(value.toString());
                if (value instanceof Long)
                    write("L");
            }

        } else if (_instruction instanceof AccessLocalVariable) {
//				write("/* access local var */");
            final AccessLocalVariable localVariableLoadInstruction =
                (AccessLocalVariable) _instruction;
            final LocalVariableInfo localVariable =
                localVariableLoadInstruction.getLocalVariableInfo();
            String varName = null;
            if (localVariable == null)
                varName = "j2faDummyVar" +
                          localVariableLoadInstruction.getLocalVariableTableIndex();
            else
                varName = localVariable.getVariableName();
            write(varName);
        } else if (_instruction instanceof I_IINC) {
//				write("/* iinc */");
            final I_IINC location = (I_IINC) _instruction;
            final LocalVariableInfo localVariable = location.getLocalVariableInfo();
            final int adjust = location.getAdjust();

            write(localVariable.getVariableName());
            if (adjust == 1)
                write("++");
            else if (adjust == -1)
                write("--");
            else if (adjust > 1)
                write("+=" + adjust);
            else if (adjust < -1)
                write("-=" + (-adjust));
        } else if (_instruction instanceof BinaryOperator) {
//					write("/* binary */");
            final BinaryOperator binaryInstruction = (BinaryOperator) _instruction;
            final Instruction parent = binaryInstruction.getParentExpr();
            boolean needsParenthesis = true;

            if (parent instanceof AssignToLocalVariable)
                needsParenthesis = false;
            else if (parent instanceof AssignToField)
                needsParenthesis = false;
            else if (parent instanceof AssignToArrayElement)
                needsParenthesis = false;
            else {
                /**
                            if (parent instanceof BinaryOperator) {
                               BinaryOperator parentBinaryOperator = (BinaryOperator) parent;
                               if (parentBinaryOperator.getOperator().ordinal() > binaryInstruction.getOperator().ordinal()) {
                                  needsParenthesis = false;
                               }
                            }
                **/
            }

            if (needsParenthesis)
                write("(");

            writeInstruction(binaryInstruction.getLhs());

            write(" " + binaryInstruction.getOperator().getText() + " ");
            writeInstruction(binaryInstruction.getRhs());

            if (needsParenthesis)
                write(")");

        } else if (_instruction instanceof CastOperator) {
//					write("/* cast */");
            final CastOperator castInstruction = (CastOperator) _instruction;
            write(convertCast(castInstruction.getOperator().getText()));

            writeInstruction(castInstruction.getUnary());
        } else if (_instruction instanceof UnaryOperator) {
//					write("/* unary */");
            final UnaryOperator unaryInstruction = (UnaryOperator) _instruction;
            write(unaryInstruction.getOperator().getText());

            writeInstruction(unaryInstruction.getUnary());
        } else if (_instruction instanceof Return)
            writeReturn((Return) _instruction);
        else if (_instruction instanceof MethodCall) {
//			write("/* methodcall */");
            final MethodCall methodCall = (MethodCall) _instruction;
            final MethodEntry methodEntry = methodCall.getConstantPoolMethodEntry();
            final String clazzName = methodEntry.toString().substring(0,
                                     methodEntry.toString().indexOf("."));

            writeCheck = writeMethod(methodCall, methodEntry);
        } else if (_instruction instanceof I_INVOKEINTERFACE) {
            // TODO: Method dispatcher
            final I_INVOKEINTERFACE interfaceMethodCall = (I_INVOKEINTERFACE) _instruction;
            final InterfaceMethodEntry methodEntry =
                interfaceMethodCall.getConstantPoolInterfaceMethodEntry();

            writeMethod(interfaceMethodCall, methodEntry);
        } else if (_instruction.getByteCode().equals(ByteCode.CLONE)) {
//					write("/* clone */");
            final CloneInstruction cloneInstruction = (CloneInstruction) _instruction;
            writeInstruction(cloneInstruction.getReal());
        } else if (_instruction.getByteCode().equals(ByteCode.INCREMENT)) {
//					write("/* inc */");
            final IncrementInstruction incrementInstruction = (IncrementInstruction)
                    _instruction;

            if (incrementInstruction.isPre()) {
                if (incrementInstruction.isInc())
                    write("++");
                else
                    write("--");
            }

            writeInstruction(incrementInstruction.getFieldOrVariableReference());
            if (!incrementInstruction.isPre()) {
                if (incrementInstruction.isInc())
                    write("++");
                else
                    write("--");
            }
        } else if (_instruction.getByteCode().equals(ByteCode.MULTI_ASSIGN)) {
//					write("/* multi assign */");
            final MultiAssignInstruction multiAssignInstruction = (MultiAssignInstruction)
                    _instruction;
            AssignToLocalVariable from = (AssignToLocalVariable)
                                         multiAssignInstruction.getFrom();
            final AssignToLocalVariable last = (AssignToLocalVariable)
                                               multiAssignInstruction.getTo();
            final Instruction common = multiAssignInstruction.getCommon();
            final Stack<AssignToLocalVariable> stack = new Stack<AssignToLocalVariable>();

            while (from != last) {
                stack.push(from);
                from = (AssignToLocalVariable) ((Instruction) from).getNextExpr();
            }

            for (AssignToLocalVariable alv = stack.pop(); alv != null;
                    alv = stack.size() > 0 ? stack.pop() : null) {

                final LocalVariableInfo localVariableInfo = alv.getLocalVariableInfo();
                if (alv.isDeclaration())
                    write(convertType(localVariableInfo.getVariableDescriptor(), true));
                if (localVariableInfo == null)
                    throw new CodeGenException("outOfScope" + _instruction.getThisPC() + " = ");
                else
                    write(localVariableInfo.getVariableName() + " = ");

            }
            writeInstruction(common);
        } else if (_instruction.getByteCode().equals(ByteCode.INLINE_ASSIGN)) {
//					write("/* inline assign */");
            final InlineAssignInstruction inlineAssignInstruction =
                (InlineAssignInstruction) _instruction;
            final AssignToLocalVariable assignToLocalVariable =
                inlineAssignInstruction.getAssignToLocalVariable();

            final LocalVariableInfo localVariableInfo =
                assignToLocalVariable.getLocalVariableInfo();
            if (assignToLocalVariable.isDeclaration()) {
                // this is bad! we need a general way to hoist up a required declaration
                throw new CodeGenException("/* we can't declare this " + convertType(
                                               localVariableInfo.getVariableDescriptor(), true)
                                           + " here */");
            }
            write(localVariableInfo.getVariableName());
            write("=");
            writeInstruction(inlineAssignInstruction.getRhs());
        } else if (_instruction.getByteCode().equals(
                       ByteCode.FIELD_ARRAY_ELEMENT_ASSIGN)) {
//					write("/* field array ele assign */");
            final FieldArrayElementAssign inlineAssignInstruction =
                (FieldArrayElementAssign) _instruction;
            final AssignToArrayElement arrayAssignmentInstruction =
                inlineAssignInstruction.getAssignToArrayElement();

            writeInstruction(arrayAssignmentInstruction.getArrayRef());
            write("[");
            writeInstruction(arrayAssignmentInstruction.getArrayIndex());
            write("]");
            write(" ");
            write(" = ");

            writeInstruction(inlineAssignInstruction.getRhs());
        } else if (_instruction.getByteCode().equals(
                       ByteCode.FIELD_ARRAY_ELEMENT_INCREMENT)) {
//				 write("/* field array ele inc */");

            final FieldArrayElementIncrement fieldArrayElementIncrement =
                (FieldArrayElementIncrement)
                _instruction;
            final AssignToArrayElement arrayAssignmentInstruction =
                fieldArrayElementIncrement.getAssignToArrayElement();
            if (fieldArrayElementIncrement.isPre()) {
                if (fieldArrayElementIncrement.isInc())
                    write("++");
                else
                    write("--");
            }
            writeInstruction(arrayAssignmentInstruction.getArrayRef());

            write("[");
            writeInstruction(arrayAssignmentInstruction.getArrayIndex());
            write("]");
            if (!fieldArrayElementIncrement.isPre()) {
                if (fieldArrayElementIncrement.isInc())
                    write("++");
                else
                    write("--");
            }
        } else if (_instruction instanceof Branch) {
            throw new CodeGenException(String.format("%s -> %04d",
                                       _instruction.getByteCode().toString().toLowerCase(),
                                       ((Branch) _instruction).getTarget().getThisPC()));
        } else if (_instruction instanceof I_POP) {
            //POP discarded void call return?
            writeInstruction(_instruction.getFirstChild());
        } else if (_instruction instanceof ConstructorCall) {
//				write("/* constructor */");
            final ConstructorCall call = (ConstructorCall)_instruction;
            writeConstructorCall(call);
        } else if (_instruction instanceof I_CHECKCAST) {
//				write("/* checkcast */");
            // Do nothing
            I_CHECKCAST checkCast = (I_CHECKCAST)_instruction;
            writeInstruction(checkCast.getPrevPC());
        } else if (_instruction instanceof I_NEWARRAY)
            writeNewFixedSizeArray(_instruction);
        else if (_instruction instanceof New) {
            // Skip it?
        } else {
            System.err.println(_instruction.toString());
            throw new CodeGenException(String.format("%s",
                                       _instruction.getByteCode().toString().toLowerCase()));
        }

        return writeCheck;
    }

    public abstract void writeConstructorCall(ConstructorCall call) throws
        CodeGenException;
    public abstract void writeReturn(Return ret) throws CodeGenException;

    private boolean isMultiDimensionalArray(NameAndTypeEntry nameAndTypeEntry) {
        return nameAndTypeEntry.getDescriptorUTF8Entry().getUTF8().startsWith("[[");
    }

    private boolean isObjectArray(NameAndTypeEntry nameAndTypeEntry) {
        return nameAndTypeEntry.getDescriptorUTF8Entry().getUTF8().startsWith("[L");
    }

    private boolean isMultiDimensionalArray(final AccessArrayElement
                                            arrayLoadInstruction) {
        AccessInstanceField accessInstanceField = getUltimateInstanceFieldAccess(
                    arrayLoadInstruction);
        return isMultiDimensionalArray(
                   accessInstanceField.getConstantPoolFieldEntry().getNameAndTypeEntry());
    }

    private boolean isObjectArray(final AccessArrayElement arrayLoadInstruction) {
        AccessInstanceField accessInstanceField = getUltimateInstanceFieldAccess(
                    arrayLoadInstruction);
        return isObjectArray(
                   accessInstanceField.getConstantPoolFieldEntry().getNameAndTypeEntry());
    }

    private AccessInstanceField getUltimateInstanceFieldAccess(
        final AccessArrayElement
        arrayLoadInstruction) {
        Instruction load = arrayLoadInstruction.getArrayRef();
        while (load instanceof I_AALOAD)
            load = load.getFirstChild();

        return (AccessInstanceField) load;
    }

    public boolean writeMethod(I_INVOKEINTERFACE _methodCall,
                               InterfaceMethodEntry _methodEntry) throws CodeGenException {
        final Instruction instanceInstruction = _methodCall.getInstanceReference();
        final String clazzName =
            _methodEntry.getClassEntry().getNameUTF8Entry().getUTF8();
        final String methodName =
            _methodEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();

        final int argc = _methodEntry.getStackConsumeCount();
        write(clazzName.replace("/", "__") + "__" + methodName);
        write("(");

        if (instanceInstruction instanceof I_ALOAD_0)
            write("this");
        else
            writeInstruction(instanceInstruction);

        for (int arg = 0; arg < argc; arg++) {
            write(", ");
            writeInstruction(_methodCall.getArg(arg));
        }
        write(")");

        return false;
    }

    public boolean writeMethod(MethodCall _methodCall,
                               MethodEntry _methodEntry) throws CodeGenException {
        boolean noCL = _methodEntry.getOwnerClassModel().getNoCLMethods()
                       .contains(_methodEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8());
        if (noCL)
            return false;

        if (_methodCall instanceof VirtualMethodCall) {
            final Instruction instanceInstruction = ((VirtualMethodCall)
                                                    _methodCall).getInstanceReference();
            if (!(instanceInstruction instanceof I_ALOAD_0)) {
                writeInstruction(instanceInstruction);
                write(".");
            } else
                write("this->");
        }
        final int argc = _methodEntry.getStackConsumeCount();
        write(_methodEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8());
        write("(");

        for (int arg = 0; arg < argc; arg++) {
            if (arg != 0)
                write(", ");
            writeInstruction(_methodCall.getArg(arg));
        }
        write(")");

        return false;
    }

    public void writeMethodBody(MethodModel _methodModel) throws CodeGenException {
        if (_methodModel.getMethodType() == METHODTYPE.GETTER &&
                !_methodModel.isNoCL()) {
            FieldEntry accessorVariableFieldEntry =
                _methodModel.getAccessorVariableFieldEntry();
            writeGetterBlock(accessorVariableFieldEntry);
        } else
            writeBlock(_methodModel.getExprHead(), null);
    }

    public abstract void write(String kernelName, Entrypoint entryPoint,
                               Collection<JParameter> params) throws CodeGenException;
}
