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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.{RequestContext, RouteResult}
import org.apache.pekko.stream.scaladsl.Keep
import org.apache.pekko.stream.testkit.scaladsl.{TestSink, TestSource}
import org.apache.pekko.testkit.TestKit
import org.scalamock.matchers.ArgCapture.CaptureOne
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{ExecutionContext, Future}

class HttpMetricsSpec
    extends TestKit(ActorSystem("HttpMetricsSpec"))
    with AnyFlatSpecLike
    with Matchers
    with MockFactory
    with ScalaFutures
    with BeforeAndAfterAll {

  implicit val ec: ExecutionContext = system.dispatcher

  abstract class Fixture[T] {
    val handler = mock[HttpMetricsHandler]
    val server  = mockFunction[RequestContext, Future[RouteResult]]

    (handler.onConnection _).expects().returns((): Unit)
    (handler.onDisconnection _).expects().returns((): Unit)

    val (source, sink) = TestSource
      .probe[HttpRequest]
      .via(HttpMetrics.meterFlow(handler).join(HttpMetrics.metricsRouteToFlow(server)))
      .toMat(TestSink.probe[HttpResponse])(Keep.both)
      .run()

    // wait connection to be established so next mock stubbing does not interfere with onConnect()
    Thread.sleep(50)
  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "HttpMetrics" should "provide newMeteredServerAt extension" in {
    """
      |import org.apache.pekko.http.scaladsl.Http
      |import fr.davit.pekko.http.metrics.core.HttpMetrics._
      |val registry = new TestRegistry(TestRegistry.settings)
      |implicit val system: ActorSystem = ActorSystem()
      |Http().newMeteredServerAt("localhost", 8080, registry)
    """.stripMargin should compile
  }

  it should "seal route mark unhandled requests" in {
    {
      val handler  = HttpMetrics.metricsRouteToFunction(reject)
      val response = handler(HttpRequest()).futureValue
      response.attributes(PathLabeler.key) shouldBe "unhandled"
    }

    {
      val handler  = HttpMetrics.metricsRouteToFunction(failWith(new Exception("BOOM!")))
      val response = handler(HttpRequest()).futureValue
      response.attributes(PathLabeler.key) shouldBe "unhandled"
    }
  }

  it should "call the metrics handler on connection" in new Fixture {
    sink.request(1)
    source.sendComplete()
    sink.expectComplete()
  }

  it should "call the metrics handler on handled requests" in new Fixture {
    val request  = CaptureOne[HttpRequest]()
    val response = CaptureOne[HttpResponse]()

    (handler.onRequest _)
      .expects(capture(request))
      .onCall { (req: HttpRequest) => req }

    server
      .expects(*)
      .onCall(complete(StatusCodes.OK))

    (handler.onResponse _)
      .expects(*, capture(response))
      .onCall { (_: HttpRequest, resp: HttpResponse) => resp }

    val expectedRequest = HttpRequest()
    sink.request(1)
    source.sendNext(expectedRequest)
    sink.expectNext()

    source.sendComplete()
    sink.expectComplete()

    val expectedResponse = Marshal(StatusCodes.OK)
      .to[HttpResponse]
      .futureValue

    request.value shouldBe expectedRequest
    response.value shouldBe expectedResponse
  }

  it should "call the metrics handler on rejected requests" in new Fixture {
    val request  = CaptureOne[HttpRequest]()
    val response = CaptureOne[HttpResponse]()
    (handler.onRequest _)
      .expects(capture(request))
      .onCall { (req: HttpRequest) => req }

    server
      .expects(*)
      .onCall(reject)

    (handler.onResponse _)
      .expects(*, capture(response))
      .onCall { (_: HttpRequest, resp: HttpResponse) => resp }

    val expectedRequest = HttpRequest()
    sink.request(1)
    source.sendNext(expectedRequest)
    sink.expectNext()

    source.sendComplete()
    sink.expectComplete()

    val expectedResponse = Marshal(StatusCodes.NotFound -> "The requested resource could not be found.")
      .to[HttpResponse]
      .futureValue
      .addAttribute(PathLabeler.key, "unhandled")

    request.value shouldBe expectedRequest
    response.value shouldBe expectedResponse
  }

  it should "call the metrics handler on error requests" in new Fixture {
    val request  = CaptureOne[HttpRequest]()
    val response = CaptureOne[HttpResponse]()
    (handler.onRequest _)
      .expects(capture(request))
      .onCall { (req: HttpRequest) => req }

    server
      .expects(*)
      .onCall(failWith(new Exception("BOOM!")))

    (handler.onResponse _)
      .expects(*, capture(response))
      .onCall { (_: HttpRequest, resp: HttpResponse) => resp }

    val expectedRequest = HttpRequest()
    sink.request(1)
    source.sendNext(expectedRequest)
    sink.expectNext()

    source.sendComplete()
    sink.expectComplete()

    val expectedResponse = Marshal(StatusCodes.InternalServerError)
      .to[HttpResponse]
      .futureValue
      .addAttribute(PathLabeler.key, "unhandled")

    request.value shouldBe expectedRequest
    response.value shouldBe expectedResponse
  }

}
