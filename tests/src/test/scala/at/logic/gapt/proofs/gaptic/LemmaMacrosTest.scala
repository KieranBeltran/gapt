package at.logic.gapt.proofs.gaptic

import at.logic.gapt.expr._
import at.logic.gapt.proofs.Context.ProofNames
import at.logic.gapt.proofs.{ MutableContext, Sequent }
import org.specs2.mutable.Specification

class ProofMacrosTest extends Specification {

  "proof" in {
    Proof( Sequent() :+ ( "goal" -> hof"a -> a" ) ) {
      decompose
      trivial
    }
    ok
  }

  "incomplete proof" in {
    IncompleteProof( Sequent() :+ ( "goal" -> hof"(a -> a) & b" ) ) {
      destruct( "goal" )
      decompose; trivial
    }
    ok
  }

  "lemma" in {
    implicit val ctx = MutableContext.default()
    ctx += hoc"a: o"
    val proof = Lemma( Sequent() :+ ( "goal" -> hof"a -> a" ) ) {
      decompose
      trivial
    }
    ctx.get[ProofNames].names.keySet must contain( "proof" )
  }

}
