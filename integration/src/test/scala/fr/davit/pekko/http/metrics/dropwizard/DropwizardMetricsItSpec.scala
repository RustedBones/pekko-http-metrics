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

package fr.davit.pekko.http.metrics.dropwizard

import fr.davit.pekko.http.metrics.core.HttpMetrics.*
import fr.davit.pekko.http.metrics.core.scaladsl.server.HttpMetricsDirectives.*
import fr.davit.pekko.http.metrics.dropwizard.marshalling.DropwizardMarshallers.*
import io.dropwizard.metrics5.MetricRegistry
import io.dropwizard.metrics5.jvm.{CachedThreadStatesGaugeSet, GarbageCollectorMetricSet, MemoryUsageGaugeSet}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.apache.pekko.http.scaladsl.model.{HttpRequest, StatusCodes, Uri}
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.apache.pekko.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import spray.json.{DefaultJsonProtocol, JsValue, RootJsonFormat}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*

class DropwizardMetricsItSpec
    extends TestKit(ActorSystem("DropwizardMetricsItSpec"))
    with AnyFlatSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll
    with SprayJsonSupport
    with DefaultJsonProtocol {

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(500, Millis))

  private case class JsonResponse(metrics: Map[String, JsValue])
  implicit private val metricsFormat: RootJsonFormat[JsonResponse] = jsonFormat1(JsonResponse.apply)

  override def afterAll(): Unit = {
    Http().shutdownAllConnectionPools()
    TestKit.shutdownActorSystem(system)
  }

  "DropwizardMetrics" should "expose external metrics" in {
    val settings                   = DropwizardSettings.default
    val dropwizard: MetricRegistry = new MetricRegistry()
    dropwizard.register("jvm.gc", new GarbageCollectorMetricSet())
    dropwizard.register("jvm.threads", new CachedThreadStatesGaugeSet(10, TimeUnit.SECONDS))
    dropwizard.register("jvm.memory", new MemoryUsageGaugeSet())

    val registry = DropwizardRegistry(dropwizard, settings)

    val route: Route = (get & path("metrics"))(metrics(registry))

    val binding = Http()
      .newMeteredServerAt("localhost", 0, registry)
      .bindFlow(route)
      .futureValue

    val uri     = Uri("/metrics")
      .withScheme("http")
      .withAuthority(binding.localAddress.getHostString, binding.localAddress.getPort)
    val request = HttpRequest().withUri(uri)

    val response = Http()
      .singleRequest(request)
      .futureValue

    response.status shouldBe StatusCodes.OK
    val body = Unmarshal(response).to[JsonResponse].futureValue

    body.metrics.keys.filter(_.startsWith("jvm.gc")) should not be empty
    body.metrics.keys.filter(_.startsWith("jvm.memory")) should not be empty
    body.metrics.keys.filter(_.startsWith("jvm.threads")) should not be empty

    binding.terminate(30.seconds).futureValue
  }
}
