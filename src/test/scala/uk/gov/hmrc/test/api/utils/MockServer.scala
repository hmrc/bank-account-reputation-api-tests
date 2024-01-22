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

package uk.gov.hmrc.test.api.utils

import com.typesafe.config.Config
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.{HttpRequest, HttpResponse}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import uk.gov.hmrc.test.api.client.HttpClient

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

trait MockServer extends BeforeAndAfterEach with BeforeAndAfterAll with HttpClient {
  this: Suite with ({ def config: Config }) =>

  private val mockServerPort           = config.getInt("mock.server.port")
  lazy val mockServer: ClientAndServer = ClientAndServer.startClientAndServer(mockServerPort)

  override def beforeAll: Unit =
    super.beforeAll()

  override def beforeEach(): Unit = {

    mockServer
      .when(
        HttpRequest
          .request()
          .withMethod("POST")
          .withPath("/write/audit")
      )
      .respond(
        HttpResponse
          .response()
          .withStatusCode(200)
      )
    mockServer
      .when(
        HttpRequest
          .request()
          .withMethod("POST")
          .withPath("/write/audit/merged")
      )
      .respond(
        HttpResponse
          .response()
          .withStatusCode(200)
      )
    mockServer
      .when(
        HttpRequest
          .request()
          .withMethod("POST")
          .withPath("/surepay/oauth/client_credential/accesstoken")
      )
      .respond(
        HttpResponse
          .response()
          .withHeader("Content-Type", "application/json")
          .withBody(s"""{"access_token" : "${UUID
            .randomUUID()
            .toString}", "expires_in" : "3599", "token_type" : "BearerToken" }""".stripMargin)
          .withStatusCode(200)
      )
  }

  override def afterEach(): Unit = {
    mockServer.reset()
    deleteAuthSessions()
  }

  override def afterAll: Unit = {
    mockServer.stop()
    super.afterAll()
  }

  def deleteAuthSessions() =
    Await.result(delete(s"http://localhost:8585/sessions"), 10.seconds)
}
