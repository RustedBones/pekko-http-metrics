import _root_.io.github.davidgregory084.ScalacOption

// General info
val username = "RustedBones"
val repo     = "pekko-http-metrics"

lazy val filterScalacOptions = { options: Set[ScalacOption] =>
  options.filterNot(Set(
    ScalacOptions.privateWarnValueDiscard,
    ScalacOptions.warnValueDiscard,
    ScalacOptions.warnNonUnitStatement
  ))
}

// for sbt-github-actions
ThisBuild / crossScalaVersions := Seq("2.13.11", "2.12.18")
ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Sbt(name = Some("Check project"), commands = List("scalafmtCheckAll", "headerCheckAll")),
  WorkflowStep.Sbt(name = Some("Build project"), commands = List("test", "it:test"))
)
ThisBuild / githubWorkflowTargetBranches := Seq("main")
ThisBuild / githubWorkflowPublishTargetBranches := Seq.empty

lazy val commonSettings = Defaults.itSettings ++
  headerSettings(IntegrationTest) ++
  inConfig(IntegrationTest)(ScalafmtPlugin.scalafmtConfigSettings) ++
  Seq(
    organization := "fr.davit",
    organizationName := "Michel Davit",
    crossScalaVersions := (ThisBuild / crossScalaVersions).value,
    scalaVersion := crossScalaVersions.value.head,
    tpolecatScalacOptions ~= filterScalacOptions,
    homepage := Some(url(s"https://github.com/$username/$repo")),
    licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
    startYear := Some(2019),
    scmInfo := Some(ScmInfo(url(s"https://github.com/$username/$repo"), s"git@github.com:$username/$repo.git")),
    developers := List(
      Developer(
        id = s"$username",
        name = "Michel Davit",
        email = "michel@davit.fr",
        url = url(s"https://github.com/$username")
      )
    ),
    publishMavenStyle := true,
    Test / publishArtifact := false,
    // Release version of Pekko not yet available so use Apache nightlies for now
    resolvers += "Apache Nightlies" at "https://repository.apache.org/content/groups/snapshots",
    publishTo := {
      if (isSnapshot.value) {
        Resolver.sonatypeOssRepos("snapshots").headOption
      } else {
        Resolver.sonatypeOssRepos("releases").headOption
      }
    },
    releaseCrossBuild := true,
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    credentials ++= (for {
      username <- sys.env.get("SONATYPE_USERNAME")
      password <- sys.env.get("SONATYPE_PASSWORD")
    } yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)).toSeq
  )

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
      Dependencies.PekkoHttp,
      Dependencies.Enumeratum,
      Dependencies.Provided.PekkoStream,
      Dependencies.Test.PekkoHttpTestkit,
      Dependencies.Test.PekkoSlf4j,
      Dependencies.Test.Logback,
      Dependencies.Test.PekkoStreamTestkit,
      Dependencies.Test.ScalaMock,
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
      Dependencies.Test.PekkoHttpTestkit,
      Dependencies.Test.PekkoSlf4j,
      Dependencies.Test.PekkoStreamTestkit,
      Dependencies.Test.Logback,
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
      Dependencies.Test.PekkoHttpJson,
      Dependencies.Test.PekkoHttpTestkit,
      Dependencies.Test.PekkoSlf4j,
      Dependencies.Test.PekkoStreamTestkit,
      Dependencies.Test.PekkoTestkit,
      Dependencies.Test.DropwizardJvm,
      Dependencies.Test.Logback,
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
      Dependencies.Test.PekkoHttpJson,
      Dependencies.Test.PekkoHttpTestkit,
      Dependencies.Test.PekkoSlf4j,
      Dependencies.Test.PekkoStreamTestkit,
      Dependencies.Test.PekkoTestkit,
      Dependencies.Test.DropwizardV5Jvm,
      Dependencies.Test.Logback,
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
      Dependencies.Test.PekkoHttpTestkit,
      Dependencies.Test.PekkoSlf4j,
      Dependencies.Test.PekkoStreamTestkit,
      Dependencies.Test.Logback,
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
      Dependencies.Test.PekkoHttpTestkit,
      Dependencies.Test.PekkoSlf4j,
      Dependencies.Test.PekkoStreamTestkit,
      Dependencies.Test.PekkoTestkit,
      Dependencies.Test.Logback,
      Dependencies.Test.PrometheusHotspot,
      Dependencies.Test.ScalaTest
    )
  )
