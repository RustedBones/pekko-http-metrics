// General info
val username  = "RustedBones"
val repo      = "pekko-http-metrics"
val githubUrl = s"https://github.com/$username/$repo"

ThisBuild / tlBaseVersion    := "1.0"
ThisBuild / organization     := "fr.davit"
ThisBuild / organizationName := "Michel Davit"
ThisBuild / startYear        := Some(2019)
ThisBuild / licenses         := Seq(License.Apache2)
ThisBuild / homepage         := Some(url(githubUrl))
ThisBuild / scmInfo          := Some(ScmInfo(url(githubUrl), s"git@github.com:$username/$repo.git"))
ThisBuild / developers       := List(
  Developer(
    id = s"$username",
    name = "Michel Davit",
    email = "michel@davit.fr",
    url = url(s"https://github.com/$username")
  )
)

// scala versions
val scala3       = "3.3.0"
val scala213     = "2.13.11"
val scala212     = "2.12.18"
val defaultScala = scala3

// github actions
val java17      = JavaSpec.temurin("17")
val java11      = JavaSpec.temurin("11")
val defaultJava = java17

ThisBuild / scalaVersion                 := defaultScala
ThisBuild / crossScalaVersions           := Seq(scala3, scala213, scala212)
ThisBuild / githubWorkflowTargetBranches := Seq("main")
ThisBuild / githubWorkflowJavaVersions   := Seq(java17, java11)

// build
ThisBuild / tlFatalWarnings := true
ThisBuild / tlJdkRelease    := Some(8)

// mima
ThisBuild / mimaBinaryIssueFilters ++= Seq()

lazy val commonSettings = Defaults.itSettings ++
  headerSettings(IntegrationTest) ++
  inConfig(IntegrationTest)(ScalafmtPlugin.scalafmtConfigSettings)

lazy val `pekko-http-metrics` = (project in file("."))
  .aggregate(
    `pekko-http-metrics-core`,
    `pekko-http-metrics-datadog`,
    `pekko-http-metrics-graphite`,
    `pekko-http-metrics-dropwizard`,
    `pekko-http-metrics-dropwizard-v5`,
    `pekko-http-metrics-prometheus`
  )
  .settings(commonSettings)
  .settings(
    publishArtifact := false
  )

lazy val `pekko-http-metrics-core` = (project in file("core"))
  .configs(IntegrationTest)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.Enumeratum,
      Dependencies.PekkoHttp,
      Dependencies.Provided.PekkoStream,
      Dependencies.Test.Logback,
      Dependencies.Test.Mockito,
      Dependencies.Test.PekkoHttpTestkit,
      Dependencies.Test.PekkoSlf4j,
      Dependencies.Test.PekkoStreamTestkit,
      Dependencies.Test.ScalaTest
    )
  )

lazy val `pekko-http-metrics-datadog` = (project in file("datadog"))
  .configs(IntegrationTest)
  .dependsOn(`pekko-http-metrics-core`)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.Datadog,
      Dependencies.Provided.PekkoStream,
      Dependencies.Test.Logback,
      Dependencies.Test.PekkoHttpTestkit,
      Dependencies.Test.PekkoSlf4j,
      Dependencies.Test.PekkoStreamTestkit,
      Dependencies.Test.ScalaTest
    )
  )

lazy val `pekko-http-metrics-dropwizard` = (project in file("dropwizard"))
  .configs(IntegrationTest)
  .dependsOn(`pekko-http-metrics-core`)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.DropwizardCore,
      Dependencies.DropwizardJson,
      Dependencies.ScalaLogging,
      Dependencies.Provided.PekkoStream,
      Dependencies.Test.DropwizardJvm,
      Dependencies.Test.Logback,
      Dependencies.Test.PekkoHttpJson,
      Dependencies.Test.PekkoHttpTestkit,
      Dependencies.Test.PekkoSlf4j,
      Dependencies.Test.PekkoStreamTestkit,
      Dependencies.Test.PekkoTestkit,
      Dependencies.Test.ScalaCollectionCompat,
      Dependencies.Test.ScalaTest
    )
  )

lazy val `pekko-http-metrics-dropwizard-v5` = (project in file("dropwizard-v5"))
  .configs(IntegrationTest)
  .dependsOn(`pekko-http-metrics-core`)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.DropwizardV5Core,
      Dependencies.DropwizardV5Json,
      Dependencies.Provided.PekkoStream,
      Dependencies.Test.DropwizardV5Jvm,
      Dependencies.Test.Logback,
      Dependencies.Test.PekkoHttpJson,
      Dependencies.Test.PekkoHttpTestkit,
      Dependencies.Test.PekkoSlf4j,
      Dependencies.Test.PekkoStreamTestkit,
      Dependencies.Test.PekkoTestkit,
      Dependencies.Test.ScalaCollectionCompat,
      Dependencies.Test.ScalaTest
    )
  )

lazy val `pekko-http-metrics-graphite` = (project in file("graphite"))
  .configs(IntegrationTest)
  .dependsOn(`pekko-http-metrics-core`)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.Provided.PekkoStream,
      Dependencies.Test.Logback,
      Dependencies.Test.PekkoHttpTestkit,
      Dependencies.Test.PekkoSlf4j,
      Dependencies.Test.PekkoStreamTestkit,
      Dependencies.Test.ScalaTest
    )
  )

lazy val `pekko-http-metrics-prometheus` = (project in file("prometheus"))
  .configs(IntegrationTest)
  .dependsOn(`pekko-http-metrics-core`)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.PrometheusCommon,
      Dependencies.Provided.PekkoStream,
      Dependencies.Test.Logback,
      Dependencies.Test.PekkoHttpTestkit,
      Dependencies.Test.PekkoSlf4j,
      Dependencies.Test.PekkoStreamTestkit,
      Dependencies.Test.PekkoTestkit,
      Dependencies.Test.PrometheusHotspot,
      Dependencies.Test.ScalaTest
    )
  )
