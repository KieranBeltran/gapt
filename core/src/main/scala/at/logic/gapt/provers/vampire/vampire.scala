package at.logic.gapt.provers.vampire

import java.io.IOException

import at.logic.gapt.expr._
import at.logic.gapt.formats.StringInputFile
import at.logic.gapt.formats.tptp.{ TPTPFOLExporter, TptpProofParser }
import at.logic.gapt.proofs.resolution.{ ResolutionProof, fixDerivation }
import at.logic.gapt.proofs.{ FOLClause, HOLClause }
import at.logic.gapt.proofs.sketch.RefutationSketchToResolution
import at.logic.gapt.provers.{ ResolutionProver, renameConstantsToFi }
import at.logic.gapt.utils.{ ExternalProgram, runProcess }

object Vampire extends Vampire( commandName = "vampire", extraArgs = Nil )
class Vampire( commandName: String = "vampire", extraArgs: List[String] = Nil ) extends ResolutionProver with ExternalProgram {
  override def getResolutionProof( seq: Traversable[HOLClause] ): Option[ResolutionProof] =
    renameConstantsToFi.wrap( seq.toList )(
      ( renaming, cnf: List[HOLClause] ) => {
        val labelledCNF = cnf.zipWithIndex.map { case ( clause, index ) => s"formula$index" -> clause.asInstanceOf[FOLClause] }.toMap
        val tptpIn = TPTPFOLExporter.exportLabelledCNF( labelledCNF ).toString
        val output = runProcess.withTempInputFile(
          commandName +: "-p" +: "tptp" +: extraArgs,
          tptpIn
        ).split( "\n" )
        if ( output.head startsWith "Refutation" ) {
          val sketch = TptpProofParser.parse( StringInputFile( output.drop( 1 ).takeWhile( !_.startsWith( "---" ) ).mkString( "\n" ) ) )._2
          val Right( resolution ) = RefutationSketchToResolution( sketch )
          Some( fixDerivation( resolution, cnf ) )
        } else None
      }
    )

  override val isInstalled: Boolean =
    try {
      runProcess( commandName +: extraArgs :+ "--version" )
      true
    } catch {
      case ex: IOException => false
    }
}

