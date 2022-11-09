package org.abs_models.crowbar.types

import org.abs_models.crowbar.data.*
import org.abs_models.crowbar.interfaces.translateExpression
import org.abs_models.crowbar.interfaces.translateStatement
import org.abs_models.crowbar.investigator.Type
import org.abs_models.crowbar.main.*
import org.abs_models.crowbar.rule.FreshGenerator
import org.abs_models.crowbar.rule.MatchCondition
import org.abs_models.crowbar.rule.Rule
import org.abs_models.crowbar.tree.*
import org.abs_models.frontend.ast.ClassDecl
import org.abs_models.frontend.ast.FunctionDecl
import org.abs_models.frontend.ast.MainBlock
import org.abs_models.frontend.ast.Model
import java.util.*
import kotlin.system.exitProcess


interface PDLType : DeductType {
    companion object : PDLType

    override fun extractMethodNode(classDecl: ClassDecl, name: String, repos: Repository): SymbolicNode {
        throw Exception("PDL only applies to the main block")
    }

    override fun extractInitialNode(classDecl: ClassDecl): SymbolicNode {
        throw Exception("PDL only applies to the main block")
    }

    override fun exctractMainNode(model: Model): SymbolicNode {
        if(!model.hasMainBlock()){
            if(reporting) throw Exception("model has no main block!")
            System.err.println("model has no main block!")
            exitProcess(-1)
        }

        val v = appendStmt(translateStatement(model.mainBlock, Collections.emptyMap()), SkipStmt)
        val spec = extractPDLSpec(model.mainBlock)
        return SymbolicNode(SymbolicState(True, EmptyUpdate, Modality(v, spec), listOf()), Collections.emptyList())
    }

    fun extractPDLSpec(mainBlock: MainBlock) : PDLSpec{
        val postCond = extractSpec(mainBlock, "Ensures",mainBlock.type)
        println("Post Cond: "+ postCond.toString());
        val prob = extractTermSpec(mainBlock, "Prob")?.toSMT()
        println("Probability: "+ prob);

        return PDLSpec(postCond, prob.toString(), setOf())

        //TODO("IMPLEMENT ME")
    }

    override fun exctractFunctionNode(fDecl: FunctionDecl): SymbolicNode {
        throw Exception("PDL only applies to the main block")
    }

}

abstract class PDLEquation{
    abstract fun collectVars( set : MutableSet<String>)
    abstract fun toSMT() : String
}

data class PDLSetEquation(val head : String, val value : String) : PDLEquation(){
    override fun collectVars(set: MutableSet<String>) {
        if(head.startsWith("p")) set.add(head)
        if(value.startsWith("p")) set.add(value)
    }

    override fun toSMT(): String = "(assert (= $head $value))"

    override fun toString(): String =
        "$head = $value"

}
data class PDLBindEquation(val head : String, val value : String) : PDLEquation(){
    override fun collectVars(set: MutableSet<String>) {
        if(head.startsWith("p")) set.add(head)
        if(value.startsWith("p")) set.add(value)
    }

    override fun toSMT(): String = "(assert (<= $head $value))"

    override fun toString(): String =
        "$head <= $value"

}
data class PDLMinEquation(val head : String, val tail1 : String, val tail2 : String) : PDLEquation(){
    override fun collectVars(set: MutableSet<String>) {
        if(head.startsWith("p")) set.add(head)
        if(tail1.startsWith("p")) set.add(tail1)
        if(tail2.startsWith("p")) set.add(tail2)
    }

    override fun toSMT(): String = "(assert (<= $head (min $tail1 $tail2)))"

    override fun toString(): String =
        "$head <= min($tail1, $tail2)"

}

data class PDLSplitEquation(val head : String, val split : String, val tail1 : String, val tail2 : String) : PDLEquation(){
    override fun toString(): String =
        "$head = $split*$tail1 + (1-$split)*$tail2 "

    override fun collectVars(set: MutableSet<String>) {
        if(head.startsWith("p")) set.add(head)
        if(tail1.startsWith("p")) set.add(tail1)
        if(tail2.startsWith("p")) set.add(tail2)
    }

    override fun toSMT(): String
        = "(assert (<= ${head} (+ (* ${split} ${tail1}) (* (- 1 ${split}) ${tail2}))))"
}

data class PDLAbstractVar(val name : String) : PDLType, AbstractVar{
    override fun prettyPrint(): String {
        return name
    }
}


data class PDLSpec(val post : Formula, val prob : String, val equations : Set<PDLEquation>) : PDLType {
    override fun prettyPrint(): String {
        return post.prettyPrint()+" with "+prob+ " "+equations.joinToString(", ")
    }
    override fun iterate(f: (Anything) -> Boolean) : Set<Anything> = super.iterate(f)
}

object PDLScopeSkip : Rule(Modality(
    SeqStmt(ScopeMarker, StmtAbstractVar("CONT")),
    PDLAbstractVar("TYPE"))) {

    override fun transform(cond: MatchCondition, input : SymbolicState): List<SymbolicTree> {
        val cont = cond.map[StmtAbstractVar("CONT")] as Stmt
        val pitype = cond.map[PDLAbstractVar("TYPE")] as DeductType
        val res = SymbolicNode(SymbolicState(input.condition, input.update, Modality(cont, pitype), input.exceptionScopes), info = InfoScopeClose())
        return listOf(res)
    }
}

object PDLSkip : Rule(Modality(
        SkipStmt,
        PDLAbstractVar("Spec"))) {

    override fun transform(cond: MatchCondition, input : SymbolicState): List<SymbolicTree> {

        val spec = cond.map[PDLAbstractVar("Spec")] as PDLSpec

        val res = LogicNode(
            input.condition,
                UpdateOnFormula(input.update, spec.post)
            ,
            info = NoInfo()
        )

        var stNode: StaticNode? = null
        if(res.evaluate() && spec.prob.startsWith("p")){ //We need to do this in a proper way
            println(spec.prob + ">=1")
            val eqT = PDLSetEquation(spec.prob, "1")
            stNode = StaticNode("",spec.equations.plus(eqT))//.plus(eqT)
//            println("Static Node: " + stNode.toString())
        } else if(spec.prob.startsWith("p")){
            println(spec.prob + "<=0")
            val eqF = PDLSetEquation(spec.prob,"0")
            stNode = StaticNode("",spec.equations.plus(eqF))
//            println("Static Node: " + stNode.toString())
        } else  stNode = StaticNode("",spec.equations)
//        val zeros  = divByZeroNodes(listOf(retExpr), SkipStmt, input, repos)
        return listOf(stNode)
    }
    }
    object PDLSkipComposition : Rule(Modality(
        SeqStmt(SkipStmt, StmtAbstractVar("COUNT"))
        , PDLAbstractVar("Spec"))) {

        override fun transform(cond: MatchCondition, input: SymbolicState): List<SymbolicTree> {
            val count = cond.map[StmtAbstractVar("COUNT")] as Stmt
            val spec = cond.map[PDLAbstractVar("Spec")] as PDLSpec
//            println("count: "+count)
//            println("spec: " + spec)
            val sStat = SymbolicState(
                input.condition,
                input.update,
                Modality(count, spec),
                input.exceptionScopes
            )
//            println("Skip Composition: ")
//            println(sStat.modality)
            return listOf<SymbolicTree>(SymbolicNode(sStat, info = NoInfo()))
        }
    }
    abstract class PDLAssign(val repos: Repository,
                         conclusion : Modality) : Rule(conclusion){

    protected fun assignFor(loc : Location, rhs : Term) : ElementaryUpdate{
        return if(loc is Field)   ElementaryUpdate(Heap, store(loc, rhs)) else ElementaryUpdate(loc as ProgVar, rhs)
    }

    protected fun symbolicNext(loc : Location,
                               rhs : Term,
                               remainder : Stmt,
                               target : DeductType,
                               iForm : Formula,
                               iUp : UpdateElement,
                               infoObj: NodeInfo,
                               scopes: List<ConcreteExceptionScope>) : SymbolicNode{
        return SymbolicNode(SymbolicState(
            iForm,
            ChainUpdate(iUp, assignFor(loc,rhs)),
            Modality(remainder, target),
            scopes
        ), info = infoObj)
    }
    }

    class PDLLocAssign(repos: Repository) : PDLAssign(repos,Modality(
        SeqStmt(AssignStmt(LocationAbstractVar("LHS"), ExprAbstractVar("EXPR")), StmtAbstractVar("CONT")),
        PDLAbstractVar("TYPE"))) {

        override fun transform(cond: MatchCondition, input : SymbolicState): List<SymbolicTree> {
//            println("PDLAssign matched")
            val lhs = cond.map[LocationAbstractVar("LHS")] as Location
            val rhsExpr = cond.map[ExprAbstractVar("EXPR")] as Expr
            val rhs = exprToTerm(rhsExpr)
            val remainder = cond.map[StmtAbstractVar("CONT")] as Stmt
            val target = cond.map[PDLAbstractVar("TYPE")] as DeductType
            // for the CEG
            val info = InfoLocAssign(lhs, rhsExpr)

            //ABS pure expression may still throw implicit exceptions, which are handled by ZeroNodes
            val zeros  = divByZeroNodes(listOf(rhsExpr), remainder, input, repos)

            //consume statement and add ZeroNodes
//            println("PDLLocAssign is applied.")
            return listOf(symbolicNext(lhs, rhs, remainder, target, input.condition, input.update, info, input.exceptionScopes)) + zeros
        }
    }

    class PDLIf(val repos: Repository) : Rule(Modality(
        SeqStmt(IfStmt(ExprAbstractVar("LHS"), StmtAbstractVar("THEN"), StmtAbstractVar("ELSE")),
            StmtAbstractVar("CONT")),
        PDLAbstractVar("TYPE"))) {

        override fun transform(cond: MatchCondition, input : SymbolicState): List<SymbolicTree> {

            val contBody = SeqStmt(ScopeMarker, cond.map[StmtAbstractVar("CONT")] as Stmt) // Add a ScopeMarker statement to detect scope closure
            val guardExpr = cond.map[ExprAbstractVar("LHS")] as Expr

            //then
            val guardYes = exprToForm(guardExpr)
            val bodyYes = SeqStmt(cond.map[StmtAbstractVar("THEN")] as Stmt, contBody)
            val updateYes = input.update
            val typeYes = cond.map[PDLAbstractVar("TYPE")] as DeductType
            val resThen = SymbolicState(And(input.condition, UpdateOnFormula(updateYes, guardYes)), updateYes, Modality(bodyYes, typeYes), input.exceptionScopes)
//           println("PDLIf is applied: ")
//            println("PDLIf Then branch: "+ resThen.toString())
            val shortThen = LogicNode(And(input.condition, UpdateOnFormula(updateYes, guardYes)), False).evaluate()

            //else
            val guardNo = Not(exprToForm(guardExpr))
            val bodyNo = SeqStmt(cond.map[StmtAbstractVar("ELSE")] as Stmt, contBody)
            val updateNo = input.update
            val typeNo = cond.map[PDLAbstractVar("TYPE")] as DeductType
            val resElse = SymbolicState(And(input.condition, UpdateOnFormula(updateNo, guardNo)), updateNo, Modality(bodyNo, typeNo), input.exceptionScopes)
//            println("PDLIf Else branch: "+ resElse.toString())
            val shortElse = LogicNode(And(input.condition, UpdateOnFormula(updateYes, guardNo)), False).evaluate()
            val zeros  = divByZeroNodes(listOf(guardExpr), contBody, input, repos)
            var next = zeros
            if(shortThen && !shortElse) next = next + listOf(SymbolicNode(resElse, info = InfoIfThen(guardExpr)))
            else if(shortElse && !shortThen) next = next +listOf(SymbolicNode(resThen, info = InfoIfElse(guardExpr)))
            else next = next+ listOf(SymbolicNode(resThen, info = InfoIfThen(guardExpr)), SymbolicNode(resElse, info = InfoIfElse(guardExpr)))
            return next //listOf<SymbolicTree>(SymbolicNode(resThen, info = InfoIfThen(guardExpr)), SymbolicNode(resElse, info = InfoIfElse(guardExpr))) + zeros
        }
    }


    class PDLDemonIf(val repos: Repository) : Rule(Modality(
        SeqStmt(DemonicIfStmt(StmtAbstractVar("THEN"), StmtAbstractVar("ELSE")),
            StmtAbstractVar("CONT")),
        PDLAbstractVar("TYPE"))) {

        override fun transform(cond: MatchCondition, input : SymbolicState): List<SymbolicTree> {

            val contBody = SeqStmt(ScopeMarker, cond.map[StmtAbstractVar("CONT")] as Stmt) // Add a ScopeMarker statement to detect scope closure
            //val guardExpr = cond.map[ExprAbstractVar("LHS")] as Expr

            val spec = cond.map[PDLAbstractVar("TYPE")] as PDLSpec
            val p1 = FreshGenerator.getFreshPP().toSMT()
            val p2 = FreshGenerator.getFreshPP().toSMT()
            val p = spec.prob
            val eqs  = spec.equations.plus(PDLMinEquation(p,p1,p2))

            //then
           // val guardYes = exprToForm(guardExpr)
            val bodyYes = SeqStmt(cond.map[StmtAbstractVar("THEN")] as Stmt, contBody)
            val updateYes = input.update
            val typeYes = PDLSpec(spec.post, p1, eqs)
            val resThen = SymbolicState(input.condition, updateYes, Modality(bodyYes, typeYes), input.exceptionScopes)
//            println("PDLDemonIf is applied: ")
//            println("Demonic Then branch: "+ resThen.toString())
            //else
            //val guardNo = Not(exprToForm(guardExpr))
            val bodyNo = SeqStmt(cond.map[StmtAbstractVar("ELSE")] as Stmt, contBody)
            val updateNo = input.update
            val typeNo = PDLSpec(spec.post, p2, eqs)
            val resElse = SymbolicState(input.condition, updateNo, Modality(bodyNo, typeNo), input.exceptionScopes)
//            println("Demonic Else branch: "+ resElse.toString())

            val zeros  = divByZeroNodes(listOf(), contBody, input, repos)
            return listOf<SymbolicTree>(SymbolicNode(resThen), SymbolicNode(resElse)) + zeros
        }
    }

class PDLProbIf(val repos: Repository) : Rule(Modality(
    SeqStmt(ProbIfStmt(ExprAbstractVar("LHS"), StmtAbstractVar("THEN"), StmtAbstractVar("ELSE")),
        StmtAbstractVar("CONT")),
    PDLAbstractVar("Spec"))) {

    override fun transform(cond: MatchCondition, input: SymbolicState): List<SymbolicTree> {

        val contBody = SeqStmt(
            ScopeMarker,
            cond.map[StmtAbstractVar("CONT")] as Stmt
        ) // Add a ScopeMarker statement to detect scope closure
        val expectedValue = cond.map[ExprAbstractVar("LHS")] as Expr
        val spec = cond.map[PDLAbstractVar("Spec")] as PDLSpec

        val expTerm = exprToTerm(expectedValue).toSMT()

        val p1 = FreshGenerator.getFreshPP().toSMT()
        val p2 = FreshGenerator.getFreshPP().toSMT()
        val p = spec.prob

        //then
        val bodyYes = appendStmt(cond.map[StmtAbstractVar("THEN")] as Stmt, contBody)
        val updateYes = input.update
        val resThen = SymbolicState(
            input.condition,
            updateYes,
            Modality(bodyYes, PDLSpec(spec.post, p1, spec.equations.plus(PDLSplitEquation(p, expTerm, p1, p2)))),
            input.exceptionScopes
        )//Ask Eduard: Should we also add 0 <= p1 <=1?
//        println("PDLProbIf is applied: ")
        println("Probablistic Then branch: " + spec.equations)
        //else
        val bodyNo = appendStmt(cond.map[StmtAbstractVar("ELSE")] as Stmt, contBody)
        val updateNo = input.update
        val resElse = SymbolicState(
            input.condition,
            updateNo,
            Modality(bodyNo, PDLSpec(spec.post, p2, spec.equations.plus(PDLSplitEquation(p, expTerm, p1, p2)))),
            input.exceptionScopes
        )
        println("Probablistic Else branch: " + spec.equations)

//        val shortThen = LogicNode(input.condition, False).evaluate()
//        val shortElse = LogicNode(input.condition, UpdateOnFormula(updateYes, guardNo)), False).evaluate()

        return listOf<SymbolicTree>(SymbolicNode(resThen, info = NoInfo()), SymbolicNode(resElse, info = NoInfo()))
    }
}
class PDLWeakening(val repos: Repository) : Rule(Modality(
    StmtAbstractVar("COUNT")
    , PDLAbstractVar("Spec"))) {

    override fun transform(cond: MatchCondition, input: SymbolicState): List<SymbolicTree> {//Why this doesn't apply?
        val count = cond.map[StmtAbstractVar("COUNT")] as Stmt
        val spec = cond.map[PDLAbstractVar("Spec")] as PDLSpec
            println("count: "+count)
            println("spec: " + spec)

        val newProb = FreshGenerator.getFreshPP().toSMT()
        val newEq = PDLSplitEquation(spec.prob, " 1 ", newProb, " 0 ") //??
        val sStat = SymbolicState(
            input.condition,
            input.update,
            Modality(count, PDLSpec(spec.post, newProb, spec.equations.plus(newEq))),
            input.exceptionScopes
        )
        println("Weakening: ")
        println(sStat.modality)
        return listOf<SymbolicTree>(SymbolicNode(sStat, info = NoInfo()))
    }
}



