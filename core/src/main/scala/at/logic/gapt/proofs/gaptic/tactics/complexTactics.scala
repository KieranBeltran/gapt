package at.logic.gapt.proofs.gaptic.tactics

import at.logic.gapt.expr.{ Const => Con, _ }
import at.logic.gapt.expr.hol.HOLPosition
import at.logic.gapt.proofs._
import at.logic.gapt.proofs.expansion.ExpansionProofToLK
import at.logic.gapt.proofs.gaptic._
import at.logic.gapt.proofs.lk._
import at.logic.gapt.provers.escargot.{ Escargot, NonSplittingEscargot }
import at.logic.gapt.provers.prover9.Prover9

import scalaz._
import Scalaz._
import Validation.FlatMap._
import at.logic.gapt.utils.ScalazHelpers._

/**
 * Attempts to decompose a formula by trying all tactics that don't require additional information.
 *
 * Note that this tactic only decomposes the outermost symbol, i.e. it only performs one step.
 *
 * @param applyToLabel The label of the formula to be decomposed.
 */
case class DestructTactic( applyToLabel: String ) extends Tactic[Any] {

  override def apply( goal: OpenAssumption ) = {
    val goalSequent = goal.labelledSequent

    val indices =
      for ( ( ( `applyToLabel`, _ ), index ) <- goalSequent.zipWithIndex.elements )
        yield index

    // Select some formula index!
    indices.headOption match {
      case Some( i ) =>
        val ( existingLabel, _ ) = goalSequent( i )

        val tac = allR( existingLabel ) orElse
          exL( existingLabel ) orElse
          andL( existingLabel ) orElse
          andR( existingLabel ) orElse
          orL( existingLabel ) orElse
          orR( existingLabel ) orElse
          impL( existingLabel ) orElse
          impR( existingLabel ) orElse
          negL( existingLabel ) orElse
          negR( existingLabel )
        tac( goal ).leftMap( _ => NonEmptyList( TacticalFailure( this, Some( goal ), s"Cannot destruct ${goalSequent( i )._2}" ) ) )
      case None => TacticalFailure( this, Some( goal ), "No destructible formula found." ).failureNel
    }
  }
}

/**
 * Performs backwards chaining:
 * A goal of the form `∀x (P(x) → Q(x)), Γ :- Δ, Q(t)` is replaced by the goal `∀x (P(x) → Q(x)), Γ :- Δ, P(t)`.
 *
 * @param hyp
 * @param target
 * @param substitution
 */
case class ChainTactic( hyp: String, target: Option[String] = None, substitution: Map[Var, LambdaExpression] = Map() ) extends Tactic[Unit] {

  def subst( map: ( Var, LambdaExpression )* ) = copy( substitution = substitution ++ map )
  def at( target: String ) = copy( target = Option( target ) )

  override def apply( goal: OpenAssumption ) = {
    val goalSequent = goal.labelledSequent

    ( for ( ( ( `hyp`, _ ), index: Ant ) <- goalSequent.zipWithIndex.elements ) yield index ).headOption.
      toSuccessNel( TacticalFailure( this, Some( goal ), s"hyp $hyp not found" ) ) flatMap { hypIndex =>

        // Extract hypothesis
        val ( _, quantifiedFormula ) = goalSequent( hypIndex )
        val All.Block( hypVar, hypInner ) = quantifiedFormula

        // Extract formula to match against target
        def f( x: HOLFormula ): HOLFormula = x match {
          case Imp( _, r ) => f( r )
          case _           => x
        }

        val hypTargetMatch = f( hypInner )

        // Find target index and substitution
        ( target match {
          case Some( x ) =>
            ( for (
              ( ( `x`, y ), index ) <- goalSequent.zipWithIndex.succedent;
              sub <- syntacticMatching( List( hypTargetMatch -> y ), substitution ++ freeVariables( quantifiedFormula ).map { v => v -> v } )
            ) yield ( x, index, sub ) ).headOption
          case None =>
            ( for (
              ( ( x, y ), index ) <- goalSequent.zipWithIndex.succedent;
              sub <- syntacticMatching( List( hypTargetMatch -> y ), substitution ++ freeVariables( quantifiedFormula ).map { v => v -> v } )
            ) yield ( x, index, sub ) ).headOption
        } ).toSuccessNel( TacticalFailure( this, Some( goal ), s"target $target not found" ) ) map {

          // Proceed only if a matching formula exists
          case ( targetLabel, targetIndex, sub ) =>

            // Recursively apply implication left to the left until the end of the chain is reached,
            // where the sequent is an axiom (following some contractions).
            // The right premises of the implication left rules become new sub goals,
            // but where the initial target formula is then "forgotten".
            def handleAnds( curGoal: Sequent[( String, HOLFormula )], hypCond: Suc ): LKProof = curGoal( hypCond ) match {
              case ( existingLabel, And( lhs, rhs ) ) =>
                AndRightRule(
                  handleAnds( curGoal.updated( hypCond, existingLabel -> lhs ), hypCond ),
                  handleAnds( curGoal.updated( hypCond, existingLabel -> rhs ), hypCond ),
                  And( lhs, rhs )
                )
              case _ =>
                OpenAssumption( curGoal )
            }

            def handleImps( curGoal: Sequent[( String, HOLFormula )], hyp: Ant ): LKProof = {
              curGoal( hyp ) match {
                case ( hypLabel, Imp( lhs, rhs ) ) =>
                  // Different methods must be applied depending on how the chain is defined.
                  val premiseLeft = handleAnds( curGoal.delete( targetIndex ).delete( hyp ) :+ ( targetLabel -> lhs ), Suc( curGoal.succedent.length - 1 ) )
                  val premiseRight = handleImps( curGoal.updated( hyp, hypLabel -> rhs ), hyp )
                  ImpLeftRule( premiseLeft, premiseRight, Imp( lhs, rhs ) )

                case ( _, formula ) =>
                  WeakeningMacroRule( LogicalAxiom( formula ), curGoal map { _._2 } )
              }
            }

            val auxFormula = sub( hypInner )
            val newGoal = ( NewLabel( goalSequent, hyp ) -> auxFormula ) +: goalSequent
            val premise = handleImps( newGoal, Ant( 0 ) )
            val auxProofSegment = ForallLeftBlock( premise, quantifiedFormula, sub( hypVar ) )
            () -> ContractionLeftRule( auxProofSegment, quantifiedFormula )

        }

      }
  }
}

/**
 * Rewrites using the specified equations at the target, either once or as often as possible.
 *
 * @param equations  Universally quantified equations on the antecedent, with direction (left-to-right?)
 * @param target  Formula to rewrite.
 * @param once  Rewrite exactly once?
 */
case class RewriteTactic(
    equations:  Traversable[( String, Boolean )],
    target:     Option[String],
    fixedSubst: Map[Var, LambdaExpression],
    once:       Boolean
) extends Tactic[Unit] {
  def apply( goal: OpenAssumption ) = target match {
    case Some( tgt ) => apply( goal, tgt ) map { () -> _ }
    case _ => goal.labelledSequent match {
      case Sequent( _, Seq( ( tgt, _ ) ) ) => apply( goal, tgt ) map { () -> _ }
      case _                               => TacticalFailure( this, Some( goal ), "target formula not found" ).failureNel
    }
  }

  def apply( goal: OpenAssumption, target: String ): ValidationNel[TacticalFailure, LKProof] = {
    for {
      ( eqLabel, leftToRight ) <- equations
      ( ( `target`, tgt ), tgtIdx ) <- goal.labelledSequent.zipWithIndex.elements
      ( `eqLabel`, quantEq @ All.Block( vs, eq @ Eq( t, s ) ) ) <- goal.labelledSequent.antecedent
      ( t_, s_ ) = if ( leftToRight ) ( t, s ) else ( s, t )
      pos <- HOLPosition getPositions tgt
      subst <- syntacticMatching( List( t_ -> tgt( pos ) ), fixedSubst ++ freeVariables( quantEq ).map { v => v -> v }.toMap )
    } return {
      val newTgt = tgt.replace( pos, subst( s_ ) )
      val newGoal = OpenAssumption( goal.labelledSequent.updated( tgtIdx, target -> newTgt ) )
      val p1 = if ( once ) newGoal else apply( newGoal, target ) getOrElse newGoal
      val p2 = WeakeningLeftRule( p1, subst( eq ) )
      val p3 =
        if ( tgtIdx isSuc ) EqualityRightRule( p2, Ant( 0 ), newTgt, tgt )
        else EqualityLeftRule( p2, Ant( 0 ), newTgt, tgt )
      val p4 = ForallLeftBlock( p3, quantEq, subst( vs ) )
      val p5 = ContractionLeftRule( p4, quantEq )
      require( p5.conclusion multiSetEquals goal.conclusion )
      p5.success
    }
    if ( once ) TacticalFailure( this, Some( goal ), "cannot rewrite at least once" ).failureNel else goal.success
  }

  def ltr( eqs: String* ) = copy( equations = equations ++ eqs.map { _ -> true } )
  def rtl( eqs: String* ) = copy( equations = equations ++ eqs.map { _ -> false } )
  def in( tgt: String ) = copy( target = Some( tgt ) )
  def many = copy( once = false )
  def subst( s: ( Var, LambdaExpression )* ) = copy( fixedSubst = fixedSubst ++ s )
}

/**
 * Reduces a subgoal via induction.
 *
 * @param mode How to apply the tactic: To a specific label, to the only fitting formula, or to any fitting formula.
 * @param ctx A [[at.logic.gapt.proofs.Context]]. Used to find the constructors of inductive types.
 */
case class InductionTactic( mode: TacticApplyMode, v: Var )( implicit ctx: Context ) extends Tactic[Unit] {

  /**
   * Reads the constructors of type `t` from the context.
   *
   * @param t A base type.
   * @return Either a list containing the constructors of `t` or a TacticalFailure.
   */
  private def getConstructors( goal: OpenAssumption, t: TBase ): ValidationNel[TacticalFailure, Seq[Con]] =
    ctx.typeDef( t.name ) match {
      case Some( Context.InductiveType( _, constructors ) ) => constructors.success
      case Some( typeDef ) => TacticalFailure( this, Some( goal ), s"Type $t is not inductively defined: $typeDef" ).failureNel
      case None => TacticalFailure( this, Some( goal ), s"Type $t is not defined" ).failureNel
    }

  def apply( goal: OpenAssumption ) =
    for {
      ( label, main, idx: Suc ) <- findFormula( goal, mode )
      formula = main
      constrs <- getConstructors( goal, v.exptype.asInstanceOf[TBase] )
    } yield {
      val cases = constrs map { constr =>
        val FunctionType( _, argTypes ) = constr.exptype
        var nameGen = rename.awayFrom( freeVariables( goal.conclusion ) )
        val evs = argTypes map { at => nameGen.fresh( if ( at == v.exptype ) v else Var( "x", at ) ) }
        val hyps = NewLabels( goal.labelledSequent, s"IH${v.name}" ) zip ( evs filter { _.exptype == v.exptype } map { ev => Substitution( v -> ev )( formula ) } )
        val subGoal = hyps ++: goal.labelledSequent.delete( idx ) :+ ( label -> Substitution( v -> constr( evs: _* ) )( formula ) )
        InductionCase( OpenAssumption( subGoal ), constr, subGoal.indices.take( hyps.size ), evs, subGoal.indices.last )
      }
      () -> InductionRule( cases, Abs( v, main ), v )
    }
}

case class UnfoldTacticHelper( definition: String, definitions: Seq[String] )( implicit ctx: Context ) {
  def in( label: String, labels: String* ) = labels.foldLeft[Tactical[Unit]]( UnfoldTactic( label, definition, definitions ) ) {
    ( acc, l ) => acc andThen UnfoldTactic( l, definition, definitions )
  }
}

case class UnfoldTactic( target: String, definition: String, definitions: Seq[String] )( implicit ctx: Context ) extends Tactic[Unit] {
  def getDef: ValidationNel[TacticalFailure, Definition] =
    ctx.definition( definition ) match {
      case Some( by ) =>
        Definition( Con( definition, by.exptype ), by ).success
      case None =>
        TacticalFailure( this, None, s"Definition $definition not present in context: $ctx" ).failureNel
    }

  def apply( goal: OpenAssumption ) =
    for {
      ( label, main: HOLFormula, idx: SequentIndex ) <- findFormula( goal, OnLabel( target ) )
      defn <- getDef; Definition( what, by ) = defn
      defPositions = main.find( what )
      unfolded = defPositions.foldLeft( main )( ( f, p ) => f.replace( p, by ) )
      normalized = BetaReduction.betaNormalize( unfolded )
      repContext = replacementContext.abstractTerm( main )( what )
      newGoal = OpenAssumption( goal.labelledSequent.updated( idx, label -> normalized ) )
      proof_ : ValidationNel[TacticalFailure, LKProof] = ( defPositions, definitions ) match {
        case ( p :: ps, _ ) =>
          DefinitionRule( newGoal, normalized, defn, repContext, idx.polarity ).successNel[TacticalFailure]
        case ( Nil, hd +: tl ) =>
          UnfoldTactic( target, hd, tl )( ctx )( newGoal ) map { _._2 }
        case _ =>
          TacticalFailure( this, None, s"Definition $definition not found in formula $main." ).failureNel[LKProof]
      }
      proof <- proof_
    } yield () -> proof

  /*def apply( goal: OpenAssumption ) =
    for {
      ( label, main: HOLFormula, idx: SequentIndex ) <- findFormula( goal, OnLabel( target ) )
      defn <- getDef; ( what, by ) = defn
      defPositions = main.find( what )
      unfolded = defPositions.foldLeft( main )( ( f, p ) => f.replace( p, by ) )
      normalized = BetaReduction.betaNormalize( unfolded )
      newGoal = OpenAssumption( goal.s.updated( idx, label -> normalized ) )
      ( (), proof ) <- ( defPositions, definitions ) match {
        case ( _ :: _, _ ) =>
          DefinitionRule( newGoal, normalized, main, idx.isSuc ).successNel[TacticalFailure]
        case ( Nil, hd +: tl ) =>
          UnfoldTactic( target, hd, tl: _* )( ctx )( newGoal )
        case _ =>
          TacticalFailure( this, None, s"Definition $definition not found in formula $main." ).failureNel[( Unit, LKProof )]
      }
    } yield () -> proof*/
}

/**
 * Calls the GAPT tableau prover on the subgoal.
 */
case object PropTactic extends Tactic[Unit] {
  override def apply( goal: OpenAssumption ) = {
    solvePropositional( goal.conclusion ).toOption.toSuccessNel( TacticalFailure( this, Some( goal ), "search failed" ) ) map { () -> _ }
  }
}

case object QuasiPropTactic extends Tactic[Unit] {
  override def apply( goal: OpenAssumption ) =
    solveQuasiPropositional( goal.conclusion ).toOption.toSuccessNel( TacticalFailure( this, Some( goal ), "search failed" ) ) map { () -> _ }
}

/**
 * Calls prover9 on the subgoal.
 */
case object Prover9Tactic extends Tactic[Unit] {
  override def apply( goal: OpenAssumption ) = {
    Prover9.getLKProof( goal.conclusion ).toSuccessNel( TacticalFailure( this, Some( goal ), "search failed" ) ) map { () -> _ }
  }
}

/**
 * Calls Escargot on the subgoal.
 */
case object EscargotTactic extends Tactic[Unit] {
  override def apply( goal: OpenAssumption ) =
    Escargot getExpansionProof goal.conclusion toSuccessNel TacticalFailure( this, Some( goal ), "search failed" ) map { p => () -> ExpansionProofToLK( p ).get }
}
