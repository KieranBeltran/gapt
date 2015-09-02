package at.logic.gapt.proofs.lkNew

import at.logic.gapt.expr._
import at.logic.gapt.expr.hol.HOLPosition
import at.logic.gapt.proofs.lk.base._

import scala.collection.mutable

//<editor-fold desc="Base proof classes">

abstract class LKProof {
  /**
   * The name of the rule (in symbols).
   *
   * @return
   */
  def name: String

  /**
   * The name of the rule (in words).
   * @return
   */
  def longName: String

  /**
   * A list of SequentIndices denoting the main formula(s) of the rule.
   *
   * @return
   */
  def mainIndices: Seq[SequentIndex]

  /**
   * The list of main formulas of the rule.
   *
   * @return
   */
  def mainFormulas: Seq[HOLFormula] = mainIndices map { endSequent( _ ) }

  /**
   * A list of lists of SequentIndices denoting the auxiliary formula(s) of the rule.
   * The first list contains the auxiliary formulas in the first premise and so on.
   *
   * @return
   */
  def auxIndices: Seq[Seq[SequentIndex]]

  /**
   * The end-sequent of the rule.
   *
   * @return
   */
  def endSequent: HOLSequent

  /**
   * The immediate subproofs of the rule.
   *
   * @return
   */
  def subProofs: Seq[LKProof]

  /**
   * The upper sequents of the rule.
   *
   * @return
   */
  def premises: Seq[HOLSequent] = subProofs map ( _.endSequent )

  /**
   * A list of lists containing the auxiliary formulas of the rule.
   * The first list constains the auxiliary formulas in the first premise and so on.
   *
   * @return
   */
  def auxFormulas: Seq[Seq[HOLFormula]] = for ( ( p, is ) <- premises zip auxIndices ) yield p( is )

  /**
   * Returns the subproofs of this in post order.
   *
   * @return
   */
  def postOrder: Seq[LKProof]

  /**
   * Checks whether indices are in the right place and premise is defined at all of them.
   *
   * @param premise The sequent to be checked.
   * @param antecedentIndices Indices that should be in the antecedent.
   * @param succedentIndices Indices that should be in the succedent.
   */
  protected def validateIndices( premise: HOLSequent, antecedentIndices: Seq[SequentIndex], succedentIndices: Seq[SequentIndex] ): Unit = {
    val antSet = mutable.HashSet[SequentIndex]()
    val sucSet = mutable.HashSet[SequentIndex]()

    for ( i <- antecedentIndices ) i match {
      case Ant( _ ) =>

        if ( !premise.isDefinedAt( i ) )
          throw new LKRuleCreationException( s"Cannot create $longName: Sequent $premise is not defined at index $i." )

        if ( antSet contains i )
          throw new LKRuleCreationException( s"Cannot create $longName: Duplicate index $i for sequent $premise." )

        antSet += i

      case Suc( _ ) => throw new LKRuleCreationException( s"Cannot create $longName: Index $i should be in the antecedent." )
    }

    for ( i <- succedentIndices ) i match {
      case Suc( _ ) =>

        if ( !premise.isDefinedAt( i ) )
          throw new LKRuleCreationException( s"Cannot create $longName: Sequent $premise is not defined at index $i." )

        if ( sucSet contains i )
          throw new LKRuleCreationException( s"Cannot create $longName: Duplicate index $i for sequent $premise." )

        sucSet += i

      case Ant( _ ) => throw new LKRuleCreationException( s"Cannot create $longName: Index $i should be in the succedent." )
    }
  }
}

/**
 * An LKProof deriving a sequent from another sequent:
 * <pre>
 *        (π)
 *      Γ :- Δ
 *    ----------
 *     Γ' :- Δ'
 * </pre>
 */
abstract class UnaryLKProof extends LKProof {
  /**
   * The immediate subproof of the rule.
   *
   * @return
   */
  def subProof: LKProof

  /**
   * The object connecting the lower and upper sequents.
   *
   * @return
   */
  def getOccConnector: OccConnector

  /**
   * The upper sequent of the rule.
   *
   * @return
   */
  def premise = subProof.endSequent

  override def subProofs = Seq( subProof )

  override def postOrder = subProof.postOrder :+ this

}

object UnaryLKProof {
  def unapply( p: UnaryLKProof ) = Some( p.endSequent, p.subProof )
}

/**
 * An LKProof deriving a sequent from two other sequents:
 * <pre>
 *     (π1)     (π2)
 *    Γ :- Δ   Γ' :- Δ'
 *   ------------------
 *        Π :- Λ
 * </pre>
 */
abstract class BinaryLKProof extends LKProof {
  /**
   * The immediate left subproof of the rule.
   *
   * @return
   */
  def leftSubProof: LKProof

  /**
   * The immediate right subproof of the rule.
   *
   * @return
   */
  def rightSubProof: LKProof

  /**
   * The object connecting the lower and left upper sequents.
   *
   * @return
   */
  def getLeftOccConnector: OccConnector

  /**
   * The object connecting the lower and right upper sequents.
   *
   * @return
   */
  def getRightOccConnector: OccConnector

  /**
   * The left upper sequent of the rule.
   *
   * @return
   */
  def leftPremise = leftSubProof.endSequent

  /**
   * The right upper sequent of the rule.
   *
   * @return
   */
  def rightPremise = rightSubProof.endSequent

  override def subProofs = Seq( leftSubProof, rightSubProof )

  override def postOrder = leftSubProof.postOrder ++ rightSubProof.postOrder :+ this
}

object BinaryLKProof {
  def unapply( p: BinaryLKProof ) = Some( p.endSequent, p.leftSubProof, p.rightSubProof )
}

//</editor-fold>

// <editor-fold desc="Axioms">

/**
 * An LKProof consisting of a single sequent:
 * <pre>
 *     --------ax
 *      Γ :- Δ
 * </pre>
 */
abstract class InitialSequent extends LKProof {

  override def name = "ax"

  override def mainIndices = endSequent.indices

  override def auxIndices = Seq()

  override def subProofs = Seq()

  override def postOrder = Seq( this )
}

object InitialSequent {
  def unapply( proof: InitialSequent ) = Some( proof.endSequent )
}

case class ArbitraryAxiom( endSequent: HOLSequent ) extends InitialSequent {
  override def longName = "ArbitraryAxiom"
}

/**
 * An LKProof introducing ⊤ on the right:
 * <pre>
 *       --------⊤:r
 *         :- ⊤
 * </pre>
 */
case object TopAxiom extends InitialSequent {
  override def name: String = "⊤:r"
  override def longName = "TopAxiom"
  override def endSequent = HOLSequent( Nil, Seq( Top() ) )
  def mainFormula = Top()
}

/**
 * An LKProof introducing ⊥ on the left:
 * <pre>
 *       --------⊥:l
 *         ⊥ :-
 * </pre>
 */
case object BottomAxiom extends InitialSequent {
  override def name: String = "⊥:l"
  override def longName = "BottomAxiom"
  override def endSequent = HOLSequent( Seq( Bottom() ), Nil )
  def mainFormula = Top()
}

/**
 * An LKProof consisting of a logical axiom:
 * <pre>
 *    --------ax
 *     A :- A
 * </pre>
 * with A atomic.
 *
 * @param A The atom A.
 */
case class LogicalAxiom( A: HOLAtom ) extends InitialSequent {
  override def endSequent = HOLSequent( Seq( A ), Seq( A ) )
  override def longName = "LogicalAxiom"
  def mainFormula = A
}

/**
 * An LKProof consisting of a reflexivity axiom:
 * <pre>
 *    ------------ax
 *      :- s = s
 * </pre>
 * with s a term.
 *
 * @param s The term s.
 */
case class ReflexivityAxiom( s: LambdaExpression ) extends InitialSequent {
  override def endSequent = HOLSequent( Seq(), Seq( Eq( s, s ) ) )
  override def longName = "ReflexivityAxiom"
  def mainFormula = Eq( s, s )
}

object Axiom {
  def apply( sequent: HOLSequent ): InitialSequent = sequent match {
    case Sequent( Seq( f: HOLAtom ), Seq( g: HOLAtom ) ) if f == g => LogicalAxiom( f )
    case Sequent( Seq(), Seq( Top() ) ) => TopAxiom
    case Sequent( Seq( Bottom() ), Seq() ) => BottomAxiom
    case Sequent( Seq(), Seq( Eq( s: LambdaExpression, t: LambdaExpression ) ) ) if s == t => ReflexivityAxiom( s )
    case _ => ArbitraryAxiom( sequent )
  }

  def apply( ant: Seq[HOLFormula], suc: Seq[HOLFormula] ): InitialSequent = apply( Sequent( ant, suc ) )
}
// </editor-fold>

// <editor-fold desc="Structural rules">

/**
 * An LKProof ending with a left contraction:
 * <pre>
 *         (π)
 *     A, A, Γ :- Δ
 *    --------------
 *      A, Γ :- Δ
 * </pre>
 * @param subProof The subproof π.
 * @param aux1 The index of one occurrence of A.
 * @param aux2 The index of the other occurrence of A.
 */
case class ContractionLeftRule( subProof: LKProof, aux1: SequentIndex, aux2: SequentIndex ) extends UnaryLKProof {

  // <editor-fold desc="Sanity checks">

  validateIndices( premise, Seq( aux1, aux2 ), Seq() )

  if ( premise( aux1 ) != premise( aux2 ) )
    throw new LKRuleCreationException( s"Cannot create $longName: Auxiliar formulas ${premise( aux1 )} and ${premise( aux2 )} are not equal." )
  // </editor-fold>

  val mainFormula = premise( aux1 )
  val ( a1, a2 ) = if ( aux1 <= aux2 ) ( aux1, aux2 ) else ( aux2, aux1 )

  private val newContext = premise delete a2 delete a1

  // <editor-fold desc="Overrides">

  override def endSequent = mainFormula +: newContext

  override def mainIndices = Seq( Ant( 0 ) )

  override def auxIndices = Seq( Seq( aux1, aux2 ) )

  override def name = "c:l"

  override def longName = "ContractionLeftRule"

  override def getOccConnector = new OccConnector(
    endSequent,
    premise,
    Seq( aux1, aux2 ) +: ( premise.indicesSequent delete a2 delete a1 map { i => Seq( i ) } )
  )

  // </editor-fold>
}

object ContractionLeftRule extends RuleConvenienceObject( "ContractionLeftRule" ) {
  /**
   * Convenience constructor for c:l that, given a formula to contract on the left, will automatically pick the first two occurrences of that formula.
   *
   * @param subProof The subproof π.
   * @param f The formula to contract.
   * @return
   */
  def apply( subProof: LKProof, f: HOLFormula ): ContractionLeftRule = {
    val premise = subProof.endSequent

    val ( indices, _ ) = findFormulasInPremise( premise, Seq( f, f ), Seq() )

    new ContractionLeftRule( subProof, Ant( indices( 0 ) ), Ant( indices( 1 ) ) )
  }

}

/**
 * An LKProof ending with a right contraction:
 * <pre>
 *         (π)
 *     Γ :- Δ, A, A
 *    --------------
 *      Γ :- Δ, A
 * </pre>
 * @param subProof The subproof π.
 * @param aux1 The index of one occurrence of A.
 * @param aux2 The index of the other occurrence of A.
 */
case class ContractionRightRule( subProof: LKProof, aux1: SequentIndex, aux2: SequentIndex ) extends UnaryLKProof {

  // <editor-fold desc="Sanity checks">

  validateIndices( premise, Seq(), Seq( aux1, aux2 ) )

  if ( premise( aux1 ) != premise( aux2 ) )
    throw new LKRuleCreationException( s"Cannot create $longName: Auxiliar formulas ${premise( aux1 )} and ${premise( aux2 )} are not equal." )
  // </editor-fold>

  val mainFormula = premise( aux1 )
  val ( a1, a2 ) = if ( aux1 <= aux2 ) ( aux1, aux2 ) else ( aux2, aux1 )

  private val newContext = premise.delete( a2 ).delete( a1 )

  // <editor-fold desc="Overrides">

  override def endSequent = newContext :+ mainFormula

  private val n = endSequent.succedent.length - 1

  override def mainIndices = Seq( Suc( n ) )

  override def auxIndices = Seq( Seq( aux1, aux2 ) )

  override def name = "c:r"

  override def longName = "ContractionRightRule"

  override def getOccConnector = new OccConnector(
    endSequent,
    premise,
    ( premise.indicesSequent delete a2 delete a1 map { i => Seq( i ) } ) :+ Seq( aux1, aux2 )
  )

  // </editor-fold>
}

object ContractionRightRule extends RuleConvenienceObject( "ContractionRightRule" ) {
  /**
   * Convenience constructor for c:r that, given a formula to contract on the right, will automatically pick the first two occurrences of that formula.
   *
   * @param subProof The subproof π.
   * @param f The formula to contract.
   * @return
   */
  def apply( subProof: LKProof, f: HOLFormula ): ContractionRightRule = {
    val premise = subProof.endSequent

    val ( _, indices ) = findFormulasInPremise( premise, Seq(), Seq( f, f ) )
    new ContractionRightRule( subProof, Suc( indices( 0 ) ), Suc( indices( 1 ) ) )
  }

}

/**
 * An LKProof ending with a left weakening:
 * <pre>
 *        (π)
 *       Γ :- Δ
 *     ---------w:l
 *     A, Γ :- Δ
 * </pre>
 * @param subProof The subproof π.
 * @param formula The formula A.
 */
case class WeakeningLeftRule( subProof: LKProof, formula: HOLFormula ) extends UnaryLKProof {
  def endSequent = formula +: premise
  def auxIndices = Seq( Seq() )
  def name = "w:l"
  override def longName = "WeakeningLeftRule"
  def mainIndices = Seq( Ant( 0 ) )
  def mainFormula = formula

  def getOccConnector = new OccConnector(
    endSequent,
    premise,
    Seq() +: ( premise.indicesSequent map { i => Seq( i ) } )
  )
}

/**
 * An LKProof ending with a right weakening:
 * <pre>
 *        (π)
 *       Γ :- Δ
 *     ---------w:r
 *     Γ :- Δ, A
 * </pre>
 * @param subProof The subproof π.
 * @param formula The formula A.
 */
case class WeakeningRightRule( subProof: LKProof, formula: HOLFormula ) extends UnaryLKProof {
  def endSequent = premise :+ formula
  def auxIndices = Seq( Seq() )
  def name = "w:r"
  override def longName = "WeakeningRightRule"

  private val n = endSequent.succedent.length - 1
  def mainIndices = Seq( Suc( n ) )
  def mainFormula = formula

  def getOccConnector = new OccConnector(
    endSequent,
    premise,
    ( premise.indicesSequent map { i => Seq( i ) } ) :+ Seq()
  )
}

/**
 * An LKProof ending with a cut:
 * <pre>
 *      (π1)         (π2)
 *    Γ :- Δ, A   A, Π :- Λ
 *   ------------------------
 *        Γ, Π :- Δ, Λ
 * </pre>
 * @param leftSubProof The proof π,,1,,.
 * @param aux1 The index of A in π,,1,,.
 * @param rightSubProof The proof π,,2,,.
 * @param aux2 The index of A in π,,2,,.
 */
case class CutRule( leftSubProof: LKProof, aux1: SequentIndex, rightSubProof: LKProof, aux2: SequentIndex ) extends BinaryLKProof {
  // <editor-fold desc="Sanity checks">

  validateIndices( leftPremise, Seq(), Seq( aux1 ) )
  validateIndices( rightPremise, Seq( aux2 ), Seq() )

  if ( leftPremise( aux1 ) != rightPremise( aux2 ) )
    throw new LKRuleCreationException( s"Cannot create $longName: Auxiliar formulas are not the same." )
  // </editor-fold>

  private val ( leftContext, rightContext ) = ( leftPremise delete aux1, rightPremise delete aux2 )
  def endSequent = leftContext ++ rightContext

  def name = "cut"

  def longName = "CutRule"

  def auxIndices = Seq( Seq( aux1 ), Seq( aux2 ) )

  def mainIndices = Seq()

  def getLeftOccConnector = new OccConnector(
    endSequent,
    leftPremise,
    ( leftPremise.indicesSequent delete aux1 map { i => Seq( i ) } ) ++ ( rightPremise delete aux2 map { i => Seq() } )
  )

  def getRightOccConnector = new OccConnector(
    endSequent,
    rightPremise,
    ( leftPremise delete aux1 map { i => Seq() } ) ++ ( rightPremise.indicesSequent delete aux2 map { i => Seq( i ) } )

  )
}

object CutRule extends RuleConvenienceObject( "CutRule" ) {
  /**
   * Convenience constructor for cut that, given a formula A, will attempt to create a cut inference with that cut formula.
   *
   * @param leftSubProof
   * @param rightSubProof
   * @param A
   * @return
   */
  def apply( leftSubProof: LKProof, rightSubProof: LKProof, A: HOLFormula ): CutRule = {
    val ( leftPremise, rightPremise ) = ( leftSubProof.endSequent, rightSubProof.endSequent )

    val ( _, sucIndices ) = findFormulasInPremise( leftPremise, Seq(), Seq( A ) )
    val ( antIndices, _ ) = findFormulasInPremise( rightPremise, Seq( A ), Seq() )

    new CutRule( leftSubProof, Suc( sucIndices( 0 ) ), rightSubProof, Ant( antIndices( 0 ) ) )
  }
}

// </editor-fold>

// <editor-fold desc="Propositional rules">

/**
 * An LKProof ending with a negation on the left:
 * <pre>
 *       (π)
 *    Γ :- Δ, A
 *   -----------¬:l
 *   ¬A, Γ :- Δ
 * </pre>
 * @param subProof The proof π.
 * @param aux The index of A in the succedent.
 */
case class NegLeftRule( subProof: LKProof, aux: SequentIndex ) extends UnaryLKProof {

  // <editor-fold desc="Sanity checks">
  validateIndices( premise, Seq(), Seq( aux ) )
  // </editor-fold>

  val ( auxFormula, context ) = premise.focus( aux )
  val mainFormula = Neg( auxFormula )

  override def auxIndices = Seq( Seq( aux ) )
  override def mainIndices = Seq( Ant( 0 ) )
  override def endSequent = mainFormula +: context
  override def name = "¬:l"
  override def longName = "NegLeftRule"

  override def getOccConnector = new OccConnector(
    endSequent,
    premise,
    Seq( aux ) +: ( premise.indicesSequent delete aux map { i => Seq( i ) } )
  )
}

object NegLeftRule extends RuleConvenienceObject( "NegLeftRule" ) {
  /**
   * Convenience constructor that automatically uses the first occurrence of supplied aux formula.
   *
   * @param subProof
   * @param formula
   * @return
   */
  def apply( subProof: LKProof, formula: HOLFormula ): NegLeftRule = {
    val premise = subProof.endSequent

    val ( _, indices ) = findFormulasInPremise( premise, Seq(), Seq( formula ) )

    new NegLeftRule( subProof, Suc( indices( 0 ) ) )
  }
}

/**
 * An LKProof ending with a negation on the right:
 * <pre>
 *       (π)
 *    A, Γ :- Δ
 *   -----------¬:r
 *   Γ :- Δ, ¬A
 * </pre>
 * @param subProof The proof π.
 * @param aux The index of A in the antecedent.
 */
case class NegRightRule( subProof: LKProof, aux: SequentIndex ) extends UnaryLKProof {

  // <editor-fold desc="Sanity checks">
  validateIndices( premise, Seq( aux ), Seq() )
  // </editor-fold>

  val ( auxFormula, context ) = premise.focus( aux )
  val mainFormula = Neg( auxFormula )

  private val n = endSequent.succedent.length - 1
  override def auxIndices = Seq( Seq( aux ) )
  override def mainIndices = Seq( Suc( n ) )
  override def endSequent = context :+ mainFormula
  override def name = "¬:r"
  override def longName = "NegRightRule"

  override def getOccConnector = new OccConnector(
    endSequent,
    premise,
    ( premise.indicesSequent delete aux map { i => Seq( i ) } ) :+ Seq( aux )
  )
}

object NegRightRule extends RuleConvenienceObject( "NegRightRule" ) {
  /**
   * Convenience constructor that automatically uses the first occurrence of supplied aux formula.
   *
   * @param subProof
   * @param formula
   * @return
   */
  def apply( subProof: LKProof, formula: HOLFormula ): NegRightRule = {
    val premise = subProof.endSequent

    val ( indices, _ ) = findFormulasInPremise( premise, Seq( formula ), Seq() )

    new NegRightRule( subProof, Ant( indices( 0 ) ) )
  }
}

/**
 * An LKProof ending with a conjunction on the left:
 * <pre>
 *         (π)
 *     A, B, Γ :- Δ
 *    --------------
 *    A ∧ B, Γ :- Δ
 * </pre>
 * @param subProof The subproof π.
 * @param aux1 The index of A.
 * @param aux2 The index of B.
 */
case class AndLeftRule( subProof: LKProof, aux1: SequentIndex, aux2: SequentIndex ) extends UnaryLKProof {

  // <editor-fold desc="Sanity checks">

  validateIndices( premise, Seq( aux1, aux2 ), Seq() )

  // </editor-fold>

  val leftConjunct = premise( aux1 )
  val rightConjunct = premise( aux2 )
  val formula = And( leftConjunct, rightConjunct )
  val ( a1, a2 ) = if ( aux1 <= aux2 ) ( aux1, aux2 ) else ( aux2, aux1 )

  val newContext = premise delete a2 delete a1

  // <editor-fold desc="Overrides">

  override def endSequent = formula +: newContext

  override def mainIndices = Seq( Ant( 0 ) )

  override def auxIndices = Seq( Seq( aux1, aux2 ) )

  override def name = "∧:l"

  override def longName = "AndLeftRule"

  override def getOccConnector = new OccConnector(
    endSequent,
    premise,
    Seq( aux1, aux2 ) +: ( premise.indicesSequent delete a2 delete a1 map { i => Seq( i ) } )
  )

  // </editor-fold>
}

object AndLeftRule extends RuleConvenienceObject( "AndLeftRule" ) {
  /**
   * Convenience constructor for ∧:l that, given two formulas on the left, will automatically pick the respective first instances of these formulas.
   *
   * @param subProof
   * @param A
   * @param B
   * @return
   */
  def apply( subProof: LKProof, A: HOLFormula, B: HOLFormula ): AndLeftRule = {
    val premise = subProof.endSequent

    val ( indices, _ ) = findFormulasInPremise( premise, Seq( A, B ), Seq() )

    new AndLeftRule( subProof, Ant( indices( 0 ) ), Ant( indices( 1 ) ) )
  }

  /**
   * Convenience constructor for ∧:l that, given a proposed main formula A ∧ B, will attempt to create an inference with this main formula.
   *
   * @param subProof
   * @param F
   * @return
   */
  def apply( subProof: LKProof, F: HOLFormula ): AndLeftRule = F match {
    case And( f, g ) => apply( subProof, f, g )
    case _           => throw exception( s"Proposed main formula $F is not a conjunction." )
  }
}

/**
 * An LKProof ending with a conjunction on the right:
 * <pre>
 *    (π1)         (π2)
 *   Γ :- Δ, A    Π :- Λ, B
 * --------------------------
 *     Γ, Π :- Δ, Λ, A∧B
 * </pre>
 * @param leftSubProof The proof π,,1,,.
 * @param aux1 The index of A.
 * @param rightSubProof The proof π,,2,,
 * @param aux2 The index of B.
 */
case class AndRightRule( leftSubProof: LKProof, aux1: SequentIndex, rightSubProof: LKProof, aux2: SequentIndex ) extends BinaryLKProof {

  // <editor-fold desc="Sanity checks">
  validateIndices( leftPremise, Seq(), Seq( aux1 ) )
  validateIndices( rightPremise, Seq(), Seq( aux2 ) )
  // </editor-fold>

  val ( leftConjunct, leftContext ) = leftPremise focus aux1
  val ( rightConjunct, rightContext ) = rightPremise focus aux2

  val formula = And( leftConjunct, rightConjunct )

  def endSequent = leftContext ++ rightContext :+ formula

  private val n = endSequent.sizes._2 - 1

  def mainIndices = Seq( Suc( n ) )

  def auxIndices = Seq( Seq( aux1 ), Seq( aux2 ) )

  def name = "∧:r"

  def longName = "AndRightRule"

  def getLeftOccConnector = new OccConnector(
    endSequent,
    leftPremise,
    ( leftPremise.indicesSequent delete aux1 map { Seq.apply( _ ) } ) ++ ( rightPremise delete aux2 map { i => Seq() } ) :+ Seq( aux1 )
  )

  def getRightOccConnector = new OccConnector(
    endSequent,
    rightPremise,
    ( leftPremise delete aux1 map { i => Seq() } ) ++ ( rightPremise.indicesSequent delete aux2 map { Seq.apply( _ ) } ) :+ Seq( aux2 )
  )

}

object AndRightRule extends RuleConvenienceObject( "AndRightRule" ) {
  /**
   * Convenience constructor for ∧:r that, given formulas on the right, will automatically pick their respective first occurrences.
   *
   * @param leftSubProof
   * @param A
   * @param rightSubProof
   * @param B
   * @return
   */
  def apply( leftSubProof: LKProof, A: HOLFormula, rightSubProof: LKProof, B: HOLFormula ): AndRightRule = {
    val ( leftPremise, rightPremise ) = ( leftSubProof.endSequent, rightSubProof.endSequent )

    val ( _, leftIndices ) = findFormulasInPremise( leftPremise, Seq(), Seq( A ) )
    val ( _, rightIndices ) = findFormulasInPremise( rightPremise, Seq(), Seq( B ) )

    new AndRightRule( leftSubProof, Suc( leftIndices( 0 ) ), rightSubProof, Suc( rightIndices( 0 ) ) )
  }

  /**
   * Convenience constructor for ∧:r that, given a proposed main formula A ∧ B, will attempt to create an inference with this main formula.
   *
   * @param leftSubProof
   * @param rightSubProof
   * @param F
   * @return
   */
  def apply( leftSubProof: LKProof, rightSubProof: LKProof, F: HOLFormula ): AndRightRule = F match {
    case And( f, g ) => apply( leftSubProof, f, rightSubProof, g )
    case _           => throw exception( s"Proposed main formula $F is not a conjunction." )
  }
}

/**
 * An LKProof ending with a disjunction on the left:
 * <pre>
 *     (π1)         (π2)
 *   A, Γ :- Δ    B, Π :- Λ
 * --------------------------
 *     A∨B, Γ, Π :- Δ, Λ
 * </pre>
 * @param leftSubProof The proof π,,1,,.
 * @param aux1 The index of A.
 * @param rightSubProof The proof π,,2,,
 * @param aux2 The index of B.
 */
case class OrLeftRule( leftSubProof: LKProof, aux1: SequentIndex, rightSubProof: LKProof, aux2: SequentIndex ) extends BinaryLKProof {

  // <editor-fold desc="Sanity checks">
  validateIndices( leftPremise, Seq( aux1 ), Seq() )
  validateIndices( rightPremise, Seq( aux2 ), Seq() )
  // </editor-fold>

  val ( leftDisjunct, leftContext ) = leftPremise focus aux1
  val ( rightDisjunct, rightContext ) = rightPremise focus aux2

  val formula = Or( leftDisjunct, rightDisjunct )

  def endSequent = formula +: ( leftContext ++ rightContext )

  def mainIndices = Seq( Ant( 0 ) )

  def auxIndices = Seq( Seq( aux1 ), Seq( aux2 ) )

  def name = "∨:l"

  def longName = "OrLeftRule"

  def getLeftOccConnector = new OccConnector(
    endSequent,
    leftPremise,
    Seq( aux1 ) +: ( ( leftPremise.indicesSequent delete aux1 map { Seq.apply( _ ) } ) ++ ( rightPremise delete aux2 map { i => Seq() } ) )
  )

  def getRightOccConnector = new OccConnector(
    endSequent,
    rightPremise,
    Seq( aux2 ) +: ( ( leftPremise delete aux1 map { i => Seq() } ) ++ ( rightPremise.indicesSequent delete aux2 map { Seq.apply( _ ) } ) )
  )
}

object OrLeftRule extends RuleConvenienceObject( "OrLeftRule" ) {
  /**
   * Convenience constructor for ∨:l that, given formulas on the left, will automatically pick their respective first occurrences.
   *
   * @param leftSubProof
   * @param A
   * @param rightSubProof
   * @param B
   * @return
   */
  def apply( leftSubProof: LKProof, A: HOLFormula, rightSubProof: LKProof, B: HOLFormula ): OrLeftRule = {
    val ( leftPremise, rightPremise ) = ( leftSubProof.endSequent, rightSubProof.endSequent )

    val ( leftIndices, _ ) = findFormulasInPremise( leftPremise, Seq( A ), Seq() )
    val ( rightIndices, _ ) = findFormulasInPremise( rightPremise, Seq( B ), Seq() )

    new OrLeftRule( leftSubProof, Ant( leftIndices( 0 ) ), rightSubProof, Ant( rightIndices( 0 ) ) )
  }

  /**
   * Convenience constructor for ∨:r that, given a proposed main formula A ∨ B, will attempt to create an inference with this main formula.
   *
   * @param leftSubProof
   * @param rightSubProof
   * @param F
   * @return
   */
  def apply( leftSubProof: LKProof, rightSubProof: LKProof, F: HOLFormula ): OrLeftRule = F match {
    case Or( f, g ) => apply( leftSubProof, f, rightSubProof, g )
    case _          => throw exception( s"Proposed main formula $F is not a disjunction." )
  }
}

/**
 * An LKProof ending with a disjunction on the right:
 * <pre>
 *         (π)
 *     Γ :- Δ, A, B
 *    --------------
 *     Γ :- Δ, A ∨ B
 * </pre>
 * @param subProof The subproof π.
 * @param aux1 The index of A.
 * @param aux2 The index of B.
 */
case class OrRightRule( subProof: LKProof, aux1: SequentIndex, aux2: SequentIndex ) extends UnaryLKProof {

  // <editor-fold desc="Sanity checks">

  validateIndices( premise, Seq(), Seq( aux1, aux2 ) )

  // </editor-fold>

  val leftDisjunct = premise( aux1 )
  val rightDisjunct = premise( aux2 )
  val formula = Or( leftDisjunct, rightDisjunct )
  val ( a1, a2 ) = if ( aux1 <= aux2 ) ( aux1, aux2 ) else ( aux2, aux1 )

  val newContext = premise.focus( a2 )._2.focus( a1 )._2

  // <editor-fold desc="Overrides">

  override def endSequent = newContext :+ formula

  private val n = endSequent.succedent.length - 1

  override def mainIndices = Seq( Suc( n ) )

  override def auxIndices = Seq( Seq( aux1, aux2 ) )

  override def name = "∨:r"

  override def longName = "OrRightRule"

  override def getOccConnector = new OccConnector(
    endSequent,
    premise,
    ( premise.indicesSequent delete a2 delete a1 map { i => Seq( i ) } ) :+ Seq( aux1, aux2 )
  )

  // </editor-fold>
}

object OrRightRule extends RuleConvenienceObject( "OrRightRule" ) {
  /**
   * Convenience constructor for ∨:r that, given two formulas on the right, will automatically pick the respective first instances of these formulas.
   *
   * @param subProof
   * @param A
   * @param B
   * @return
   */
  def apply( subProof: LKProof, A: HOLFormula, B: HOLFormula ): OrRightRule = {
    val premise = subProof.endSequent

    val ( _, indices ) = findFormulasInPremise( premise, Seq(), Seq( A, B ) )

    new OrRightRule( subProof, Suc( indices( 0 ) ), Suc( indices( 1 ) ) )
  }

  /**
   * Convenience constructor for ∨:r that, given a proposed main formula A ∨ B, will attempt to create an inference with this main formula.
   *
   * @param subProof
   * @param F
   * @return
   */
  def apply( subProof: LKProof, F: HOLFormula ): OrRightRule = F match {
    case Or( f, g ) => apply( subProof, f, g )
    case _          => throw exception( s"Proposed main formula $F is not a disjunction." )
  }
}

/**
 * An LKProof ending with an implication on the left:
 * <pre>
 *     (π1)         (π2)
 *   Γ :- Δ, A    B, Π :- Λ
 * --------------------------
 *     A→B, Γ, Π :- Δ, Λ
 * </pre>
 * @param leftSubProof The proof π,,1,,.
 * @param aux1 The index of A.
 * @param rightSubProof The proof π,,2,,
 * @param aux2 The index of B.
 */
case class ImpLeftRule( leftSubProof: LKProof, aux1: SequentIndex, rightSubProof: LKProof, aux2: SequentIndex ) extends BinaryLKProof {
  // <editor-fold desc="Sanity checks">

  validateIndices( leftPremise, Seq(), Seq( aux1 ) )
  validateIndices( rightPremise, Seq( aux2 ), Seq() )

  // </editor-fold>

  val ( impPremise, leftContext ) = leftPremise focus aux1
  val ( impConclusion, rightContext ) = rightPremise focus aux2

  val formula = Imp( impPremise, impConclusion )

  def endSequent = formula +: ( leftContext ++ rightContext )

  def mainIndices = Seq( Ant( 0 ) )

  def auxIndices = Seq( Seq( aux1 ), Seq( aux2 ) )

  def name = "\u2283:l"

  def longName = "ImpLeftRule"

  def getLeftOccConnector = new OccConnector(
    endSequent,
    leftPremise,
    Seq( aux1 ) +: ( ( leftPremise.indicesSequent delete aux1 map { Seq.apply( _ ) } ) ++ ( rightPremise delete aux2 map { i => Seq() } ) )
  )

  def getRightOccConnector = new OccConnector(
    endSequent,
    rightPremise,
    Seq( aux2 ) +: ( ( leftPremise delete aux1 map { i => Seq() } ) ++ ( rightPremise.indicesSequent delete aux2 map { Seq.apply( _ ) } ) )
  )
}

object ImpLeftRule extends RuleConvenienceObject( "ImpLeftRule" ) {
  /**
   * Convenience constructor for →:l that, given aux formulas, will automatically pick their respective first occurrences.
   *
   * @param leftSubProof
   * @param A
   * @param rightSubProof
   * @param B
   * @return
   */
  def apply( leftSubProof: LKProof, A: HOLFormula, rightSubProof: LKProof, B: HOLFormula ): ImpLeftRule = {
    val ( leftPremise, rightPremise ) = ( leftSubProof.endSequent, rightSubProof.endSequent )

    val ( _, leftIndices ) = findFormulasInPremise( leftPremise, Seq(), Seq( A ) )
    val ( rightIndices, _ ) = findFormulasInPremise( rightPremise, Seq( B ), Seq() )

    new ImpLeftRule( leftSubProof, Suc( leftIndices( 0 ) ), rightSubProof, Ant( rightIndices( 0 ) ) )
  }

  /**
   * Convenience constructor for →:l that, given a proposed main formula A → B, will attempt to create an inference with this main formula.
   *
   * @param leftSubProof
   * @param rightSubProof
   * @param F
   * @return
   */
  def apply( leftSubProof: LKProof, rightSubProof: LKProof, F: HOLFormula ): ImpLeftRule = F match {
    case Imp( f, g ) => apply( leftSubProof, f, rightSubProof, g )
    case _           => throw exception( s"Proposed main formula $F is not a implication." )
  }
}

/**
 * An LKProof ending with an implication on the right:
 * <pre>
 *         (π)
 *     A, Γ :- Δ, B
 *    --------------
 *     Γ :- Δ, A → B
 * </pre>
 * @param subProof The subproof π.
 * @param aux1 The index of A.
 * @param aux2 The index of B.
 */
case class ImpRightRule( subProof: LKProof, aux1: SequentIndex, aux2: SequentIndex ) extends UnaryLKProof {

  // <editor-fold desc="Sanity checks">

  validateIndices( premise, Seq( aux1 ), Seq( aux2 ) )

  // </editor-fold>

  val impPremise = premise( aux1 )
  val impConclusion = premise( aux2 )
  val formula = Imp( impPremise, impConclusion )

  val newContext = premise delete aux1 delete aux2

  // <editor-fold desc="Overrides">

  override def endSequent = newContext :+ formula

  private val n = endSequent.succedent.length - 1

  override def mainIndices = Seq( Suc( n ) )

  override def auxIndices = Seq( Seq( aux1, aux2 ) )

  override def name = "\u2283:r"

  override def longName = "ImpRightRule"

  override def getOccConnector = new OccConnector(
    endSequent,
    premise,
    ( premise.indicesSequent delete aux1 delete aux2 map { i => Seq( i ) } ) :+ Seq( aux1, aux2 )
  )

  // </editor-fold>
}

object ImpRightRule extends RuleConvenienceObject( "ImpRightRule" ) {
  /**
   * Convenience constructor for →:r that, given two aux formulas, will automatically pick the respective first instances of these formulas.
   *
   * @param subProof
   * @param A
   * @param B
   * @return
   */
  def apply( subProof: LKProof, A: HOLFormula, B: HOLFormula ): ImpRightRule = {
    val premise = subProof.endSequent

    val ( antIndices, sucIndices ) = findFormulasInPremise( premise, Seq( A ), Seq( B ) )

    new ImpRightRule( subProof, Ant( antIndices( 0 ) ), Suc( sucIndices( 0 ) ) )
  }

  /**
   * Convenience constructor for →:r that, given a proposed main formula A → B, will attempt to create an inference with this main formula.
   *
   * @param subProof
   * @param F
   * @return
   */
  def apply( subProof: LKProof, F: HOLFormula ): ImpRightRule = F match {
    case Imp( f, g ) => apply( subProof, f, g )
    case _           => throw exception( s"Proposed main formula $F is not an implication." )
  }
}
// </editor-fold>

// <editor-fold desc="Quantifier rules">

/**
 * An LKProof ending with a universal quantifier on the left:
 * <pre>
 *            (π)
 *      A[x\t], Γ :- Δ
 *     ----------------∀:l
 *       ∀x.A, Γ :- Δ
 * </pre>
 * @param subProof The proof π.
 * @param aux The index of A[x\t].
 * @param A The formula A.
 * @param term The term t.
 * @param v The variable x.
 */
case class ForallLeftRule( subProof: LKProof, aux: SequentIndex, A: HOLFormula, term: LambdaExpression, v: Var ) extends UnaryLKProof {
  // <editor-fold desc="Sanity checks">

  validateIndices( premise, Seq( aux ), Seq() )

  val ( auxFormula, context ) = premise focus aux

  if ( auxFormula != Substitution( v, term )( A ) )
    throw new LKRuleCreationException( s"Cannot create $longName: Substituting $term for $v in $A does not result in $auxFormula." )

  // </editor-fold>

  val mainFormula = All( v, A )

  def name = "∀:l"

  def longName = "ForallLeftRule"

  def endSequent = mainFormula +: context

  def mainIndices = Seq( Ant( 0 ) )

  def auxIndices = Seq( Seq( aux ) )

  def getOccConnector = new OccConnector(
    endSequent,
    premise,
    Seq( aux ) +: ( premise.indicesSequent delete aux map { i => Seq( i ) } )
  )
}

object ForallLeftRule {
  /**
   * Convenience constructor for ∀:l that, a main formula and a term, will try to construct an inference with these formulas.
   *
   * @param subProof
   * @param mainFormula
   * @param term
   * @return
   */
  def apply( subProof: LKProof, mainFormula: HOLFormula, term: LambdaExpression ): ForallLeftRule = {
    val premise = subProof.endSequent

    mainFormula match {
      case All( v, subFormula ) =>
        val auxFormula = Substitution( v, term )( subFormula )
        val i = premise.antecedent indexOf auxFormula

        if ( i == -1 )
          throw new LKRuleCreationException( s"Cannot create ForallLeftRule: Formula $auxFormula not found in antecedent of $premise." )

        ForallLeftRule( subProof, Ant( i ), subFormula, term, v )

      case _ => throw new LKRuleCreationException( s"Cannot create ForallLeftRule: Proposed main formula $mainFormula is not universally quantified." )
    }
  }

  def apply( subProof: LKProof, mainFormula: HOLFormula ): ForallLeftRule = mainFormula match {
    case All( v, subFormula ) => apply( subProof, mainFormula, v )

    case _                    => throw new LKRuleCreationException( s"Cannot create ForallLeftRule: Proposed main formula $mainFormula is not universally quantified." )
  }
}

/**
 * An LKProof ending with a universal quantifier on the right:
 * <pre>
 *           (π)
 *      Γ :- Δ, A[x\α]
 *     ---------------∀:r
 *      Γ :- Δ, ∀x.A
 * </pre>
 * This rule is only applicable if the eigenvariable condition is satisfied: α must not occur freely in Γ :- Δ.
 *
 * @param subProof The proof π.
 * @param aux The index of A[x\α].
 * @param eigenVariable The variable α.
 * @param quantifiedVariable The variable x.
 */
case class ForallRightRule( subProof: LKProof, aux: SequentIndex, eigenVariable: Var, quantifiedVariable: Var ) extends UnaryLKProof {

  // <editor-fold desc="Sanity checks">

  validateIndices( premise, Seq(), Seq( aux ) )

  val ( auxFormula, context ) = premise focus aux

  //eigenvariable condition
  if ( freeVariables( context ) contains eigenVariable )
    throw new LKRuleCreationException( s"Cannot create $longName: Eigenvariable condition is violated." )

  // </editor-fold>

  val mainFormula = All( quantifiedVariable, Substitution( eigenVariable, quantifiedVariable )( auxFormula ) )

  def endSequent = context :+ mainFormula

  def name = "∀:r"

  def longName = "ForallRightRule"

  private val n = endSequent.succedent.length - 1

  def mainIndices = Seq( Suc( n ) )

  def auxIndices = Seq( Seq( aux ) )

  def getOccConnector = new OccConnector(
    endSequent,
    premise,
    ( premise.indicesSequent delete aux map { i => Seq( i ) } ) :+ Seq( aux )
  )
}

object ForallRightRule extends RuleConvenienceObject( "ForallRightRule" ) {
  def apply( subProof: LKProof, mainFormula: HOLFormula, eigenVariable: Var ): ForallRightRule = mainFormula match {
    case All( v, subFormula ) =>
      val auxFormula = Substitution( v, eigenVariable )( subFormula )

      val premise = subProof.endSequent

      val ( _, indices ) = findFormulasInPremise( premise, Seq(), Seq( auxFormula ) )

      ForallRightRule( subProof, Suc( indices( 0 ) ), eigenVariable, v )

    case _ => throw exception( s"Proposed main formula $mainFormula is not universally quantified." )
  }

  def apply( subProof: LKProof, mainFormula: HOLFormula ): ForallRightRule = mainFormula match {
    case All( v, subFormula ) => apply( subProof, mainFormula, v )

    case _                    => throw exception( s"Proposed main formula $mainFormula is not universally quantified." )
  }
}

/**
 * An LKProof ending with an existential quantifier on the left:
 * <pre>
 *           (π)
 *      A[x\α], Γ :- Δ
 *     ---------------∀:r
 *       ∃x.A Γ :- Δ
 * </pre>
 * This rule is only applicable if the eigenvariable condition is satisfied: α must not occur freely in Γ :- Δ.
 *
 * @param subProof The proof π.
 * @param aux The index of A[x\α].
 * @param eigenVariable The variable α.
 * @param quantifiedVariable The variable x.
 */
case class ExistsLeftRule( subProof: LKProof, aux: SequentIndex, eigenVariable: Var, quantifiedVariable: Var ) extends UnaryLKProof {

  // <editor-fold desc="Sanity checks">

  validateIndices( premise, Seq( aux ), Seq() )

  val ( auxFormula, context ) = premise focus aux

  //eigenvariable condition
  if ( freeVariables( context ) contains eigenVariable )
    throw new LKRuleCreationException( s"Cannot create $longName: Eigenvariable condition is violated." )

  // </editor-fold>

  val mainFormula = Ex( quantifiedVariable, Substitution( eigenVariable, quantifiedVariable )( auxFormula ) )

  def endSequent = mainFormula +: context

  def name = "∃:l"

  def longName = "ExistsLeftRule"

  def mainIndices = Seq( Ant( 0 ) )

  def auxIndices = Seq( Seq( aux ) )

  def getOccConnector = new OccConnector(
    endSequent,
    premise,
    Seq( aux ) +: ( premise.indicesSequent delete aux map { i => Seq( i ) } )
  )
}

object ExistsLeftRule extends RuleConvenienceObject( "ExistsLeftRule" ) {
  def apply( subProof: LKProof, mainFormula: HOLFormula, eigenVariable: Var ): ExistsLeftRule = mainFormula match {
    case Ex( v, subFormula ) =>
      val auxFormula = Substitution( v, eigenVariable )( subFormula )

      val premise = subProof.endSequent

      val ( indices, _ ) = findFormulasInPremise( premise, Seq( auxFormula ), Seq() )
      ExistsLeftRule( subProof, Ant( indices( 0 ) ), eigenVariable, v )

    case _ => throw exception( s"Proposed main formula $mainFormula is not existentially quantified." )
  }

  def apply( subProof: LKProof, mainFormula: HOLFormula ): ExistsLeftRule = mainFormula match {
    case Ex( v, subFormula ) => apply( subProof, mainFormula, v )

    case _                   => throw exception( s"Proposed main formula $mainFormula is not existentially quantified." )
  }
}

/**
 * An LKProof ending with an existential quantifier on the right:
 * <pre>
 *            (π)
 *      Γ :- Δ, A[x\t]
 *     ----------------∃:r
 *       Γ :- Δ, ∃x.A
 * </pre>
 * @param subProof The proof π.
 * @param aux The index of A[x\t].
 * @param A The formula A.
 * @param term The term t.
 * @param v The variable x.
 */
case class ExistsRightRule( subProof: LKProof, aux: SequentIndex, A: HOLFormula, term: LambdaExpression, v: Var ) extends UnaryLKProof {
  // <editor-fold desc="Sanity checks">

  validateIndices( premise, Seq(), Seq( aux ) )

  val ( auxFormula, context ) = premise focus aux

  if ( auxFormula != Substitution( v, term )( A ) )
    throw new LKRuleCreationException( s"Cannot create $longName: Substituting $term for $v in $A does not result in $auxFormula." )

  // </editor-fold>

  val mainFormula = Ex( v, A )

  def name = "∃:r"

  def longName = "ExistsRightRule"

  def endSequent = context :+ mainFormula

  private val n = endSequent.succedent.length - 1

  def mainIndices = Seq( Suc( n ) )

  def auxIndices = Seq( Seq( aux ) )

  def getOccConnector = new OccConnector(
    endSequent,
    premise,
    ( premise.indicesSequent delete aux map { i => Seq( i ) } ) :+ Seq( aux )
  )
}

object ExistsRightRule {
  /**
   * Convenience constructor for ∃:r that, a main formula and a term, will try to construct an inference with these formulas.
   *
   * @param subProof
   * @param mainFormula
   * @param term
   * @return
   */
  def apply( subProof: LKProof, mainFormula: HOLFormula, term: LambdaExpression ): ExistsRightRule = {
    val premise = subProof.endSequent

    mainFormula match {
      case Ex( v, subFormula ) =>
        val auxFormula = Substitution( v, term )( subFormula )
        val i = premise.succedent indexOf auxFormula

        if ( i == -1 )
          throw new LKRuleCreationException( s"Cannot create ExistsRightRule: Formula $auxFormula not found in antecedent of $premise." )

        ExistsRightRule( subProof, Suc( i ), subFormula, term, v )

      case _ => throw new LKRuleCreationException( s"Cannot create ExistsRightRule: Proposed main formula $mainFormula is not existentially quantified." )
    }
  }

  def apply( subProof: LKProof, mainFormula: HOLFormula ): ExistsRightRule = mainFormula match {
    case Ex( v, subFormula ) => apply( subProof, mainFormula, v )

    case _                   => throw new LKRuleCreationException( s"Cannot create ExistsRightRule: Proposed main formula $mainFormula is not existentially quantified." )
  }
}

// </editor-fold>

//<editor-fold desc="Equality rules">

case class EqualityLeft1Rule( subProof: LKProof, eq: SequentIndex, aux: SequentIndex, pos: HOLPosition ) extends UnaryLKProof {
  // <editor-fold desc="Sanity checks">

  validateIndices( premise, Seq( eq, aux ), Seq() )

  // </editor-fold>

  def name = "eq:l1"

  def longName = "EqualityLeft1Rule"

  val equation = premise( eq )

  val ( auxFormula, context ) = premise focus aux

  val mainFormula = equation match {
    case Eq( s, t ) =>
      if ( auxFormula( pos ) != s )
        throw new LKRuleCreationException( s"Cannot create $longName: Position $pos in $auxFormula should be $s, but is ${auxFormula( pos )}." )

      auxFormula.replace( pos, t )
    case _ => throw new LKRuleCreationException( s"Cannot create $longName: Formula $equation is not an equation." )
  }

  def endSequent = mainFormula +: context

  def auxIndices = Seq( Seq( eq, aux ) )

  def mainIndices = Seq( Ant( 0 ) )

  def getOccConnector = new OccConnector(
    endSequent,
    premise,
    Seq( aux ) +: premise.indicesSequent.delete( aux ).map( i => Seq( i ) )
  )
}

object EqualityLeft1Rule extends RuleConvenienceObject( "EqualityLeft1Rule" ) {
  def apply( subProof: LKProof, eqFormula: HOLFormula, auxFormula: HOLFormula, main: HOLFormula ): EqualityLeft1Rule = eqFormula match {
    case Eq( s, t ) =>
      val premise = subProof.endSequent

      val ( indices, _ ) = findFormulasInPremise( premise, Seq( eqFormula, auxFormula ), Seq() )

      val diffPos = HOLPosition.differingPositions( auxFormula, main )

      diffPos match {
        case p +: Seq() =>
          if ( main( p ) != t )
            throw exception( s"Position $p in $main should be $t, but is ${main( p )}." )

          EqualityLeft1Rule( subProof, Ant( indices( 0 ) ), Ant( indices( 1 ) ), p )

        case _ => throw exception( s"Formulas $eqFormula and $auxFormula don't differ in exactly one position." )

      }

    case _ => throw exception( s"Formula $eqFormula is not an equation." )
  }
}

case class EqualityLeft2Rule( subProof: LKProof, eq: SequentIndex, aux: SequentIndex, pos: HOLPosition ) extends UnaryLKProof {
  // <editor-fold desc="Sanity checks">

  validateIndices( premise, Seq( eq, aux ), Seq() )

  // </editor-fold>

  def name = "eq:l2"

  def longName = "EqualityLeft2Rule"

  val equation = premise( eq )

  val ( auxFormula, context ) = premise focus aux

  val mainFormula = equation match {
    case Eq( s, t ) =>
      if ( auxFormula( pos ) != t )
        throw new LKRuleCreationException( s"Cannot create $longName: Position $pos in $auxFormula should be $t, but is ${auxFormula( pos )}." )

      auxFormula.replace( pos, s )
    case _ => throw new LKRuleCreationException( s"Cannot create $longName: Formula $equation is not an equation." )
  }

  def endSequent = mainFormula +: context

  def auxIndices = Seq( Seq( eq, aux ) )

  def mainIndices = Seq( Ant( 0 ) )

  def getOccConnector = new OccConnector(
    endSequent,
    premise,
    Seq( aux ) +: premise.indicesSequent.delete( aux ).map( i => Seq( i ) )
  )
}

object EqualityLeft2Rule extends RuleConvenienceObject( "EqualityLeft2Rule" ) {
  def apply( subProof: LKProof, eqFormula: HOLFormula, auxFormula: HOLFormula, main: HOLFormula ): EqualityLeft2Rule = eqFormula match {
    case Eq( s, t ) =>
      val premise = subProof.endSequent

      val ( indices, _ ) = findFormulasInPremise( premise, Seq( eqFormula, auxFormula ), Seq() )

      val diffPos = HOLPosition.differingPositions( auxFormula, main )

      diffPos match {
        case p +: Seq() =>
          if ( main( p ) != s )
            throw exception( s"Position $p in $main should be $s, but is ${main( p )}." )

          EqualityLeft2Rule( subProof, Ant( indices( 0 ) ), Ant( indices( 1 ) ), p )

        case _ => throw exception( s"Formulas $eqFormula and $auxFormula don't differ in exactly one position." )

      }

    case _ => throw exception( s"Formula $eqFormula is not an equation." )
  }
}
//</editor-fold>

//<editor-fold desc="Utility classes">

/**
 * This class models the connection of formula occurrences between two sequents in a proof.
 *
 */
class OccConnector( val lowerSequent: HOLSequent, val upperSequent: HOLSequent, val parentsSequent: Sequent[Seq[SequentIndex]] ) {
  outer =>

  val childrenSequent = upperSequent.indicesSequent map { i => parentsSequent.indicesWhere( is => is contains i ) }

  /**
   * Given a SequentIndex for the lower sequent, this returns the list of parents of that occurrence in the upper sequent (if defined).
   *
   * @param idx
   * @return
   */
  def parents( idx: SequentIndex ): Seq[SequentIndex] = parentsSequent( idx )

  /**
   * Given a SequentIndex for the upper sequent, this returns the list of children of that occurrence in the lower sequent (if defined).
   *
   * @param idx
   * @return
   */
  def children( idx: SequentIndex ): Seq[SequentIndex] = childrenSequent( idx )

  def *( that: OccConnector ) = {
    val newAnt = for ( is <- parentsSequent.antecedent; i <- is ) yield that.parents( i )

    val newSuc = for ( is <- parentsSequent.succedent; i <- is ) yield that.parents( i )

    new OccConnector( this.lowerSequent, that.upperSequent, Sequent( newAnt, newSuc ) )
  }
}

object prettyString {
  /**
   * Produces a console-readable string representation of the lowermost rule.
   *
   * @param p The rule to be printed.
   * @return
   */
  def apply( p: LKProof ): String = p match {

    case InitialSequent( seq ) =>
      produceString( "", seq.toString, p.name )

    case UnaryLKProof( endSequent, subProof ) =>
      val upperString = sequentToString( subProof.endSequent, p.auxIndices.head )
      val lowerString = sequentToString( endSequent, p.mainIndices )
      produceString( upperString, lowerString, p.name )

    case BinaryLKProof( endSequent, leftSubproof, rightSubProof ) =>
      val upperString = sequentToString( leftSubproof.endSequent, p.auxIndices.head ) +
        "    " +
        sequentToString( rightSubProof.endSequent, p.auxIndices.tail.head )

      val lowerString = sequentToString( endSequent, p.mainIndices )
      produceString( upperString, lowerString, p.name )
  }

  private def produceString( upperString: String, lowerString: String, ruleName: String ): String = {

    val n = Math.max( upperString.length, lowerString.length ) + 2
    val line = "-" * n
    val ( upperDiff, lowerDiff ) = ( n - upperString.length, n - lowerString.length )
    val nLine = sys.props( "line.separator" )

    val upperNew = " " * Math.floor( upperDiff / 2 ).toInt + upperString + " " * Math.ceil( upperDiff / 2 ).toInt
    val lowerNew = " " * Math.floor( lowerDiff / 2 ).toInt + lowerString + " " * Math.ceil( lowerDiff / 2 ).toInt

    upperNew + nLine + line + ruleName + nLine + lowerNew
  }

  private def sequentToString( sequent: HOLSequent, auxFormulas: Seq[SequentIndex] ): String = {
    val stringSequent = sequent map { _.toString() }
    auxFormulas.foldLeft( stringSequent ) { ( acc, i ) =>
      val currentString = acc( i )
      val newString = "[" + currentString + "]"
      acc.updated( i, newString )
    }.toString

  }
}

/**
 * Class for reducing boilerplate code in LK companion objects.
 *
 * @param longName The long name of the rule.
 */
private[lkNew] class RuleConvenienceObject( val longName: String ) {
  /**
   * Create an LKRuleCreationException with a message starting with "Cannot create $longName: ..."
   *
   * @param text The rest of the message.
   * @return
   */
  def exception( text: String ): LKRuleCreationException = new LKRuleCreationException( s"Cannot create $longName: " + text )

  /**
   * Method to determine the indices of formulas in a sequent.
   *
   * If either list contains duplicate formulas, they must occur that many times in the respective cedent.
   *
   * @param premise The sequent to find formulas in.
   * @param antFormulas Formulas to be found in the antecedent.
   * @param sucFormulas Formulas to be found in the succedent.
   * @return
   */
  def findFormulasInPremise( premise: HOLSequent, antFormulas: Seq[HOLFormula], sucFormulas: Seq[HOLFormula] ): ( Seq[Int], Seq[Int] ) = {
    val antMap = scala.collection.mutable.HashMap.empty[HOLFormula, ( Int, Int )]
    val sucMap = scala.collection.mutable.HashMap.empty[HOLFormula, ( Int, Int )]

    val antIndices = for ( f <- antFormulas ) yield if ( antMap contains f ) {
      val ( i, count ) = antMap( f )
      val iNew = premise.antecedent.indexOf( f, i + 1 )

      if ( iNew == -1 )
        throw exception( s"Formula $f only found $count time(s) in antecedent of $premise." )

      antMap += ( f -> ( iNew, count + 1 ) )
      iNew
    } else {
      val iNew = premise.antecedent.indexOf( f )

      if ( iNew == -1 )
        throw exception( s"Formula $f not found in antecedent of $premise." )

      antMap += ( f -> ( iNew, 1 ) )
      iNew
    }

    val sucIndices = for ( f <- sucFormulas ) yield if ( sucMap contains f ) {
      val ( i, count ) = sucMap( f )
      val iNew = premise.succedent.indexOf( f, i + 1 )

      if ( iNew == -1 )
        throw exception( s"Formula $f only found $count time(s) in succedent of $premise." )

      sucMap += ( f -> ( iNew, count + 1 ) )
      iNew
    } else {
      val iNew = premise.succedent.indexOf( f )

      if ( iNew == -1 )
        throw exception( s"Formula $f not found in succedent of $premise." )

      sucMap += ( f -> ( iNew, 1 ) )
      iNew
    }

    ( antIndices, sucIndices )
  }
}

//</editor-fold>