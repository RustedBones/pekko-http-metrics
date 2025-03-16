/*
 * Copyright 2019 Michel Davit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.davit.pekko.http.metrics.prometheus

import org.apache.pekko.http.scaladsl.model.{HttpResponse, StatusCodes}
import fr.davit.pekko.http.metrics.core.HttpMetricsNames.HttpMetricsNamesImpl
import fr.davit.pekko.http.metrics.core.{Dimension, HttpMessageLabeler, HttpMetricsNames, HttpMetricsSettings}
import fr.davit.pekko.http.metrics.prometheus.Quantiles.Quantile

import scala.collection.immutable
import scala.concurrent.duration._

/** SamplingConfig configures the metric sampling.
  *   - For Summary, use [[Quantiles]]
  *   - For Histogram, use [[Buckets]]
  *
  * See https://prometheus.io/docs/practices/histograms/
  */
sealed trait SamplingConfig

final case class Quantiles(qs: List[Quantile], maxAge: FiniteDuration = 10.minutes, ageBuckets: Int = 5)
    extends SamplingConfig

object Quantiles {

  final case class Quantile(percentile: Double, error: Double = 0.001)

  def apply(percentiles: Double*): Quantiles = {
    val quantiles = percentiles.map { p =>
      // the higher the percentile, the lowe the error
      val error = (1 - p) / 10
      Quantile(p, error)
    }
    Quantiles(quantiles.toList)
  }
}

sealed trait Buckets extends SamplingConfig

/** ClassicBuckets defines the bucket upper bounds for the classic histogram buckets
  * @param bs
  *   upper bounds
  */
final case class ClassicBuckets(bs: List[Double]) extends Buckets
object ClassicBuckets {
  def apply(b: Double*): ClassicBuckets = ClassicBuckets(b.toList)
}

/** ClassicLinearBuckets defines the bucket upper bounds with linear boundaries
  *
  * @param start
  *   the first bucket boundary
  * @param width
  *   the width of each bucket
  * @param count
  *   the total number of buckets, including start
  */
final case class ClassicLinearBuckets(start: Double, width: Double, count: Int) extends Buckets

/** ClassicExponentialBuckets defines the bucket upper bounds with exponential boundaries
  *
  * @param start
  *   the first bucket boundary
  * @param factor
  *   the growth factor
  * @param count
  *   the total number of buckets, including start
  */
final case class ClassicExponentialBuckets(start: Double, factor: Double, count: Int) extends Buckets

/** NativeBuckets defines the behavior for the native histogram buckets.
  *
  * See https://prometheus.io/docs/specs/native_histograms/
  *
  * @param initialSchema
  *   the resolution of the native histogram
  * @param minZeroThreshold
  *   the initial and minimum value of the zero threshold
  * @param maxZeroThreshold
  *   the maximum value of the zero threshold
  * @param maxNumberOfBuckets
  *   the maximum number of buckets
  * @param resetDuration
  *   the delay after histogram may seset when native histogram buckets exceeds nativeMaxBuckets (0 indicates no reset)
  */
final case class NativeBuckets(
    initialSchema: Int = 5,
    minZeroThreshold: Double = Math.pow(2.0, -128),
    maxZeroThreshold: Double = Math.pow(2.0, -128),
    maxNumberOfBuckets: Int = 160,
    resetDuration: FiniteDuration = Duration.Zero
) extends Buckets

object PrometheusMetricsNames {

  val default: HttpMetricsNames = HttpMetricsNamesImpl(
    requests = "requests_total",
    requestsActive = "requests_active",
    requestsFailures = "requests_failures_total",
    requestsSize = "requests_size_bytes",
    responses = "responses_total",
    responsesErrors = "responses_errors_total",
    responsesDuration = "responses_duration_seconds",
    responsesSize = "responses_size_bytes",
    connections = "connections_total",
    connectionsActive = "connections_active"
  )
}

/** Prometheus metrics settings
  *
  * @param namespace
  *   Metrics namespace
  * @param metricsNames
  *   Name of the individual metrics
  * @param defineError
  *   Function that defines if the http response should be counted as an error
  * @param includeMethodDimension
  *   Include the method dimension on metrics
  * @param includePathDimension
  *   Include the path dimension on metrics
  * @param includeStatusDimension
  *   Include the status group dimension on metrics
  * @param serverDimensions
  *   Static dimensions to be set on all metrics
  * @param customDimensions
  *   Custom dimensions
  * @param receivedBytesConfig
  *   Sampling configuraton to recrod recieved bytes
  * @param durationConfig
  *   Sampling configuraton to recrod server response duration
  * @param sentBytesConfig
  *   Sampling configuraton to recrod sent bytes
  */
final case class PrometheusSettings(
    namespace: String,
    metricsNames: HttpMetricsNames,
    defineError: HttpResponse => Boolean,
    includeMethodDimension: Boolean,
    includePathDimension: Boolean,
    includeStatusDimension: Boolean,
    serverDimensions: immutable.Seq[Dimension] = immutable.Seq.empty,
    customDimensions: immutable.Seq[HttpMessageLabeler] = immutable.Seq.empty,
    receivedBytesConfig: SamplingConfig,
    durationConfig: SamplingConfig,
    sentBytesConfig: SamplingConfig
) extends HttpMetricsSettings {

  def withNamespace(namespace: String): PrometheusSettings                 = copy(namespace = namespace)
  def withMetricsNames(metricsNames: HttpMetricsNames): PrometheusSettings = copy(metricsNames = metricsNames)
  def withDefineError(fn: HttpResponse => Boolean): PrometheusSettings     = copy(defineError = defineError)
  def withIncludeMethodDimension(include: Boolean): PrometheusSettings     = copy(includeMethodDimension = include)
  def withIncludePathDimension(include: Boolean): PrometheusSettings       = copy(includePathDimension = include)
  def withIncludeStatusDimension(include: Boolean): PrometheusSettings     = copy(includeStatusDimension = include)
  def withServerDimensions(dims: Dimension*): PrometheusSettings           = copy(serverDimensions = dims.toVector)
  def withCustomDimensions(dims: HttpMessageLabeler*): PrometheusSettings  = copy(customDimensions = dims.toVector)
  def withReceivedBytesConfig(config: SamplingConfig): PrometheusSettings  = copy(receivedBytesConfig = config)
  def withDurationConfig(config: SamplingConfig): PrometheusSettings       = copy(durationConfig = config)
  def withSentBytesConfig(config: SamplingConfig): PrometheusSettings      = copy(sentBytesConfig = config)
}

object PrometheusSettings {

  // generic durations adapted to network durations in seconds
  val DurationBuckets: ClassicBuckets = {
    ClassicBuckets(0.005, 0.01, .025, .05, .075, .1, .25, .5, .75, 1, 2.5, 5, 7.5, 10)
  }

  // generic buckets adapted to network messages size
  val BytesBuckets: ClassicBuckets = {
    val buckets = Range(0, 1000, 100) ++ Range(1000, 10000, 1000) ++ Range(10000, 100000, 10000)
    ClassicBuckets(buckets.map(_.toDouble).toList)
  }

  // basic quantiles
  val DefaultQuantiles: Quantiles = Quantiles(0.75, 0.95, 0.98, 0.99, 0.999)

  val default: PrometheusSettings = PrometheusSettings(
    namespace = "pekko_http",
    defineError = _.status.isInstanceOf[StatusCodes.ServerError],
    includeMethodDimension = false,
    includePathDimension = false,
    includeStatusDimension = false,
    serverDimensions = immutable.Seq.empty[Dimension],
    receivedBytesConfig = BytesBuckets,
    durationConfig = DurationBuckets,
    sentBytesConfig = BytesBuckets,
    metricsNames = PrometheusMetricsNames.default
  )
}
