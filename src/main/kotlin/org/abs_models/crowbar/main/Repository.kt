package org.abs_models.crowbar.main

import org.abs_models.crowbar.data.*
import org.abs_models.crowbar.data.Function
import org.abs_models.crowbar.interfaces.*
import org.abs_models.crowbar.tree.SymbolicNode
import org.abs_models.crowbar.types.getReturnType
import org.abs_models.frontend.ast.*
import org.abs_models.frontend.typechecker.*
import kotlin.reflect.KClass
import kotlin.system.exitProcess

/**
 *   The structures in this file keep track of information from the AST that is not visible in the IR because it is outside of statement:
 *    - ADTRepos manages the data types
 *    - FunctionRepos manages the functional layer
 *    - Repository manages the specification
 */
object ADTRepos {

	var model:Model? = null

	private val dtypeMap: MutableMap<String, HeapDecl> = mutableMapOf()
	val dTypesDecl = mutableSetOf<DataTypeDecl>()
	val primitiveDtypesDecl = mutableListOf<DataTypeDecl>()
	val exceptionDecl = mutableListOf<ExceptionDecl>()
	val interfaceDecl = mutableListOf<InterfaceDecl>()

	val placeholdersMap : MutableMap<Placeholder,Term> = mutableMapOf()

	val objects : MutableMap<String,UnionType> = mutableMapOf()

	private val concreteGenerics :MutableMap<String, DataTypeType> = mutableMapOf()
	val parametricGenerics :MutableMap<String, List<TypeParameter>> = mutableMapOf()
	private val usedHeaps = mutableSetOf<String>()

	//These are either handled manually as special cases (e.g., float, Exception)
	private val ignorableBuiltInDataTypes : Set<String> = setOf(
			//"ABS.StdLib.Map",
			"ABS.StdLib.Float",
			"ABS.StdLib.Time",
			"ABS.StdLib.Duration",
			"ABS.StdLib.Exception",
			"ABS.StdLib.Annotation",
			"ABS.StdLib.LocationType",
			"ABS.StdLib.NullableType",
			"ABS.StdLib.ClassKindAnnotation",
			"ABS.StdLib.FinalAnnotation",
			"ABS.StdLib.AtomicityAnnotation",
			"ABS.StdLib.ReadonlyAnnotation",
			"ABS.StdLib.HTTPCallableAnnotation")


	private fun addUsedHeap(heap :String){
		usedHeaps.add(heap)
	}

	fun setUsedHeaps(usedHeapsPar : Set<String>) {
		usedHeaps.clear()
		usedHeaps.addAll(usedHeapsPar)
	}

	private fun isBuildInType(type : Type) :Boolean{
		return type.isBoolType || type.isIntType
	}

	fun getSMTDType(dType : String) : HeapDecl =
		try {
			dtypeMap[libPrefix(dType)]!!
		}catch ( e:KotlinNullPointerException ){
			if(reporting)  throw Exception("Type $dType not supported")
			System.err.println("Type $dType not supported")
				exitProcess(-1)
		}
	fun getDeclForType(dType: String) : DataTypeDecl = dTypesDecl.find{ it.qualifiedName == dType }!!

	fun getAllTypePrefixes() : Set<String> = dtypeMap.keys
	fun getUsedTypePrefixes() : Set<String> = usedHeaps

	fun clearConcreteGenerics(){
		concreteGenerics.clear()
	}

	fun isKnownGeneric(type: DataTypeType) = type.toString() in concreteGenerics

	fun addGeneric(type : DataTypeType) {
		if(!type.typeArgs.any{it.isUnknownType}) {
			if (!isKnownGeneric(type)) {

				val isParametricGenerics = type.typeArgs.isNotEmpty() && type.typeArgs.first() is TypeParameter
				if (isParametricGenerics && type.qualifiedName !in parametricGenerics) {
					parametricGenerics[type.qualifiedName] = type.typeArgs as List<TypeParameter>
				} else {
					concreteGenerics[type.toString()] = type
					if (!type.typeArgs.any { it.isUnknownType }) {
						type.typeArgs.map { if (isGeneric(it)) addGeneric(it as DataTypeType) }
						type.decl.dataConstructorList.forEach { tt ->
							tt.constructorArgList.forEach { constr ->
								if (constr.type is DataTypeType) {
									val t = type
									val mapTypeParamType =
										((type.decl.type as DataTypeType).typeArgs as List<TypeParameter>).zip(
											type.typeArgs
										).toMap()
									val boundType = (constr.type as DataTypeType).applyBinding(mapTypeParamType)
									if (isGeneric(boundType)) addGeneric(boundType as DataTypeType)
								}
							}

						}
					}
					val heapName = genericTypeSMTName(type)
					dtypeMap[heapName] = HeapDecl(heapName)
					addUsedHeap(heapName)
				}
			}
		}
	}

	fun genericsToSMT() :String{
		val names = mutableSetOf<ArgsSMT>()
		val decls = mutableSetOf<ArgSMT>()
		val valueOf = mutableSetOf<FunctionDeclSMT>()

		concreteGenerics.values.map{
			val map = (it.decl.type as DataTypeType).typeArgs.zip(it.typeArgs).toMap()
			val list = GenericTypeDecl(it.decl, map, it.typeArgs).getDecl()
			names.add(list[0] as ArgsSMT)
			decls.add(list[1] as ArgSMT)
			valueOf.add(list[2] as FunctionDeclSMT)
		}
		val decl = Function(
			"declare-datatypes", (
					listOf(ArgSMT(names.toList()), ArgSMT(decls.toList()))))
		return if(names.isNotEmpty())
			decl.toSMT() + "\n${valueOf.joinToString("") { it.toSMT("\n") }}"
		else
			""
	}

	fun heapsToSMT() :String{
		var header = ""
		for (dtype in dtypeMap) {

			header +=
				if(!conciseProofs || dtype.key in usedHeaps)
					dtype.value.toSMT()
				else
					"\n; no fields of type ${dtype.key}: omitting declaration of ${dtype.value.heapType}"

		}
		return header
	}

	fun interfaceExtendsToSMT() : String {
		var res = ""
		interfaceDecl.forEach { i1 ->
			i1.extendedInterfaceUseListNoTransform.forEach { i2 ->

				res += "(assert (extends ${i1.type.qualifiedName} ${i2.type.qualifiedName}))\n\t"
			}
		}
		return res
	}

	fun dTypesToSMT() :String{
		return DataTypesDecl(dTypesDecl.toList(), exceptionDecl,interfaceDecl).toSMT()
	}

	override fun toString() : String {
		return DataTypesDecl(dTypesDecl.toList(), exceptionDecl,interfaceDecl).toSMT()
	}

	fun initBuiltIn(){
		dtypeMap.clear()
		dTypesDecl.clear()
		primitiveDtypesDecl.clear()
		exceptionDecl.clear()
		interfaceDecl.clear()
		objects.clear()
		dtypeMap["ABS.StdLib.Int"] = HeapDecl("ABS.StdLib.Int")
		dtypeMap["ABS.StdLib.Float"] = HeapDecl("ABS.StdLib.Float")
	}
	fun init(parModel: Model){
		model = parModel
		initBuiltIn()
		for(moduleDecl in parModel.moduleDecls){

			if(moduleDecl.name.startsWith("ABS.")
				&& !moduleDecl.name.startsWith("ABS.StdLib")) continue
			for(decl in moduleDecl.decls){
				if(!moduleDecl.name.startsWith("ABS.StdLib.Exceptions") && decl is DataTypeDecl && decl.name != "Spec" && decl.qualifiedName !in ignorableBuiltInDataTypes){
					if(!isBuildInType(decl.type)) {
						if (decl.hasDataConstructor())
							dTypesDecl.add(decl)
						else
							primitiveDtypesDecl.add(decl)
					}
					dtypeMap[decl.qualifiedName] = HeapDecl(decl.type.qualifiedName)
				}
				if(decl is ExceptionDecl) exceptionDecl.add(decl)
				if(decl is InterfaceDecl)  interfaceDecl.add(decl)
			}
		}

	}
	fun libPrefix(type: String): String {
		return when {
			type == "Unbound Type" -> "UNBOUND"
			type == "<UNKNOWN>" -> throw Exception("Unknown Type")
			dtypeMap.containsKey(type) || type.startsWith("ABS.StdLib") -> type
			else -> "ABS.StdLib.Int"
		}
	}

}

object FunctionRepos{

	val builtInFunctionNames = setOf(
		"abs",
		"head","tail", "appendright", "concatenate", //list
		"fst","snd", //pair
		"fstT", "sndT","trdT", //triple
		"contains", //set
		"emptyMap", "lookup" //map
	)
	val known : MutableMap<String, FunctionDecl> = mutableMapOf()
	val genericFunctions = mutableMapOf<String,Triple<DataTypeType, List<Type>, Function>>()
	val parametricFunctions = mutableMapOf<String,FunctionDecl>()
	val concreteParametricNameSMT = mutableMapOf<Pair<String,List<Type>>,String>()
	val parametricFunctionTypeMap = mutableMapOf<Pair<String,List<Type>>,Map<TypeParameter,Type>>()
	val concreteFunctionsToSMT = mutableMapOf<Pair<String,List<Type>>,Pair<String,String>>()

    fun isKnown(str: String) = known.containsKey(str)
    fun get(str: String) = known.getValue(str)
	fun hasContracts() = known.filter { hasContract(it.value) }.any()
	private fun contracts() = known.filter { hasContract(it.value) }

	fun initParametricFunctions(){
		val direct = known.filter { !hasContract(it.value) }
		if(direct.isNotEmpty()) {
			for (pair in direct) {
				if(pair.value is ParametricFunctionDecl){
					parametricFunctions[functionNameSMT(pair.value)] = pair.value
				}
			}
		}
	}
    override fun toString() : String {
	    val contracts = contracts()
	    val direct = known.filter { !hasContract(it.value) }
	    var ret = ""

		if(contracts.isNotEmpty()) {
		    var sigs = ""
		    var defs = ""
		    for (pair in contracts) {

				if(pair.value is ParametricFunctionDecl)
					throw Exception("Parametric functions are not supported, please flatten your model")
			    val name = functionNameSMT(pair.value)
			    val params = pair.value.params
				val paramTypes = params.map{
					it.type
				}

			    val zParams = params.joinToString(" ") { translateType(it.type) }

			    val nextsig =  "\n(declare-fun $name ($zParams) ${translateType(pair.value.type)})" //todo: use interface to get SMT declaration
			    sigs += nextsig

			    val callParams = params.joinToString(" ") { it.name }

			    val funpre = extractSpec(pair.value, "Requires", pair.value.type)
			    val funpost = extractSpec(pair.value, "Ensures", pair.value.type)

				val paramsTyped = params.joinToString(" ") { "(${it.name} ${
					translateType(it.type)
				})" }
				if(params.count() > 0) {
					val transpost = funpost.toSMT().replace("result","($name $callParams)")
					val nextDef = "\n(assert (forall ($paramsTyped) (=> ${funpre.toSMT()} $transpost)))"

					if(isGeneric(pair.value.type) && !isConcreteGeneric(pair.value.type)){
						genericFunctions[name] = Triple((pair.value.type as DataTypeType), paramTypes,
							Function("(=> ${funpre.toSMT()} $transpost)", params.map{Function(it.name)} ))
					}
					defs += nextDef
				}else{
					val transpost = funpost.toSMT().replace("result","$name ")
					val nextDef = "\n(assert  (=> ${funpre.toSMT()} $transpost))"
					defs += nextDef
				}

		    }
		    ret += (sigs+defs)
	    }
		    if(direct.isNotEmpty()) {
			    var sigs = ""
			    var defs = ""
			    for (pair in direct) {
					val def =  pair.value.functionDef.getChild(0) as PureExp
					val term =exprToTerm(translateExpression(def, def.type, emptyMap(),true).first)
					if(pair.value !is ParametricFunctionDecl){
						val params = pair.value.params
						sigs += "\t(${functionNameSMT(pair.value)} (${
							params.fold("") { acc, nx ->
								"$acc (${nx.name} ${translateType(nx.type)})"
							}
						})  ${translateType(def.type)})\n"
						defs += "\t${term.toSMT()}\n"
					}
			    }
				if(sigs.isNotBlank())
					ret += "\n(define-funs-rec(\n$sigs)(\n$defs))"
		    }
	    return ret
    }

	private fun hasContract(fDecl: FunctionDecl) : Boolean {
		return fDecl.annotationList.any { it.type.toString().endsWith(".Spec") }
	}


	fun init(model: Model) {
		known.clear()
		genericFunctions.clear()
		parametricFunctions.clear()
		concreteParametricNameSMT.clear()
		parametricFunctionTypeMap.clear()
		concreteFunctionsToSMT.clear()

		ADTRepos.clearConcreteGenerics()
		for (mDecl in model.moduleDecls){
			if(mDecl.name.startsWith("ABS") && !mDecl.name.startsWith("ABS.StdLib")) continue

			for (decl in mDecl.decls){

				if(decl is FunctionDecl){
					if(mDecl.name.startsWith("ABS") && decl.name !in builtInFunctionNames.minus("abs")) continue
					initFunctionDef(decl)
				}
			}
		}
		initParametricFunctions()
	}

	private fun initFunctionDef(fDecl: FunctionDecl) {
		if(fDecl.functionDef is ExpFunctionDef){
			known[fDecl.qualifiedName] = fDecl
		} else {
			if(reporting) throw Exception("builtin types not supported")
			System.err.println("builtin types not supported")
			exitProcess(-1)
		}
	}
	fun extractAll(usedType: KClass<out DeductType>) : List<Pair<String,SymbolicNode>> {
		return known.filter { hasContract(it.value) }.map { Pair(it.key,it.value.exctractFunctionNode(usedType)) }
	}

	fun genericFunctionsName(function : Function) :String{
		val genericType = genericFunctions[function.name]!!.first
		val genericParams = genericFunctions[function.name]!!.second
		val concreteParams = function.params.map { param -> getReturnType(param) }

		val mapGenericConcrete = genericParams.zip(concreteParams).filter { pair -> pair.first != pair.second }.toMap()
		val concreteTypes = genericType.typeArgs.map { gT :Type -> if(gT in mapGenericConcrete) mapGenericConcrete[gT] else gT }
		val additionalName = concreteTypes.joinToString("_") { cT -> genericTypeSMTName(cT!!) }
		return "${function.name}_$additionalName"
	}

	fun functionNameSMT(functionDecl: FunctionDecl):String{
		if(functionDecl.qualifiedName.startsWith("ABS."))
			return functionDecl.name
		return functionDecl.qualifiedName.replace(".", "-")
	}

	fun concretizeFunctionTosSMT() : String{
		val list = concreteParametricNameSMT.keys. map { Pair(it.first, it.second)}
		list.forEach { concretizeFunctionToSMT(it.first, it.second) }
		val sigsDefs = concreteFunctionsToSMT.map { it.value }.toMap()
		if (sigsDefs.isNotEmpty())
			return "\n(define-funs-rec (\n${sigsDefs.keys.joinToString(" ") { it }})(\n${sigsDefs.values.joinToString(" ") { it }}))"
		return "; no parametric declarations"
	}

	fun getParameterMap(function: Function) : Map<TypeParameter,Type>{
		if(function.name !in parametricFunctions)
			throw Exception("Function ${function.name} not defined")
		else {
			val map = getMapTypes(parametricFunctions[function.name]!!.params.toList(), function.params)
			val newMap = (parametricFunctions[function.name] as ParametricFunctionDecl).typeParameterList.associate{
				Pair(it.type as TypeParameter,map[it.type as TypeParameter]!!)
			}
			return newMap
		}
	}

	fun concretizeNameToSMT(function:Function): String{
		val first = Pair(function.name,function.params.map{ getReturnType(it)})
		parametricFunctionTypeMap[first] = getParameterMap(function)
		if(first !in concreteParametricNameSMT)
			concreteParametricNameSMT[first] = "${function.name}_${parametricFunctionTypeMap[first]!!.values.joinToString("_") { translateType(it) }}"
		return concreteParametricNameSMT[first]!!
	}

	fun concretizeFunctionToSMT(name:String, paramTypes:List<Type>) {
		if(Pair(name,paramTypes) !in parametricFunctionTypeMap )
			throw Exception("Function $name with parameters of types ${paramTypes.map { it.qualifiedName }} not defined")

		val map = parametricFunctionTypeMap[Pair(name,paramTypes)]!!
		val functionDecl = this.parametricFunctions[name]!!

		val concreteFunctionName =
			"${name}_${map.values.joinToString("_") { translateType(it) }}"

		val definition = functionDecl.functionDef.getChild(0) as PureExp
		val term = exprToTerm(translateExpression(definition, applyBinding(definition.type, map), emptyMap(), true, map).first)

		(term.iterate { it is Function  && it.name in parametricFunctions } as Set<Function>).forEach{
			func:Function ->
				val funcName = func.name
				val funcParamTypes = func.params.map { applyBinding(getReturnType(it), map) }
				if(name != funcName || funcParamTypes!=paramTypes) {
					concretizeNameToSMT(func)
					concretizeFunctionToSMT(funcName, funcParamTypes)
				}
		}
		val params = functionDecl.params
		val sig = "\t($concreteFunctionName (${
			params.fold("") { acc, nx ->
				"$acc (${nx.name} ${
						translateType(applyBinding(nx.type, map))
				})"
			}
		})  ${translateType(applyBinding(definition.type, map))})\n"

		val def = "\t${term.toSMT()}\n"

		concreteFunctionsToSMT[Pair(name,paramTypes)] =  Pair(sig, def)
	}

}

data class Repository(val model : Model?,
					  val classReqs : MutableMap<String,Pair<Formula, ClassDecl>> = mutableMapOf(),
					  val methodReqs : MutableMap<String,Pair<Formula, MethodSig>> = mutableMapOf(),
					  val methodEnss : MutableMap<String,Pair<Formula, MethodSig>> = mutableMapOf(),
					  val syncMethodReqs : MutableMap<String,Pair<Formula, MethodSig>> = mutableMapOf(),
					  val syncMethodEnss : MutableMap<String,Pair<Formula, MethodSig>> = mutableMapOf()){

    fun populateClassReqs(model: Model) {
        for(moduleDecl in model.moduleDecls) {
            if(moduleDecl.name.startsWith("ABS.")) continue
            for (decl in moduleDecl.decls) {
                if (decl is ClassDecl) {
                    val spec = extractSpec(decl, "Requires", UnknownType.INSTANCE)
                    classReqs[moduleDecl.name+"."+decl.name] = Pair(spec,decl)
                }
            }
        }
    }
    fun populateMethodReqs(model: Model) {
        for(moduleDecl in model.moduleDecls) {
            if(moduleDecl.name.startsWith("ABS.")) continue
            for (decl in moduleDecl.decls) {
                if (decl is InterfaceDecl) {
                    for (mDecl in decl.allMethodSigs) {
                        val spec = extractSpec(mDecl, "Requires", mDecl.type)
                        val spec2 = extractSpec(mDecl, "Ensures", mDecl.type)
                        methodReqs[decl.qualifiedName+"."+mDecl.name] = Pair(spec, mDecl)
                        methodEnss[decl.qualifiedName+"."+mDecl.name] = Pair(spec2, mDecl)
                    }
                }
                if(decl is ClassDecl){
                    for(mImpl in decl.methods){
                        val iUse = getDeclaration(mImpl.methodSig, mImpl.contextDecl as ClassDecl)
                        val syncSpecReq = extractSpec(mImpl, "Requires", mImpl.type)
                        val syncSpecEns = extractSpec(mImpl, "Ensures", mImpl.type)
                        syncMethodReqs[decl.qualifiedName+"."+mImpl.methodSig.name] = Pair(syncSpecReq, mImpl.methodSig)
                        syncMethodEnss[decl.qualifiedName+"."+mImpl.methodSig.name] = Pair(syncSpecEns, mImpl.methodSig)
                        if(iUse == null){
                            methodReqs[decl.qualifiedName+"."+mImpl.methodSig.name] = Pair(True, mImpl.methodSig)
                            methodEnss[decl.qualifiedName+"."+mImpl.methodSig.name] = Pair(True, mImpl.methodSig)
                        } else {
                            val spec = extractSpec(iUse.allMethodSigs.first { it.matches(mImpl.methodSig) }, "Requires",
								iUse.allMethodSigs.first { it.matches(mImpl.methodSig) }.type
							)
                            methodReqs[decl.qualifiedName+"."+mImpl.methodSig.name] = Pair(spec, mImpl.methodSig)
                            val spec2 = extractSpec(iUse.allMethodSigs.first { it.matches(mImpl.methodSig) }, "Ensures",
								iUse.allMethodSigs.first { it.matches(mImpl.methodSig) }.type
							)
                            methodEnss[decl.qualifiedName+"."+mImpl.methodSig.name] = Pair(spec2, mImpl.methodSig)
                        }
                    }
                }
            }
        }
    }
}

fun getMapTypes(parametricTypes:List<ParamDecl>, concreteType:List<Term>): Map<TypeParameter,Type>{
	val zip = parametricTypes.zip(concreteType)
	val map = zip.map {
		getMapType(it.first.type, getReturnType(it.second))
	}
	val sequence = map.asSequence()
	return sequence.flatMap { it.asSequence() }.groupBy({ it.key }, { it.value }).mapValues { entry -> entry.value.first() }
}

fun getMapType(parametricType:Type, concreteType:Type) : Map<TypeParameter,Type>{
	val map = mutableMapOf<TypeParameter,Type>()
	if(parametricType is TypeParameter)
		return mapOf(parametricType to concreteType)
	else if(parametricType is DataTypeType && concreteType is DataTypeType)
	parametricType.typeArgs.zip(concreteType.typeArgs).forEach {
		if(it.first is TypeParameter)
			map += Pair(it.first as TypeParameter, it.second)
		else if(it.first is DataTypeType && it.second is DataTypeType)
			map.putAll(getMapType(it.first as DataTypeType, it.second as DataTypeType))
		else throw Exception("Cannot Map Types: [$parametricType] and [$concreteType]")
	}
	else if(parametricType is DataTypeType || concreteType is DataTypeType) throw Exception("Cannot Map Types: [$parametricType] and [$concreteType]")
	return map
}


fun applyBinding(type:Type, map: Map<TypeParameter,Type>) :Type {
	val ret= if(type is DataTypeType){
		val argTypes = mutableListOf<Type>()
		type.typeArgs.forEach {
			argTypes+=applyBinding(it , map)
		}
		DataTypeType(type.getDecl(), argTypes)

		}else {
			if ((type is TypeParameter || type is BoundedType) && type in map)  map[type]!! else type
		}
	return ret
}