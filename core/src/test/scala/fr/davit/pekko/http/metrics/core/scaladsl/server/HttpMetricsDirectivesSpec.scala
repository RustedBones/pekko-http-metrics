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

package fr.davit.pekko.http.metrics.core.scaladsl.server

import fr.davit.pekko.http.metrics.core.AttributeLabeler
import fr.davit.pekko.http.metrics.core.PathLabeler
import fr.davit.pekko.http.metrics.core.TestRegistry
import org.apache.pekko.http.scaladsl.marshalling.PredefinedToEntityMarshallers.*
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.HttpEncoding
import org.apache.pekko.http.scaladsl.model.headers.HttpEncodings
import org.apache.pekko.http.scaladsl.model.headers.`Accept-Encoding`
import org.apache.pekko.http.scaladsl.model.headers.`Content-Encoding`
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.Matcher
import org.scalatest.matchers.should.Matchers
import org.apache.pekko.http.scaladsl.coding.Coders
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import scala.concurrent.Await
import scala.concurrent.duration.*

class HttpMetricsDirectivesSpec extends AnyFlatSpec with Matchers with ScalatestRouteTest {

  import HttpMetricsDirectives._

  def haveNoContentEncoding: Matcher[HttpResponse]                       =
    be(None).compose { (_: HttpResponse).header[`Content-Encoding`] }
  def haveContentEncoding(encoding: HttpEncoding): Matcher[HttpResponse] =
    be(Some(`Content-Encoding`(encoding))).compose { (_: HttpResponse).header[`Content-Encoding`] }

  "HttpMetricsDirectives" should "expose the registry" in {
    implicit val marshaller = StringMarshaller.compose[TestRegistry](r => s"active: ${r.requestsActive.value()}")
    val registry            = new TestRegistry()
    registry.requestsActive.inc()

    val route = path("metrics") {
      metrics(registry)
    }

    Get("/metrics") ~> route ~> check {
      response should haveNoContentEncoding
      responseAs[String] shouldBe "active: 1"
    }

    // gzip
    Get("/metrics") ~> `Accept-Encoding`(HttpEncodings.gzip) ~> route ~> check {
      response should haveContentEncoding(HttpEncodings.gzip)
      val decodedResponse = Coders.Gzip.decodeMessage(response)
      val data            = Await.result(Unmarshal(decodedResponse).to[String], 1.second)
      data shouldBe "active: 1"
    }

    // deflate
    Get("/metrics") ~> `Accept-Encoding`(HttpEncodings.deflate) ~> route ~> check {
      response should haveContentEncoding(HttpEncodings.deflate)
      val decodedResponse = Coders.Deflate.decodeMessage(response)
      val data            = Await.result(Unmarshal(decodedResponse).to[String], 1.second)
      data shouldBe "active: 1"
    }

    // unknown -> accept and skip encoding
    Get("/metrics") ~> `Accept-Encoding`(HttpEncodings.`x-zip`) ~> route ~> check {
      response should haveNoContentEncoding
      responseAs[String] shouldBe "active: 1"
    }
  }

  it should "put label on custom dimension" in {
    object CustomLabeler extends AttributeLabeler {
      def name = "dim"
    }
    val route = metricsLabeled(CustomLabeler, "label") {
      complete(StatusCodes.OK)
    }

    Get() ~> route ~> check {
      response.attribute(CustomLabeler.key) shouldBe Some("label")
    }
  }

  it should "put label on path" in {
    val route = pathPrefixLabeled("api") {
      pathPrefix("user" / LongNumber) { _ =>
        path("address") {
          complete(StatusCodes.OK)
        }
      }
    }

    Get("/api/user/1234/address") ~> route ~> check {
      response.attribute(PathLabeler.key) shouldBe Some("/api")
    }
  }

  it should "combine labelled segments" in {
    val route = pathPrefixLabeled("api") {
      pathPrefixLabeled("user" / LongNumber, "user/:userId") { _ =>
        pathLabeled("address") {
          complete(StatusCodes.OK)
        }
      }
    }

    Get("/api/user/1234/address") ~> route ~> check {
      response.attribute(PathLabeler.key) shouldBe Some("/api/user/:userId/address")
    }
  }

  it should "not add extra attribute when label directives are not used" in {
    val route = pathPrefix("api") {
      pathPrefix("user" / LongNumber) { _ =>
        path("address") {
          complete(StatusCodes.OK)
        }
      }
    }

    Get("/api/user/1234/address") ~> route ~> check {
      response.attribute(PathLabeler.key) shouldBe empty
    }
  }
}
