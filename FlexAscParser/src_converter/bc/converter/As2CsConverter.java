package bc.converter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import bc.code.ListWriteDestination;
import bc.code.WriteDestination;
import bc.help.BcCodeHelper;
import bc.help.CsCodeHelper;
import bc.lang.BcArgumentsList;
import bc.lang.BcClassDefinitionNode;
import bc.lang.BcFuncParam;
import bc.lang.BcFunctionDeclaration;
import bc.lang.BcFunctionTypeNode;
import bc.lang.BcInterfaceDefinitionNode;
import bc.lang.BcTypeNode;
import bc.lang.BcVariableDeclaration;
import bc.lang.BcVectorTypeNode;

public class As2CsConverter extends As2WhateverConverter
{
	private ListWriteDestination src;
	
	public As2CsConverter()
	{
		super(new CsCodeHelper());
	}
	
	@Override
	protected void writeForeach(WriteDestination dest, Object loopVarName, BcTypeNode loopVarType, Object collection, BcTypeNode collectionType, Object body)
	{
		final String collectionTemp = "__" + loopVarName + "s_";
		dest.writelnf("%s %s = %s;", type(collectionType), collectionTemp, collection);
		dest.writelnf("if (%s != %s)", collectionTemp, getCodeHelper().literalNull());
		dest.writeBlockOpen();
		dest.writelnf("foreach (%s %s in %s)", type(loopVarType), loopVarName, collectionTemp);		
		dest.writeln(body);		
		dest.writeBlockClose();
	}
	
	private void writeImports(WriteDestination dest, List<String> imports)	
	{
		List<String> sortedImports = new ArrayList<String>(imports);
		Collections.sort(sortedImports);
		
		for (String importString : sortedImports)
		{
			dest.writelnf("using %s;", importString);
		}
	}

	private void writeInterfaceFunctions(BcClassDefinitionNode bcClass)
	{
		List<BcFunctionDeclaration> functions = bcClass.getFunctions();
		for (BcFunctionDeclaration bcFunc : functions)
		{
			String type = bcFunc.hasReturnType() ? type(bcFunc.getReturnType()) : "void";
			String name = getCodeHelper().identifier(bcFunc.getName());
			
			if (bcFunc.isConstructor())
			{
				continue;
			}
			
			src.writef("%s %s(", type, name);
			
			StringBuilder paramsBuffer = new StringBuilder();
			StringBuilder argsBuffer = new StringBuilder();
			List<BcFuncParam> params = bcFunc.getParams();
			int paramIndex = 0;
			for (BcFuncParam bcParam : params)
			{
				String paramType = type(bcParam.getType());
				String paramName = getCodeHelper().identifier(bcParam.getIdentifier());
				paramsBuffer.append(String.format("%s %s", paramType, paramName));
				argsBuffer.append(paramName);
				if (++paramIndex < params.size())
				{
					paramsBuffer.append(", ");
					argsBuffer.append(", ");
				}
			}
			
			src.write(paramsBuffer);
			src.writeln(");");
		}
	}

	@Override
	protected void writeClassDefinition(BcClassDefinitionNode bcClass, File outputDir) throws IOException
	{
		boolean isInterface = bcClass instanceof BcInterfaceDefinitionNode;
		
		String className = getClassName(bcClass);
		
		String packageName = bcClass.getPackageName();
		String subPath = packageName.replace(".", "/");
		
		File srcFileDir = new File(outputDir, subPath);
		if (!srcFileDir.exists())
		{
			boolean successed = srcFileDir.mkdirs();
			assert successed : srcFileDir.getAbsolutePath();
		}
		
		File outputFile = new File(srcFileDir, className + ".cs");
		
		if (!shouldWriteClassToFile(bcClass, outputFile))
		{
			return;
		}
		
		src = new ListWriteDestination();		
		
		src.writeln("using System;");
		writeBlankLine(src);
		
		writeImports(src, getImports(bcClass));
		writeBlankLine(src);
		
		src.writeln("namespace " + getCodeHelper().namespace(bcClass.getPackageName()));
		writeBlockOpen(src);
		
		if (bcClass.hasFunctionTypes())
		{
			writeFunctionTypes(bcClass);
		}
		
		if (isInterface)
		{
			src.writelnf("public interface %s", className);
			writeBlockOpen(src);
			writeInterfaceFunctions(bcClass);
			writeBlockClose(src);
		}
		else
		{
			if (bcClass.isFinal())
			{
				src.writef("public sealed class %s", className);
			}
			else
			{
				src.writef("public class %s", className);
			}
			
			boolean hasExtendsType = bcClass.hasExtendsType();
			boolean hasInterfaces = bcClass.hasInterfaces();
			
			if (hasExtendsType || hasInterfaces)
			{
				src.write(" : ");
			}
			
			if (hasExtendsType)
			{
				src.write(type(bcClass.getExtendsType()));
				if (hasInterfaces)
				{
					src.write(", ");
				}
			}
			
			if (hasInterfaces)
			{
				List<BcTypeNode> interfaces = bcClass.getInterfaces();
				int interfaceIndex= 0;
				for (BcTypeNode bcInterface : interfaces) 
				{					
					String interfaceType = type(bcInterface);
					src.write(++interfaceIndex == interfaces.size() ? interfaceType : (interfaceType + ", "));
				}
			}
			
			List<BcVariableDeclaration> bcInitializedFields = collectFieldsWithInitializer(bcClass);
			needFieldsInitializer = bcInitializedFields.size() > 0;
			
			src.writeln();
			writeBlockOpen(src);
			
			writeFields(bcClass);
			if (needFieldsInitializer)
			{
				writeFieldsInitializer(bcClass, bcInitializedFields);
			}
			writeFunctions(bcClass);
			
			writeBlockClose(src);
		}		
		
		writeBlockClose(src);
		
		writeDestToFile(src, outputFile);
	}

	private void writeFunctionTypes(BcClassDefinitionNode bcClass) 
	{
		List<BcFunctionTypeNode> functionTypes = bcClass.getFunctionTypes();
		for (BcFunctionTypeNode funcType : functionTypes) 
		{
			writeFunctionType(bcClass, funcType);
		}
	}

	private void writeFunctionType(BcClassDefinitionNode bcClass, BcFunctionTypeNode funcType) 
	{
		String type = funcType.hasReturnType() ? type(funcType.getReturnType()) : "void";
		String name = getCodeHelper().identifier(funcType.getName());			
		
		src.writelnf("public delegate %s %s(%s);", type, type(name), paramsDef(funcType.getParams()));
	}

	private void writeFields(BcClassDefinitionNode bcClass)
	{
		List<BcVariableDeclaration> fields = bcClass.getFields();
		
		for (BcVariableDeclaration bcField : fields)
		{
			String type = type(bcField.getType());
			String name = getCodeHelper().identifier(bcField.getIdentifier());
						
			src.write(bcField.getVisiblity() + " ");
			
			if (bcField.isConst())
			{
				if (canBeClass(bcField.getType()))
				{
					src.write("static ");
				}
				else
				{
					src.write("const ");
				}
			}
			else if (bcField.isStatic())
			{
				src.write("static ");
			}			
			
			src.writef("%s %s", type, name);
			if (bcField.hasInitializer() && isSafeInitialized(bcClass, bcField))
			{
				src.writef(" = %s", bcField.getInitializer());
			}
			src.writeln(";");
		}
	}
	
	private void writeFieldsInitializer(BcClassDefinitionNode bcClass, List<BcVariableDeclaration> bcFields) 
	{
		src.writelnf("private void %s()", internalFieldInitializer);
		writeBlockOpen(src);
		
		for (BcVariableDeclaration bcVar : bcFields) 
		{
			String name = getCodeHelper().identifier(bcVar.getIdentifier());
			src.writelnf("%s = %s;", name, bcVar.getInitializer());
		}
		
		writeBlockClose(src);
	}
	
	private void writeFunctions(BcClassDefinitionNode bcClass)
	{
		List<BcFunctionDeclaration> functions = bcClass.getFunctions();
		for (BcFunctionDeclaration bcFunc : functions)
		{
			src.write(bcFunc.getVisiblity() + " ");
			if (bcFunc.isConstructor())
			{
				src.write(getClassName(bcClass));
			}			
			else
			{
				if (bcFunc.isStatic())
				{
					src.write("static ");
				}
				else if (bcFunc.isOverride())
				{
					src.write("override ");
				}
				else if (!bcFunc.isPrivate() && !bcClass.isFinal())
				{
					src.write("virtual ");
				}
				
				String type = bcFunc.hasReturnType() ? type(bcFunc.getReturnType()) : "void";
				String name = getCodeHelper().identifier(bcFunc.getName());			
				
				if (bcFunc.isGetter())
				{
					name = getCodeHelper().getter(name);
				}
				else if (bcFunc.isSetter())
				{
					name = getCodeHelper().setter(name);
				}
				src.writef("%s %s", type, name);
			}
			
			src.writelnf("(%s)", paramsDef(bcFunc.getParams()));
			
			ListWriteDestination body = bcFunc.getBody();
			if (bcFunc.isConstructor())
			{
				writeConstructorBody(body);
			}
			else
			{
				src.writeln(body);
			}
		}
	}

	private void writeConstructorBody(ListWriteDestination body) 
	{
		List<String> lines = body.getLines();
		String firstLine = lines.get(1).trim();
		if (firstLine.startsWith(BcCodeHelper.thisCallMarker))
		{
			firstLine = firstLine.replace(BcCodeHelper.thisCallMarker, "this");
			if (firstLine.endsWith(";"))
			{
				firstLine = firstLine.substring(0, firstLine.length() - 1);
			}
			
			src.writeln(" : " + firstLine);
			lines.remove(1);
		}
		else if (firstLine.startsWith(BcCodeHelper.superCallMarker))
		{
			firstLine = firstLine.replace(BcCodeHelper.superCallMarker, "base");
			if (firstLine.endsWith(";"))
			{
				firstLine = firstLine.substring(0, firstLine.length() - 1);
			}
			
			src.writeln(" : " + firstLine);
			lines.remove(1);
		}
		
		if (needFieldsInitializer)
		{
			lines.add(1, String.format("\t%s();", internalFieldInitializer));
		}
		
		src.writeln(new ListWriteDestination(lines));
	}
	
	private List<String> getImports(BcClassDefinitionNode bcClass)
	{
		List<String> imports = new ArrayList<String>();
		
		if (bcClass.hasExtendsType())
		{
			tryAddUniqueNamespace(imports, bcClass.getExtendsType());
		}
		
		if (bcClass.hasInterfaces())
		{
			List<BcTypeNode> interfaces = bcClass.getInterfaces();
			for (BcTypeNode bcInterface : interfaces)
			{
				tryAddUniqueNamespace(imports, bcInterface);
			}
		}
		
		List<BcVariableDeclaration> classVars = bcClass.getDeclaredVars();
		for (BcVariableDeclaration bcVar : classVars)
		{
			BcTypeNode type = bcVar.getType();
			tryAddUniqueNamespace(imports, type);
		}
		
		List<BcFunctionDeclaration> functions = bcClass.getFunctions();
		for (BcFunctionDeclaration bcFunc : functions)
		{
			if (bcFunc.hasReturnType())
			{
				BcTypeNode returnType = bcFunc.getReturnType();
				tryAddUniqueNamespace(imports, returnType);
			}
			
			List<BcFuncParam> params = bcFunc.getParams();
			for (BcFuncParam param : params)
			{
				BcTypeNode type = param.getType();
				tryAddUniqueNamespace(imports, type);
			}
		}
		
		List<BcTypeNode> additionalImports = bcClass.getAdditionalImports();
		for (BcTypeNode bcType : additionalImports) 
		{
			tryAddUniqueNamespace(imports, bcType);
		}
		
		return imports;
	}
	
	private void tryAddUniqueNamespace(List<String> imports, BcTypeNode type)
	{
		if (canBeClass(type))
		{
			BcClassDefinitionNode classNode = type.getClassNode();
			assert classNode != null : type.getName();
			
			String packageName = classNode.getPackageName();
			assert packageName != null : classNode.getName();
			
			if (!imports.contains(packageName))
			{
				imports.add(packageName);
			}
			
			if (type instanceof BcVectorTypeNode)
			{
				BcVectorTypeNode vectorType = (BcVectorTypeNode) type;
				BcTypeNode generic = vectorType.getGeneric();
				if (generic != null)
				{
					tryAddUniqueNamespace(imports, generic);
				}
			}
		}
	}
	
	/* code helper */
	
	private static final String NEW = "new";
	private static final String IS = "is";
	
	protected static final String VECTOR_BC_TYPE = "Vector";
		
	@Override
	protected String classType(String name)
	{
		if (name.equals("String"))
		{
			return name;
		}
		
		return super.classType(name);
	}
	
	@Override
	public String construct(String type, Object initializer)
	{
		return NEW + " " + type(type) + "(" + initializer + ")";
	}
	
	@Override
	protected String vectorType(BcVectorTypeNode vectorType)
	{
		String genericName = type(vectorType.getGeneric());
		return type(VECTOR_BC_TYPE) + "<" + genericName + ">";
	}
	
	@Override
	public String constructVector(BcVectorTypeNode vectorType, BcArgumentsList args)
	{
		return NEW + " " + type(VECTOR_BC_TYPE) + "<" + type(vectorType.getGeneric()) + ">" + "(" + args + ")";
	}
	
	@Override
	public String constructLiteralVector(BcVectorTypeNode vectorType, BcArgumentsList args)
	{
		return constructVector(vectorType, args);
	}
	
	@Override
	public String operatorIs(Object lhs, Object rhs)
	{
		return String.format("%s %s %s", lhs, IS, type(rhs.toString()));
	}
}
