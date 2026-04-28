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

import org.mockserver.client.MockServerClient
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.{HttpRequest, HttpResponse, JsonPathBody}
import org.mockserver.verify.VerificationTimes
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import uk.gov.hmrc.test.api.client.HttpClientHelper
import uk.gov.hmrc.test.api.conf.TestConfiguration

trait MockServer extends BeforeAndAfterEach with BeforeAndAfterAll with HttpClientHelper {
  this: Suite =>

  private lazy val mockServerPort      = TestConfiguration.config.getInt("mock.server.port")
  lazy val mockServer: ClientAndServer = ClientAndServer.startClientAndServer(mockServerPort)

  override def beforeAll(): Unit =
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
  }

  override def afterEach(): Unit = {
    mockServer.reset()
    deleteAuthSessions()
  }

  override def afterAll(): Unit = {
    mockServer.stop()
    super.afterAll()
  }

  def deleteAuthSessions(): StandaloneWSResponse =
    delete(s"http://localhost:8585/sessions")

  def verifyTxSucceededAuditEvent(callCredit: String, numberOfTimes: Int): MockServerClient =
    mockServer.verify(
      request()
        .withPath("/write/audit")
        .withBody(
          JsonPathBody.jsonPath(
            "$[?(" +
              "@.auditType=='TxSucceeded' " +
              s"""&& @.detail["response.callcredit"]=='$callCredit' """ +
              "&& @.detail.callingClient=='bars-acceptance-tests'" +
              ")]"
          )
        ),
      VerificationTimes.exactly(numberOfTimes)
    )

  def verifyBusinessBankAccountCheckAuditEvent(callCredit: String, numberOfTimes: Int): MockServerClient =
    mockServer.verify(
      request()
        .withPath("/write/audit")
        .withBody(
          JsonPathBody.jsonPath(
            "$[?(" +
              "@.auditType=='businessBankAccountCheck' " +
              s"""&& @.detail["context"]=='$callCredit' """ +
              "&& @.detail.callingClient=='bars-acceptance-tests'" +
              ")]"
          )
        ),
      VerificationTimes.exactly(numberOfTimes)
    )
}
