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

package fr.davit.pekko.http.metrics.dropwizard.marshalling

import java.io.StringWriter

import org.apache.pekko.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity}
import com.fasterxml.jackson.databind.ObjectMapper
import fr.davit.pekko.http.metrics.dropwizard.DropwizardRegistry

trait DropwizardMarshallers {

  implicit val registryToEntityMarshaller: ToEntityMarshaller[DropwizardRegistry] = {

    val writer = new ObjectMapper().writer()

    Marshaller.opaque { registry =>
      val output = new StringWriter()
      try {
        writer.writeValue(output, registry.underlying)
        HttpEntity(output.toString).withContentType(ContentTypes.`application/json`)
      } finally {
        output.close()
      }
    }
  }
}

object DropwizardMarshallers extends DropwizardMarshallers
