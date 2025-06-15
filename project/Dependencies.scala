import sbt._

object Dependencies {

  object Versions {
    val Datadog               = "4.4.3"
    val Dropwizard            = "4.2.30"
    val DropwizardV5          = "5.0.0"
    val Enumeratum            = "1.7.5"
    val Logback               = "1.5.18"
    val Pekko                 = "1.1.3"
    val PekkoHttp             = "1.1.0"
    val Prometheus            = "1.3.6"
    val ScalaCollectionCompat = "2.13.0"
    val ScalaLogging          = "3.9.5"
    val ScalaMock             = "6.2.0"
    val ScalaTest             = "3.2.19"
  }

  val Datadog                     = "com.datadoghq"          % "java-dogstatsd-client"   % Versions.Datadog
  val DropwizardCore              = "io.dropwizard.metrics"  % "metrics-core"            % Versions.Dropwizard
  val DropwizardJson              = "io.dropwizard.metrics"  % "metrics-json"            % Versions.Dropwizard
  val DropwizardV5Core            = "io.dropwizard.metrics5" % "metrics-core"            % Versions.DropwizardV5
  val DropwizardV5Json            = "io.dropwizard.metrics5" % "metrics-json"            % Versions.DropwizardV5
  val Enumeratum                  = "com.beachape"          %% "enumeratum"              % Versions.Enumeratum
  val PekkoHttp                   = "org.apache.pekko"      %% "pekko-http"              % Versions.PekkoHttp
  val PrometheusCore              = "io.prometheus"          % "prometheus-metrics-core" % Versions.Prometheus
  val PrometheusExpositionFormats =
    "io.prometheus" % "prometheus-metrics-exposition-formats" % Versions.Prometheus
  val ScalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % Versions.ScalaLogging

  object Provided {
    val PekkoStream = "org.apache.pekko" %% "pekko-stream" % Versions.Pekko % "provided"
  }

  object Test {
    val DropwizardJvm         = "io.dropwizard.metrics"  % "metrics-jvm"                            % Versions.Dropwizard   % "test"
    val DropwizardV5Jvm       = "io.dropwizard.metrics5" % "metrics-jvm"                            % Versions.DropwizardV5 % "test"
    val Logback               = "ch.qos.logback"         % "logback-classic"                        % Versions.Logback      % "test"
    val PekkoHttpJson         = "org.apache.pekko"      %% "pekko-http-spray-json"                  % Versions.PekkoHttp    % "test"
    val PekkoHttpTestkit      = "org.apache.pekko"      %% "pekko-http-testkit"                     % Versions.PekkoHttp    % "test"
    val PekkoSlf4j            = "org.apache.pekko"      %% "pekko-slf4j"                            % Versions.Pekko        % "test"
    val PekkoStreamTestkit    = "org.apache.pekko"      %% "pekko-stream-testkit"                   % Versions.Pekko        % "test"
    val PekkoTestkit          = "org.apache.pekko"      %% "pekko-testkit"                          % Versions.Pekko        % "test"
    val PrometheusJvm         = "io.prometheus"          % "prometheus-metrics-instrumentation-jvm" % Versions.Prometheus   % "test"
    val ScalaCollectionCompat =
      "org.scala-lang.modules" %% "scala-collection-compat" % Versions.ScalaCollectionCompat % "test"
    val ScalaMock = "org.scalamock" %% "scalamock" % Versions.ScalaMock % "test"
    val ScalaTest = "org.scalatest" %% "scalatest" % Versions.ScalaTest % "test"
  }
}
