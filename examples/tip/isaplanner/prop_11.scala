package at.logic.gapt.examples.tip.isaplanner

import at.logic.gapt.expr.{ TBase, _ }
import at.logic.gapt.proofs.Context
import at.logic.gapt.proofs.gaptic._

object prop_11 extends TacticsProof {

  ctx += TBase( "sk" )
  ctx += Context.InductiveType( ty"Nat", hoc"Z:Nat", hoc"S:Nat>Nat" )
  ctx += hoc"p:Nat>Nat"

  ctx += Context.InductiveType( ty"list", hoc"nil:list", hoc"cons:sk>list>list" )
  ctx += hoc"head:list>sk"
  ctx += hoc"tail:list>list"

  ctx += hoc"drop:Nat>list>list"

  val sequent = hols"""
                          def_head: ∀x ∀xs head(cons(x, xs)) = x,
                          def_tail: ∀x ∀xs tail(cons(x, xs)) = xs,
                          def_p: ∀x p(S(x)) = x,
                          def_drop_1: ∀y (drop(Z, y)) = y,
                          def_drop_2: ∀z drop(S(z), nil) = nil,
                          def_drop_3: ∀z ∀x2 ∀x3 drop(S(z), cons(x2, x3)) = drop(z, x3),
                          ax_list: ∀x ∀xs ¬nil = cons(x, xs),
                          ax_nat: ∀x ¬Z = S(x)
                          :-
                          goal: ∀xs drop(Z, xs) = xs
    """

  val proof = Lemma( sequent ) {
    trivial
  }
}
