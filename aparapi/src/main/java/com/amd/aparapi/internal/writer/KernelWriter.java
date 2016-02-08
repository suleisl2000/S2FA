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
import com.amd.aparapi.internal.instruction.InstructionSet.*;
import com.amd.aparapi.internal.model.*;
import com.amd.aparapi.internal.model.ClassModel.AttributePool.*;
import com.amd.aparapi.internal.model.ClassModel.AttributePool.RuntimeAnnotationsEntry.*;
import com.amd.aparapi.internal.model.ClassModel.*;
import com.amd.aparapi.internal.model.HardCodedClassModel.TypeParameters;
import com.amd.aparapi.internal.model.HardCodedMethodModel.METHODTYPE;
import com.amd.aparapi.internal.model.ClassModel.ConstantPool.*;
import com.amd.aparapi.internal.model.FullMethodSignature;
import com.amd.aparapi.internal.model.FullMethodSignature.TypeSignature;

import java.util.*;

public abstract class KernelWriter extends BlockWriter {

	private final String cvtBooleanToChar = "char ";

	private final String cvtBooleanArrayToCharStar = "char* ";

	private final String cvtByteToChar = "char ";

	private final String cvtByteArrayToCharStar = "char* ";

	private final String cvtCharToShort = "unsigned short ";

	private final String cvtCharArrayToShortStar = "unsigned short* ";

	private final String cvtIntArrayToIntStar = "int* ";

	private final String cvtFloatArrayToFloatStar = "float* ";

	private final String cvtDoubleArrayToDoubleStar = "double* ";

	private final String cvtLongArrayToLongStar = "long* ";

	private final String cvtShortArrayToShortStar = "short* ";

	// private static Logger logger = Logger.getLogger(Config.getLoggerName());

	private boolean processingConstructor = false;

	private boolean isArrayTypeOutput = false;

	private int countAllocs = 0;

	private String currentReturnType = null;

	private Set<MethodModel> mayFailHeapAllocation = null;

	public final static Set<String> scalaMapped = new HashSet<String>();
	{
		scalaMapped.add("scala/math/package$.sqrt(D)D");
		scalaMapped.add("scala/math/package$.pow(D)D");
		scalaMapped.add("scala/math/package$.exp(D)D");
		scalaMapped.add("scala/math/package$.log(D)D");
		scalaMapped.add("scala/math/package$.log(D)D");
		scalaMapped.add("scala/math/package$.abs(D)D");
		scalaMapped.add("scala/math/package$.abs(I)I");
		scalaMapped.add("scala/math/package$.abs(F)F");
	}

	public final static Set<String> SelfMapped = new HashSet<String>();
	{
		SelfMapped.add("scala/math/package$.random()D");
	}

	public final static Map<String, String[]> XilinxMethodMap = new HashMap<String, String[]>();
	{
		XilinxMethodMap.put("sqrt", new String[] {"float"});
		XilinxMethodMap.put("pow", new String[] {"float", "float"});
		XilinxMethodMap.put("exp", new String[] {"float"});
		XilinxMethodMap.put("log", new String[] {"float"});
		XilinxMethodMap.put("abs", new String[] {"int"});
		XilinxMethodMap.put("fabs", new String[] {"float"});
	}

	public final static Map<String, String> javaToCLIdentifierMap = new HashMap<String, String>();
	{
		javaToCLIdentifierMap.put("getGlobalId()I", "get_global_id(0)");
		javaToCLIdentifierMap.put("getGlobalId(I)I",
		                          "get_global_id"); // no parenthesis if we are conveying args
		javaToCLIdentifierMap.put("getGlobalX()I", "get_global_id(0)");
		javaToCLIdentifierMap.put("getGlobalY()I", "get_global_id(1)");
		javaToCLIdentifierMap.put("getGlobalZ()I", "get_global_id(2)");

		javaToCLIdentifierMap.put("getGlobalSize()I", "get_global_size(0)");
		javaToCLIdentifierMap.put("getGlobalSize(I)I",
		                          "get_global_size"); // no parenthesis if we are conveying args
		javaToCLIdentifierMap.put("getGlobalWidth()I", "get_global_size(0)");
		javaToCLIdentifierMap.put("getGlobalHeight()I", "get_global_size(1)");
		javaToCLIdentifierMap.put("getGlobalDepth()I", "get_global_size(2)");

		javaToCLIdentifierMap.put("getLocalId()I", "get_local_id(0)");
		javaToCLIdentifierMap.put("getLocalId(I)I",
		                          "get_local_id"); // no parenthesis if we are conveying args
		javaToCLIdentifierMap.put("getLocalX()I", "get_local_id(0)");
		javaToCLIdentifierMap.put("getLocalY()I", "get_local_id(1)");
		javaToCLIdentifierMap.put("getLocalZ()I", "get_local_id(2)");

		javaToCLIdentifierMap.put("getLocalSize()I", "get_local_size(0)");
		javaToCLIdentifierMap.put("getLocalSize(I)I",
		                          "get_local_size"); // no parenthesis if we are conveying args
		javaToCLIdentifierMap.put("getLocalWidth()I", "get_local_size(0)");
		javaToCLIdentifierMap.put("getLocalHeight()I", "get_local_size(1)");
		javaToCLIdentifierMap.put("getLocalDepth()I", "get_local_size(2)");

		javaToCLIdentifierMap.put("getNumGroups()I", "get_num_groups(0)");
		javaToCLIdentifierMap.put("getNumGroups(I)I",
		                          "get_num_groups"); // no parenthesis if we are conveying args
		javaToCLIdentifierMap.put("getNumGroupsX()I", "get_num_groups(0)");
		javaToCLIdentifierMap.put("getNumGroupsY()I", "get_num_groups(1)");
		javaToCLIdentifierMap.put("getNumGroupsZ()I", "get_num_groups(2)");

		javaToCLIdentifierMap.put("getGroupId()I", "get_group_id(0)");
		javaToCLIdentifierMap.put("getGroupId(I)I",
		                          "get_group_id"); // no parenthesis if we are conveying args
		javaToCLIdentifierMap.put("getGroupX()I", "get_group_id(0)");
		javaToCLIdentifierMap.put("getGroupY()I", "get_group_id(1)");
		javaToCLIdentifierMap.put("getGroupZ()I", "get_group_id(2)");

		javaToCLIdentifierMap.put("getPassId()I", "get_pass_id(this)");

		javaToCLIdentifierMap.put("localBarrier()V", "barrier(CLK_LOCAL_MEM_FENCE)");

		javaToCLIdentifierMap.put("globalBarrier()V", "barrier(CLK_GLOBAL_MEM_FENCE)");
	}

	/**
	 * These three convert functions are here to perform
	 * any type conversion that may be required between
	 * Java and OpenCL.
	 *
	 * @param _typeDesc
	 *          String in the Java JNI notation, [I, etc
	 * @return Suitably converted string, "char*", etc
	 */
	@Override public String convertType(String _typeDesc, boolean useClassModel) {
		if (_typeDesc.equals("Z") || _typeDesc.equals("boolean"))
			return (cvtBooleanToChar);
		else if (_typeDesc.equals("[Z") || _typeDesc.equals("boolean[]"))
			return (cvtBooleanArrayToCharStar);
		else if (_typeDesc.equals("B") || _typeDesc.equals("byte"))
			return (cvtByteToChar);
		else if (_typeDesc.equals("[B") || _typeDesc.equals("byte[]"))
			return (cvtByteArrayToCharStar);
		else if (_typeDesc.equals("C") || _typeDesc.equals("char"))
			return (cvtCharToShort);
		else if (_typeDesc.equals("[C") || _typeDesc.equals("char[]"))
			return (cvtCharArrayToShortStar);
		else if (_typeDesc.equals("[I") || _typeDesc.equals("int[]"))
			return (cvtIntArrayToIntStar);
		else if (_typeDesc.equals("[F") || _typeDesc.equals("float[]"))
			return (cvtFloatArrayToFloatStar);
		else if (_typeDesc.equals("[D") || _typeDesc.equals("double[]"))
			return (cvtDoubleArrayToDoubleStar);
		else if (_typeDesc.equals("[J") || _typeDesc.equals("long[]"))
			return (cvtLongArrayToLongStar);
		else if (_typeDesc.equals("[S") || _typeDesc.equals("short[]"))
			return (cvtShortArrayToShortStar);
		// if we get this far, we haven't matched anything yet
		if (useClassModel)
			return (ClassModel.convert(_typeDesc, "", true));
		else
			return _typeDesc;
	}

	@Override public void writeReturn(Return ret) throws CodeGenException {
		if (isArrayTypeOutput)
			writeInstruction(ret.getFirstChild());
		else {
			write("return");
			if (processingConstructor)
				write(" (this)");
			else if (ret.getStackConsumeCount() > 0) {
				write("(");
				writeInstruction(ret.getFirstChild());
				write(")");
			}
		}
	}

	private String doIndent(String str) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < indent; i++)
			builder.append("   ");
		builder.append(str);
		return builder.toString();
	}

	@Override public String getAllocCheck() {
		assert(currentReturnType != null);
		final String nullReturn;
		if (currentReturnType.startsWith("L") || currentReturnType.startsWith("["))
			nullReturn = "0x0";
		else if (currentReturnType.equals("I") || currentReturnType.equals("L") ||
		         currentReturnType.equals("F") || currentReturnType.equals("D"))
			nullReturn = "0";
		else
			throw new RuntimeException("Unsupported type descriptor " + currentReturnType);

		String checkStr = "if (this->alloc_failed) { return (" + nullReturn + "); }";
		String indentedCheckStr = doIndent(checkStr);

		return indentedCheckStr;
	}

	@Override public void writeConstructorCall(ConstructorCall call) throws CodeGenException {
		I_INVOKESPECIAL invokeSpecial = call.getInvokeSpecial();

		MethodEntry constructorEntry = invokeSpecial.getConstantPoolMethodEntry();
		final String constructorName =
		  constructorEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
		final String constructorSignature =
		  constructorEntry.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8();

		MethodModel m = entryPoint.getCallTarget(constructorEntry, true);
		if (m == null) {
			throw new RuntimeException("Unable to find constructor for name=" +
			                           constructorEntry + " sig=" + constructorSignature);
		}

		write(m.getName());
		write("(");

		String typeName = m.getOwnerClassMangledName();
		String allocVarName = "__alloc" + (countAllocs++);
		String allocStr = __global + typeName + " * " + allocVarName +
		                  " = (" + __global + typeName + 
											" *)alloc(this->heap, this->free_index, this->heap_size, " +
		                  "sizeof(" + typeName + "), &this->alloc_failed);";
		String indentedAllocStr = doIndent(allocStr);

		String indentedCheckStr = getAllocCheck();

		StringBuilder allocLine = new StringBuilder();
		allocLine.append(indentedAllocStr);
		allocLine.append("\n");
		allocLine.append(indentedCheckStr);
		writeBeforeCurrentLine(allocLine.toString());

		write(allocVarName);

		for (int i = 0; i < constructorEntry.getStackConsumeCount(); i++) {
			write(", ");
			writeInstruction(invokeSpecial.getArg(i));
		}

		write(")");
	}

	@Override public boolean writeMethod(MethodCall _methodCall,
	                                     MethodEntry _methodEntry) throws CodeGenException {
		final int argc = _methodEntry.getStackConsumeCount();
		final String methodName =
		  _methodEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
		final String methodSignature =
		  _methodEntry.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8();
		final String methodClass =
		  _methodEntry.getClassEntry().getNameUTF8Entry().getUTF8();

		// comaniac: Issue #1, the struct used for kernel argument cannot have pointer type.
		// Since we enforced scalar type of Tuple2 struct (elimiate *), here we have to send
		// the reference of "Tuple2 struct" with "L" to the method by adding "&()".

		if (methodName.equals("<init>") && !_methodEntry.toString().equals("java/lang/Object.<init>()V")) {
			writeConstructorCall(new ConstructorCall(((Instruction)_methodCall).getMethod(),
			                     (I_INVOKESPECIAL)_methodCall, null));
			return false;
		}

		if (methodClass.equals("scala/runtime/BoxesRunTime")) {
			final Set<String> ignorableMethods = new HashSet<String>();
			ignorableMethods.add("boxToInteger");
			ignorableMethods.add("boxToFloat");
			ignorableMethods.add("unboxToFloat");
			ignorableMethods.add("unboxToInt");
			ignorableMethods.add("unboxToDouble");

			if (ignorableMethods.contains(methodName)) {
				writeInstruction(_methodCall.getArg(0));
				return false;
			} else
				throw new RuntimeException("Encountered unknown boxing method " + methodName);
		}

		final String barrierAndGetterMappings =
		  javaToCLIdentifierMap.get(methodName + methodSignature);

		boolean writeAllocCheck = false;
		if (barrierAndGetterMappings != null) {
			write("/* WARNING: method call with barrier cannot be adapted for FPGA (Issue #1). */");
			// this is one of the OpenCL barrier or size getter methods
			// write the mapping and exit
			if (argc > 0) {
				write(barrierAndGetterMappings);
				write("(");
				for (int arg = 0; arg < argc; arg++) {
					if ((arg != 0))
						write(", ");
					writeInstruction(_methodCall.getArg(arg));
				}
				write(")");
			} else
				write(barrierAndGetterMappings);
		} else {
			final boolean isSpecial = _methodCall instanceof I_INVOKESPECIAL;
			MethodModel m = entryPoint.getCallTarget(_methodEntry, isSpecial);
			writeAllocCheck = mayFailHeapAllocation.contains(m);

			String getterFieldName = null;
			FieldEntry getterField = null;
			if (m != null && m.isGetter())
				getterFieldName = m.getGetterField();

			if (getterFieldName != null) {
				boolean isObjectField = m.getReturnType().startsWith("L");

				if (isThis(_methodCall.getArg(0))) {
					String fieldName = getterFieldName;

					// Issue #1
					if (isObjectField && m.getOwnerClassMangledName().contains("Tuple2"))
						write("&(");

					write("this->");
					write(fieldName);

					// Issue #1
					if (isObjectField && m.getOwnerClassMangledName().contains("Tuple2"))
						write(")");
					return false;
				} else if (_methodCall instanceof VirtualMethodCall) { // C: call an element in the struct
					VirtualMethodCall virt = (VirtualMethodCall) _methodCall;
					Instruction target = virt.getInstanceReference();
					if (target instanceof I_CHECKCAST)
						target = target.getPrevPC();

					if (target instanceof LocalVariableConstIndexLoad) { // scalar element
						LocalVariableConstIndexLoad ld = (LocalVariableConstIndexLoad)target;
						LocalVariableInfo info = ld.getLocalVariableInfo();
						if (!info.isArray()) {
							// Issue #1
							if (isObjectField && m.getOwnerClassMangledName().contains("Tuple2"))
								write("&(");

							write(info.getVariableName() + "->" + getterFieldName);

							// Issue #1
							if (isObjectField && m.getOwnerClassMangledName().contains("Tuple2"))
								write(")");
							return false;
						}
					} else if (target instanceof VirtualMethodCall) { // struct element
						VirtualMethodCall nestedCall = (VirtualMethodCall)target;
						writeMethod(nestedCall, nestedCall.getConstantPoolMethodEntry());
						write("->" + getterFieldName);
						return false;
					} else if (target instanceof AccessArrayElement) { // array element
						AccessArrayElement arrayAccess = (AccessArrayElement)target;

						final Instruction refAccess = arrayAccess.getArrayRef();
						//assert refAccess instanceof I_GETFIELD : "ref should come from getfield";
						final String fieldName = ((AccessField) refAccess).getConstantPoolFieldEntry().getNameAndTypeEntry()
						                         .getNameUTF8Entry().getUTF8();

						// Issue #1
						if (isObjectField && m.getOwnerClassMangledName().contains("Tuple2"))
							write("&(");

						write("(this->" + fieldName);
						write("[");
						writeInstruction(arrayAccess.getArrayIndex());
						write("])." + getterFieldName);

						// Issue #1
						if (isObjectField && m.getOwnerClassMangledName().contains("Tuple2"))
							write(")");

						return false;
					} else {
						throw new RuntimeException("Unhandled target \"" +
						                           target.toString() + "\" for getter " +
						                           getterFieldName);
					}
				}
			}
			boolean noCL = _methodEntry.getOwnerClassModel().getNoCLMethods()
			               .contains(_methodEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8());
			if (noCL)
				return false;
			final String intrinsicMapping = Kernel.getMappedMethodName(_methodEntry);
			boolean isIntrinsic = false;

			if (intrinsicMapping == null) {
				assert entryPoint != null : "entryPoint should not be null";
				boolean isMapped = Kernel.isMappedMethod(_methodEntry);
				boolean isScalaMapped = scalaMapped.contains(_methodEntry.toString());
				boolean isSelfMapped = SelfMapped.contains(_methodEntry.toString());

				if (m != null)
					write(m.getName());
				else if (_methodEntry.toString().equals("java/lang/Object.<init>()V")) {
					/*
					 * Do nothing if we're in a constructor calling the
					 * java.lang.Object super constructor
					 */
				} else {
					// Must be a library call like rsqrt // FIXME: Tuple2
					if (!isMapped && !isScalaMapped && !isSelfMapped && !_methodEntry.toString().contains("scala/Tuple2"))
						throw new RuntimeException(_methodEntry + " should be mapped method!");
					write(methodName);
					isIntrinsic = true;
				}
			} else
				write(intrinsicMapping);

			// write arguments of real method call
			write("(");

			if ((intrinsicMapping == null) && (_methodCall instanceof VirtualMethodCall) && (!isIntrinsic)) {

				Instruction i = ((VirtualMethodCall) _methodCall).getInstanceReference();
				if (i instanceof CloneInstruction)
					i = ((CloneInstruction)i).getReal();

				if (i instanceof I_ALOAD_0)
					write("this");
				else if (i instanceof LocalVariableConstIndexLoad)
					writeInstruction(i);
				else if (i instanceof AccessArrayElement) {
					final AccessArrayElement arrayAccess = (AccessArrayElement)i;
					final Instruction refAccess = arrayAccess.getArrayRef();
					//assert refAccess instanceof I_GETFIELD : "ref should come from getfield";
					final String fieldName = ((AccessField) refAccess).getConstantPoolFieldEntry().getNameAndTypeEntry()
					                         .getNameUTF8Entry().getUTF8();
					write(" &(this->" + fieldName);
					write("[");
					writeInstruction(arrayAccess.getArrayIndex());
					write("])");
				} else if (i instanceof New) {
					// Constructor call
					assert methodName.equals("<init>");
					writeInstruction(i);
				} else
					throw new RuntimeException("unhandled call to " + _methodEntry + " from: " + i);
			}
			for (int arg = 0; arg < argc; arg++) {
				if (((intrinsicMapping == null) && (_methodCall instanceof VirtualMethodCall) && (!isIntrinsic)) ||
				    (arg != 0))
					write(", ");

				// comaniac Issue #2, we have to match method arguments with Xilinx supported intrinsic functions.
				if (isIntrinsic &&
				    (_methodCall.getArg(arg) instanceof CastOperator)) {
					final CastOperator castInstruction = (CastOperator) _methodCall.getArg(arg);
					String targetType = convertCast(castInstruction.getOperator().getText());
					targetType = targetType.substring(1, targetType.length() - 1);
					String validType = getXilinxMethodArgType(methodName, arg);
					if (!targetType.equals(validType)) {
//								System.out.println("[Aparapi] WARNING: Intrinsic method " + methodName +
//									" has mismatched argument " + arg + ": " + targetType + " should be " + validType);
						write("(" + validType + ")");
						writeInstruction(castInstruction.getUnary());
					}
				} else
					writeInstruction(_methodCall.getArg(arg));
			}
			write(")");
		}
		return writeAllocCheck;
	}

	private boolean isThis(Instruction instruction) {
		return instruction instanceof I_ALOAD_0;
	}

	public void writePragma(String _name, boolean _enable) {
		write("#pragma OPENCL EXTENSION " + _name + " : " + (_enable ? "en" : "dis") + "able");
		newLine();
	}

	public final static String __local = "__local ";

	public final static String __global = "__global ";

	public final static String __constant = "__constant ";

	public final static String __private = "__private ";

	public final static String LOCAL_ANNOTATION_NAME = "L" +
	    com.amd.aparapi.Kernel.Local.class.getName().replace('.', '/') + ";";

	public final static String CONSTANT_ANNOTATION_NAME = "L" +
	    com.amd.aparapi.Kernel.Constant.class.getName().replace('.', '/')
	    + ";";

	private boolean doesHeapAllocation(MethodModel mm,
	                                   Set<MethodModel> mayFailHeapAllocation) {
		if (mayFailHeapAllocation.contains(mm))
			return true;

		for (MethodModel callee : mm.getCalledMethods()) {
			if (doesHeapAllocation(callee, mayFailHeapAllocation)) {
				mayFailHeapAllocation.add(mm);
				return true;
			}
		}

		return false;
	}

	private void emitExternalObjectDef(ClassModel cm) {
		final ArrayList<FieldNameInfo> fieldSet = cm.getStructMembers();

		final String mangledClassName = cm.getMangledClassName();
		newLine();
		write("typedef struct __attribute__ ((packed)) " + mangledClassName + "_s{");
		in();
		newLine();

		if (fieldSet.size() > 0) {
			int totalSize = 0;
			int alignTo = 0;

			final Iterator<FieldNameInfo> it = fieldSet.iterator();
			while (it.hasNext()) {
				final FieldNameInfo field = it.next();
				final String fType = field.desc;
				final int fSize = entryPoint.getSizeOf(fType);

				if (fSize > alignTo)
					alignTo = fSize;
				totalSize += fSize;

				String cType = convertType(field.desc, true);
				assert field.desc.startsWith("["): "array type " + field.desc + " in Tuple2 is not allowed";

				if (field.desc.startsWith("L")) {

					// comaniac: Issue #1, the struct used for kernel argument cannot have pointer type.
					// Original version use pointer to access object (L), here we use scalar instead, so the
					// rest of kernel implementations have to be changed in BlockWriter.writeMethodBody as well.
					cType = cType.replace('.', '_');
				}
				assert cType != null : "could not find type for " + field.desc;
				writeln(cType + " " + field.name + ";");
			}

			// compute total size for OpenCL buffer
			int totalStructSize = 0;
			if ((totalSize % alignTo) == 0)
				totalStructSize = totalSize;
			else {
				// Pad up if necessary
				totalStructSize = ((totalSize / alignTo) + 1) * alignTo;
			}
			// if (totalStructSize > alignTo) {
			//   while (totalSize < totalStructSize) {
			//     // structBuffer.put((byte)-1);
			//     writeln("char _pad_" + totalSize + ";");
			//     totalSize++;
			//   }
			// }

			out();
			newLine();
		}
		write("} " + mangledClassName + ";");
		newLine();
	}

	class SignatureMatcher extends ClassModelMatcher {
		private final TypeSignature targetSig;

		public SignatureMatcher(TypeSignature targetSig) {
			this.targetSig = targetSig;
		}

		@Override
		public boolean matches(ClassModel model) {
			String modelDesc = "L" + model.getClassWeAreModelling().getName().replace('.', '/') + ";";
			if (modelDesc.equals(targetSig.getBaseType())) {
				if (model instanceof HardCodedClassModel) {
					HardCodedClassModel hc = (HardCodedClassModel)model;

					TypeParameters hcTypes = hc.getTypeParamDescs();
					List<String> targetTypes = targetSig.getTypeParameters();

					if (hcTypes.size() == targetTypes.size()) {
						for (int index = 0; index < hcTypes.size(); index++) {
							String target = targetTypes.get(index);
							String curr = hcTypes.get(index);
							if (!TypeSignature.compatible(target, curr))
								return false;
						}
						return true;
					} else
						return false;
				} else {
					if (!targetSig.getTypeParameters().isEmpty()) {
						throw new RuntimeException("Do not support mathing " +
						                           "loaded classes with generic types");
					}
					return true;
				}
			} else
				return false;

		}
	}

	@Override public void write(Entrypoint _entryPoint,
	                            Collection<ScalaParameter> params) throws CodeGenException {
		final List<String> thisStruct = new ArrayList<String>();
		final List<String> argLines = new ArrayList<String>();
		final List<String> assigns = new ArrayList<String>();

		// Set true if there has at least one method returns Iterator
		boolean isMapPartitions = false;

		entryPoint = _entryPoint;

		// Add reference fields (broadcast) to 1) "this", 2) argument of main, 3) "this" assignment
		for (final ClassModelField field : _entryPoint.getReferencedClassModelFields()) {
			final StringBuilder thisStructLine = new StringBuilder();
			final StringBuilder argLine = new StringBuilder();
			final StringBuilder assignLine = new StringBuilder();

			String signature = field.getDescriptor();
//System.err.println("Field: " + field.getName() + ", sig: " + signature);

			ScalaParameter param = Utils.createScalaParameter(signature, field.getName(), ScalaParameter.DIRECTION.IN);
			param.setAsReference();

			// check the suffix

			boolean isPointer = signature.startsWith("[");

			argLine.append(param.getInputParameterString(this));
			thisStructLine.append(param.getStructString(this));
			assignLine.append(param.getAssignString(this));

			assigns.add(assignLine.toString());
			argLines.add(argLine.toString());
			thisStruct.add(thisStructLine.toString());

			// Add int field into "this" struct for supporting java arraylength op
			// named like foo__javaArrayLength
			if (isPointer && _entryPoint.getArrayFieldArrayLengthUsed().contains(field.getName())) {
				final StringBuilder lenStructLine = new StringBuilder();
				final StringBuilder lenArgLine = new StringBuilder();
				final StringBuilder lenAssignLine = new StringBuilder();

				String suffix = "";
				String lenName = field.getName() + BlockWriter.arrayLengthMangleSuffix + suffix;

				lenStructLine.append("int " + lenName);

				lenAssignLine.append("this->");
				lenAssignLine.append(lenName);
				lenAssignLine.append(" = ");
				lenAssignLine.append(lenName);

				lenArgLine.append("int " + lenName);

				assigns.add(lenAssignLine.toString());
				argLines.add(lenArgLine.toString());
				thisStruct.add(lenStructLine.toString());
			}
		}


// 2/2/16: We don't use these features now.
		// Include self mapped function libaray
//		write("#include \"boko.h\"");
//		newLine();

		// Macros
//		write("#define ITER_INC(x, inc) (((x)+=(inc))-(inc))");
//		newLine();

		write("#include <math.h>");
		newLine();
		write("#include <assert.h>");
		newLine();
		write("#define PE 16");
		newLine();
		write("#define MAX_PARTITION_SIZE 32767");
		newLine();

		if (Config.enableDoubles || _entryPoint.requiresDoublePragma()) {
			writePragma("cl_khr_fp64", true);
			newLine();
		}

		// Emit structs for oop transformation accessors
		List<String> lexicalOrdering = _entryPoint.getLexicalOrderingOfObjectClasses();
		Set<String> emitted = new HashSet<String>();
		for (String className : lexicalOrdering) {

			for (final ClassModel cm : _entryPoint.getModelsForClassName(className)) {
				final String mangled = cm.getMangledClassName();
				if (emitted.contains(mangled)) continue;

				emitExternalObjectDef(cm);
				emitted.add(mangled);
			}
		}

		write("typedef struct This_s{");

		in();
		newLine();
		for (final String line : thisStruct) {
			write(line);
			writeln(";");
		}
		out();
		write("} This;");
		newLine();

		final List<MethodModel> merged = new ArrayList<MethodModel>(_entryPoint.getCalledMethods().size() +
		    1);
		merged.addAll(_entryPoint.getCalledMethods());
		merged.add(_entryPoint.getMethodModel());

		assert(mayFailHeapAllocation == null);
		mayFailHeapAllocation = new HashSet<MethodModel>();
		for (final MethodModel mm : merged) {
			if (mm.requiresHeap()) mayFailHeapAllocation.add(mm);
		}

		for (final MethodModel mm : merged)
			doesHeapAllocation(mm, mayFailHeapAllocation);

/* For customized class, not supported now
		for (HardCodedClassModel model : _entryPoint.getHardCodedClassModels()) {
			for (HardCodedMethodModel method : model.getMethods()) {
				if (!method.isGetter()) {
					newLine();
					write(method.getMethodDef(model, this));
					newLine();
					newLine();
				}
			}
		}
*/
		for (final MethodModel mm : merged) {
			// write declaration :)
			if (mm.isPrivateMemoryGetter())
				continue;

			final String returnType = mm.getReturnType();
			this.currentReturnType = returnType;

			if (returnType.contains("scala/collection/Iterator"))
				isMapPartitions = true;

			String fullReturnType;
			String convertedReturnType = convertType(returnType, true);
			if (returnType.startsWith("L")) {
				SignatureEntry sigEntry =
				  mm.getMethod().getAttributePool().getSignatureEntry();
				final TypeSignature sig;
				if (sigEntry != null)
					sig = new FullMethodSignature(sigEntry.getSignature()).getReturnType();
				else
					sig = new TypeSignature(returnType);

				ClassModel cm = entryPoint.getModelFromObjectArrayFieldsClasses(
				                  convertedReturnType.trim(), new SignatureMatcher(sig));
				if (cm != null)
					fullReturnType = cm.getMangledClassName();
				else {
					String returnTypeHint = _entryPoint.getArgumentTypeHint("j2faOut");
					// FIXME: Tuple2 <T, U> ?
					returnTypeHint = returnTypeHint.substring(returnTypeHint.indexOf("<") + 1, returnTypeHint.indexOf(">"));
					fullReturnType = Utils.mapPrimitiveType(returnTypeHint);
				}
			} else
				fullReturnType = convertedReturnType;

			write("static ");
			if (mm.getSimpleName().equals("<init>")) {
				// Transform constructors to return a reference to their object type
				ClassModel owner = mm.getMethod().getClassModel();
				write(__global + owner.getClassWeAreModelling().getName().replace('.', '_') + " * ");
				processingConstructor = true;
			} else if (returnType.startsWith("L") && !Utils.isHardCodedClass(returnType)) {
				write(__global + fullReturnType);
				write(" *");
				processingConstructor = false;
			} else {
				// Issue #40 Array type output support:
				// Change the return type to void
				if (returnType.startsWith("[") || Utils.isArrayBasedClass(returnType)) {
					write("/* [Blaze CodeGen] WARNING: This method tries to return an array in ByteCode.");
					newLine();
					write("   This function must be called by the main function of the kernel, ");
					newLine();
					write("   or the kernel might not be synthesized or compiled. */");
					newLine();
					write("void ");
					isArrayTypeOutput = true;
				} else
					write(fullReturnType);
				processingConstructor = false;
			}

			write(mm.getName() + "(");

			if (!mm.getMethod().isStatic()) {
				if ((mm.getMethod().getClassModel() == _entryPoint.getClassModel())
				    || mm.getMethod().getClassModel().isSuperClass(
				      _entryPoint.getClassModel().getClassWeAreModelling()))
					write("This *this");
				else {
					// Call to an object member or superclass of member
					Iterator<ClassModel> classIter = _entryPoint.getObjectArrayFieldsClassesIterator();
					while (classIter.hasNext()) {
						final ClassModel c = classIter.next();
						if (mm.getMethod().getClassModel() == c) {
							write(__global + mm.getMethod().getClassModel().getClassWeAreModelling().getName().replace('.',
							      '_')
							      + " *this");
							break;
						} else if (mm.getMethod().getClassModel().isSuperClass(c.getClassWeAreModelling())) {
							write(__global + c.getClassWeAreModelling().getName().replace('.', '_') + " *this");
							break;
						}
					}
				}
			}

			boolean alreadyHasFirstArg = !mm.getMethod().isStatic();

			// A list of arguments which will be copied to the local variable in advacne.
			// Map<Name, Type>. Type "0" means we just want to initalize the argument.
			@SuppressWarnings("unchecked")
			final Map<String, String> promoteLocalArguments = new HashMap<String, String>();
			final Set<String> iterArguments = new HashSet<String>();

			final LocalVariableTableEntry<LocalVariableInfo> lvte = mm.getLocalVariableTableEntry();
			for (final LocalVariableInfo lvi : lvte) {
				if ((lvi.getStart() == 0) && ((lvi.getVariableIndex() != 0) ||
				                              mm.getMethod().isStatic())) { // full scope but skip this
					final String clazzDesc = lvi.getVariableDescriptor();
					List<String> descList = new LinkedList<String>();
					List<String> fieldList = new LinkedList<String>();

					// Issue #49: Support the argument with type parameter.
					// NOTICE: Only support "call" method with the argument has type parameter.
					if (mm.getName().contains("call") && Utils.isHardCodedClass(clazzDesc)) {
						for (ScalaParameter p : params) {
						  String paramString = null;
						  if (p.getDir() == ScalaParameter.DIRECTION.IN) {
						    String [] descArray = p.getDescArray();
								for (int i = 0; i < descArray.length; i += 1) {
									String desc = descArray[i];
									descList.add(desc);
								}
								Set<String> fields = Utils.getHardCodedClassMethods(clazzDesc, METHODTYPE.VAR_ACCESS);
								for (String field : fields) {
									fieldList.add(field);
								}
								break;
							}
						}
					}
					else
						descList.add(clazzDesc);

					for (int i = 0; i < descList.size(); i += 1) {
						String descriptor = descList.get(i);

						if (alreadyHasFirstArg)
							write(", ");

						if (descriptor.startsWith("[") || Utils.isArrayBasedClass(clazzDesc))
							write(" " + __global);

						// FIXME: We haven't create a programming model for user-defined classes.
						if (descriptor.startsWith("L"))
							write(__global);

						final String convertedType;
						if (descriptor.startsWith("L")) {
							final String converted = convertType(descriptor, true).trim();
							final SignatureEntry sigEntry = mm.getMethod().getAttributePool().getSignatureEntry();
							final TypeSignature sig;

							if (sigEntry != null) {
								final int argumentOffset = (mm.getMethod().isStatic() ?
								                            lvi.getVariableIndex() : lvi.getVariableIndex() - 1);
								final FullMethodSignature methodSig = new FullMethodSignature(
								  sigEntry.getSignature());
								sig =
								  methodSig.getTypeParameters().get(argumentOffset);
							} else
								sig = new TypeSignature(descriptor);
							ClassModel cm = entryPoint.getModelFromObjectArrayFieldsClasses(
							                  converted, new SignatureMatcher(sig));
							convertedType = cm.getMangledClassName() + "* ";
						} else
							convertedType = convertType(descriptor, true);
						write(convertedType);

						String varName = lvi.getVariableName();
						if (fieldList.size() != 0) { // Explicit hard coded class method as variable name
							if (Utils.isArrayBasedClass(clazzDesc))
								write ("* ");
							varName = varName + Utils.getDeclareHardCodedMethodString(clazzDesc, fieldList.get(i), varName);
						}
						if (Utils.isIteratorClass(clazzDesc)) // Add iterator variable
							iterArguments.add(varName + BlockWriter.iteratorIndexSuffix);

						// Issue #39: Replace variable name with "_" for local variable promotion
						// 2/2/16: We now leverage Merlin Compiler to perform memory burst.
						//if (useFPGAStyle && lvi.getVariableName().contains("j2faLocal")) {
						//	write("_" + lvi.getVariableName());
						//	promoteLocalArguments.put(varName, convertedType.toString());
						//} else
							write(varName);

						// Add array length argument for mapPartitions.
						if (isMapPartitions)
							write(", int " + lvi.getVariableName() + BlockWriter.arrayLengthMangleSuffix);

						// Add item length argument for input array.
						if (descriptor.startsWith("[") || Utils.isArrayBasedClass(clazzDesc))
							write(", int " + lvi.getVariableName() + BlockWriter.arrayItemLengthMangleSuffix);
						alreadyHasFirstArg = true;
					}
				}
			}

			// Issue #40: Add output array as an argument.
			if (isArrayTypeOutput) {

				// Find the local variable name used for the return value in Java.
				// aload/invoke/cast	<- the 2nd instruction from the last
				// areturn
				Instruction retVar = mm.getPCHead();
				while (retVar.getNextPC() != null) // Find the last
					retVar = retVar.getNextPC();

				Instruction loadVar = retVar;
				while (!(loadVar instanceof AccessLocalVariable))
					loadVar = loadVar.getPrevPC();

				final LocalVariableInfo localVariable = ((AccessLocalVariable) loadVar).getLocalVariableInfo();
				String varName = localVariable.getVariableName();
				// 2/2/16: We now leverage Merlin Compiler to perform memory burst.
				//if (fullReturnType.contains("float"))
				//	promoteLocalArguments.put(varName, "0.0f");
				//else if (fullReturnType.contains("double"))
				//	promoteLocalArguments.put(varName, "0.0");
				//else
				//	promoteLocalArguments.put(varName, "0");

				// Skip return value when writing method body
				// since we want to assign the value to the output argument directly
				while (loadVar != retVar) {
					loadVar.setByteCode(ByteCode.NONE);
					loadVar = loadVar.getNextPC();
				}
				
				// Return void
				retVar.setByteCode(ByteCode.RETURN);

				// Skip local variable declaration when writing method body
				// since we want to send this through argument
				Instruction newArrayInst = mm.getPCHead();
				while (newArrayInst.getNextPC() != null) {
					if (newArrayInst instanceof I_NEWARRAY) {
						// Get variable name from astore (parent instruction)
						Instruction parent = newArrayInst.getParentExpr();
						if (parent instanceof LocalVariableTableIndexAccessor) {
							LocalVariableTableIndexAccessor var = (LocalVariableTableIndexAccessor) parent;
							if (var.getLocalVariableInfo().getVariableName().equals(varName)) {
								newArrayInst.setByteCode(ByteCode.NONE);
								parent.setByteCode(ByteCode.NONE);
								break; 
							}
						}
					}
					newArrayInst = newArrayInst.getNextPC();
				}
				if (newArrayInst == null)
					System.err.println("WARNING: Cannot find local variable declaration for array type output.");

				write(", " + __global + fullReturnType);
				if (Utils.isArrayBasedClass(returnType))
					write("* ");
				write(varName);
				write(", int " + varName + BlockWriter.arrayItemLengthMangleSuffix);
			}

			write(")");
			newLine();
/*
			// 2/2/16: We now leverage Merlin Compiler to perform memory burst.
			write("{");
			newLine();

			// Write iterator variables
			in();
			for (String arg : iterArguments) {
				write("int " + arg + " = 0;");
				newLine();
			}
			out();
			if (useFPGAStyle) {
				// Issue #37: Local variable promotion for input arguments
				// Issue #40: Inialize array type output
				for (final String varName: promoteLocalArguments.keySet()) {
					in();
					newLine();
					if (!writeLocalArrayAssign(null, varName, promoteLocalArguments.get(varName))) {
						if (promoteLocalArguments.get(varName).equals("0"))
							write("// [Blaze CodeGen] Cannot initial argument " + varName);
						else
							write(promoteLocalArguments.get(varName) + varName + " = _" + varName + ";");
					}
					out();
					newLine();
				}
			}
*/
			writeMethodBody(mm);
			newLine();
/*
			write("}");
			newLine();
*/
		}

		// Start writing main function
		ScalaParameter outParam = null;
		write("__kernel ");
		newLine();
		write("void run(");
		in();
		in();
		newLine();

		// Main method argunments: (dataNum, input, output, reference)
		boolean first = true;
		for (ScalaParameter p : params) {
			if (first) {
				first = false;
				write("int N");
			}
			write(", ");
			newLine();

			// Write arguments and find output parameter
			String paramString = null;
			if (p.getDir() == ScalaParameter.DIRECTION.OUT) {
				assert(outParam == null); // Expect only one output parameter.
				outParam = p;
				paramString = p.getOutputParameterString(this);
			} else
				paramString = p.getInputParameterString(this);

			// I/O must be an array for kernel.run
// FIXME: Should be set in ScalaParameter
//			if (!p.isArray())
//				paramString = __global + paramString.replace(" ", " * ");
			write(paramString);

			// Add length and item number for 1-D array I/O.
			if (p.isArray()) {
				write(", ");
				newLine();
				write("int " + p.getName() + BlockWriter.arrayItemLengthMangleSuffix);
			}
		}

		if (isMapPartitions && !useFPGAStyle)
			throw new RuntimeException("MapParitions can only be adopted by FPGA kernel.");

		// Write reference data
		for (final String line : argLines) {
			write(", ");
			write(line);
		}

		write(") {");
		out();
		newLine();
		assert(outParam != null);

		// FPGA uses 1 work group while GPU uses multiple.
		if (!useFPGAStyle) {
			writeln("int nthreads = get_global_size(0);");
			writeln("int idx = get_global_id(0);");
		}

		writeln("This thisStruct;");
		writeln("This* this = &thisStruct;");
		for (final String line : assigns) {
			write(line);
			writeln(";");
		}

		String aryIdxStr = "idx";
		if (useFPGAStyle)
			aryIdxStr = "(idx + pe)";
		if (!isMapPartitions) {
			if (!useFPGAStyle)
				write("for (; idx < N; idx += nthreads) {");
			else {
				write("for (int idx = 0; idx < N; idx += PE) {");
				in();
				newLine();
				write("for (int pe = 0; pe < PE; pe++) {");
			}
			in();
			newLine();
		}

		// Call the kernel function.
		if (outParam.getClazz() != null) { // User defined class
			write(__global + outParam.getType() + "* result = " +
			      _entryPoint.getMethodModel().getName() + "(this");
		} else {
			if (!outParam.isArray() && !isMapPartitions) // Issue #40: We don't use return value for array type
				write(outParam.getName() + "[" + aryIdxStr + "] = ");
			write(_entryPoint.getMethodModel().getName() + "(this");
		}

		for (ScalaParameter p : params) {
			if (p.getDir() == ScalaParameter.DIRECTION.IN) {
				if (p.isArray()) { // Deserialized access
					if (!isMapPartitions) {
						if (p.isPrimitive()) // Primitive type
							write(", &" + p.getName() + "[" + aryIdxStr + " * " + p.getName() + 
									BlockWriter.arrayItemLengthMangleSuffix + "]");
						else if (Utils.isHardCodedClass(p.getClassName())) { 
							Set<String> fields = Utils.getHardCodedClassMethods(p.getClassName(), METHODTYPE.VAR_ACCESS);
							for (String field : fields) {
								write(", &" + p.getName() + Utils.getDeclareHardCodedMethodString(p.getClassName(), 
										field, p.getName()) + "[" + aryIdxStr + " " + p.getName() + 
										BlockWriter.arrayItemLengthMangleSuffix + "]");
							}
						}
						else // Object array is not allowed.
							throw new RuntimeException();
					}
					else { // MapPartitions
						Set<String> fields = Utils.getHardCodedClassMethods(p.getClassName(), METHODTYPE.VAR_ACCESS);
						for (String field : fields) {
							write(", " + p.getName() + Utils.getDeclareHardCodedMethodString(p.getClassName(), 
							field, p.getName()));
						}
						write(", N"); // Data length is necessary for mapPartitions
					}
					write(", " + p.getName() + BlockWriter.arrayItemLengthMangleSuffix);
				}
				else {
					if (p.isPrimitive()) // Primitive type
						write(", " + p.getName() + "[" + aryIdxStr + "]");
					else if (Utils.isHardCodedClass(p.getClassName())) {
						Set<String> fields = Utils.getHardCodedClassMethods(p.getClassName(), METHODTYPE.VAR_ACCESS);
						for (String field : fields) {
							write(", " + p.getName() + 
								Utils.getDeclareHardCodedMethodString(p.getClassName(), field, p.getName()) + "[" + aryIdxStr + "]");
						}
					}
					else // User defined class
						write(", " + p.getName() + " + i");
				}
			}
		}

		if (isArrayTypeOutput) { // Issue #40: Add another argument for output array.
			if (outParam.isPrimitive()) // Primitive type
				write(", &" + outParam.getName() + "[" + aryIdxStr + " * " + outParam.getName() + 
					BlockWriter.arrayItemLengthMangleSuffix + "]");
			else if (Utils.isHardCodedClass(outParam.getClassName())) {
				Set<String> fields = Utils.getHardCodedClassMethods(outParam.getClassName(), METHODTYPE.VAR_ACCESS);
				if (!isMapPartitions) {
					for (String field : fields) {
						write(", &" + outParam.getName() + 
							Utils.getDeclareHardCodedMethodString(outParam.getClassName(), field, outParam.getName()) + 
							"[" + aryIdxStr + " " + outParam.getName() + BlockWriter.arrayItemLengthMangleSuffix + "]");
					}
				}
				else {
					for (String field : fields) {
						write(", " + outParam.getName() + 
							Utils.getDeclareHardCodedMethodString(outParam.getClassName(), field, outParam.getName()));
					}
				}
			}
			else {
				; // TODO: User defined class
			}
			write(", " + outParam.getName() + BlockWriter.arrayItemLengthMangleSuffix);
		}

		write(");");
		newLine();

		if (!isMapPartitions) {
			out();
			newLine();
			write("}");
			if (useFPGAStyle) {
				out();
				newLine();
				write("}");
			}
		}

		out();
		newLine();
		writeln("}");
	}

	@Override public void writeThisRef() {
		write("this->");
	}

	@Override public boolean writeInstruction(Instruction _instruction) throws CodeGenException {
		if ((_instruction instanceof I_IUSHR) || (_instruction instanceof I_LUSHR)) {
			final BinaryOperator binaryInstruction = (BinaryOperator) _instruction;
			final Instruction parent = binaryInstruction.getParentExpr();
			boolean needsParenthesis = true;

			if (parent instanceof AssignToLocalVariable)
				needsParenthesis = false;
			else if (parent instanceof AssignToField)
				needsParenthesis = false;
			else if (parent instanceof AssignToArrayElement)
				needsParenthesis = false;
			if (needsParenthesis)
				write("(");

			if (binaryInstruction instanceof I_IUSHR)
				write("((unsigned int)");
			else
				write("((unsigned long)");
			writeInstruction(binaryInstruction.getLhs());
			write(")");
			write(" >> ");
			writeInstruction(binaryInstruction.getRhs());

			if (needsParenthesis)
				write(")");
			return false;
		} else
			return super.writeInstruction(_instruction);
	}

	public static class WriterAndKernel {
		public final KernelWriter writer;
		public final String kernel;

		public WriterAndKernel(KernelWriter writer, String kernel) {
			this.writer = writer;
			this.kernel = kernel;
		}
	}

	public static String getXilinxMethodArgType(String _method, int idx) {
		return XilinxMethodMap.get(_method)[idx];
	}

	public static String applyXilinxPatch(String kernel) {
		String xKernel = kernel.replace("$", "___");

		// Add specified work group number
		xKernel = xKernel.replace("__kernel", "__kernel __attribute__((reqd_work_group_size(1, 1, 1)))");

		// Add loop pipeline to each for-loop
		//xKernel = xKernel.replace("for (", "__attribute__((xcl_pipeline_loop)) for (");

		// Add loop pipeline to each while-loop
		//xKernel = xKernel.replace("while (", "__attribute__((xcl_pipeline_loop)) while (");

		return xKernel;
	}

	public static WriterAndKernel writeToString(Entrypoint _entrypoint,
	    Collection<ScalaParameter> params) throws CodeGenException, AparapiException {

		final StringBuilder openCLStringBuilder = new StringBuilder();
		final KernelWriter openCLWriter = new KernelWriter() {
			private int writtenSinceLastNewLine = 0;

			@Override public void deleteCurrentLine() {
				openCLStringBuilder.delete(openCLStringBuilder.length() - writtenSinceLastNewLine - 1,
				                           openCLStringBuilder.length());
				newLine();
			}

			@Override public void writeBeforeCurrentLine(String _string) {
				openCLStringBuilder.insert(openCLStringBuilder.length() -
				                           writtenSinceLastNewLine, _string + "\n");
			}

			@Override public void write(String _string) {
				int lastNewLine = _string.lastIndexOf('\n');
				if (lastNewLine != -1)
					writtenSinceLastNewLine = _string.length() - lastNewLine - 1;
				else
					writtenSinceLastNewLine += _string.length();
				openCLStringBuilder.append(_string);
			}
		};
		try {
			openCLWriter.write(_entrypoint, params);
		} catch (final CodeGenException codeGenException) {
			throw codeGenException;
		}/* catch (final Throwable t) {
         throw new CodeGenException(t);
       }*/

		String kernel = openCLStringBuilder.toString();

		// Remove OpenCL tags for Merlin Compiler
		if (useMerlinKernel) {
			kernel = kernel.replace("__global", "")
										 .replace("__local", "")
										 .replace("__kernel", "");
		}
		return (new WriterAndKernel(openCLWriter, kernel));
	}
}