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

import fr.davit.pekko.http.metrics.core._

import scala.concurrent.duration.FiniteDuration

class PrometheusCounter(counter: io.prometheus.metrics.core.metrics.Counter) extends Counter {

  override def inc(dimensions: Seq[Dimension]): Unit = {
    counter.labelValues(dimensions.map(_.label): _*).inc()
  }
}

class PrometheusGauge(gauge: io.prometheus.metrics.core.metrics.Gauge) extends Gauge {

  override def inc(dimensions: Seq[Dimension] = Seq.empty): Unit = {
    gauge.labelValues(dimensions.map(_.label): _*).inc()
  }

  override def dec(dimensions: Seq[Dimension] = Seq.empty): Unit = {
    gauge.labelValues(dimensions.map(_.label): _*).dec()
  }
}

class PrometheusSummaryTimer(summary: io.prometheus.metrics.core.metrics.Summary) extends Timer {

  override def observe(duration: FiniteDuration, dimensions: Seq[Dimension] = Seq.empty): Unit = {
    summary.labelValues(dimensions.map(_.label): _*).observe(duration.toMillis.toDouble / 1000.0)
  }
}

class PrometheusHistogramTimer(summary: io.prometheus.metrics.core.metrics.Histogram) extends Timer {

  override def observe(duration: FiniteDuration, dimensions: Seq[Dimension] = Seq.empty): Unit = {
    summary.labelValues(dimensions.map(_.label): _*).observe(duration.toMillis.toDouble / 1000.0)
  }
}

class PrometheusSummary(summary: io.prometheus.metrics.core.metrics.Summary) extends Histogram {

  override def update[T](value: T, dimensions: Seq[Dimension] = Seq.empty)(implicit numeric: Numeric[T]): Unit = {
    summary.labelValues(dimensions.map(_.label): _*).observe(numeric.toDouble(value))
  }
}

class PrometheusHistogram(histogram: io.prometheus.metrics.core.metrics.Histogram) extends Histogram {

  override def update[T](value: T, dimensions: Seq[Dimension] = Seq.empty)(implicit numeric: Numeric[T]): Unit = {
    histogram.labelValues(dimensions.map(_.label): _*).observe(numeric.toDouble(value))
  }
}
