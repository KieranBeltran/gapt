resolvers += Classpaths.sbtPluginReleases
logLevel := Level.Warn

libraryDependencies += "org.apache.commons" % "commons-compress" % "1.12"

addSbtPlugin( "org.scoverage" %% "sbt-scoverage" % "1.4.0" )

// Provides an assembly task which produces a fat jar with all dependencies included.
addSbtPlugin( "com.eed3si9n" % "sbt-assembly" % "0.14.3" )

addSbtPlugin( "com.eed3si9n" % "sbt-unidoc" % "0.3.3" )

addSbtPlugin( "org.scalariform" % "sbt-scalariform" % "1.6.0" )

addSbtPlugin( "me.lessis" % "bintray-sbt" % "0.3.0" )
