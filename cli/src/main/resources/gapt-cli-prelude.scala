import ammonite.ops._
import at.logic.gapt._
import at.logic.gapt.examples._
import at.logic.gapt.expr._
import at.logic.gapt.expr.fol._
import at.logic.gapt.expr.hol._
import at.logic.gapt.formats.babel.BabelParser.parseFormula
import at.logic.gapt.formats.dimacs._
import at.logic.gapt.formats.tip._
import at.logic.gapt.formats.tptp._
import at.logic.gapt.formats.verit._
import at.logic.gapt.formats.llk._
import at.logic.gapt.formats.latex._
import at.logic.gapt.formats.lean._
import at.logic.gapt.grammars._
import at.logic.gapt.proofs.reduction._
import at.logic.gapt.proofs.drup._
import at.logic.gapt.proofs.epsilon._
import at.logic.gapt.proofs.expansion._
import at.logic.gapt.proofs.hoare._
import at.logic.gapt.proofs._
import at.logic.gapt.proofs.ceres._
import at.logic.gapt.proofs.lk._
import at.logic.gapt.cutintro._
import at.logic.gapt.proofs.resolution._
import at.logic.gapt.provers.sat._
import at.logic.gapt.provers.leancop._
import at.logic.gapt.provers.viper._
import at.logic.gapt.provers.prover9._
import at.logic.gapt.provers.maxsat._
import at.logic.gapt.provers.eprover._
import at.logic.gapt.provers.metis._
import at.logic.gapt.provers.vampire._
import at.logic.gapt.provers.verit._
import at.logic.gapt.provers.smtlib._
import at.logic.gapt.provers.escargot._
import at.logic.gapt.provers.spass._
import at.logic.gapt.prooftool.prooftool
import at.logic.gapt.utils._
import cats.syntax.all._, cats.instances.all._, EitherHelpers._
import at.logic.gapt.cli.GPL.{apply => copying, printLicense => license}
