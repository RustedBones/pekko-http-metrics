# pekko-http-metrics

[![Continuous Integration](https://github.com/RustedBones/pekko-http-metrics/actions/workflows/ci.yml/badge.svg)](https://github.com/RustedBones/pekko-http-metrics/actions/workflows/ci.yml)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/fr.davit/pekko-http-metrics-core_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/fr.davit/pekko-http-metrics-core_2.13)
[![Software License](https://img.shields.io/badge/license-Apache%202-brightgreen.svg?style=flat)](LICENSE)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

Easily collect and expose metrics in your pekko-http server.

The following implementations are supported:

* [datadog](#datadog) (via StatsD)
* [dropwizard](#dropwizard)
* [graphite](#graphite) (via Carbon)
* [prometheus](#prometheus)

## Versions

| Version | Release date | Pekka Http version | Scala versions        |
|---------|--------------|--------------------|-----------------------|
| `2.1.0` | 2025-06-16   | `1.2.0`            | `3.3`, `2.13`         |
| `2.0.0` | 2025-03-16   | `1.1.0`            | `3.3`, `2.13`         |
| `1.1.0` | 2025-03-02   | `1.1.0`            | `3.3`, `2.13`         |
| `1.0.1` | 2024-04-09   | `1.0.1`            | `3.3`, `2.13`, `2.12` |
| `1.0.0` | 2023-08-14   | `1.0.0`            | `3.3`, `2.13`, `2.12` |

## Getting pekko-http-metrics

Libraries are published to Maven Central. Add to your `build.sbt`:

```scala
libraryDependencies += "fr.davit" %% "pekko-http-metrics-<backend>" % <version>
```

### Server metrics

The library enables you to easily record the following metrics from a pekko-http server into a registry. The
following labeled metrics are recorded:

- requests (`counter`) [method]
- requests active (`gauge`) [method]
- requests failures (`counter`) [method]
- requests size (`histogram`) [method]
- responses (`counter`) [method | path | status group]
- responses errors [method | path | status group]
- responses duration (`histogram`) [method | path | status group]
- response size (`histogram`) [method | path | status group]
- connections (`counter`)
- connections active (`gauge`)

Record metrics from your pekko server by creating an `HttpMetricsServerBuilder` with the `newMeteredServerAt` extension
method located in `HttpMetrics`

```scala
import pekko.actor.ActorSystem
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.server.Directives._
import pekko.http.scaladsl.server.Route
import fr.davit.pekko.http.metrics.core.{HttpMetricsRegistry, HttpMetricsSettings}
import fr.davit.pekko.http.metrics.core.HttpMetrics._ // import extension methods

implicit val system = ActorSystem()

val settings: HttpMetricsSettings = ... // concrete settings implementation

val registry: HttpMetricsRegistry = ... // concrete registry implementation

val route: Route = ... // your route

Http()
  .newMeteredServerAt("localhost", 8080, registry)
  .bindFlow(route)
```

Requests failure counter is incremented when no response could be emitted by the server (network error, ...)

By default, the response error counter will be incremented when the returned status code is an `Server error (5xx)`.
You can override this behaviour in the settings.

```scala
settings.withDefineError(_.status.isFailure)
```

In this example, all responses with status >= 400 are considered as errors.

For HTTP2 you must use the `bind` or `bindSync` on the `HttpMetricsServerBuilder`. 
In this case the connection metrics won't be available.

```scala
Http()
  .newMeteredServerAt("localhost", 8080)
  .bind(route)
```

#### Dimensions

By default, metrics dimensions are disabled. You can enable them in the settings.

```scala
settings
  .withIncludeMethodDimension(true)
  .withIncludePathDimension(true)
  .withIncludeStatusDimension(true)
```

Custom dimensions can be added to the message metrics:
- extend the `HttpRequestLabeler` to add labels on requests & their associated response 
- extend the `HttpResponseLabeler` to add labels on responses only

In the example below, the `browser` dimension will be populated based on the user-agent header on requests and responses.
The responses going through the route will have the `user` dimension set with the provided username, other responses
will be `unlabelled`.

```scala
import fr.davit.pekko.http.metrics.core.{AttributeLabeler, HttpRequestLabeler}

// based on https://developer.mozilla.org/en-US/docs/Web/HTTP/Browser_detection_using_the_user_agent#browser_name
object BrowserLabeler extends HttpRequestLabeler {
 override def name: String = "browser"
 override def label(request: HttpRequest): String = {
  val products = for {
   ua <- request.header[`User-Agent`].toSeq
   pv <- ua.products
  } yield pv.product
  if (products.contains("Seamonkey")) "seamonkey"
  else if (products.contains("Firefox")) "firefox"
  else if (products.contains("Chromium")) "chromium"
  else if (products.contains("Chrome")) "chrome"
  else if (products.contains("Safari")) "safari"
  else if (products.contains("OPR") || products.contains("Opera")) "opera"
  else "other"
 }
}

object UserLabeler extends AttributeLabeler {
  def name: String = "user"
}

val route = auth { username =>
 metricsLabeled(UserLabeler, username) {
  ...
 }
}

settings.withCustomDimensions(BrowserLabeler, UserLabeler)
```


Additional static server-level dimensions can be set to all metrics collected by the library.
In the example below, the `env` dimension with `prod` label will be added. 

```scala
import fr.davit.pekko.http.metrics.core.Dimension
settings.withServerDimensions(Dimension("env", "prod"))
```

##### Method

The method of the request is used as dimension on the metrics. eg. `GET`

##### Path

Matched path of the request is used as dimension on the metrics.

When enabled, all metrics will get `unlabelled` as path dimension by default,
You must use the labelled path directives defined in `HttpMetricsDirectives` to set the dimension value.

You must also be careful about cardinality: see [here](https://prometheus.io/docs/practices/naming/#labels).
If your path contains unbounded dynamic segments, you must give an explicit label to override the dynamic part:

```scala
import fr.davit.pekko.http.metrics.core.scaladsl.server.HttpMetricsDirectives._

val route = pathPrefixLabel("api") {
  pathLabeled("user" / JavaUUID, "user/:user-id") { userId =>
    ...
  }
}
```

Moreover, all unhandled requests will have path dimension set to `unhandled`.

##### Status group

The status group creates the following dimensions on the metrics: `1xx|2xx|3xx|4xx|5xx|other`

### Expose metrics

Expose the metrics from the registry on an http endpoint with the `metrics` directive.

```scala
import fr.davit.pekko.http.metrics.core.scaladsl.server.HttpMetricsDirectives._

val route = (get & path("metrics"))(metrics(registry))
```

Of course, you will also need to have the implicit marshaller for your registry in scope.


## Implementations

### [Datadog]( https://docs.datadoghq.com/developers/dogstatsd/)

| metric             | name                    |
|--------------------|-------------------------|
| requests           | requests_count          |
| requests active    | requests_active         |
| requests failures  | requests_failures_count |
| requests size      | requests_bytes          |
| responses          | responses_count         |
| responses errors   | responses_errors_count  |
| responses duration | responses_duration      |
| responses size     | responses_bytes         |
| connections        | connections_count       |
| connections active | connections_active      |

The `DatadogRegistry` is just a facade to publish to your StatsD server. The registry itself not located in the JVM, 
for this reason it is not possible to expose the metrics in your API.

Add to your `build.sbt`:

```scala
libraryDependencies += "fr.davit" %% "pekko-http-metrics-datadog" % <version>
```

Create your registry

```scala
import com.timgroup.statsd.StatsDClient
import fr.davit.pekko.http.metrics.core.HttpMetricsSettings
import fr.davit.pekko.http.metrics.datadog.{DatadogRegistry, DatadogSettings}

val client: StatsDClient = ... // your statsd client
val settings: HttpMetricsSettings = DatadogSettings.default
val registry = DatadogRegistry(client, settings) // or DatadogRegistry(client) to use default settings
```

See datadog's [documentation](https://github.com/dataDog/java-dogstatsd-client) on how to create a StatsD client.


### [Dropwizard](https://metrics.dropwizard.io/)

| metric             | name                            |
|--------------------|---------------------------------|
| requests           | ${namespace}.requests           |
| requests active    | ${namespace}.requests.active    |
| requests failures  | ${namespace}.requests.failures  |
| requests size      | ${namespace}.requests.bytes     |
| responses          | ${namespace}.responses          |
| responses errors   | ${namespace}.responses.errors   |
| responses duration | ${namespace}.responses.duration |
| responses size     | ${namespace}.responses.bytes    |
| connections        | ${namespace}.connections        |
| connections active | ${namespace}.connections.active |

**Important**: The `DropwizardRegistry` does not support labels.
This feature will be available with dropwizard `v5` (still in pre-release phase).

Add to your `build.sbt`:

```scala
libraryDependencies += "fr.davit" %% "pekko-http-metrics-dropwizard" % <version>
// or for dropwizard v5
libraryDependencies += "fr.davit" %% "pekko-http-metrics-dropwizard-v5" % <version>
```

Create your registry

```scala
import com.codahale.metrics.MetricRegistry
import fr.davit.pekko.http.metrics.core.HttpMetricsSettings
import fr.davit.pekko.http.metrics.dropwizard.{DropwizardRegistry, DropwizardSettings}

val dropwizard: MetricRegistry = ... // your dropwizard registry
val settings: HttpMetricsSettings = DropwizardSettings.default
val registry = DropwizardRegistry(dropwizard, settings) // or DropwizardRegistry() to use a fresh registry & default settings
```

Expose the metrics

```scala
import fr.davit.pekko.http.metrics.core.scaladsl.server.HttpMetricsDirectives._
import fr.davit.pekko.http.metrics.dropwizard.marshalling.DropwizardMarshallers._

val route = (get & path("metrics"))(metrics(registry))
```

All metrics from the dropwizard metrics registry will be exposed.
You can find some external exporters [here](https://github.com/dropwizard/metrics/). For instance,
to expose some JVM metrics, you have to add the dedicated dependency and register the metrics set into your collector registry:

```sbt
libraryDependencies += "com.codahale.metrics" % "metrics-jvm" % <version>
```

```scala
import com.codahale.metrics.jvm._

val dropwizard: MetricRegistry = ... // your dropwizard registry
dropwizard.register("jvm.gc", new GarbageCollectorMetricSet())
dropwizard.register("jvm.threads", new CachedThreadStatesGaugeSet(10, TimeUnit.SECONDS))
dropwizard.register("jvm.memory", new MemoryUsageGaugeSet())

val registry = DropwizardRegistry(dropwizard, settings)
```

### [Graphite](https://graphiteapp.org/)

| metric             | name                            |
|--------------------|---------------------------------|
| requests           | ${namespace}.requests           |
| requests active    | ${namespace}.requests.active    |
| requests failures  | ${namespace}.requests.failures  |
| requests size      | ${namespace}.requests.bytes     |
| responses          | ${namespace}.responses          |
| responses errors   | ${namespace}.responses.errors   |
| responses duration | ${namespace}.responses.duration |
| response size      | ${namespace}.responses.bytes    |
| connections        | ${namespace}.connections        |
| connections active | ${namespace}.connections.active |

Add to your `build.sbt`:

```scala
libraryDependencies += "fr.davit" %% "pekko-http-metrics-graphite" % <version>
```

Create your carbon client and your registry

```scala
import fr.davit.pekko.http.metrics.core.HttpMetricsSettings
import fr.davit.pekko.http.metrics.graphite.{CarbonClient, GraphiteRegistry, GraphiteSettings}

val carbonClient: CarbonClient = CarbonClient("hostname", 2003)
val settings: HttpMetricsSettings = GraphiteSettings.default
val registry = GraphiteRegistry(carbonClient, settings) // or PrometheusRegistry(carbonClient) to use default settings
```

### [Prometheus](http://prometheus.io/)

| metric             | name                                    |
|--------------------|-----------------------------------------|
| requests           | ${namespace}_requests_total             |
| requests active    | ${namespace}_requests_active            |
| requests failures  | ${namespace}_requests_failures_total    |
| requests size      | ${namespace}_requests_size_bytes        |
| responses          | ${namespace}_responses_total            |
| responses errors   | ${namespace}_responses_errors_total     |
| responses duration | ${namespace}_responses_duration_seconds |
| responses size     | ${namespace}_responses_size_bytes       |
| connections        | ${namespace}_connections_total          |
| connections active | ${namespace}_connections_active         |

Add to your `build.sbt`:

```scala
libraryDependencies += "fr.davit" %% "pekko-http-metrics-prometheus" % <version>
```

Create your registry

```scala
import io.prometheus.metrics.core.metrics.CollectorRegistry
import fr.davit.pekko.http.metrics.prometheus.{PrometheusRegistry, PrometheusSettings}

val prometheus: CollectorRegistry = ... // your prometheus registry
val settings: PrometheusSettings = PrometheusSettings.default
val registry = PrometheusRegistry(prometheus, settings) // or PrometheusRegistry() to use the default registry & settings
```

You can fine-tune the `histogram/summary` configuration of `buckets/quantiles` for the `request
 size`, `duration` and `response size` metrics.
 
```scala
settings
  .withDurationConfig(ClassicBuckets(1, 2, 3, 5, 8, 13, 21, 34))
  .withReceivedBytesConfig(Quantiles(0.5, 0.75, 0.9, 0.95, 0.99))
  .withSentBytesConfig(PrometheusSettings.DefaultQuantiles)
```

Expose the metrics

```scala
import fr.davit.pekko.http.metrics.core.scaladsl.server.HttpMetricsDirectives._
import fr.davit.pekko.http.metrics.prometheus.marshalling.PrometheusMarshallers._

val route = (get & path("metrics"))(metrics(registry))
```

All metrics from the prometheus collector registry will be exposed.
Marshalling [format](https://prometheus.github.io/client_java/exporters/formats/) depends on the `Accept`/`Content-Type` header sent by the client:

* `Content-Type: text/plain`: Prometheus text format
* `Content-Type: application/openmetrics-text`: OpenMetrics text format
* `Content-Type: application/vnd.google.protobuf`: Prometheus protobuf format

No `Accept` header or matching several (eg `Accept: application/*`) will take the 1st matching type from the above list.


You can find some instrumentations [here](https://prometheus.github.io/client_java). For instance, to expose some JVM
metrics, you have to add the dedicated dependency and initialize/register it to your collector registry:

```sbt
libraryDependencies += "io.prometheus" % "rometheus-metrics-instrumentation-jvm" % <vesion>
```

```scala
import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics

val prometheus: PrometheusRegistry = ??? // your prometheus registry
JvmMetrics.builder().register(prometheus)  // or JvmMetrics.builder().register() to use the default registry
```
