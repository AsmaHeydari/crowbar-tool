import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import org.abs_models.crowbar.main.*
import java.nio.file.Paths

class PDLTest : CrowbarTest() {
	init {
		"typeerror"{
			shouldThrow<Exception> {
				load(listOf(Paths.get("src/test/resources/exception.abs")))
			}
		}
		for (smt in listOf(z3)){
			if (!backendAvailable(smt)) continue
			println("testing with $smt as backend")


			"$smt pdlExample0" {
				smtPath = smt
				val (model, repos) = load(listOf(Paths.get("src/test/resources/pdlExample0.abs")))
				val res = model.exctractMainNode(pdl)
				executeNode(res, repos, pdl) shouldBe true
			}

			"$smt pdlExample1" {
				smtPath = smt
				val (model, repos) = load(listOf(Paths.get("src/test/resources/pdlExample1.abs")))
				val res = model.exctractMainNode(pdl)
				executeNode(res, repos, pdl) shouldBe false
			}

			"$smt pdlExample2" {
				smtPath = smt
				val (model, repos) = load(listOf(Paths.get("src/test/resources/pdlExample2.abs")))
				val res = model.exctractMainNode(pdl)
				executeNode(res, repos, pdl) shouldBe true
			}

			"$smt pdlExampleBernoli" {
				smtPath = smt
				val (model, repos) = load(listOf(Paths.get("src/test/resources/pdlExampleBernoli.abs")))
				val res = model.exctractMainNode(pdl)
				executeNode(res, repos, pdl) shouldBe true
			}

			"$smt pdlExampleDice" {
				smtPath = smt
				val (model, repos) = load(listOf(Paths.get("src/test/resources/pdlExampleDice.abs")))
				val res = model.exctractMainNode(pdl)
				executeNode(res, repos, pdl) shouldBe true
			}

			"$smt pdlExampleEduard" {
				smtPath = smt
				val (model, repos) = load(listOf(Paths.get("src/test/resources/pdlExampleEduard.abs")))
				val res = model.exctractMainNode(pdl)
				executeNode(res, repos, pdl) shouldBe true
			}

			"$smt pdlExampleMontyHall" {
				smtPath = smt
				val (model, repos) = load(listOf(Paths.get("src/test/resources/pdlExampleMontyHall.abs")))
				val res = model.exctractMainNode(pdl)
				executeNode(res, repos, pdl) shouldBe true
			}
		}
	}
}