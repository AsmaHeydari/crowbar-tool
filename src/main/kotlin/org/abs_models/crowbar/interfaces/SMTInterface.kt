package org.abs_models.crowbar.interfaces

import org.abs_models.crowbar.data.*
import org.abs_models.crowbar.data.Function
import org.abs_models.crowbar.main.*
import org.abs_models.crowbar.main.ADTRepos.libPrefix
import org.abs_models.crowbar.main.ADTRepos.objects
import org.abs_models.crowbar.main.ADTRepos.setUsedHeaps
import org.abs_models.crowbar.main.FunctionRepos.concretizeFunctionTosSMT
import org.abs_models.crowbar.types.booleanFunction
import org.abs_models.frontend.typechecker.DataTypeType
import org.abs_models.frontend.typechecker.Type
import java.io.File
import java.util.concurrent.TimeUnit


val valueOf = """
    (declare-fun   valueOf_ABS_StdLib_Int (ABS.StdLib.Fut) Int)
    (declare-fun   valueOf_ABS_StdLib_Bool(ABS.StdLib.Fut) Bool)
""".trimIndent()
val smtHeader = """
    ; static header
    (set-option :produce-models true)
    (set-logic ALL)
    (declare-fun valueOf_Int (Int) Int)
    (declare-fun hasRole (Int String) Bool)
    (define-sort ABS.StdLib.Int () Int)
    (define-sort ABS.StdLib.Float () Real)
    (define-sort ABS.StdLib.Bool () Bool)
    (define-sort ABS.StdLib.String () String)
    (declare-const Unit Int)
    (assert (= Unit 0))
    (declare-sort UNBOUND 0)
    ${DefineSortSMT("Field", "Int").toSMT("\n")}
    ; end static header
    """.trimIndent()

@Suppress("UNCHECKED_CAST")
fun generateSMT(ante : Formula, succ: Formula, modelCmd: String = "") : String {

    resetWildCards()

    // application of update to generate pre and post condition
    var  pre = deupdatify(ante)
    var post = deupdatify(succ)


    // storing information about used heap for concise proofs
    val fields =  (pre.iterate { it is Field } + post.iterate { it is Field }) as Set<Field>
    setUsedHeaps(fields.map{libPrefix(it.concrType.qualifiedName)}.toSet())


    // generation of the generics occurring in pre and post condition
    ((pre.iterate { it is DataTypeConst && isConcreteGeneric(it.concrType!!) } + post.iterate { it is DataTypeConst && isConcreteGeneric(it.concrType!!) }) as Set<DataTypeConst>).map {
        ADTRepos.addGeneric(it.concrType!! as DataTypeType) }

    val vars =  ((pre.iterate { it is ProgVar } + post.iterate { it is ProgVar  }) as Set<ProgVar>).filter {
        it.name != "heap" && it.name !in specialHeapKeywords}
    val heaps =  ((pre.iterate { it is Function } + post.iterate{ it is Function }) as Set<Function>).filter { it.name.startsWith("NEW") }
    val funcs =  ((pre.iterate { it is Function } + post.iterate { it is Function }) as Set<Function>).filter { it.name.startsWith("f_") }

    val prePlaceholders = pre.iterate { it is Placeholder } as Set<Placeholder>
    val postPlaceholders = post.iterate { it is Placeholder } as Set<Placeholder>
    val allPhs = prePlaceholders.union(postPlaceholders)
    val placeholders = prePlaceholders.intersect(postPlaceholders)
    val globPlaceholders = prePlaceholders.union(postPlaceholders).map {  ProgVar("${it.name}_${it.concrType}", it.concrType)}


    // replacing placeholders in precondition
    (pre.iterate { it is Predicate }.toList() as List<Predicate>).map {
        oldPredicate ->
        var newFormula= (oldPredicate.iterate { el -> el is Placeholder && el in placeholders } as Set<Placeholder>).fold(oldPredicate) {
                acc:Formula, ph : Placeholder->
            And(acc, Predicate("=", listOf(ph, ProgVar("${ph.name}_${ph.concrType}", ph.concrType))))
        }
        val wildcards = oldPredicate.iterate { it is WildCardVar || it is Placeholder } as Set<ProgVar>
         newFormula= Exists(wildcards.toList(), newFormula)
        pre = replaceInFormula(pre as Formula, oldPredicate, newFormula)
    }

    // replacing placeholders in precondition
    post.iterate { it is Predicate }.map {
            oldPredicate ->
        if(placeholders.isNotEmpty()) {
            postPlaceholders.map {
                val globalPh= ProgVar("${it.name}_${it.concrType}", it.concrType)
                val newFormula = replaceInLogicElement(oldPredicate as Predicate, mapOf(Pair(it, globalPh))) as Predicate
                post = replaceInFormula(post as Formula, oldPredicate, newFormula)
            }
        }
    }

    val preSMT =  (pre as Formula).toSMT()
    val negPostSMT = Not(post as Formula).toSMT()
    val functionDecl = FunctionRepos.toString()
    val concretizeFunctionTosSMT= concretizeFunctionTosSMT()
    //generation of translation for primitive
    val primitiveTypesDecl = ADTRepos.primitiveDtypesDecl.filter{!it.type.isStringType}.joinToString("\n\t") { "(declare-sort ${it.qualifiedName} 0)" }
    //generation of translation for wildcards
    val wildcards: String = wildCardsConst.map { FunctionDeclSMT(it.key,it.value).toSMT("\n\t") }.joinToString("") { it }
    //generation of translation for fields and variable declarations
    val fieldsDecl = fields.joinToString("\n\t"){ "(declare-const ${it.name} Field)\n" +
            if(it.concrType.isInterfaceType)
                "(assert (implements ${it.name} ${it.concrType.qualifiedName}))\n\t"
            else ""}
    val varsDecl = (vars.union(globPlaceholders).union(allPhs)).joinToString("\n\t"){"(declare-const ${it.name} ${
        translateType(it.concrType)}) ; ${it}\n" +
        if(it.concrType.isInterfaceType)
            "(assert (implements ${it.name} ${it.concrType.qualifiedName}))\n\t"
        else ""
    }


    //generation of translation for object "implements" assertions
    val objectImpl = heaps.joinToString("\n"){
        x:Function ->
        if(x.name in objects)
            objects[x.name]!!.types.joinToString("\n\t") {
                "(assert (implements " +
                        if(x.params.isNotEmpty()){
                        "(${x.name} " +
                        x.params.joinToString (" "){term -> term.toSMT()} +
                        ")  ${it.qualifiedName}))"}
                    else{
                        "${x.name} ${it.qualifiedName}))"
                        }

        }else ""

    }
    //generation of translation for object declaration
    val objectsDecl = heaps.joinToString("\n\t"){"(declare-fun ${it.name} (${it.params.joinToString (" "){
        term ->
        if(term is DataTypeConst) {
            ADTRepos.addGeneric(term.concrType!! as DataTypeType)
            genericTypeSMTName(term.concrType)
        }
        else if(term is Function && term.name in booleanFunction) "Bool"
        else { "Int"
        }
    }}) Int)"

    }

    //generation of translation for function declaration
    val funcsDecl = funcs.joinToString("\n") { "(declare-const ${it.name} Int)"}


    //generation of translation for fields contraints: each field has to be unique
    var fieldsConstraints = ""
    fields.forEach { f1 -> fields.minus(f1).forEach{ f2 -> if(libPrefix(f1.concrType.qualifiedName) == libPrefix(f2.concrType.qualifiedName)) fieldsConstraints += "(assert (not ${Eq(f1,f2).toSMT()}))" } } //??

    return """
;header
    $smtHeader
;primitive type declaration
    $primitiveTypesDecl
;valueOf
    $valueOf
;data type declaration
    ${ADTRepos.dTypesToSMT()}

;interface type declaration
    (declare-fun   implements (ABS.StdLib.Int Interface) Bool)
    (declare-fun   extends (Interface Interface) Bool)
    (assert (forall ((i1 Interface) (i2 Interface) (i3 Interface))
     (=> (and (extends i1 i2) (extends i2 i3))
      (extends i1 i3))))
      
    (assert (forall ((i1 Interface) (i2 Interface) (object ABS.StdLib.Int))
     (=> (and (extends i1 i2) (implements object i1))
      (implements object i2))))
      
      ${ADTRepos.interfaceExtendsToSMT()}
      
;generics declaration
    ${ADTRepos.genericsToSMT()}
;heaps declaration
    ${ADTRepos.heapsToSMT()}
;wildcards declaration
    $wildcards
    
; parametric functions decl
    $concretizeFunctionTosSMT
;functions declaration
    $functionDecl
;generic functions declaration :to be implemented and added
;    
;fields declaration
    $fieldsDecl
;variables declaration
    $varsDecl
;objects declaration
    $objectsDecl
    
;objects interface declaration
    $objectImpl
;funcs declaration
    $funcsDecl
;fields constraints
    $fieldsConstraints
    ; Precondition
    (assert $preSMT )
    ; Negated postcondition
    (assert $negPostSMT) 
    (check-sat)
    $modelCmd
    (exit)
    """.trimIndent()
}

/* https://stackoverflow.com/questions/35421699 */
fun String.runCommand(
        workingDir: File = File("."),
        timeoutAmount: Long = 60,
        timeoutUnit: TimeUnit = TimeUnit.SECONDS
): String? = try {
    ProcessBuilder(split("\\s".toRegex()))
            .directory(workingDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start().apply { waitFor(timeoutAmount, timeoutUnit) }
            .inputStream.bufferedReader().readText()
} catch (e: java.io.IOException) {
    e.printStackTrace()
    null
}


fun plainSMTCommand(smtRep: String) : String? {
    val path = "${tmpPath}out.smt2"
    File(path).writeText(smtRep)
    return "$smtPath $path".runCommand()
}

fun evaluateSMT(smtRep : String) : Boolean {
    val res = plainSMTCommand(smtRep)
    if(res != null && res.trim() == "unsat") return true
    if(res != null && res.trim() == "sat") return false
    if(res != null && res.trim() == "unknown") return false
    throw Exception("Error during SMT evaluation: $res")
}

fun evaluateSMT(ante: Formula, succ: Formula) : Boolean {
    val smtRep = generateSMT(ante, succ)
    if(verbosity >= Verbosity.VV) println("crowbar-v: \n$smtRep")
    return evaluateSMT(smtRep)
}

private val wildCardsConst = mutableMapOf<String,String>()

private var countWildCard = 0

fun createWildCard(dType: String,dTypeConcr: Type) : String{
    val wildCard = "_${countWildCard++}"
    if(dTypeConcr.simpleName in setOf("Pair","Triple"))
        wildCardsConst[wildCard] = genericSMTName(dTypeConcr.qualifiedName,dTypeConcr)
    else
        wildCardsConst[wildCard] = translateType(dTypeConcr)
    return wildCard
}

fun refreshWildCard(name: String, dType: String,dTypeConcr: Type) {
    if(dTypeConcr.simpleName in setOf("Pair","Triple"))
        wildCardsConst[name] = genericSMTName(dTypeConcr.qualifiedName,dTypeConcr)
    else
        wildCardsConst[name] = translateType(dTypeConcr)
}

fun resetWildCards() {
    wildCardsConst.clear()
    countWildCard = 0
}

    /*
    * Function that translates an ABS type into the SMT representation
    */
fun translateType(type:Type) : String{
    return if(type.isUnknownType)
        throw Exception("Unknown Type Cannot be Translated")
    else if (isGeneric(type)) {
        ADTRepos.addGeneric(type as DataTypeType)
        genericTypeSMTName(type)
    }else if(type.isTypeParameter)
        throw Exception("Parameter Type Cannot Be Translated")
    else
        libPrefix(type.qualifiedName)
}