package at.logic.gapt.examples

import at.logic.gapt.expr._
import at.logic.gapt.proofs.Context._
import at.logic.gapt.proofs.gaptic._
import at.logic.gapt.proofs.Context
import at.logic.gapt.proofs.Sequent

object FirstSchema4 extends TacticsProof {
  //Type
  ctx += Context.InductiveType( "nat", hoc"0 : nat", hoc"s : nat>nat" )
  ctx += Context.Sort( "i" )
  //Term Constants
  ctx += hoc"z:i"
  ctx += hoc"g:i>i"
  ctx += hoc"f:i>nat"
  ctx += hoc"max:i>i>i"
  //Predicate Constants
  ctx += hoc"E: nat>nat>o"
  ctx += hoc"LEQ: i>i>o"
  ctx += hoc"LE: i>i>o"
  //Theory Axioms
  ctx += "efef" -> hos"E(f(p),n),E(f(q),n) :- E(f(p),f(q))"
  ctx += "leq_refl" -> hos" :- LEQ(p,p)"
  ctx += "leq_g" -> hos"LEQ(g(p),q):- LE(p,q)"
  ctx += "leq_max1" -> hos"LEQ(max(a, b), c) :- LEQ(a, c)"
  ctx += "leq_max2" -> hos"LEQ(max(a, b), c) :- LEQ(b, c)"
  //Proof Names
  ctx += hoc"omega: nat>nat"
  ctx += hoc"phi: nat>nat"
  ctx += hoc"chi: nat>i>nat"
  //Primitive Recursive Definitions
  ctx += PrimRecFun( hoc"POR:nat>i>o", "POR 0 x = E (f x) 0", "POR (s y) x = (E (f x) (s y) ∨ POR y x)" )
  //Proof End Sequent
  val esOmega = Sequent( Seq( hof"!x POR(n,x)" ), Seq( hof"?p?q (LE(p,q) & E(f(p),f(q)))" ) )
  val esphi = Sequent( Seq( hof"!x?y (LEQ(x,y) & POR(n,y) )" ), Seq( hof"?p?q (LE(p,q) & E(f(p),f(q)))" ) )
  val eschi = Sequent( Seq( hof" POR(n,a) " ), Seq( hof"POR(n,a)" ) )
  //Proof Declarations
  ctx += Context.ProofNameDeclaration( le"omega n", esOmega )
  ctx += Context.ProofNameDeclaration( le"phi n", esphi )
  ctx += Context.ProofNameDeclaration( le"chi n a", eschi )

  //We start by proving the basecase of chi. At this point it is safe to assume that each proof schema component
  // has at most one stepcase and one basecase. The system can handle more, but that algorithms associated with
  // proof schema only work for the above mentioned case.

  // To work with the base case we need to take the sequent from the proof name declaration and instantiate
  // it in the proper way, i.e. n-> 0 and a-> a
  val esChiBc = Sequent( Seq( "Ant_0" -> hof" POR(0,a)" ), Seq( "Suc_0" -> hof"POR(0,a)" ) )
  //notice that we associated a name with each formula this type. The propose  of this naming is to
  //refer to them in the tactic proof. we construct a tactic proof with the follow command. Try to run the following
  //in  gapt by typing FirstSchema.chiBc after loading the file and see what happens:
  val chiBc = Lemma( esChiBc ) {
    unfold( "POR" ) atMost 1 in "Suc_0"
  }

  //You should get the following:
  /*
gapt> FirstSchema.chiBc
at.logic.gapt.proofs.gaptic.QedFailureException: Proof not completed. There are still 1 open sub goals:
Ant_0: POR(0, a)
:-
Suc_0: E(f(a), 0)

  at at.logic.gapt.proofs.gaptic.LemmaMacros$.finish(language.scala:45)
  at at.logic.gapt.proofs.gaptic.LemmaMacros$.finishLemma(language.scala:55)
  ... 28 elided
*/

  //The Tactic unfold( "POR" ) atMost 1 in "Suc_0" unfolds the PR symbol "POR" at most one time
  // in the formula "Suc_0". If it is not unfoldable than it does not, otherwise it unfolds it once
  //notice that it tells us that there is still an open goal which we must close to prove the lemma.

  //go to FirstSchema5.scala to get the next step
}
