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

package fr.davit.pekko.http.metrics.prometheus.marshalling

import org.apache.pekko.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import org.apache.pekko.http.scaladsl.model.{ContentType, HttpCharsets, MediaType, MediaTypes}
import fr.davit.pekko.http.metrics.prometheus.PrometheusRegistry
import io.prometheus.metrics.expositionformats.{
  OpenMetricsTextFormatWriter,
  PrometheusProtobufWriter,
  PrometheusTextFormatWriter
}
import java.io.ByteArrayOutputStream
import org.apache.pekko.http.scaladsl.model.MediaType.NotCompressible

trait PrometheusMarshallers {

  val OpenMetricsContentType: ContentType = MediaType
    .applicationWithFixedCharset("openmetrics-text", HttpCharsets.`UTF-8`)
    .withParams(Map("version" -> "1.0.0"))

  val OpenMetricsMarshaller: ToEntityMarshaller[PrometheusRegistry] = openMetricsMarshaller()
  def openMetricsMarshaller(
      createdTimestampsEnabled: Boolean = false,
      exemplarsOnAllMetricTypesEnabled: Boolean = false
  ): ToEntityMarshaller[PrometheusRegistry] = {
    val writer = OpenMetricsTextFormatWriter
      .builder()
      .setCreatedTimestampsEnabled(createdTimestampsEnabled)
      .setExemplarsOnAllMetricTypesEnabled(exemplarsOnAllMetricTypesEnabled)
      .build()
    Marshaller
      .byteArrayMarshaller(OpenMetricsContentType)
      .compose { registry =>
        val output = new ByteArrayOutputStream()
        writer.write(output, registry.underlying.scrape())
        output.toByteArray()
      }
  }

  val TextContentType: ContentType = MediaTypes.`text/plain`
    .withParams(Map("version" -> "0.0.4"))
    .withCharset(HttpCharsets.`UTF-8`)

  val TextMarshaller: ToEntityMarshaller[PrometheusRegistry]                                            = textMarshaller()
  def textMarshaller(includeCreatedTimestamps: Boolean = false): ToEntityMarshaller[PrometheusRegistry] = {
    val writer = PrometheusTextFormatWriter
      .builder()
      .setIncludeCreatedTimestamps(includeCreatedTimestamps)
      .build()
    Marshaller
      .byteArrayMarshaller(TextContentType)
      .compose { registry =>
        val output = new ByteArrayOutputStream()
        writer.write(output, registry.underlying.scrape())
        output.toByteArray()
      }
  }

  val ProtobufContentType: ContentType = MediaType
    .applicationBinary("application/vnd.google.protobuf", NotCompressible)
    .withParams(
      Map(
        "proto"    -> "io.prometheus.client.MetricFamily",
        "encoding" -> "delimited"
      )
    )

  val ProtobufMarshaller: ToEntityMarshaller[PrometheusRegistry] = {
    val writer = new PrometheusProtobufWriter()
    Marshaller
      .byteArrayMarshaller(ProtobufContentType)
      .compose { registry =>
        val output = new ByteArrayOutputStream()
        writer.write(output, registry.underlying.scrape())
        output.toByteArray()
      }
  }

  implicit val PrometheusRegistryMarshaller: ToEntityMarshaller[PrometheusRegistry] =
    Marshaller.oneOf(
      TextMarshaller,
      OpenMetricsMarshaller,
      ProtobufMarshaller
    )
}

object PrometheusMarshallers extends PrometheusMarshallers
