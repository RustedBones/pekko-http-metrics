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

package fr.davit.pekko.http.metrics.core.scaladsl

import org.apache.pekko.actor.ClassicActorSystemProvider
import org.apache.pekko.event.LoggingAdapter
import org.apache.pekko.http.scaladsl.Http.ServerBinding
import org.apache.pekko.http.scaladsl.*
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.http.scaladsl.settings.ServerSettings
import org.apache.pekko.stream.scaladsl.{Flow, Source}
import org.apache.pekko.stream.{Materializer, SystemMaterializer}
import fr.davit.pekko.http.metrics.core.{HttpMetrics, HttpMetricsHandler}
import org.apache.pekko.http.scaladsl.server.Route

import scala.annotation.nowarn
import scala.concurrent.Future

/** Metered server builder
  *
  * Use HttpExt.newMeteredServerAt() to create a builder, use methods to customize settings, and then call one of the
  * bind* methods to bind a server.
  */
final case class HttpMetricsServerBuilder(
    interface: String,
    port: Int,
    metricsHandler: HttpMetricsHandler,
    context: ConnectionContext,
    log: LoggingAdapter,
    settings: ServerSettings,
    system: ClassicActorSystemProvider,
    materializer: Materializer
) extends ServerBuilder {

  private lazy val http: HttpExt = Http(system.classicSystem)

  def meterTo(metricsHandler: HttpMetricsHandler): HttpMetricsServerBuilder                 =
    copy(metricsHandler = metricsHandler)
  override def onInterface(newInterface: String): HttpMetricsServerBuilder                  =
    copy(interface = newInterface)
  override def onPort(newPort: Int): HttpMetricsServerBuilder                               =
    copy(port = newPort)
  override def logTo(newLog: LoggingAdapter): HttpMetricsServerBuilder                      =
    copy(log = newLog)
  override def withSettings(newSettings: ServerSettings): HttpMetricsServerBuilder          =
    copy(settings = newSettings)
  override def adaptSettings(f: ServerSettings => ServerSettings): HttpMetricsServerBuilder =
    copy(settings = f(settings))
  override def enableHttps(newContext: HttpsConnectionContext): HttpMetricsServerBuilder    =
    copy(context = newContext)
  override def withMaterializer(newMaterializer: Materializer): HttpMetricsServerBuilder    =
    copy(materializer = newMaterializer)

  // Define an extra bind method for Route to ensure proper metric instrumentation.
  // This must be favored from the implicit RouteResult.routeToFunction
  // as erasure creates conflict with ServerBuilder.bind definition, add DummyImplicit parameter
  def bind(route: Route)(implicit ev: DummyImplicit): Future[ServerBinding] = {
    bind(HttpMetrics.metricsRouteToFunction(route)(system))
  }

  @nowarn("msg=deprecated")
  override def bind(f: HttpRequest => Future[HttpResponse]): Future[ServerBinding] = {
    val meteredHandler = HttpMetrics.meterFunction(f, metricsHandler)(materializer.executionContext)
    http.bindAndHandleAsync(
      meteredHandler,
      interface,
      port,
      context,
      settings,
      parallelism = 0,
      log
    )(materializer)
  }

  @nowarn("msg=deprecated")
  override def bindSync(handler: HttpRequest => HttpResponse): Future[ServerBinding] = {
    val meteredHandler = HttpMetrics.meterFunctionSync(handler, metricsHandler)
    http.bindAndHandleSync(
      meteredHandler,
      interface,
      port,
      context,
      settings,
      log
    )(materializer)
  }

  // Define an extra bind method for Route to ensure proper metric instrumentation.
  // This must be favored from the implicit RouteResult.routeToFlow
  def bindFlow(route: Route): Future[ServerBinding] = {
    bindFlow(HttpMetrics.metricsRouteToFlow(route)(system))
  }

  @nowarn("msg=deprecated")
  override def bindFlow(handlerFlow: Flow[HttpRequest, HttpResponse, ?]): Future[ServerBinding] = {
    val meteredFlow = HttpMetrics.meterFlow(metricsHandler).join(handlerFlow)
    http.bindAndHandle(
      meteredFlow,
      interface,
      port,
      context,
      settings,
      log
    )(materializer)
  }

  @nowarn("msg=deprecated")
  override def connectionSource(): Source[Http.IncomingConnection, Future[ServerBinding]] =
    http
      .bind(interface, port, context, settings, log)
      .map(c => c.copy(_flow = c._flow.join(HttpMetrics.meterFlow(metricsHandler))))
}

object HttpMetricsServerBuilder {

  def apply(
      interface: String,
      port: Int,
      metricsHandler: HttpMetricsHandler,
      system: ClassicActorSystemProvider
  ): HttpMetricsServerBuilder =
    HttpMetricsServerBuilder(
      interface,
      port,
      metricsHandler,
      HttpConnectionContext,
      system.classicSystem.log,
      ServerSettings(system.classicSystem),
      system,
      SystemMaterializer(system).materializer
    )
}
