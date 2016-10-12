package at.logic.gapt.testing
import java.nio.file._

import at.logic.gapt.expr._
import at.logic.gapt.proofs.expansion.InstanceTermEncoding
import at.logic.gapt.proofs.loadExpansionProof

import scala.App
import better.files._

object dumpTermset extends App {
  val Array( inputFileName, outputFileName ) = args

  def simplifyNames( termset: Set[FOLTerm] ): Set[FOLTerm] = {
    val renaming: Map[LambdaExpression, LambdaExpression] =
      ( constants( termset ).toSeq ++ freeVariables( termset ).toSeq ).sortBy( _.toString ).
        zipWithIndex.map { case ( c, i ) => c -> Const( s"f$i", c.exptype ) }.
        toMap
    termset.map( TermReplacement( _, renaming ).asInstanceOf[FOLTerm] )
  }

  def termToString( t: FOLTerm ): String = t match {
    case FOLConst( f )          => s"$f"
    case FOLFunction( f, args ) => s"$f(${args map termToString mkString ","})"
  }

  def writeTermset( outFile: Path, termset: Set[FOLTerm] ) =
    Files.write( outFile, termset.map( termToString ).toSeq.
      sorted.map( _ + "\n" ).mkString.getBytes( "UTF-8" ) )

  val expansionProof = loadExpansionProof( inputFileName.toFile )
  val encoding = InstanceTermEncoding( expansionProof.shallow, Ti )
  val termSet = encoding.encode( expansionProof ).map( _.asInstanceOf[FOLTerm] )
  writeTermset( Paths get outputFileName, simplifyNames( termSet ) )
}