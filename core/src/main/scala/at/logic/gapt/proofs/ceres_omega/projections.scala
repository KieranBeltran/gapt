/*
 * projections.scala
 *
 */

package at.logic.gapt.proofs.ceres_omega

import at.logic.gapt.expr.hol.HOLPosition
import at.logic.gapt.proofs._
import at.logic.gapt.expr._
import at.logic.gapt.proofs.lksk.LKskProof.{ Label, LabelledFormula, LabelledSequent }
import at.logic.gapt.proofs.lksk._
import at.logic.gapt.proofs.ceres_omega.Pickrule._
import at.logic.gapt.utils.Logger

case class ProjectionException( message: String, original_proof: LKskProof, new_proofs: List[LKskProof], nested: Exception )
  extends Exception( message, nested ) {}

object Projections extends Logger {
  def reflexivity_projection( es: LabelledSequent, term: LambdaExpression, label: Label ): ( LKskProof, Sequent[Boolean] ) = {
    require( term.exptype == Ti, "Only first order reflexivity projections are allowed!" )
    // create a fresh variable to create x = x
    val ax: LKskProof = Reflexivity( label, term )
    val cut_anc = ax.conclusion.map( _ => true )
    weakenESAncs( es, Set( ( ax, cut_anc ) ) ).toList( 0 )
  }

  // This method computes the standard projections according to the original CERES definition.
  def apply( proof: LKskProof ): Set[( LKskProof, Sequent[Boolean] )] =
    apply( proof, proof.conclusion.map( _ => false ), x => true )

  def apply( proof: LKskProof, pred: HOLFormula => Boolean ): Set[( LKskProof, Sequent[Boolean] )] =
    apply( proof, proof.conclusion.map( _ => false ), pred )

  /**
   * Given an LKsk proof proof, compute the set of projections for it.
   *
   * @param proof The original proof.
   * @param cut_ancs A characteristic function indicating which formula occurrences in the conclusion are cut ancestors
   * @param pred A predicate deciding if a cut-formula should be included in the projection
   * @return A set of pairs (projection, cut_ancs) where cut_ancs indicates the cut ancestors of the projection's conclusion
   */
  def apply( proof: LKskProof, cut_ancs: Sequent[Boolean], pred: HOLFormula => Boolean ): Set[( LKskProof, Sequent[Boolean] )] = {
    implicit val c_ancs = cut_ancs
    proof.occConnectors
    try {
      val r: Set[( LKskProof, Sequent[Boolean] )] = proof match {
        /* Structural rules except cut */
        case TopRight( _ ) | BottomLeft( _ ) => Set( ( proof, cut_ancs ) )
        case Axiom( _, _, f ) =>
          /*
          cut_ancs match {
            case Sequent( Seq( false ), Seq( false ) ) => ()
            case Sequent( Seq( true ), Seq( false ) ) =>
              //println( s"negative: $f" )
              ()
            case Sequent( Seq( false ), Seq( true ) ) =>
              //println( s"positive: $f" )
              ()
            case Sequent( Seq( true ), Seq( true ) ) => ()
          }
          */
          Set( ( proof, cut_ancs ) )
        case Reflexivity( _, _ )                        => Set( ( proof, cut_ancs ) )

        case ContractionLeft( p, a1, a2 )               => handleContractionRule( proof, p, a1, a2, ContractionLeft.apply, pred )
        case ContractionRight( p, a1, a2 )              => handleContractionRule( proof, p, a1, a2, ContractionRight.apply, pred )
        case WeakeningLeft( p, m )                      => handleWeakeningRule( proof, p, m, WeakeningLeft.apply, pred )
        case WeakeningRight( p, m )                     => handleWeakeningRule( proof, p, m, WeakeningRight.apply, pred )

        /* Logical rules */
        case AndRight( p1, a1, p2, a2 )                 => handleBinaryRule( proof, p1, p2, a1, a2, AndRight.apply, pred )
        case OrLeft( p1, a1, p2, a2 )                   => handleBinaryRule( proof, p1, p2, a1, a2, OrLeft.apply, pred )
        case ImpLeft( p1, a1, p2, a2 )                  => handleBinaryRule( proof, p1, p2, a1, a2, ImpLeft.apply, pred )
        case NegLeft( p, a )                            => handleNegRule( proof, p, a, NegLeft.apply, pred )
        case NegRight( p, a )                           => handleNegRule( proof, p, a, NegRight.apply, pred )
        case OrRight( p, a1, a2 )                       => handleUnaryRule( proof, p, a1, a2, OrRight.apply, pred )
        case AndLeft( p, a1, a2 )                       => handleUnaryRule( proof, p, a1, a2, AndLeft.apply, pred )
        case ImpRight( p, a1, a2 )                      => handleUnaryRule( proof, p, a1, a2, ImpRight.apply, pred )

        /* quantifier rules  */
        case AllRight( p, a, eigenv, qvar )             => handleStrongQuantRule( proof, p, a, AllRight.apply, pred )
        case ExLeft( p, a, eigenvar, qvar )             => handleStrongQuantRule( proof, p, a, ExLeft.apply, pred )
        case AllLeft( p, a, f, t )                      => handleWeakQuantRule( proof, p, a, f, t, AllLeft.apply, pred )
        case ExRight( p, a, f, t )                      => handleWeakQuantRule( proof, p, a, f, t, ExRight.apply, pred )

        case AllSkRight( p, a, main, sk_const, sk_def ) => handleStrongSkQuantRule( proof, p, a, main, sk_const, sk_def, AllSkRight.apply, pred )
        case ExSkLeft( p, a, main, sk_const, sk_def )   => handleStrongSkQuantRule( proof, p, a, main, sk_const, sk_def, ExSkLeft.apply, pred )
        case AllSkLeft( p, a, f, t )                    => handleWeakQuantRule( proof, p, a, f, t, AllSkLeft.apply, pred )
        case ExSkRight( p, a, f, t )                    => handleWeakQuantRule( proof, p, a, f, t, ExSkRight.apply, pred )

        case Equality( p, e, a, flipped, con ) =>
          handleEqRule( proof, p, e, a, flipped, con, pred )

        case r @ Cut( p1, a1, p2, a2 ) =>
          val main_is_cutanc = pred( r.cutFormula )
          val new_cut_ancs_left = mapToUpperProof( proof.occConnectors( 0 ), cut_ancs, main_is_cutanc )
          val new_cut_ancs_right = mapToUpperProof( proof.occConnectors( 1 ), cut_ancs, main_is_cutanc )
          require( new_cut_ancs_left.size == p1.conclusion.size, "Cut ancestor information does not fit to end-sequent!" )
          require( new_cut_ancs_right.size == p2.conclusion.size, "Cut ancestor information does not fit to end-sequent!" )
          val s1 = apply( p1, new_cut_ancs_left, pred )
          val s2 = apply( p2, new_cut_ancs_right, pred )
          if ( main_is_cutanc ) {
            /* this cut is taken into account */
            handleBinaryCutAnc( proof, p1, p2, s1, s2, new_cut_ancs_left, new_cut_ancs_right )
          } else {
            //this one is skipped
            s1.foldLeft( Set.empty[( LKskProof, Sequent[Boolean] )] )( ( s, pm1 ) =>
              s ++ s2.map( pm2 => {
                val es1 = p1.conclusion
                val es2 = p2.conclusion
                val List( aux1: Suc, aux2: Ant ) = pickrule( proof, List( p1, p2 ), List( pm1, pm2 ), List( a1, a2 ), pick_from_es )
                val rule = Cut( pm1._1, aux1, pm2._1, aux2 )
                val nca = calculate_child_cut_ecs( rule, rule.occConnectors( 0 ), rule.occConnectors( 1 ), pm1, pm2, main_is_cutanc )
                ( rule, nca )
              } ) )
          }
        case _ => throw new Exception( "No such a rule in Projections.apply " + proof.longName )
      }

      val orig_es = ( proof.conclusion zip cut_ancs ).filterNot( _._2 ).map( _._1 )
      r.map( x => {
        val ( rp, rca ) = x
        val proj_es = ( rp.conclusion zip rca ).filterNot( _._2 ).map( _._1 )
        require( orig_es multiSetEquals proj_es, s"Error computing projection for ${proof.longName} ${proof.conclusion}:\n$orig_es\nis not equal to\n$proj_es" )
      } )

      /*
      r.map( {
        case ( proof, ca ) => {
          println( s"${proof.conclusion.map( _._2 )} with cutanc ${proof.conclusion.zipWithIndex.filter( x => ca( x._2 ) ).map( _._1._2 )}" )
          println( s"$proof" )
        }
      } )
      println()
      */
      r
    } catch {
      case e: ProjectionException =>
        //println("passing exception up...")
        //throw ProjectionException(e.getMessage, proof, Nil, null)
        throw e
      case e: Exception =>
        throw ProjectionException( "Error computing projection: " + e.getMessage + sys.props( "line.separator" ) + e.getStackTrace, proof, Nil, e )
    }
  }

  /**
   * finds the cut ancestor sequent in the parent connected with the occurrence connector
   *
   * @param connector
   * @param s
   * @return
   */
  def copySetToAncestor( connector: OccConnector[LabelledFormula], s: Sequent[Boolean] ) = {
    connector.parents( s ).map( _.head )
  }

  /**
   * given an inference from pm._1 to child, infer the child_s cut_anc sequent from pm._2
   *
   * @param child a child proof of pm._1
   * @param connector connector from parent child to pm._1
   * @param pm a pair proof + cut_ancestor sequent
   * @param default the default to take if a formula has no parent
   * @tparam T must be the same as the contents of the conclusion of pm._1
   * @return the cut ancestorship sequent for the parent of pm._1
   */
  def calculate_child_cut_ecs[T](
    child:     LKskProof,
    connector: OccConnector[T],
    pm:        ( LKskProof, Sequent[Boolean] ),
    default:   Boolean
  ): Sequent[Boolean] = {
    //convert child to index sequent
    val idxseq = child.conclusion.indicesSequent
    //find parent indices
    val parents: Sequent[Seq[SequentIndex]] = idxseq.map( connector.parents )
    //look up parent indices in pm._2 and carry them over. use default if there is no parent.
    val nca: Sequent[Boolean] =
      parents.map( {
        case x if x.isEmpty =>
          default
        case x =>
          require(
            x.forall( idx => pm._2( idx ) == pm._2( x( 0 ) ) ),
            s"Ancestors $x (${x.map( pm._1.conclusion( _ ) )}) of a formula must agree on cut-ancestorship ${x.map( pm._2( _ ) )}." +
              s"Parent conclusion: ${child.conclusion}. Proof: $child"
          )
          pm._2( x( 0 ) )
      } )
    nca
  }

  /**
   * finds the successor cut-ancestor sequent from two parents
   *
   * @param child child proof of binary inference with left parent pm1._1 and right parent pm2._1
   * @param connector1 connector from pm1._1 to child
   * @param pm1 a pair proof + cut_ancestor sequent
   * @param connector2 connector from pm2._1 to child
   * @param pm2 a pair proof + cut_ancestor sequent
   * @param default the default to take if a formula has no parent
   * @tparam T must be the same as the contents of the conclusion of pm1._1 and pm2._1
   * @return the cut ancestorship sequent for the parent of pm._1
   */
  def calculate_child_cut_ecs[T](
    child:      LKskProof,
    connector1: OccConnector[T],
    connector2: OccConnector[T],
    pm1:        ( LKskProof, Sequent[Boolean] ),
    pm2:        ( LKskProof, Sequent[Boolean] ),
    default:    Boolean
  ): Sequent[Boolean] = {
    val nca1 = calculate_child_cut_ecs( child, connector1, pm1, default )
    val nca2 = calculate_child_cut_ecs( child, connector2, pm2, default )
    nca1.indicesSequent.map( x => nca1( x ) || nca2( x ) )
  }

  /* traces the ancestor relationship to infer cut-formulas in the parent proof. if a formula does not have parents,
     use default */
  private def mapToUpperProof[Formula]( conn: OccConnector[Formula], cut_occs: Sequent[Boolean], default: Boolean ) =
    conn.parents( cut_occs ).map( _.headOption getOrElse default )

  def handleBinaryESAnc[Side1 <: SequentIndex, Side2 <: SequentIndex]( proof: LKskProof, parent1: LKskProof, parent2: LKskProof,
                                                                       s1:          Set[( LKskProof, Sequent[Boolean] )],
                                                                       s2:          Set[( LKskProof, Sequent[Boolean] )],
                                                                       constructor: ( LKskProof, Side1, LKskProof, Side2 ) => LKskProof ) =
    s1.foldLeft( Set.empty[( LKskProof, Sequent[Boolean] )] )( ( s, p1 ) =>
      s ++ s2.map( p2 => {
        val List( a1: Side1, a2: Side2 ) = pickrule( proof, List( parent1, parent2 ), List( p1, p2 ),
          List( proof.auxIndices( 0 )( 0 ), proof.auxIndices( 1 )( 0 ) ), pick_from_es )
        val rule = constructor( p1._1, a1, p2._1, a2 )
        val nca = calculate_child_cut_ecs( rule, rule.occConnectors( 0 ), rule.occConnectors( 1 ), p1, p2, false )
        ( rule, nca )
      } ) )

  def getESAncs( proof: LKskProof, cut_ancs: Sequent[Boolean] ): LabelledSequent = {
    //use cut_ancs as characteristic function to filter the cut-ancestors from the current sequent
    ( proof.conclusion zip cut_ancs ).filterNot( _._2 ).map( _._1 )
  }

  // Handles the case of a binary rule operating on a cut-ancestor.
  def handleBinaryCutAnc( proof: LKskProof, p1: LKskProof, p2: LKskProof,
                          s1: Set[( LKskProof, Sequent[Boolean] )], s2: Set[( LKskProof, Sequent[Boolean] )],
                          cut_ancs1: Sequent[Boolean], cut_ancs2: Sequent[Boolean] ): Set[( LKskProof, Sequent[Boolean] )] = {
    val new_p1 = weakenESAncs( getESAncs( p2, cut_ancs2 ), s1 )
    val new_p2 = weakenESAncs( getESAncs( p1, cut_ancs1 ), s2 )
    new_p1 ++ new_p2
  }

  // Apply weakenings to add the end-sequent ancestor of the other side to the projection.
  def weakenESAncs( esancs: LabelledSequent, s: Set[( LKskProof, Sequent[Boolean] )] ) = {
    val wl = s.map( p => esancs.antecedent.foldLeft( p )( ( p, fo ) => {
      val rule = WeakeningLeft( p._1, fo )
      val nca: Sequent[Boolean] = calculate_child_cut_ecs( rule, rule.occConnectors( 0 ), p, false )
      ( rule, nca )
    } ) )
    wl.map( p => esancs.succedent.foldLeft( p )( ( p, fo ) => {
      val rule = WeakeningRight( p._1, fo )
      val nca: Sequent[Boolean] = calculate_child_cut_ecs( rule, rule.occConnectors( 0 ), p, false )
      ( rule, nca )
    } ) )
  }

  def handleContractionRule[Side <: SequentIndex]( proof: LKskProof, p: LKskProof,
                                                   a1: SequentIndex, a2: SequentIndex,
                                                   constructor: ( LKskProof, Side, Side ) => LKskProof,
                                                   pred:        HOLFormula => Boolean )( implicit cut_ancs: Sequent[Boolean] ): Set[( LKskProof, Sequent[Boolean] )] = {
    val s = apply( p, copySetToAncestor( proof.occConnectors( 0 ), cut_ancs ), pred )
    val main_is_cutanc = cut_ancs( proof.mainIndices( 0 ) )
    if ( main_is_cutanc ) s
    else s.map( pm => {
      val List( a1_ : Side, a2_ : Side ) = pickrule( proof, List( p ), List( pm ), List( a1, a2 ), pick_from_es )
      val rp = constructor( pm._1, a1_, a2_ )
      val nca: Sequent[Boolean] = calculate_child_cut_ecs( rp, rp.occConnectors( 0 ), pm, main_is_cutanc )
      ( rp, nca )
    } )
  }

  //implication does not weaken the second argument, we need two occs
  def handleUnaryRule[Side1 <: SequentIndex, Side2 <: SequentIndex]( proof: LKskProof, p: LKskProof, a1: Side1, a2: Side2,
                                                                     constructor: ( LKskProof, Side1, Side2 ) => LKskProof,
                                                                     pred:        HOLFormula => Boolean )( implicit cut_ancs: Sequent[Boolean] ): Set[( LKskProof, Sequent[Boolean] )] = {
    val s = apply( p, copySetToAncestor( proof.occConnectors( 0 ), cut_ancs ), pred )
    if ( cut_ancs( proof.mainIndices( 0 ) ) ) s
    else s.map( pm => {
      val List( a1_ : Side1, a2_ : Side2 ) = pickrule( proof, List( p ), List( pm ), List( a1, a2 ), pick_from_es )
      val rp = constructor( pm._1, a1_, a2_ )
      val nca = calculate_child_cut_ecs( rp, rp.occConnectors( 0 ), pm, false )
      ( rp, nca )
    } )
  }

  def handleWeakeningRule( proof: LKskProof, p: LKskProof, m: LabelledFormula,
                           constructor: ( LKskProof, LabelledFormula ) => LKskProof,
                           pred:        HOLFormula => Boolean )( implicit cut_ancs: Sequent[Boolean] ): Set[( LKskProof, Sequent[Boolean] )] = {
    val maincutanc = cut_ancs( proof.mainIndices( 0 ) )
    val s = apply( p, copySetToAncestor( proof.occConnectors( 0 ), cut_ancs ), pred )
    if ( cut_ancs( proof.mainIndices( 0 ) ) ) s
    else s.map( pm => {
      val rule = constructor( pm._1, m )
      val nca = calculate_child_cut_ecs( rule, rule.occConnectors( 0 ), pm, false )
      ( rule, nca )
    } )
  }

  def handleDefRule( proof: LKskProof, p: LKskProof, a: SequentIndex, f: HOLFormula,
                     constructor: ( LKskProof, SequentIndex, HOLFormula ) => LKskProof,
                     pred:        HOLFormula => Boolean )( implicit cut_ancs: Sequent[Boolean] ): Set[( LKskProof, Sequent[Boolean] )] = {
    val s = apply( p, copySetToAncestor( proof.occConnectors( 0 ), cut_ancs ), pred )
    if ( cut_ancs( proof.mainIndices( 0 ) ) ) s
    else s.map( pm => {
      val List( a_ ) = pickrule( proof, List( p ), List( pm ), List( a ), pick_from_es )
      val rule = constructor( pm._1, a_, f )
      val nca = calculate_child_cut_ecs( rule, rule.occConnectors( 0 ), pm, false )
      ( rule, nca )
    } )
  }

  def handleNegRule[Side <: SequentIndex]( proof: LKskProof, p: LKskProof, a: SequentIndex,
                                           constructor: ( LKskProof, Side ) => LKskProof,
                                           pred:        HOLFormula => Boolean )( implicit cut_ancs: Sequent[Boolean] ): Set[( LKskProof, Sequent[Boolean] )] = {
    val s = apply( p, copySetToAncestor( proof.occConnectors( 0 ), cut_ancs ), pred )
    if ( cut_ancs( proof.mainIndices( 0 ) ) ) s
    else s.map( pm => {
      val List( a_ : Side ) = pickrule( proof, List( p ), List( pm ), List( a ), pick_from_es )
      val rule = constructor( pm._1, a_ )
      val nca = calculate_child_cut_ecs( rule, rule.occConnectors( 0 ), pm, false )
      ( rule, nca )
    } )
  }

  def handleWeakQuantRule[Side <: SequentIndex]( proof: LKskProof, p: LKskProof, a: Side, f: HOLFormula, t: LambdaExpression,
                                                 constructor: ( LKskProof, Side, HOLFormula, LambdaExpression ) => LKskProof,
                                                 pred:        HOLFormula => Boolean )( implicit cut_ancs: Sequent[Boolean] ): Set[( LKskProof, Sequent[Boolean] )] = {
    val s = apply( p, copySetToAncestor( proof.occConnectors( 0 ), cut_ancs ), pred )
    if ( cut_ancs( proof.mainIndices( 0 ) ) ) s
    else s.map( pm => {
      val List( a_ : Side ) = pickrule( proof, List( p ), List( pm ), List( a ), pick_from_es )
      val rule = constructor( pm._1, a_, f, t )
      val nca = calculate_child_cut_ecs( rule, rule.occConnectors( 0 ), pm, false )
      ( rule, nca )
    } )
  }

  def handleStrongQuantRule[Side <: SequentIndex]( proof: LKskProof, p: LKskProof, a: Side,
                                                   constructor: ( LKskProof, Side, HOLFormula, Var ) => LKskProof,
                                                   pred:        HOLFormula => Boolean )( implicit cut_ancs: Sequent[Boolean] ): Set[( LKskProof, Sequent[Boolean] )] = {
    val s = apply( p, copySetToAncestor( proof.occConnectors( 0 ), cut_ancs ), pred )
    if ( cut_ancs( proof.mainIndices( 0 ) ) ) s
    else throw new Exception( "The proof is not skolemized!" )
  }

  def handleStrongSkQuantRule[Side <: SequentIndex]( proof: LKskProof, p: LKskProof, a: Side,
                                                     main:        HOLFormula,
                                                     sk_const:    LambdaExpression,
                                                     sk_def:      LambdaExpression,
                                                     constructor: ( LKskProof, Side, HOLFormula, LambdaExpression, LambdaExpression ) => LKskProof,
                                                     pred:        HOLFormula => Boolean )( implicit cut_ancs: Sequent[Boolean] ): Set[( LKskProof, Sequent[Boolean] )] = {
    val s = apply( p, copySetToAncestor( proof.occConnectors( 0 ), cut_ancs ), pred )
    if ( cut_ancs( proof.mainIndices( 0 ) ) ) s
    else s.map( pm => {
      val List( aux: Side ) = pickrule( proof, List( p ), List( pm ), List( a ), pick_from_es )
      val rp = constructor( pm._1, aux, main, sk_const, sk_def )
      val nca = calculate_child_cut_ecs( rp, rp.occConnectors( 0 ), pm, false )
      ( rp, nca )
    } )
  }

  def handleBinaryRule[Side1 <: SequentIndex, Side2 <: SequentIndex]( proof: LKskProof, p1: LKskProof, p2: LKskProof,
                                                                      a1: Side1, a2: Side2,
                                                                      constructor: ( LKskProof, Side1, LKskProof, Side2 ) => LKskProof,
                                                                      pred:        HOLFormula => Boolean )( implicit cut_ancs: Sequent[Boolean] ) = {
    val new_cut_ancs1 = copySetToAncestor( proof.occConnectors( 0 ), cut_ancs )
    val new_cut_ancs2 = copySetToAncestor( proof.occConnectors( 1 ), cut_ancs )
    val s1 = apply( p1, new_cut_ancs1, pred )
    val s2 = apply( p2, new_cut_ancs2, pred )
    // private val nLine = sys.props("line.separator")
    //println("Binary rule on:"+nLine+s1.map(_.root)+nLine+s2.map(_.root))
    if ( cut_ancs( proof.mainIndices( 0 ) ) )
      handleBinaryCutAnc( proof, p1, p2, s1, s2, new_cut_ancs1, new_cut_ancs2 )
    else
      handleBinaryESAnc( proof, p1, p2, s1, s2, constructor )
  }

  def handleEqRule( proof: LKskProof, p: LKskProof, e: SequentIndex, a: SequentIndex,
                    flipped: Boolean, con: Abs,
                    pred: HOLFormula => Boolean )( implicit cut_ancs: Sequent[Boolean] ): Set[( LKskProof, Sequent[Boolean] )] = {
    val new_cut_ancs = copySetToAncestor( proof.occConnectors( 0 ), cut_ancs )
    val s1 = apply( p, new_cut_ancs, pred )
    /* distinguish on the cut-ancestorship of the equation (left component) and of the auxiliary formula (right component)
       since the rule does not give direct access to the occurence of e in the conclusion, we look at the premise
     */
    val e_idx_conclusion = proof.occConnectors( 0 ).child( e )
    val aux_ca = cut_ancs( proof.mainIndices( 0 ) )
    val eq_ca = cut_ancs( e_idx_conclusion )
    val mainf = proof.conclusion( proof.mainIndices( 0 ) )
    val eqf = proof.conclusion( e_idx_conclusion )
    //println( s"main formula: $mainf $aux_ca eq: $eqf $eq_ca" )
    ( aux_ca, eq_ca ) match {
      case ( true, true ) =>
        //println( "eq t t" )
        s1
      case ( true, false ) =>
        //println( s"eq t f with eq ${eqf} and aux ${mainf}" )
        val ef = p.conclusion( e )
        val ax = Axiom( ef._1, ef._1, ef._2 )
        val main_e = proof.mainIndices( 0 )
        val es = proof.conclusion.zipWithIndex.filter( x => x._2 != main_e && x._2 != e_idx_conclusion && !cut_ancs( x._2 ) ).map( _._1 )
        val wax = weakenESAncs( es, Set( ( ax, Sequent( false :: Nil, true :: Nil ) ) ) ) //TODO: check cut-ancestorship of ax
        s1 ++ wax
      case ( false, true ) =>
        //println( "eq f t" )
        s1 map ( pm => {
          //println( p.endSequent( e ) )
          //we first pick our aux formula
          val candidates = a match {
            case Ant( _ ) => pm._1.conclusion.zipWithIndex.antecedent
            case Suc( _ ) => pm._1.conclusion.zipWithIndex.succedent
          }
          val aux = pick( p, a, candidates )
          //then add the weakening
          //println( "weakening: " + p.endSequent( e ) )
          val wproof = WeakeningLeft( pm._1, p.conclusion( e ) )
          val wcas = calculate_child_cut_ecs( wproof, wproof.occConnectors( 0 ), pm, false ) //TODO: check if default false is correct
          //trace the aux formulas to the new rule
          val conn = wproof.occConnectors( 0 )
          val waux = conn.child( aux )
          val weq = wproof.mainIndices( 0 ) match {
            case a @ Ant( _ ) => a
            case _            => throw new Exception( "Equation occurrence in must be in antecedent!" )
          }
          require( waux != weq, "Aux formulas must be different!" )
          //and apply it
          val rule = Equality( wproof, weq, waux, flipped, con )
          val cas = calculate_child_cut_ecs( rule, rule.occConnectors( 0 ), ( wproof, wcas ), false ) //TODO: check if default false is correct
          ( rule, cas )
        } )
      case ( false, false ) =>
        //println( "eq f f" )
        s1 map ( pm => {
          //println( p.endSequent( e ) )
          val List( a1_, a2_ ) = pickrule( proof, List( p ), List( pm ), List( e, a ), pick_from_es )
          val aeq = a1_ match {
            case a @ Ant( _ ) => a
            case _            => throw new Exception( "Equation occurrence in must be in antecedent!" )
          }
          val r = Equality( pm._1, aeq, a2_, flipped, con )
          val cas = calculate_child_cut_ecs( r, r.occConnectors( 0 ), pm, false ) //TODO: check if default false is correct
          ( r, cas )
        } )

    }
  }

}

