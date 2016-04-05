name := "ladLasso"

version := "1.0"

scalaVersion := "2.10.4"

// additional libraries

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "1.5.2" % "provided",
  "org.apache.spark" %% "spark-sql" % "1.5.2",
  "org.apache.spark" %% "spark-hive" % "1.5.2",
  "org.apache.spark" %% "spark-mllib" % "1.5.2",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "org.scalanlp" %% "breeze" % "0.11.2",
  "org.scalanlp" %% "breeze-natives" % "0.11.2"
)




