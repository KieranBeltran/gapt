package at.logic.gapt.proofs.ceres

import at.logic.gapt.expr.{ And, _ }
import at.logic.gapt.expr.hol.{ CNFn, toNNF }
import at.logic.gapt.proofs.Context.{ PrimRecFun, PrimRecFunBatch }
import at.logic.gapt.proofs.{ Context, MutableContext, Sequent }

object CharFormN extends StructVisitor[Formula, List[Nothing]] {
  def apply[Data]( struct: Struct[Data] ): Formula = recurse( struct, StructTransformer[Formula, List[Nothing]](
    { ( x, _ ) => x }, { ( x, y, _ ) => And( x, y ) }, Top(), { ( x, y, _ ) => Or( x, y ) }, Bottom(), { ( x, _ ) => Neg( x ) },
    { ( _, _, _ ) => throw new Exception( "Should not contain CLS terms" ) } ), Nil )
}
object CharFormPRN {
  def apply( SCS: Map[Struct[Nothing], ( Struct[Nothing], Set[Var] )] ): Map[Formula, ( Formula, Set[Var] )] = Support(
    SCS, stTN ) //.map{ case (x,(y,z)) => (x,(SCSinCNF(y),z)) }
  private def SCSinCNF( st: Struct[Nothing] ): Struct[Nothing] = st match {
    case Plus( x, Times( y, z, w ) ) => Times[Nothing]( Plus[Nothing]( SCSinCNF( x ), SCSinCNF( y ) ), Plus[Nothing]( SCSinCNF( x ), SCSinCNF( z ) ), w )
    case Plus( Times( y, z, w ), x ) => Times[Nothing]( Plus[Nothing]( SCSinCNF( y ), SCSinCNF( x ) ), Plus[Nothing]( SCSinCNF( z ), SCSinCNF( x ) ), w )
    case Plus( x, y )                => Plus[Nothing]( SCSinCNF( x ), SCSinCNF( y ) )
    case Times( x, y, w )            => Times[Nothing]( SCSinCNF( x ), SCSinCNF( y ), w )
    case Dual( x )                   => Dual[Nothing]( SCSinCNF( x ) )
    case x                           => x
  }
  private val stTN = StructTransformer[Formula, Map[( ( String, Option[Expr] ), Sequent[Boolean] ), String]](
    { ( x, _ ) => x }, { ( x, y, _ ) => And( x, y ) }, Top(), { ( x, y, _ ) => Or( x, y ) }, Bottom(), { ( x, _ ) => Neg( x ) }, Support.cF )
  def PR( ChF: Map[Formula, ( Formula, Set[Var] )] )( implicit ctx: MutableContext ): Unit = Support.add( ChF, ForallC )
}
object CharFormP extends StructVisitor[Formula, List[Nothing]] {
  def apply[Data]( struct: Struct[Data] ): Formula = recurse( struct, StructTransformer[Formula, List[Nothing]](
    { ( x, _ ) => toNNF( Neg( x ) ) }, { ( x, y, _ ) => Or( x, y ) }, Bottom(), { ( x, y, _ ) => And( x, y ) }, Top(), { ( x, _ ) => Neg( x ) },
    { ( _, _, _ ) => throw new Exception( "Should not contain CLS terms" ) } ), Nil )
}
object CharFormPRP {
  def apply( SCS: Map[Struct[Nothing], ( Struct[Nothing], Set[Var] )] ): Map[Formula, ( Formula, Set[Var] )] = Support( SCS, stTP )
  private val stTP = StructTransformer[Formula, Map[( ( String, Option[Expr] ), Sequent[Boolean] ), String]](
    { ( x, _ ) => Neg( x ) }, { ( x, y, _ ) => Or( x, y ) }, Bottom(), { ( x, y, _ ) => And( x, y ) }, Top(), { ( x, _ ) => Neg( x ) }, Support.cF )
  def PR( ChF: Map[Formula, ( Formula, Set[Var] )] )( implicit ctx: MutableContext ): Unit = Support.add( ChF, ExistsC )
}

private object Support {
  def apply(
    SCS: Map[Struct[Nothing], ( Struct[Nothing], Set[Var] )],
    stT: StructTransformer[Formula, Map[( ( String, Option[Expr] ), Sequent[Boolean] ), String]] ): Map[Formula, ( Formula, Set[Var] )] = {
    val names = structNames( SCS )
    SCS.keySet.map( x => {
      val CLS( Apps( Const( name, _ ), vs ), cc ) = x
      val thefirst = vs.headOption.getOrElse( {
        throw new Exception( "Should not be empty" )
      } )
      val result = freeVariables( thefirst ).headOption
      val ( one, two ) = SCS.getOrElse( x, {
        throw new Exception( "?????" )
      } )
      ( Atom( names.getOrElse( ( ( name, result ), cc ), {
        throw new Exception( "?????" )
      } ) + "PR", vs ), ( constructingForm( one, names, stT ), two ) )
    } ).toMap
  }

  def cF( pn: Expr, cc: Sequent[Boolean], mn: Map[( ( String, Option[Expr] ), Sequent[Boolean] ), String] ): Formula = {
    val Apps( Const( name, _ ), vs ) = pn
    val thefirst = vs.headOption.getOrElse( { throw new Exception( "Should not be empty" ) } )
    val result: Option[Expr] = freeVariables( thefirst ).headOption
    Atom( mn.getOrElse( ( ( name, result ), cc ), { throw new Exception( "Should be in map" ) } ), vs )
  }
  //assuming NNFCNF
  private def QuantIntroForAll( f: Formula, qType: QuantifierC, evar: Set[Var] ): Formula = f match {
    case And( x, y )  => And( QuantIntroForAll( x, qType, evar ), QuantIntroForAll( y, qType, evar ) )
    case Or( _, _ )   => evar.intersect( freeVariables( f ) ).foldLeft( f )( ( x, y ) => Apps( qType( y.ty ), Abs( y, x ) ).asInstanceOf[Formula] )
    case Atom( _, _ ) => evar.intersect( freeVariables( f ) ).foldLeft( f )( ( x, y ) => Apps( qType( y.ty ), Abs( y, x ) ).asInstanceOf[Formula] )
    case Top()        => Top()
    case Bottom()     => Bottom()
    case Neg( x )     => Neg( QuantIntroForAll( x, qType, evar ) )
    case _            => throw new Exception( "???????????????" + f )
  }
  private def QuantIntroExists( f: Formula, qType: QuantifierC, evar: Set[Var] ): Formula = f match {
    case And( x, y )  => And( QuantIntroExists( x, qType, evar ), QuantIntroExists( y, qType, evar ) )
    case Or( _, _ )   => evar.intersect( freeVariables( f ) ).foldLeft( f )( ( x, y ) => Apps( qType( y.ty ), Abs( y, x ) ).asInstanceOf[Formula] )
    case Atom( _, _ ) => evar.intersect( freeVariables( f ) ).foldLeft( f )( ( x, y ) => Apps( qType( y.ty ), Abs( y, x ) ).asInstanceOf[Formula] )
    case Top()        => Top()
    case Bottom()     => Bottom()
    case Neg( x )     => Neg( QuantIntroExists( x, qType, evar ) )
    case _            => throw new Exception( "???????????????" + f )
  }
  def add( ChF: Map[Formula, ( Formula, Set[Var] )], qType: QuantifierC )( implicit ctx: MutableContext ): Unit = {
    val preRes = ChF.keySet.map( x => {
      val ( one, two ) = ChF.getOrElse( x, {
        throw new Exception( "?????" )
      } )
      ( x, if ( qType.equals( ForallC ) ) QuantIntroForAll( one, qType, two ) else QuantIntroExists( one, qType, two ) )
    } ).toMap
    val ( namecha, nextRes ) = preRes.keySet.map( x => {
      val one = preRes.getOrElse( x, {
        throw new Exception( "?????" )
      } )
      val Atom( Const( name, _ ), vs ) = x
      val newEx = Const( name, vs.reverse.foldLeft( To.asInstanceOf[Ty] )( ( x, y ) => TArr( y.ty, x ) ) ).asInstanceOf[Expr]
      ( ( name.substring( 0, name.length - 2 ), newEx ), ( newEx, ( Apps( newEx, vs: _* ), one ) ) )
    } ).toList.unzip
    val namesdis = namecha.toSet
    addintern( nextRes.map {
      case ( x, ( y, z ) ) =>
        ( x, ( y, namesdis.foldLeft( z: Expr )( ( w, r ) => {
          SwitchToApps( w, r._2, r._1 )
        } ) ) )
    }.foldLeft( Map[Expr, Set[( Expr, Expr )]]() )( ( x, y ) => {
      val ( one, ( two, three ) ) = y
      val theset = x.getOrElse( one, Set() ) ++ Set( ( two, three ) )
      x ++ Map( ( one, theset ) )
    } ) )
  }
  private def structNames( sss: Map[Struct[Nothing], ( Struct[Nothing], Set[Var] )] ): Map[( ( String, Option[Expr] ), Sequent[Boolean] ), String] =
    sss.keySet.map {
      case CLS( Apps( Const( name, _ ), vs ), cc ) =>
        val thefirst = vs.headOption.getOrElse( { throw new Exception( "Should not be empty" ) } )
        val result: Option[Expr] = freeVariables( thefirst ).headOption
        ( ( ( name, result ), cc ), name )
    }.toMap
  private def SwitchToApps( Form: Expr, newEx: Expr, const: String ): Expr = {
    Form match {
      case And( x, y )                      => And( SwitchToApps( x, newEx, const ), SwitchToApps( y, newEx, const ) )
      case Or( x, y )                       => Or( SwitchToApps( x, newEx, const ), SwitchToApps( y, newEx, const ) )
      case Neg( x )                         => Neg( SwitchToApps( x, newEx, const ) )
      case App( ForallC( w ), Abs( r, x ) ) => App( ForallC( w ), Abs( r, SwitchToApps( x, newEx, const ) ) )
      case App( ExistsC( w ), Abs( r, x ) ) => App( ExistsC( w ), Abs( r, SwitchToApps( x, newEx, const ) ) )
      case Atom( Const( con, _ ), vs ) =>
        if ( con.matches( const ) ) Apps( newEx, vs: _* )
        else Atom( con, vs )
      case Top()    => Top()
      case Bottom() => Bottom()
      case x        => throw new Exception( "Should't be here " + x )
    }
  }

  private def addintern( ChF: Map[Expr, Set[( Expr, Expr )]] )( implicit ctx: MutableContext ): Unit = {
    val forCtx = ChF.keySet.map( x => {
      val one = ChF.getOrElse( x, {
        throw new Exception( "?????" )
      } )
      val ret: ( Option[Expr], Option[Expr] ) = one.foldLeft( ( Option[Expr]( Atom( "", List() ) ), Option[Expr]( Atom( "", List() ) ) ) )( ( x, z ) => {
        val ( y, w ) = z
        val Atom( _, vs ) = y
        val zero = ctx.get[Context.Constants].constants.getOrElse( "0", {
          throw new Exception( "nat not defined" + ctx.get[Context.Constants].constants.toString() )
        } )
        if ( vs.head.equals( zero ) ) ( Some( Apps( EqC( y.ty ), Seq[Expr]( y, w ) ) ), x._2 )
        else ( x._1, Some( Apps( EqC( y.ty ), Seq[Expr]( y, w ) ) ) )
      } )
      val bc = ret._1.getOrElse( {
        throw new Exception( "?????" )
      } ).toString
      val sc = ret._2.getOrElse( {
        throw new Exception( "?????" )
      } ).toString
      ( x.asInstanceOf[Const], Seq( bc, sc ) )
    } )
    ctx += PrimRecFun( forCtx )
  }
  private object constructingForm extends StructVisitor[Formula, Map[( ( String, Option[Expr] ), Sequent[Boolean] ), String]] {
    def apply[Data]( struct: Struct[Data], names: Map[( ( String, Option[Expr] ), Sequent[Boolean] ), String],
                     stT: StructTransformer[Formula, Map[( ( String, Option[Expr] ), Sequent[Boolean] ), String]] ): Formula =
      recurse( struct, stT, names )
  }
}