/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.test.api.specs

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import org.openapi4j.schema.validator.ValidationData
import org.openapi4j.schema.validator.v3.SchemaValidator
import org.scalatest.AppendedClues.convertToClueful
import org.scalatest.matchers.should.Matchers._
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.test.api.conf.TestConfiguration
import uk.gov.hmrc.test.api.service.BankAccountReputationService

import scala.concurrent.Await
import scala.jdk.StreamConverters._

class SwaggerSpecs extends AnyWordSpec with SwaggerSpec {
  val gatewayHost: String = TestConfiguration.url("bank-account-gateway")

  "Api platform swagger specification" should behave.like(
    validOpenApiSpecAt(gatewayHost, "/api/conf/1.0/application.yaml", excludePaths = Seq("/check/insights"))
  )
}

trait SwaggerSpec {
  this: AnyWordSpec =>

  val parseOptions = new ParseOptions()
  parseOptions.setResolve(true)
  parseOptions.setResolveFully(true)

  val mapper = new ObjectMapper()
  mapper.setSerializationInclusion(Include.NON_NULL);

  val applicationJson = "application/json"
  val client          = new BankAccountReputationService()

  def validOpenApiSpecAt(
    host: String,
    openApiUrl: String,
    excludePaths: Seq[String] = Seq(),
    userAgent: String = "bars-acceptance-tests"
  ) {

    "should parse" in {
      val result = new OpenAPIV3Parser().readLocation(s"$host$openApiUrl", null, parseOptions)
      result.getMessages.size() shouldBe 0 withClue result.getMessages
    }

    "should contain valid examples" when {
      val openApi = new OpenAPIV3Parser().read(s"$host$openApiUrl", null, parseOptions)

      openApi.getPaths.forEach {
        case (path, p) if !excludePaths.contains(path) =>
          val verbs = Option(p.getGet).map("GET" -> _) ++ Option(p.getPost).map("POST" -> _)
          verbs.foreach { case (verb, r) =>
            val request         = r.getRequestBody.getContent.get(applicationJson)
            val requestExamples = getExamples(request)

            s"$path $verb request examples" in {
              val json      = mapper.writeValueAsString(request.getSchema)
              val validator = new SchemaValidator(null, mapper.readTree(json))

              assume(requestExamples.nonEmpty) withClue "No examples were found for this request"
              requestExamples.foreach { e =>
                val vd = new ValidationData()
                validator.validate(e.asInstanceOf[JsonNode], vd)

                vd.isValid shouldBe true withClue vd.results()
              }
            }

            val responses = getResponses(r)
            responses.collect { case (statusCode, Some(r)) =>
              s"$path $verb $statusCode response examples" in {
                val json      = mapper.writeValueAsString(r.getSchema)
                val validator = new SchemaValidator(null, mapper.readTree(json))

                val examples = getExamples(r)
                assume(requestExamples.nonEmpty) withClue "No examples were found for this response"

                examples.foreach { e =>
                  val vd = new ValidationData()
                  validator.validate(e.asInstanceOf[JsonNode], vd)

                  vd.isValid shouldBe true withClue vd.results()
                }
              }
            }
          }

        case _ =>
      }
    }

    "should elicit valid responses from the service" when {
      val openApi = new OpenAPIV3Parser().read(s"$host$openApiUrl", null, parseOptions)

      openApi.getPaths.forEach {
        case (path, p) if !excludePaths.contains(path) =>
          val verbs = Option(p.getGet).map("GET" -> _) ++ Option(p.getPost).map("POST" -> _)

          val requests = verbs.map { case (verb, r) =>
            val request   = r.getRequestBody.getContent.get(applicationJson)
            val responses = getResponses(r)

            verb -> (request, responses)
          }

          requests.foreach { case (verb, (request, responses)) =>
            val examples = getExamples(request)
            if (examples.isEmpty) {
              s"$verb $path (no examples found)" in {
                assume(examples.nonEmpty) withClue "No examples were found for this request"
              }
            }

            examples.foreach { e =>
              val headers = Seq("Content-Type" -> applicationJson, "User-Agent" -> userAgent)
              val req     = verb match {
                case "GET"  => client.get(s"$host$path", headers: _*)
                case "POST" =>
                  client.post(s"$host$path", mapper.writeValueAsString(e.asInstanceOf[JsonNode]), headers: _*)
              }

              val response = Await.result(req, 10.seconds)
              responses.get(response.status.toString).map {
                case Some(r) =>
                  s"$verb $path - ${response.status}" in {
                    val json      = mapper.writeValueAsString(r.getSchema)
                    val validator = new SchemaValidator(null, mapper.readTree(json))

                    val vd = new ValidationData()
                    validator.validate(mapper.readTree(response.body), vd)

                    vd.isValid shouldBe true withClue vd.results()
                  }

                case _ =>
                  s"$verb $path (no matching response found)" in {
                    assume(
                      examples.nonEmpty
                    ) withClue s"No specification was found for this status code (${response.status})"
                  }
              }
            }
          }

        case _ =>
      }
    }
  }

  private def getResponses(r: Operation) =
    r.getResponses
      .entrySet()
      .stream()
      .map { e =>
        e.getKey -> Option(e.getValue.getContent).map(_.get(applicationJson))
      }
      .toScala(Map)

  def getExamples(request: MediaType) = {
    val requestExample  = Option(request.getExample)
    val requestExamples =
      Option(request.getExamples).map(_.values().stream().toScala(Seq)).getOrElse(Seq()).map(_.getValue)
    val schemaExample   = Option(request.getSchema.getExample)
    val schemaExamples  = Option(request.getSchema.getExamples).map(_.stream().toScala(Seq)).getOrElse(Seq())

    requestExample ++ requestExamples ++ schemaExample ++ schemaExamples
  }
}
