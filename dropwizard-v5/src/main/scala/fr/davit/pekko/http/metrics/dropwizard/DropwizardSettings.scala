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

import org.apache.pekko.http.scaladsl.model.StatusCodes
import fr.davit.pekko.http.metrics.core.HttpMetricsNames.HttpMetricsNamesImpl
import fr.davit.pekko.http.metrics.core.{HttpMetricsNames, HttpMetricsSettings}
import fr.davit.pekko.http.metrics.core.HttpMetricsSettings.HttpMetricsSettingsImpl

object DropwizardMetricsNames {

  val default: HttpMetricsNames = HttpMetricsNamesImpl(
    requests = "requests",
    requestsActive = "requests.active",
    requestsFailures = "requests.failures",
    requestsSize = "requests.bytes",
    responses = "responses",
    responsesErrors = "responses.errors",
    responsesDuration = "responses.duration",
    responsesSize = "responses.bytes",
    connections = "connections",
    connectionsActive = "connections.active"
  )

}

object DropwizardSettings {

  val default: HttpMetricsSettings = HttpMetricsSettingsImpl(
    "pekko.http",
    DropwizardMetricsNames.default,
    _.status.isInstanceOf[StatusCodes.ServerError],
    includeMethodDimension = false,
    includePathDimension = false,
    includeStatusDimension = false
  )

}
