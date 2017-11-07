package at.logic.gapt.proofs

import at.logic.gapt.expr._
import at.logic.gapt.proofs.expansion.ExpansionProof
import at.logic.gapt.proofs.lk.LKProof
import at.logic.gapt.proofs.resolution.ResolutionProof

trait Checkable[-T] {
  def check( obj: T )( implicit ctx: Context ): Unit
}
object Checkable {
  def requireDefEq( a: Expr, b: Expr )( implicit ctx: Context ): Unit =
    require( ctx.isDefEq( a, b ), s"${ctx.normalize( a ).toSigRelativeString} != ${ctx.normalize( b ).toSigRelativeString}" )

  implicit object contextElementIsCheckable extends Checkable[Context.Update] {
    def check( elem: Context.Update )( implicit context: Context ): Unit = elem( context )
  }

  implicit object typeIsCheckable extends Checkable[Ty] {
    override def check( ty: Ty )( implicit context: Context ): Unit =
      ty match {
        case ty @ TBase( name, params ) =>
          require(
            context.isType( ty ),
            s"Unknown base type: $name" )
          params.foreach( check )
        case TVar( _ ) =>
        case in ->: out =>
          check( in )
          check( out )
      }
  }

  implicit object expressionIsCheckable extends Checkable[Expr] {
    def check( expr: Expr )( implicit context: Context ): Unit =
      expr match {
        case c @ Const( name, _ ) =>
          require(
            context.constant( name ).exists( defC => syntacticMatching( defC, c ).isDefined ),
            s"Unknown constant: $c" )
        case Var( _, t ) => context.check( t )
        case Abs( v, e ) =>
          check( v )
          check( e )
        case App( a, b ) =>
          check( a )
          check( b )
      }
  }

  implicit def sequentIsCheckable[T: Checkable] = new Checkable[Sequent[T]] {
    def check( sequent: Sequent[T] )( implicit context: Context ) =
      sequent.foreach( context.check( _ ) )
  }

  implicit object lkIsCheckable extends Checkable[LKProof] {
    import at.logic.gapt.proofs.lk._

    def check( p: LKProof )( implicit ctx: Context ): Unit = {
      ctx.check( p.endSequent )
      p.subProofs.foreach {
        case ForallLeftRule( _, _, a, t, v )  => ctx.check( t )
        case ExistsRightRule( _, _, a, t, v ) => ctx.check( t )
        case q: EqualityRule                  =>
        case q @ InductionRule( cases, formula, term ) =>
          ctx.check( formula )
          ctx.check( term )
          val Some( ctrs ) = ctx.getConstructors( q.indTy.asInstanceOf[TBase] )
          require( q.cases.map( _.constructor ) == ctrs )
        case sk: SkolemQuantifierRule =>
          require( ctx.skolemDef( sk.skolemConst ).contains( sk.skolemDef ) )
          ctx.check( sk.skolemTerm )
        case StrongQuantifierRule( _, _, _, _, _ ) =>
        case _: ReflexivityAxiom | _: LogicalAxiom =>
        case ProofLink( name, sequent ) =>
          val declSeq = ctx.get[Context.ProofNames].lookup( name )
          require( declSeq.nonEmpty, s"Proof name $name does not exist in context" )
          require( declSeq.get isSubsetOf sequent, s"$declSeq\nis not a subsequent of\n$sequent" )
        case TopAxiom | BottomAxiom
          | _: NegLeftRule | _: NegRightRule
          | _: AndLeftRule | _: AndRightRule
          | _: OrLeftRule | _: OrRightRule
          | _: ImpLeftRule | _: ImpRightRule =>
        case _: ContractionRule | _: WeakeningLeftRule | _: WeakeningRightRule =>
        case _: CutRule =>
        case d: DefinitionRule =>
          requireDefEq( d.mainFormula, d.auxFormula )( ctx )
      }
    }
  }

  implicit object expansionProofIsCheckable extends Checkable[ExpansionProof] {
    import at.logic.gapt.proofs.expansion._

    def check( ep: ExpansionProof )( implicit ctx: Context ): Unit = {
      ctx.check( ep.shallow )
      ep.subProofs.foreach {
        case ETTop( _ ) | ETBottom( _ ) | ETNeg( _ ) | ETAnd( _, _ ) | ETOr( _, _ ) | ETImp( _, _ ) =>
        case ETWeakening( _, _ ) | ETAtom( _, _ ) =>
        case ETWeakQuantifier( _, insts ) =>
          insts.keys.foreach( ctx.check( _ ) )
        case ETStrongQuantifier( _, _, _ ) =>
        case sk @ ETSkolemQuantifier( _, skT, skD, _ ) =>
          require( ctx.skolemDef( sk.skolemConst ).contains( skD ) )
          ctx.check( skT )
        case ETDefinition( sh, child ) =>
          requireDefEq( sh, child.shallow )( ctx )
      }
    }
  }

  implicit object resolutionIsCheckable extends Checkable[ResolutionProof] {
    import at.logic.gapt.proofs.resolution._

    def check( p: ResolutionProof )( implicit ctx: Context ): Unit = {
      def checkAvatarDef( comp: AvatarDefinition ): Unit =
        for ( ( df, by ) <- comp.inducedDefinitions )
          requireDefEq( df, by )( ctx )

      p.subProofs.foreach {
        case Input( sequent )           => ctx.check( sequent )
        case Refl( term )               => ctx.check( term )
        case Taut( form )               => ctx.check( form )
        case Defn( df, by )             => require( ctx.isDefEq( df, by ) )
        case _: WeakQuantResolutionRule =>
        case q: SkolemQuantResolutionRule =>
          require( ctx.skolemDef( q.skolemConst ).contains( q.skolemDef ) )
          ctx.check( q.skolemTerm )
        case DefIntro( _, _, definition, _ ) =>
          requireDefEq( definition.what, definition.by )( ctx )
        case _: PropositionalResolutionRule =>
        case AvatarComponent( defn ) => checkAvatarDef( defn )
        case AvatarSplit( _, _, defn ) => checkAvatarDef( defn )
        case _: AvatarComponent | _: AvatarSplit | _: AvatarContradiction =>
        case _: Flip | _: Paramod =>
        case _: Resolution =>
        case _: Factor =>
        case _: Subst =>
      }
    }
  }
}
