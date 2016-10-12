package at.logic.gapt.testing

import at.logic.gapt.proofs.expansion.numberOfInstancesET
import at.logic.gapt.proofs.loadExpansionProof
import at.logic.gapt.utils.{ PrintMetrics, metrics }
import better.files._

object testTstpImport extends App {
  val Array( filename ) = args
  metrics.current.value = PrintMetrics
  val exp = loadExpansionProof( filename.toFile )
  println( s"num_insts = ${numberOfInstancesET( exp )}" )
  println( "OK" )
}