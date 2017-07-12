name := "Persistence"

version := "1.0"

scalaVersion := "2.11.7"

val akka_version = "2.4.14"

resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akka_version,
  "com.typesafe.akka" %% "akka-persistence" % akka_version,
  "org.iq80.leveldb" % "leveldb" % "0.7",
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
  "com.typesafe.akka" %% "akka-persistence-query-experimental" % akka_version,
  "com.typesafe.akka" % "akka-stream-experimental_2.11" % "1.0",
  "com.typesafe.akka" %% "akka-actor" % akka_version,
  "com.typesafe.akka" %% "akka-remote" % akka_version,

  "com.typesafe.akka" %% "akka-cluster-sharding" % akka_version,

  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "com.typesafe.akka" %% "akka-testkit" % akka_version % "test",

  "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.4.18.1"
)