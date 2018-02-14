val mysql = "mysql" % "mysql-connector-java" % "5.1.24"
val commons_pool2 = "org.apache.commons" % "commons-pool2" % "2.5.0"
val commons_dbcp2 = "org.apache.commons" % "commons-dbcp2" % "2.2.0"

val specs2 = "org.specs2" %% "specs2" % "2.3.13" % Test
val mockito = "org.mockito" % "mockito-all" % "1.9.5" % Test

val dependencies = mysql :: commons_dbcp2 :: commons_pool2 :: specs2 :: mockito :: Nil

lazy val core = Project("querulous-core", base = file("querulous-core"))
  .settings(
    organization := "com.ticketfly",
    version := "2.0.0",
    scalaVersion := "2.11.1",
    crossPaths := true,
    credentials += Credentials(Path.userHome / ".artifactory" / ".credentials"),
    publishTo := {
      if (version.value.trim.endsWith("SNAPSHOT"))
        Some("tfly-snaps" at "http://build.ticketfly.com/artifactory/libs-scala-snapshot-local")
      else
        Some("tfly-release" at "http://build.ticketfly.com/artifactory/libs-scala-release-local")
    },
    libraryDependencies ++= dependencies,
  )

