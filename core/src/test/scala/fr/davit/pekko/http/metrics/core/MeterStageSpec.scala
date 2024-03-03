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
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.stream.ClosedShape
import org.apache.pekko.stream.scaladsl.{GraphDSL, RunnableGraph}
import org.apache.pekko.stream.testkit.scaladsl.{TestSink, TestSource}
import org.apache.pekko.testkit.TestKit
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class MeterStageSpec
    extends TestKit(ActorSystem("MeterStageSpec"))
    with AnyFlatSpecLike
    with Matchers
    with MockFactory
    with ScalaFutures {

  private val request  = HttpRequest()
  private val response = HttpResponse()
  private val error    = new Exception("BOOM!")

  trait Fixture {
    val handler = stub[HttpMetricsHandler]

    (handler.onConnection _).when().returns((): Unit)
    (handler.onDisconnection _).when().returns((): Unit)
    (handler.onRequest _).when(request).returns(request)
    (handler.onResponse _).when(request, response).returns(response)
    (handler.onFailure _).when(request, error).returns(error)
    (handler.onFailure _)
      .when(request, MeterStage.PrematureCloseException)
      .returns(MeterStage.PrematureCloseException)

    val (requestIn, requestOut, responseIn, responseOut) = RunnableGraph
      .fromGraph(
        GraphDSL.createGraph(
          TestSource.probe[HttpRequest],
          TestSink.probe[HttpRequest],
          TestSource.probe[HttpResponse],
          TestSink.probe[HttpResponse]
        )((_, _, _, _)) { implicit builder => (reqIn, reqOut, respIn, respOut) =>
          import GraphDSL.Implicits.*
          val meter = builder.add(new MeterStage(handler))

          reqIn ~> meter.in1
          meter.out1 ~> reqOut
          respIn ~> meter.in2
          meter.out2 ~> respOut
          ClosedShape
        }
      )
      .run()

    // simulate downstream demand
    responseOut.request(1)
    requestOut.request(1)
    // wait connection to be established so next mock stubbing does not interfere with onConnect()
    Thread.sleep(50)
  }

  "MeterStage" should "call onConnection on materialization and onDisconnection once terminated" in new Fixture {
    requestIn.sendComplete()
    requestOut.expectComplete()

    responseIn.sendComplete()
    responseOut.expectComplete()

    (handler.onConnection _).verify()
    (handler.onDisconnection _).verify()
  }

  it should "call onRequest wen request is offered" in new Fixture {
    requestIn.sendNext(request)
    requestOut.expectNext() shouldBe request

    responseIn.sendNext(response)
    responseOut.expectNext() shouldBe response

    (handler.onRequest _).verify(request)
  }

  it should "flush the stream before stopping" in new Fixture {
    requestIn.sendNext(request)
    requestOut.expectNext() shouldBe request

    // close request side
    requestIn.sendComplete()
    requestOut.expectComplete()

    // response should still be accepted
    responseIn.sendNext(response)
    responseOut.expectNext() shouldBe response

    (handler.onRequest _).verify(request)
    (handler.onResponse _).verify(request, response)
  }

  it should "propagate error from request in" in new Fixture {
    requestIn.sendNext(request)
    requestOut.expectNext() shouldBe request

    requestIn.sendError(error)
    requestOut.expectError(error)

    (handler.onRequest _).verify(request)
  }

  it should "propagate error from request out" in new Fixture {
    requestIn.sendNext(request)
    requestOut.expectNext() shouldBe request

    requestOut.cancel(error)
    requestIn.expectCancellation()

    (handler.onRequest _).verify(request)
  }

  it should "terminate and fail pending" in new Fixture {
    requestIn.sendNext(request)
    requestIn.sendComplete()
    requestOut.expectNext() shouldBe request
    requestOut.expectComplete()

    responseIn.sendComplete()
    responseOut.expectComplete()

    (handler.onRequest _).verify(request)
    (handler.onFailure _).verify(request, MeterStage.PrematureCloseException)
  }

  it should "propagate error from response in and fail pending" in new Fixture {
    requestIn.sendNext(request)
    requestIn.sendComplete()
    requestOut.expectNext() shouldBe request
    requestOut.expectComplete()

    responseIn.sendError(error)
    responseOut.expectError(error)

    (handler.onRequest _).verify(request)
    (handler.onFailure _).verify(request, error)
  }

  it should "propagate error from response out and fail pending" in new Fixture {
    requestIn.sendNext(request)
    requestIn.sendComplete()
    requestOut.expectNext() shouldBe request
    requestOut.expectComplete()

    responseOut.cancel(error)
    responseIn.expectCancellation()

    (handler.onRequest _).verify(request)
    (handler.onFailure _).verify(request, error)
  }
}
