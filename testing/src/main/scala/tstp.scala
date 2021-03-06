package at.logic.gapt.testing

import ammonite.ops.FilePath
import at.logic.gapt.proofs.expansion.numberOfInstancesET
import at.logic.gapt.proofs.loadExpansionProof
import at.logic.gapt.utils.{ LogHandler, verbose }

object testTstpImport extends App {
  val Array( filename ) = args
  verbose {
    val exp = loadExpansionProof( FilePath( filename ) )
    println( s"num_insts = ${numberOfInstancesET( exp )}" )
    println( "OK" )
  }
}