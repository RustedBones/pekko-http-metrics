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

package fr.davit.pekko.http.metrics.core

import fr.davit.pekko.http.metrics.core.HttpMetrics.*
import fr.davit.pekko.http.metrics.core.scaladsl.server.HttpMetricsDirectives.*
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, WebSocketRequest}
import org.apache.pekko.http.scaladsl.model.{HttpRequest, StatusCodes, Uri}
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.apache.pekko.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.apache.pekko.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.duration.*

class HttpMetricsItSpec
    extends TestKit(ActorSystem("HttpMetricsItSpec"))
    with AnyFlatSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(500, Millis))

  override def afterAll(): Unit = {
    Http().shutdownAllConnectionPools()
    TestKit.shutdownActorSystem(system)
  }

  trait Fixture {
    val settings: HttpMetricsSettings = TestRegistry.settings
      .withNamespace("com.example.service")
      .withIncludePathDimension(true)

    val registry = new TestRegistry(settings)

    val greeter: Flow[Message, Message, Any] =
      Flow[Message].mapConcat {
        case tm: TextMessage   =>
          TextMessage(Source.single("Hello ") ++ tm.textStream ++ Source.single("!")) :: Nil
        case bm: BinaryMessage =>
          // ignore binary messages but drain content to avoid the stream being clogged
          bm.dataStream.runWith(Sink.ignore)
          Nil
      }

    val route: Route = {
      pathLabeled(Slash.? ~ PathEnd, "http") {
        get {
          complete("Hello world")
        }
      } ~ pathLabeled("greeter", "ws") {
        get {
          handleWebSocketMessages(greeter)
        }
      }
    }
  }

  "HttpMetrics" should "record metrics on flow handler" in new Fixture {
    val binding = Http()
      .newMeteredServerAt("localhost", 0, registry)
      .bindFlow(route)
      .futureValue

    val uri = Uri()
      .withScheme("http")
      .withAuthority(binding.localAddress.getHostString, binding.localAddress.getPort)

    val okRequest  = HttpRequest().withUri(uri)
    val okResponse = Http()
      .singleRequest(okRequest)
      .futureValue
    okResponse.status shouldBe StatusCodes.OK
    Unmarshal(okResponse).to[String].futureValue shouldBe "Hello world"

    val unhandledRequest  = HttpRequest().withUri(uri.withPath(Uri.Path("/unknown")))
    val unhandledResponse = Http()
      .singleRequest(unhandledRequest)
      .futureValue
    unhandledResponse.status shouldBe StatusCodes.NotFound

    registry.connections.value() shouldBe 1
    registry.requests.value() shouldBe 2
    registry.responses.value(Seq(Dimension("path", "/http"))) shouldBe 1
    registry.responses.value(Seq(Dimension("path", "unhandled"))) shouldBe 1

    binding.terminate(30.seconds).futureValue
  }

  it should "record metrics on function handler" in new Fixture {
    val binding = Http()
      .newMeteredServerAt("localhost", 0, registry)
      .bind(route)
      .futureValue

    val uri = Uri()
      .withScheme("http")
      .withAuthority(binding.localAddress.getHostString, binding.localAddress.getPort)

    val okRequest  = HttpRequest().withUri(uri)
    val okResponse = Http()
      .singleRequest(okRequest)
      .futureValue
    okResponse.status shouldBe StatusCodes.OK
    Unmarshal(okResponse).to[String].futureValue shouldBe "Hello world"

    val unhandledRequest  = HttpRequest().withUri(uri.withPath(Uri.Path("/unknown")))
    val unhandledResponse = Http()
      .singleRequest(unhandledRequest)
      .futureValue
    unhandledResponse.status shouldBe StatusCodes.NotFound

    registry.connections.value() shouldBe 0 // No connection metrics with function handler
    registry.requests.value() shouldBe 2
    registry.responses.value(Seq(Dimension("path", "/http"))) shouldBe 1
    registry.responses.value(Seq(Dimension("path", "unhandled"))) shouldBe 1

    binding.terminate(30.seconds).futureValue
  }

  it should "support web socket" in new Fixture {
    val binding = Http()
      .newMeteredServerAt("localhost", 0, registry)
      .bindFlow(route)
      .futureValue

    val uri     = Uri()
      .withScheme("ws")
      .withAuthority(binding.localAddress.getHostString, binding.localAddress.getPort)
      .withPath(Uri.Path("/greeter"))
    val request = WebSocketRequest(uri)
    val flow    = Flow.fromSinkAndSourceMat(Sink.ignore, Source.single(TextMessage("test")))(Keep.left)

    val (response, _) = Http()
      .singleWebSocketRequest(request, flow)

    response.futureValue.response.status shouldBe StatusCodes.SwitchingProtocols
    registry.connections.value() shouldBe 1
    registry.requests.value() shouldBe 1
    registry.responses.value(Seq(Dimension("path", "/ws"))) shouldBe 1

    binding.terminate(30.seconds).futureValue
  }
}
