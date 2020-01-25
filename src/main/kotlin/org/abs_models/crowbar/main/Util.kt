package org.abs_models.crowbar.main

import org.abs_models.crowbar.data.*
import org.abs_models.crowbar.data.Stmt
import org.abs_models.crowbar.interfaces.translateABSExpToSymExpr
import org.abs_models.crowbar.tree.LogicNode
import org.abs_models.crowbar.tree.SymbolicNode
import org.abs_models.crowbar.tree.getStrategy
import org.abs_models.frontend.ast.*
import java.io.File
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.memberFunctions
import kotlin.system.exitProcess


fun output(text : String, level : Verbosity = Verbosity.NORMAL){
    if(verbosity >= level)
        println(text)
}

fun load(paths : List<Path>) : Pair<Model,Repository> {

    output("Crowbar  : loading files....")
    val input = paths.map{ File(it.toString()) }
    if(input.any { !it.exists() }) {
        System.err.println("file not found: $paths")
        exitProcess(-1)
    }

    output("Crowbar  : loading ABS model....")
    val model = try {
        org.abs_models.frontend.parser.Main().parse(input)
    } catch (e : Exception) {
        e.printStackTrace()
        System.err.println("error during parsing, aborting")
        exitProcess(-1)
    }
    if(model.hasTypeErrors())
        throw Exception("Compilation failed with type errors")

    val repos = Repository(model)
    return Pair(model, repos)
}

fun extractInheritedSpec(iDecl : InterfaceTypeUse, expectedSpec : String, mSig: MethodSig, default:Formula) : Formula? {
    for( miSig in iDecl.decl.findChildren(MethodSig::class.java)){
        if(miSig.matches(mSig)) return extractSpec(miSig, expectedSpec, default)
    }
    if(iDecl.decl.getChild(1) !is org.abs_models.frontend.ast.List<*>) throw Exception("Invalid specification AST ${iDecl.decl}")
    @Suppress("UNCHECKED_CAST")
    val uses = iDecl.decl.getChild(1) as org.abs_models.frontend.ast.List<InterfaceTypeUse>
    for(use in uses){
        val next = extractInheritedSpec(use, expectedSpec, mSig, default)
        if(next != null) return next
    }
    return null
}

fun extractInheritedSpec(mSig : MethodSig, expectedSpec : String, default:Formula = True) : Formula {
    val direct = extractSpec(mSig, expectedSpec, default)
    val conDecl = mSig.contextDecl
    if(conDecl is ClassDecl){
        for( iDecl in conDecl.implementedInterfaceUses){
            val next = extractInheritedSpec(iDecl, expectedSpec, mSig, default)
            if(next != null) return And(direct,next)
        }
    }
    return direct
}

fun<T : ASTNode<out ASTNode<*>>?> extractSpec(decl : ASTNode<T>, expectedSpec : String, default:Formula = True, multipleAllowed:Boolean = true) : Formula {
    var ret : Formula? = null
    for(annotation in decl.nodeAnnotations){
        if(!annotation.type.toString().endsWith(".Spec")) continue
        if(annotation.value !is DataConstructorExp) {
            throw Exception("Could not extract any specification from $decl because of the expected value")
        }
        val annotated = annotation.value as DataConstructorExp
        if(annotated.constructor != expectedSpec) continue
        val next = exprToForm(translateABSExpToSymExpr(annotated.getParam(0) as Exp))
        ret = if(ret == null) next else And(ret, next)
        if(!multipleAllowed) break
    }
    val next = if(decl is MethodImpl) extractInheritedSpec(decl.methodSig,expectedSpec,default) else null
    if(ret != null && next == null) return ret
    if(ret == null && next != null) return next
    if(ret != null && next != null) return And(ret,next)
    if(verbosity >= Verbosity.VVV)
        println("Crowbar-v: Could not extract $expectedSpec specification, using ${default.prettyPrint()}")
    return default
}


fun Model.extractAllClasses() : List<ClassDecl>{
    var l = emptyList<ClassDecl>()
    for( module in this.moduleDecls){
        if(module.name.startsWith("ABS.")) continue
        for( decl in module.decls){
            if(decl is ClassDecl)
                l = l + decl
        }
    }
    return l
}

fun Model.extractClassDecl(moduleName : String, className : String, repos : Repository) : ClassDecl {
    val moduleDecl = moduleDecls.firstOrNull { it.name == moduleName }
    if(moduleDecl == null){
        System.err.println("module not found: $moduleName")
        exitProcess(-1)
    }
    val classDecl : ClassDecl? = moduleDecl.decls.firstOrNull { it is ClassDecl && it.name == className } as ClassDecl?
    if(classDecl == null){
        System.err.println("class not found: ${moduleName}.${className}")
        exitProcess(-1)
    }

    if(    classDecl.params.any { !repos.isAllowedType(it.type.toString()) }
        || classDecl.fields.any { !repos.isAllowedType(it.type.toString()) } ){
        System.err.println("fields with non-Int type not supported")
        exitProcess(-1)
    }
    return classDecl
}

fun Model.exctractMainNode(usedType: KClass<out DeductType>) : SymbolicNode{
    val callTarget = usedType.memberFunctions.first { it.name == "exctractMainNode" }
    val obj = usedType.companionObject!!.objectInstance
    return callTarget.call(obj, this) as SymbolicNode
}

fun ClassDecl.extractInitialNode(usedType: KClass<out DeductType>) : SymbolicNode {
    val callTarget = usedType.memberFunctions.first { it.name == "extractInitialNode" }
    val obj = usedType.companionObject!!.objectInstance
    return callTarget.call(obj, this) as SymbolicNode
}

fun ClassDecl.extractMethodNode(usedType: KClass<out DeductType>, name : String, repos: Repository) : SymbolicNode {
    val callTarget = usedType.memberFunctions.first { it.name == "extractMethodNode" }
    val obj = usedType.companionObject!!.objectInstance
    return callTarget.call(obj, this, name, repos) as SymbolicNode
}

fun executeNode(node : SymbolicNode, repos: Repository, usedType : KClass<out DeductType>) : Boolean{ //todo: this should handle inference and static leafs now

    output("Crowbar  : starting symbolic execution....")
    val pit = getStrategy(usedType,repos)
    pit.execute(node)

    output("Crowbar-v: symbolic execution tree:",Verbosity.V)
    output(node.debugString(0),Verbosity.V)

    if(!node.finishedExecution()){
        System.err.println("could not finish symbolic execution")
        println(node.debugString(0))
        exitProcess(-1)
    }

    output("Crowbar  : closing open branches....")
    var closed = true
    for(l in node.collectLeaves()){
        if(l is LogicNode){
            output("Crowbar-v: "+ deupdatify(l.ante).prettyPrint()+"->"+deupdatify(l.succ).prettyPrint(), Verbosity.V)
            closed = closed && l.evaluate()
            output("Crowbar-v: verified? ${l.evaluate()}", Verbosity.V)
        } else {
            System.err.println("Crowbar-v: non-logical analysis nodes not supported")
            throw Exception("Crowbar-v: non-logical analysis nodes not supported")
            //exitProcess(-1)
        }
    }

    return closed
}

fun ClassDecl.executeAll(repos: Repository, usedType: KClass<out DeductType>): Boolean{
    val iNode = extractInitialNode(usedType)
    var totalClosed = executeNode(iNode, repos, usedType)
    output("Crowbar  : Verification <init>: $totalClosed")

    for(m in methods){
        val node = extractMethodNode(usedType, m.methodSig.name, repos)
        val closed = executeNode(node, repos, usedType)
        output("Crowbar  : Verification ${m.methodSig.name}: $closed \n")
        totalClosed = totalClosed && closed
    }
    return totalClosed
}

fun normalize(st : Stmt) : Stmt {
    return when(st){
        is SeqStmt -> {
            when(st.first){
                is SeqStmt -> {
                    val a = st.first.first
                    val b = st.first.second
                    val c = st.second
                    normalize(SeqStmt(a,SeqStmt(b,c)))
                }
                else -> SeqStmt(st.first, normalize(st.second))
            }
        }
        else -> st
    }
}


fun getDeclaration(mSig: MethodSig, cDecl : ClassDecl): InterfaceDecl? {
    for(iiDecl  in cDecl.implementedInterfaceUses.map{ it.decl }){
        val next = iiDecl as InterfaceDecl
        val ret = getIDeclaration(mSig,next)
        if(ret != null) return ret
    }
    return null
}

fun getIDeclaration(mSig: MethodSig, iDecl : InterfaceDecl): InterfaceDecl?{
    for(mDecl in iDecl.allMethodSigs){
        if(mDecl.matches(mSig)) return iDecl
    }
    for(iiDecl in iDecl.extendedInterfaceUseList){
        val next = iiDecl.decl as InterfaceDecl
        val ret = getIDeclaration(mSig,next)
        if(ret != null) return ret
    }
    return null
}
