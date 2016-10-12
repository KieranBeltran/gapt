package at.logic.gapt.proofs.lk

import at.logic.gapt.examples.{ BussTautology, primediv }
import at.logic.gapt.expr._
import at.logic.gapt.expr.hol.existsclosure
import at.logic.gapt.proofs.expansion._
import at.logic.gapt.proofs.{ Sequent, SequentMatchers }
import at.logic.gapt.prooftool.prooftool
import at.logic.gapt.provers.escargot.Escargot
import org.specs2.mutable._

import scalaz.\/-

class SolveTest extends Specification with SequentMatchers {
  "SolveTest" should {
    "prove sequent where quantifier order matters" in {
      // example from Chaudhuri et.al.: A multi-focused proof system ...
      val formula = hof"∃x (¬d(x) ∨ ∀y d(y))"

      val inst1 = ETOr(
        ETNeg( ETAtom( hoa"d(u)", Polarity.InAntecedent ) ), // -d(u)
        ETStrongQuantifier( hof"∀y d(y)", hov"v", ETAtom( hoa"d(v)", Polarity.InSuccedent ) ) // forall y d(y) +^v d(v)
      )

      val inst2 = ETOr(
        ETNeg( ETAtom( hoa"d(c)", Polarity.InAntecedent ) ), // -d(c)
        ETStrongQuantifier( hof"∀y d(y)", hov"u", ETAtom( hoa"d(u)", Polarity.InSuccedent ) ) // forall y d(y) +^u d(u)
      )

      // here, the second tree, containing c, must be expanded before u, as u is used as eigenvar in the c branch
      val et = ETWeakQuantifier( formula, Map( le"u" -> inst1, le"c" -> inst2 ) )
      val etSeq = Sequent() :+ et

      val \/-( lkProof ) = ExpansionProofToLK( ExpansionProof( etSeq ) )
      lkProof.endSequent must beMultiSetEqual( etSeq.shallow )
    }

    "prove top" in { solvePropositional( Sequent() :+ Top() ).toOption must beSome }
    "not prove bottom" in { solvePropositional( Sequent() :+ Bottom() ).toOption must beNone }
    "not refute top" in { solvePropositional( Top() +: Sequent() ).toOption must beNone }
    "refute bottom" in { solvePropositional( Bottom() +: Sequent() ).toOption must beSome }

    "prove ( p ∨ p ) ⊃ ( p ∧ p )" in {
      val F = hof"p|p -> p&p"
      solvePropositional( F ).toOption must beSome
    }

    "prove ( p ∧ p ) ⊃ ( p ∨ p )" in {
      val F = hof"p&p -> p|p"
      solvePropositional( F ).toOption must beSome
    }

    "prove BussTautology(2)" in { solvePropositional( BussTautology( 2 ) ).toOption must beSome }
  }

  "ExpansionProofToLK" should {
    "top" in { ExpansionProofToLK( ExpansionProof( Sequent() :+ ETTop( Polarity.InSuccedent ) ) ) must_== \/-( TopAxiom ) }
    "bottom" in { ExpansionProofToLK( ExpansionProof( ETBottom( Polarity.InAntecedent ) +: Sequent() ) ) must_== \/-( BottomAxiom ) }

    "equality" in {
      val Some( expansion ) = Escargot getExpansionProof existsclosure(
        hof"x+(y+z) = (x+y)+z" +:
          hof"x+y = y+x" +:
          Sequent()
          :+ hof"(a+(b+c))+(d+e) = (c+(d+(a+e)))+b"
      )
      val \/-( lk ) = ExpansionProofToLK( expansion )
      lk.conclusion must beMultiSetEqual( expansion.shallow )
    }

    "cuts" in {
      val es = ETAtom( hoa"p 0", Polarity.InAntecedent ) +:
        ETWeakQuantifier( hof"∀x (p x ⊃ p (s x))", Map(
          le"z" -> ETImp( ETAtom( hoa"p z", Polarity.InSuccedent ), ETAtom( hoa"p (s z)", Polarity.InAntecedent ) ),
          le"s z" -> ETImp( ETAtom( hoa"p (s z)", Polarity.InSuccedent ), ETAtom( hoa"p (s (s z))", Polarity.InAntecedent ) )
        ) ) +: Sequent() :+ ETAtom( hoa"p (s (s (s (s 0))))", Polarity.InSuccedent )
      val cutf = hof"∀x (p x ⊃ p (s (s x)))"
      val cut = ETImp(
        ETStrongQuantifier( cutf, hov"z",
          ETImp( ETAtom( hoa"p z", Polarity.InAntecedent ), ETAtom( hoa"p (s (s z))", Polarity.InSuccedent ) ) ),
        ETWeakQuantifier( cutf, Map(
          le"0" -> ETImp( ETAtom( hoa"p 0", Polarity.InSuccedent ), ETAtom( hoa"p (s (s 0))", Polarity.InAntecedent ) ),
          le"s (s 0)" -> ETImp( ETAtom( hoa"p (s (s 0))", Polarity.InSuccedent ), ETAtom( hoa"p (s (s (s (s 0))))", Polarity.InAntecedent ) )
        ) )
      )
      val epwc = ExpansionProofWithCut( Seq( cut ), es )
      ExpansionProofToLK( epwc ) must beLike {
        case \/-( p ) => p.conclusion must beMultiSetEqual( epwc.shallow )
      }
    }

    "read back higher order prime divisor proof" in {
      val p = DefinitionElimination( primediv.defs )( primediv.proof )
      ExpansionProofToLK.withTheory( primediv.ctx )( LKToExpansionProof( p ) ) must beLike {
        case \/-( p_ ) => p_.conclusion must beMultiSetEqual( p.conclusion )
      }
    }

    "useless quantifiers" in {
      val et = ETWeakQuantifier(
        hof"∃x true",
        Map(
          le"c" -> ETTop( Polarity.InSuccedent ),
          le"d" -> ETTop( Polarity.InSuccedent )
        )
      )
      ExpansionProofToLK( ExpansionProof( Sequent() :+ et ) ) must beLike {
        case \/-( p ) => p.conclusion must_== ( Sequent() :+ et.shallow )
      }
    }

    "skolem quantifiers" in {
      val formula = hof"?x!y p(x,y) -> !y?x p(x,y)"
      val Some( skolemExpansion ) = Escargot getExpansionProof formula
      ExpansionProofToLK( skolemExpansion ) must beLike {
        case \/-( p ) => p.conclusion must_== ( Sequent() :+ formula )
      }
    }

  }
}

