# Release notes for GAPT

## Version 2.4 (unreleased)

## Version 2.3 (release on 2016-10-10)

* The minimum required Java version is now Java 8
* String interpolators for sequents
* `:-` operator to construct sequents
* `loadExpansionProof` provides convenient access to TSTP proof import
* Refactored prooftool supports n-ary inferences now
* Conversion of unit-equational resolution proofs to unary LK proofs
* Special class for polarities instead of Booleans
* Support for Vampire 4.1
* Support for SPASS 3.9
* Context now support polymorphic declarations (for equality, ...)
* Many-sorted grammars
* Sound definition rules for LK

## Version 2.2 (released on 2016-07-09)

* New resolution calculus with Avatar splitting
* Vampire proof import with splitting
* More reliable leanCoP interface
* Metis interface
* TPTP problem parser
* Removal of obsolete or unmaintained code:
  * Schemata
  * Old LK implementation
  * Old LKsk implementation
  * Old LLK parser
  * Old Delta-Table implemenetation
  * XML parser
* Skolem inferences for LK
* Sequent is now a case class
* Subproofs can be hidden in prooftool now

## Version 2.1 (released on 2016-04-18)

* Escargot, a simple built-in superposition prover
* New expansion tree implementation, now with cuts!
* Type classes for types that are closed under substitution
* gaptic, LK proof input using tactics
  * Most of the included proofs are converted to gaptic
* Faster delta-table algorithm
* Complete input/output format Babel with string interpolators
* Context class that stores data types, constants, definitions, etc.
* SPASS interface supporting splitting inferences
* Skolem symbol validation
* Many-sorted to first-order reduction
* DRUP proof import from SAT solvers (Sat4j, PicoSAT, and Glucose)

## Version 2.0 (released on 2016-01-18)

* Refactoring of (most) calculi (LK, resolution mainly)
* Replay-based proof import for TPTP proofs, including a Vampire and E interface
* New cut-intro code that doesn't need interpolation
* sbt console
* Cleaned up prooftool now supports multiple open windows at once
* Brand new CERES implementation
* Tons of bug fixes

