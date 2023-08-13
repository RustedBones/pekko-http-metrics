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
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, when}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{ExecutionContext, Future}

class HttpMetricsSpec
    extends TestKit(ActorSystem("HttpMetricsSpec"))
    with AnyFlatSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  implicit val ec: ExecutionContext = system.dispatcher

  private def anyRequestContext() = any(classOf[RequestContext])
  private def anyRequest()        = any(classOf[HttpRequest])

  abstract class Fixture[T] {
    val metricsHandler: HttpMetricsHandler                    =
      mock(classOf[HttpMetricsHandler])
    val server: Function[RequestContext, Future[RouteResult]] =
      mock(classOf[Function[RequestContext, Future[RouteResult]]])

    val (source, sink) = TestSource
      .probe[HttpRequest]
      .via(HttpMetrics.meterFlow(metricsHandler).join(HttpMetrics.metricsRouteToFlow(server)))
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
    val request: ArgumentCaptor[HttpRequest]   = ArgumentCaptor.forClass(classOf[HttpRequest])
    val response: ArgumentCaptor[HttpResponse] = ArgumentCaptor.forClass(classOf[HttpResponse])
    when(metricsHandler.onRequest(request.capture()))
      .thenAnswer(_.getArgument(0))

    when(server.apply(anyRequestContext()))
      .thenAnswer(invocation => complete(StatusCodes.OK)(invocation.getArgument[RequestContext](0)))
    when(metricsHandler.onResponse(anyRequest(), response.capture()))
      .thenAnswer(_.getArgument(1))

    sink.request(1)
    source.sendNext(HttpRequest())
    sink.expectNext()

    source.sendComplete()
    sink.expectComplete()

    val expected = Marshal(StatusCodes.OK)
      .to[HttpResponse]
      .futureValue

    response.getValue shouldBe expected
  }

  it should "call the metrics handler on rejected requests" in new Fixture {
    val request: ArgumentCaptor[HttpRequest]   = ArgumentCaptor.forClass(classOf[HttpRequest])
    val response: ArgumentCaptor[HttpResponse] = ArgumentCaptor.forClass(classOf[HttpResponse])
    when(metricsHandler.onRequest(request.capture()))
      .thenAnswer(_.getArgument(0))

    when(server.apply(anyRequestContext()))
      .thenAnswer(invocation => reject(invocation.getArgument[RequestContext](0)))

    when(metricsHandler.onResponse(anyRequest(), response.capture()))
      .thenAnswer(_.getArgument(1))

    sink.request(1)
    source.sendNext(HttpRequest())
    sink.expectNext()

    source.sendComplete()
    sink.expectComplete()

    val expected = Marshal(StatusCodes.NotFound -> "The requested resource could not be found.")
      .to[HttpResponse]
      .futureValue
      .addAttribute(PathLabeler.key, "unhandled")
    response.getValue shouldBe expected
  }

  it should "call the metrics handler on error requests" in new Fixture {
    val request: ArgumentCaptor[HttpRequest]   = ArgumentCaptor.forClass(classOf[HttpRequest])
    val response: ArgumentCaptor[HttpResponse] = ArgumentCaptor.forClass(classOf[HttpResponse])
    when(metricsHandler.onRequest(request.capture()))
      .thenAnswer(_.getArgument(0))

    when(server.apply(anyRequestContext()))
      .thenAnswer(invocation => failWith(new Exception("BOOM!"))(invocation.getArgument[RequestContext](0)))

    when(metricsHandler.onResponse(anyRequest(), response.capture()))
      .thenAnswer(_.getArgument(1))

    sink.request(1)
    source.sendNext(HttpRequest())
    sink.expectNext()

    source.sendComplete()
    sink.expectComplete()

    val expected = Marshal(StatusCodes.InternalServerError)
      .to[HttpResponse]
      .futureValue
      .addAttribute(PathLabeler.key, "unhandled")
    response.getValue shouldBe expected
  }

}
