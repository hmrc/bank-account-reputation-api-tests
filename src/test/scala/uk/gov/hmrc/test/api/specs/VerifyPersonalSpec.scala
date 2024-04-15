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

import org.mockserver.model.NottableString.string
import org.mockserver.model._
import org.mockserver.verify.VerificationTimes
import play.api.libs.json.Json
import uk.gov.hmrc.api.BaseSpec
import uk.gov.hmrc.test.api.model.request.PersonalRequest
import uk.gov.hmrc.test.api.model.request.components.{Account, Subject}
import uk.gov.hmrc.test.api.model.response.{AssessV4, BadRequest, CallValidateResponseBuilder, Forbidden}
import uk.gov.hmrc.test.api.tags.{LocalTests, ZapTests}
import uk.gov.hmrc.test.api.utils.MockServer

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.util.Random

class VerifyPersonalSpec extends BaseSpec with MockServer {

  val DEFAULT_ACCOUNT: Account      = Account(Some("404784"), Some("70872490"))
  val HMRC_ACCOUNT: Account         = Account(Some("083210"), Some("12001039"))
  val SUREPAY_TEST_ACCOUNT: Account = Account(Some("999999"), Some("00000001"))

  "Payload verification" when {

    "should send correct headers to third parties and audit third party requests" taggedAs (LocalTests, ZapTests) in {
      val xRequestId: String = UUID.randomUUID().toString
      mockServer
        .when(
          HttpRequest
            .request()
            .withMethod("POST")
            .withPath(SUREPAY_PATH)
            .withHeader("X-Request-ID", xRequestId)
        )
        .respond(
          HttpResponse
            .response()
            .withHeader("Content-Type", "application/json")
            .withBody(s"""{"Matched": false, "ReasonCode": "ACNS"}""".stripMargin)
            .withStatusCode(200)
        )

      val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(name = generateRandomName))
      val response    = service.postVerifyPersonal(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[AssessV4]

      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeBankName.get mustBe "Lloyds"
      actual.accountExists mustBe "indeterminate"
      actual.nameMatches mustBe "indeterminate"
      actual.sortCodeIsPresentOnEISCD mustBe "yes"
      response.status mustBe 200

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath(SUREPAY_PATH)
          .withHeaders(
            Header.header("X-Request-ID", xRequestId),
            Header.header(string("x-fapi-interaction-id"), NottableString.not(xRequestId))
          ),
        VerificationTimes.exactly(1)
      )
      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='TxSucceeded' " +
                "&& @.detail.callingClient=='bars-acceptance-tests'" +
                ")]"
            )
          ),
        VerificationTimes.atLeast(1)
      )
      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit/merged")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='OutboundCall' " +
                "&& @.request.tags.path=='http://localhost:6001/surepay/v1/gateway'" +
                s"&& @.request.tags.X-Request-ID=='$xRequestId'" +
                ")]"
            )
          ),
        VerificationTimes.exactly(1)
      )
    }

    "should log attempts to call third party even when they are not successful" taggedAs (LocalTests, ZapTests) in {
      val xRequestId: String = "govuk-" + UUID.randomUUID().toString
      mockServer
        .when(
          HttpRequest
            .request()
            .withMethod("POST")
            .withPath(SUREPAY_PATH)
            .withHeader("X-Request-ID", xRequestId)
        )
        .respond(
          HttpResponse
            .response()
            .withStatusCode(429)
        )

      val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(name = generateRandomName))
      val response    = service.postVerifyPersonal(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[AssessV4]

      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeBankName.get mustBe "Lloyds"
      actual.accountExists mustBe "error"
      actual.nameMatches mustBe "error"
      actual.sortCodeIsPresentOnEISCD mustBe "yes"
      response.status mustBe 200

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='TxSucceeded' " +
//                "&& @.detail.length()==20" +
                ")]"
            )
          ),
        VerificationTimes.atLeast(1)
      )
      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit/merged")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='OutboundCall' " +
                "&& @.request.tags.path=='http://localhost:6001/surepay/v1/gateway'" +
                s"&& @.request.tags.X-Request-ID=='$xRequestId'" +
                ")]"
            )
          ),
        VerificationTimes.atLeast(1)
      )
    }

    "should cache successful surepay requests" taggedAs (LocalTests, ZapTests) in {
      val xRequestId: String = UUID.randomUUID().toString
      mockServer
        .when(
          HttpRequest
            .request()
            .withMethod("POST")
            .withPath(SUREPAY_PATH)
            .withHeader("X-Request-ID", xRequestId)
        )
        .respond(
          HttpResponse
            .response()
            .withHeader("Content-Type", "application/json")
            .withBody(s"""{"Matched": true}""".stripMargin)
            .withStatusCode(200)
        )

      val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(name = generateRandomName))
      val response    = service.postVerifyPersonal(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[AssessV4]

      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeBankName.get mustBe "Lloyds"
      actual.accountExists mustBe "yes"
      actual.nameMatches mustBe "yes"
      actual.sortCodeIsPresentOnEISCD mustBe "yes"
      response.status mustBe 200

      val secondResponse = service.postVerifyPersonal(requestBody, xRequestId)
      val cached         = Json.parse(response.body).as[AssessV4]

      cached.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      cached.accountNumberIsWellFormatted mustBe "yes"
      cached.sortCodeBankName.get mustBe "Lloyds"
      cached.accountExists mustBe "yes"
      cached.nameMatches mustBe "yes"
      cached.sortCodeIsPresentOnEISCD mustBe "yes"
      secondResponse.status mustBe 200

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='TxSucceeded' " +
                "&& @.detail.length()==18" +
                "&& @.detail[\"response.callcredit\"]=='surepay_fromcache'" +
                ")]"
            )
          ),
        VerificationTimes.atLeast(1)
      )
    }

    "should not call TU if optional address is not supplied" taggedAs (LocalTests, ZapTests) in {
      val xRequestId: String = "govuk-" + UUID.randomUUID().toString
      mockServer
        .when(
          HttpRequest
            .request()
            .withMethod("POST")
            .withPath(SUREPAY_PATH)
            .withHeader("X-Request-ID", xRequestId)
        )
        .respond(
          HttpResponse
            .response()
            .withHeader("Content-Type", "application/json")
            .withBody(s"""{"Matched": false, "ReasonCode": "ACNS"}""".stripMargin)
            .withStatusCode(200)
        )

      val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(name = generateRandomName))
      val response    = service.postVerifyPersonal(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[AssessV4]

      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeBankName.get mustBe "Lloyds"
      actual.accountExists mustBe "indeterminate"
      actual.nameMatches mustBe "indeterminate"
      actual.sortCodeIsPresentOnEISCD mustBe "yes"
      response.status mustBe 200

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit/merged")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='OutboundCall' " +
                "&& @.request.tags.path=='http://localhost:6001/surepay/v1/gateway'" +
                s"&& @.request.tags.X-Request-ID=='$xRequestId'" +
                ")]"
            )
          ),
        VerificationTimes.exactly(1)
      )
      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit/merged")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='OutboundCall' " +
                "&& @.request.tags.path=='http://localhost:6001/callvalidateapi'" +
                s"&& @.request.tags.X-Request-ID=='$xRequestId'" +
                ")]"
            )
          ),
        VerificationTimes.exactly(0)
      )
    }

    "should use useragent as trueCallingClient if True-Calling-Client header is not set" taggedAs (LocalTests, ZapTests) in {
      val xRequestId: String = "govuk-" + UUID.randomUUID().toString
      mockServer
        .when(
          HttpRequest
            .request()
            .withMethod("POST")
            .withPath(SUREPAY_PATH)
            .withHeader("X-Request-ID", xRequestId)
        )
        .respond(
          HttpResponse
            .response()
            .withHeader("Content-Type", "application/json")
            .withBody(s"""{"Matched": true}""".stripMargin)
            .withStatusCode(200)
        )

      val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(name = generateRandomName))
      val response    = service.postVerifyPersonal(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[AssessV4]

      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeBankName.get mustBe "Lloyds"
      actual.accountExists mustBe "yes"
      actual.nameMatches mustBe "yes"
      actual.sortCodeIsPresentOnEISCD mustBe "yes"
      response.status mustBe 200

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='TxSucceeded' " +
                "&& @.detail.length()==18" +
                s"&& @.detail.callingClient=='$defaultUserAgent'" +
                ")]"
            )
          ),
        VerificationTimes.atLeast(1)
      )
    }

    "should use True-Calling-Client header as trueCallingClient if set" taggedAs (LocalTests, ZapTests) in {
      val xRequestId: String = "govuk-" + UUID.randomUUID().toString
      mockServer
        .when(
          HttpRequest
            .request()
            .withMethod("POST")
            .withPath(SUREPAY_PATH)
            .withHeader("X-Request-ID", xRequestId)
        )
        .respond(
          HttpResponse
            .response()
            .withHeader("Content-Type", "application/json")
            .withBody(s"""{"Matched": true}""".stripMargin)
            .withStatusCode(200)
        )

      val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(name = generateRandomName))
      val response    = service.postVerifyPersonalWithTrueCallingClient(requestBody, xRequestId, "some-upstream-service")
      val actual      = Json.parse(response.body).as[AssessV4]

      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeBankName.get mustBe "Lloyds"
      actual.accountExists mustBe "yes"
      actual.nameMatches mustBe "yes"
      actual.sortCodeIsPresentOnEISCD mustBe "yes"
      response.status mustBe 200

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='TxSucceeded' " +
                "&& @.detail.length()==18" +
                "&& @.detail.callingClient=='some-upstream-service'" +
                s"&& @.detail.userAgent=='$defaultUserAgent'" +
                ")]"
            )
          ),
        VerificationTimes.atLeast(1)
      )
    }
  }

  "Valid requests" when {

    "should be able to successfully call the assess endpoint when using valid data" taggedAs (LocalTests, ZapTests) in {
      val xRequestId: String = "govuk-" + UUID.randomUUID().toString
      mockServer
        .when(
          HttpRequest
            .request()
            .withMethod("POST")
            .withPath(SUREPAY_PATH)
            .withHeader("X-Request-ID", xRequestId)
        )
        .respond(
          HttpResponse
            .response()
            .withHeader("Content-Type", "application/json")
            .withBody(s"""{"Matched": true}""".stripMargin)
            .withStatusCode(200)
        )

      val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(name = generateRandomName))
      val response    = service.postVerifyPersonal(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[AssessV4]

      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeBankName.get mustBe "Lloyds"
      actual.accountExists mustBe "yes"
      actual.nameMatches mustBe "yes"
      actual.sortCodeIsPresentOnEISCD mustBe "yes"
      response.status mustBe 200
    }

    "should not make calls out to third parties when sort code is not on EISCD" taggedAs (LocalTests, ZapTests) in {
      val xRequestId: String = UUID.randomUUID().toString
      mockServer
        .when(
          HttpRequest
            .request()
            .withMethod("POST")
            .withPath(SUREPAY_PATH)
            .withHeader("X-Request-ID", xRequestId)
        )
        .respond(
          HttpResponse
            .response()
            .withHeader("Content-Type", "application/json")
            .withBody(s"""{"Matched": true}""".stripMargin)
            .withStatusCode(200)
        )

      val requestBody = PersonalRequest(
        Account(Some("401003"), Some("71201948")),
        Subject(name = generateRandomName)
      )
      val response    = service.postVerifyPersonal(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[AssessV4]

      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.accountExists mustBe "inapplicable"
      actual.nameMatches mustBe "inapplicable"
      actual.sortCodeIsPresentOnEISCD mustBe "no"
      actual.sortCodeSupportsDirectDebit mustBe "no"
      actual.sortCodeSupportsDirectCredit mustBe "no"
      response.status mustBe 200

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit/merged")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='OutboundCall' " +
                "&& @.request.tags.path=='http://localhost:6001/callvalidateapi'" +
                s"&& @.request.tags.X-Request-ID=='$xRequestId'" +
                ")]"
            )
          ),
        VerificationTimes.exactly(0)
      )

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit/merged")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='OutboundCall' " +
                "&& @.request.tags.path=='http://localhost:6001/surepay/v1/gateway'" +
                s"&& @.request.tags.X-Request-ID=='$xRequestId'" +
                ")]"
            )
          ),
        VerificationTimes.exactly(0)
      )

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='TxSucceeded' " +
                "&& @.detail.length()==16" +
                ")]"
            )
          ),
        VerificationTimes.atLeast(1)
      )
    }

    "should be able to successfully call the assess endpoint when sort code and account not found" taggedAs (LocalTests, ZapTests) in {
      val xRequestId: String = UUID.randomUUID().toString
      mockServer
        .when(
          HttpRequest
            .request()
            .withMethod("POST")
            .withPath(SUREPAY_PATH)
            .withHeader("X-Request-ID", xRequestId)
        )
        .respond(
          HttpResponse
            .response()
            .withHeader("Content-Type", "application/json")
            .withBody(s"""{"Matched": false,"ReasonCode": "AC01"}""".stripMargin)
            .withStatusCode(200)
        )

      val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(name = generateRandomName))
      val response    = service.postVerifyPersonal(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[AssessV4]

      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeBankName.get mustBe "Lloyds"
      actual.accountExists mustBe "no"
      actual.nameMatches mustBe "indeterminate"
      actual.sortCodeIsPresentOnEISCD mustBe "yes"
      response.status mustBe 200

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='TxSucceeded' " +
                "&& @.detail.length()==18" +
                ")]"
            )
          ),
        VerificationTimes.atLeast(1)
      )
    }

    "should not accept Surepay test credentials with default config" taggedAs (LocalTests, ZapTests) in {
      val xRequestId: String = UUID.randomUUID().toString
      val requestBody        =
        PersonalRequest(SUREPAY_TEST_ACCOUNT, Subject(name = generateRandomName))
      val response           = service.postVerifyPersonal(requestBody, xRequestId)
      val actual             = Json.parse(response.body).as[AssessV4]

      actual.accountNumberIsWellFormatted mustBe "indeterminate"
      actual.sortCodeIsPresentOnEISCD mustBe "no"
      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountExists mustBe "inapplicable"
      actual.nameMatches mustBe "inapplicable"
      response.status mustBe 200
    }

    "should receive a indeterminate response when calling the assess endpoint with empty post code" taggedAs (LocalTests, ZapTests) in {
      val xRequestId: String = UUID.randomUUID().toString
      mockServer
        .when(
          HttpRequest
            .request()
            .withMethod("POST")
            .withPath(SUREPAY_PATH)
            .withHeader("X-Request-ID", xRequestId)
        )
        .respond(
          HttpResponse
            .response()
            .withHeader("Content-Type", "application/json")
            .withBody(s"""{"Matched": false, "ReasonCode": "ACNS"}""".stripMargin)
            .withStatusCode(200)
        )

      val requestBody = PersonalRequest(
        DEFAULT_ACCOUNT,
        Subject(name = generateRandomName)
      )
      val response    = service.postVerifyPersonal(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[AssessV4]

      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeBankName.get mustBe "Lloyds"
      actual.accountExists mustBe "indeterminate"
      actual.nameMatches mustBe "indeterminate"
      actual.sortCodeIsPresentOnEISCD mustBe "yes"
      response.status mustBe 200

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='TxSucceeded' " +
                "&& @.detail.length()==18" +
                ")]"
            )
          ),
        VerificationTimes.atLeast(1)
      )
      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit/merged")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='OutboundCall' " +
                "&& @.request.tags.path=='http://localhost:6001/surepay/v1/gateway'" +
                s"&& @.request.tags.X-Request-ID=='$xRequestId'" +
                ")]"
            )
          ),
        VerificationTimes.exactly(1)
      )
      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit/merged")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='OutboundCall' " +
                "&& @.request.tags.path=='http://localhost:6001/callvalidateapi'" +
                s"&& @.request.tags.X-Request-ID=='$xRequestId'" +
                ")]"
            )
          ),
        VerificationTimes.exactly(0)
      )
    }

    "should receive a indeterminate response when calling the assess endpoint with missing post code" taggedAs (LocalTests, ZapTests) in {
      val xRequestId: String = UUID.randomUUID().toString
      mockServer
        .when(
          HttpRequest
            .request()
            .withMethod("POST")
            .withPath(SUREPAY_PATH)
            .withHeader("X-Request-ID", xRequestId)
        )
        .respond(
          HttpResponse
            .response()
            .withHeader("Content-Type", "application/json")
            .withBody(s"""{"Matched": false, "ReasonCode": "ACNS"}""".stripMargin)
            .withStatusCode(200)
        )

      val requestBody = PersonalRequest(
        DEFAULT_ACCOUNT,
        Subject(name = generateRandomName)
      )
      val response    = service.postVerifyPersonal(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[AssessV4]

      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeBankName.get mustBe "Lloyds"
      actual.accountExists mustBe "indeterminate"
      actual.nameMatches mustBe "indeterminate"
      actual.sortCodeIsPresentOnEISCD mustBe "yes"
      response.status mustBe 200

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='TxSucceeded' " +
                "&& @.detail.length()==18" +
                ")]"
            )
          ),
        VerificationTimes.atLeast(1)
      )
      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit/merged")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='OutboundCall' " +
                "&& @.request.tags.path=='http://localhost:6001/surepay/v1/gateway'" +
                s"&& @.request.tags.X-Request-ID=='$xRequestId'" +
                ")]"
            )
          ),
        VerificationTimes.exactly(1)
      )
      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit/merged")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='OutboundCall' " +
                "&& @.request.tags.path=='http://localhost:6001/callvalidateapi'" +
                s"&& @.request.tags.X-Request-ID=='$xRequestId'" +
                ")]"
            )
          ),
        VerificationTimes.exactly(0)
      )
    }

    "should receive a indeterminate response when calling the assess endpoint with invalid post code" taggedAs (LocalTests, ZapTests) in {
      val xRequestId: String = UUID.randomUUID().toString
      mockServer
        .when(
          HttpRequest
            .request()
            .withMethod("POST")
            .withPath(SUREPAY_PATH)
            .withHeader("X-Request-ID", xRequestId)
        )
        .respond(
          HttpResponse
            .response()
            .withHeader("Content-Type", "application/json")
            .withBody(s"""{"Matched": false, "ReasonCode": "ACNS"}""".stripMargin)
            .withStatusCode(200)
        )

      val requestBody = PersonalRequest(
        DEFAULT_ACCOUNT,
        Subject(name = generateRandomName)
      )
      val response    = service.postVerifyPersonal(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[AssessV4]

      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeBankName.get mustBe "Lloyds"
      actual.accountExists mustBe "indeterminate"
      actual.nameMatches mustBe "indeterminate"
      actual.sortCodeIsPresentOnEISCD mustBe "yes"
      response.status mustBe 200

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='TxSucceeded' " +
                "&& @.detail.length()==18" +
                ")]"
            )
          ),
        VerificationTimes.atLeast(1)
      )
      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit/merged")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='OutboundCall' " +
                "&& @.request.tags.path=='http://localhost:6001/surepay/v1/gateway'" +
                s"&& @.request.tags.X-Request-ID=='$xRequestId'" +
                ")]"
            )
          ),
        VerificationTimes.exactly(1)
      )
      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit/merged")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='OutboundCall' " +
                "&& @.request.tags.path=='http://localhost:6001/callvalidateapi'" +
                s"&& @.request.tags.X-Request-ID=='$xRequestId'" +
                ")]"
            )
          ),
        VerificationTimes.exactly(0)
      )
    }

    "should receive a match if the response is a close match" taggedAs (LocalTests, ZapTests) in {
      val xRequestId: String = UUID.randomUUID().toString
      mockServer
        .when(
          HttpRequest
            .request()
            .withMethod("POST")
            .withPath(SUREPAY_PATH)
            .withHeader("X-Request-ID", xRequestId)
        )
        .respond(
          HttpResponse
            .response()
            .withHeader("Content-Type", "application/json")
            .withBody(s"""{"Matched": false, "ReasonCode": "MBAM", "Name": "Patrick O'Conner-Smith"}""".stripMargin)
            .withStatusCode(200)
        )

      val requestBody = PersonalRequest(
        DEFAULT_ACCOUNT,
        Subject(name = generateRandomName)
      )
      val response    = service.postVerifyPersonal(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[AssessV4]

      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeBankName.get mustBe "Lloyds"
      actual.accountExists mustBe "yes"
      actual.nameMatches mustBe "partial"
      actual.accountName.get mustBe "Patrick O'Conner-Smith"
      actual.sortCodeIsPresentOnEISCD mustBe "yes"
      response.status mustBe 200

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='TxSucceeded' " +
                "&& @.detail.length()==19" +
                s"&& @.tags.path=='/${service.BarsEndpoints.VERIFY_PERSONAL}'" +
                ")]"
            )
          ),
        VerificationTimes.atLeast(1)
      )
      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit/merged")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='OutboundCall' " +
                "&& @.request.tags.path=='http://localhost:6001/surepay/v1/gateway'" +
                s"&& @.request.tags.X-Request-ID=='$xRequestId'" +
                ")]"
            )
          ),
        VerificationTimes.exactly(1)
      )
      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit/merged")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='OutboundCall' " +
                "&& @.request.tags.path=='http://localhost:6001/callvalidateapi'" +
                s"&& @.request.tags.X-Request-ID=='$xRequestId'" +
                ")]"
            )
          ),
        VerificationTimes.exactly(0)
      )
    }

    "should receive account does not exist if the response is a match but the account type is actually business" taggedAs (LocalTests, ZapTests) in {
      val xRequestId: String = UUID.randomUUID().toString
      mockServer
        .when(
          HttpRequest
            .request()
            .withMethod("POST")
            .withPath(SUREPAY_PATH)
            .withHeader("X-Request-ID", xRequestId)
        )
        .respond(
          HttpResponse
            .response()
            .withHeader("Content-Type", "application/json")
            .withBody(s"""{"Matched": false, "ReasonCode": "BANM"}""".stripMargin)
            .withStatusCode(200)
        )

      val requestBody = PersonalRequest(
        DEFAULT_ACCOUNT,
        Subject(name = generateRandomName)
      )
      val response    = service.postVerifyPersonal(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[AssessV4]

      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeBankName.get mustBe "Lloyds"
      actual.accountExists mustBe "no"
      actual.nameMatches mustBe "yes"
      actual.sortCodeIsPresentOnEISCD mustBe "yes"
      response.status mustBe 200

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='TxSucceeded' " +
                "&& @.detail.length()==18" +
                ")]"
            )
          ),
        VerificationTimes.atLeast(1)
      )
      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit/merged")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='OutboundCall' " +
                "&& @.request.tags.path=='http://localhost:6001/surepay/v1/gateway'" +
                s"&& @.request.tags.X-Request-ID=='$xRequestId'" +
                ")]"
            )
          ),
        VerificationTimes.exactly(1)
      )
      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit/merged")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='OutboundCall' " +
                "&& @.request.tags.path=='http://localhost:6001/callvalidateapi'" +
                s"&& @.request.tags.X-Request-ID=='$xRequestId'" +
                ")]"
            )
          ),
        VerificationTimes.exactly(0)
      )
    }

    "should return indeterminate if the response from Surepay is unrecognised" taggedAs (LocalTests, ZapTests) in {
      val xRequestId: String = UUID.randomUUID().toString
      mockServer
        .when(
          HttpRequest
            .request()
            .withMethod("POST")
            .withPath(SUREPAY_PATH)
            .withHeader("X-Request-ID", xRequestId)
        )
        .respond(
          HttpResponse
            .response()
            .withHeader("Content-Type", "application/json")
            .withBody(s"""{"Matched": true, "ReasonCode": "XXXX", "Name": "James O'Connor"}""".stripMargin)
            .withStatusCode(200)
        )

      val requestBody = PersonalRequest(
        DEFAULT_ACCOUNT,
        Subject(name = generateRandomName)
      )
      val response    = service.postVerifyPersonal(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[AssessV4]

      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeBankName.get mustBe "Lloyds"
      actual.accountExists mustBe "indeterminate"
      actual.nameMatches mustBe "indeterminate"
      actual.sortCodeIsPresentOnEISCD mustBe "yes"
      response.status mustBe 200

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='TxSucceeded' " +
                "&& @.detail.length()==18" +
                ")]"
            )
          ),
        VerificationTimes.atLeast(1)
      )
    }
  }

  "Invalid requests" when {

    "should receive a bad request when calling the assess endpoint missing name" taggedAs (LocalTests, ZapTests) in {
      val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject())
      val response    = service.postVerifyPersonal(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "BAD_NAME"
      actual.desc mustBe "Either the name or the firstName/lastName pair is required (but not both)"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with all name fields" taggedAs (LocalTests, ZapTests) in {
      val requestBody = PersonalRequest(
        DEFAULT_ACCOUNT,
        Subject(firstName = Some("Nathan"), lastName = Some("Smith"), name = generateRandomName)
      )
      val response    = service.postVerifyPersonal(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "BAD_NAME"
      actual.desc mustBe "Either the name or the firstName/lastName pair is required (but not both)"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with name and last name" taggedAs (LocalTests, ZapTests) in {
      val requestBody = PersonalRequest(
        DEFAULT_ACCOUNT,
        Subject(lastName = Some("Smith"), name = generateRandomName)
      )
      val response    = service.postVerifyPersonal(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "BAD_NAME"
      actual.desc mustBe "Either the name or the firstName/lastName pair is required (but not both)"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with name and first name" taggedAs (LocalTests, ZapTests) in {
      val requestBody = PersonalRequest(
        DEFAULT_ACCOUNT,
        Subject(firstName = Some("Nathan"), name = generateRandomName)
      )
      val response    = service.postVerifyPersonal(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "BAD_NAME"
      actual.desc mustBe "Either the name or the firstName/lastName pair is required (but not both)"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with empty first name" taggedAs (LocalTests, ZapTests) in {
      val requestBody = PersonalRequest(
        DEFAULT_ACCOUNT,
        Subject(firstName = Some(""), lastName = Some("Smith"))
      )
      val response    = service.postVerifyPersonal(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "BLANK_FIRST_NAME"
      actual.desc mustBe "firstName length is blank"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with missing first name" taggedAs (LocalTests, ZapTests) in {
      val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(lastName = Some("Smith")))
      val response    = service.postVerifyPersonal(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "BAD_NAME"
      actual.desc mustBe "Either the name or the firstName/lastName pair is required (but not both)"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with empty last name" taggedAs (LocalTests, ZapTests) in {
      val requestBody = PersonalRequest(
        DEFAULT_ACCOUNT,
        Subject(firstName = Some("Nathan"), lastName = Some(""))
      )
      val response    = service.postVerifyPersonal(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "BLANK_LAST_NAME"
      actual.desc mustBe "lastName length is blank"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with missing last name" taggedAs (LocalTests, ZapTests) in {
      val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(firstName = Some("Nathan")))
      val response    = service.postVerifyPersonal(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "BAD_NAME"
      actual.desc mustBe "Either the name or the firstName/lastName pair is required (but not both)"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with too short sort code" taggedAs (LocalTests, ZapTests) in {
      val requestBody = PersonalRequest(
        Account(Some("79880"), Some("99901100")),
        Subject(name = generateRandomName)
      )
      val response    = service.postVerifyPersonal(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "INVALID_SORTCODE"
      actual.desc mustBe "79880: invalid sortcode"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with a missing sort code" taggedAs (LocalTests, ZapTests) in {
      val requestBody = PersonalRequest(
        Account(accountNumber = Some("99901100")),
        Subject(name = generateRandomName)
      )
      val response    = service.postVerifyPersonal(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "MALFORMED_JSON"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with a missing account number" taggedAs (LocalTests, ZapTests) in {
      val requestBody = PersonalRequest(
        Account(sortCode = Some("679880")),
        Subject(name = generateRandomName)
      )
      val response    = service.postVerifyPersonal(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "MALFORMED_JSON"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with a missing sort code and account number" taggedAs (LocalTests, ZapTests) in {
      val requestBody = PersonalRequest(Account(), Subject(name = generateRandomName))
      val response    = service.postVerifyPersonal(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "MALFORMED_JSON"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with a too long sort code" taggedAs (LocalTests, ZapTests) in {
      val requestBody = PersonalRequest(
        Account(Some("6679880"), Some("99901100")),
        Subject(name = generateRandomName)
      )
      val response    = service.postVerifyPersonal(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "INVALID_SORTCODE"
      actual.desc mustBe "6679880: invalid sortcode"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with an invalid sort code" taggedAs (LocalTests, ZapTests) in {
      val requestBody = PersonalRequest(
        Account(Some("9999A7"), Some("99901100")),
        Subject(name = generateRandomName)
      )
      val response    = service.postVerifyPersonal(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "INVALID_SORTCODE"
      actual.desc mustBe "9999A7: invalid sortcode"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with a too long account number" taggedAs (LocalTests, ZapTests) in {
      val requestBody = PersonalRequest(
        Account(Some("679880"), Some("999901100")),
        Subject(name = generateRandomName)
      )
      val response    = service.postVerifyPersonal(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "INVALID_ACCOUNT_NUMBER"
      actual.desc mustBe "999901100: invalid account number"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with a too short account number" taggedAs (LocalTests, ZapTests) in {
      val requestBody = PersonalRequest(
        Account(Some("679880"), Some("9901100")),
        Subject(name = generateRandomName)
      )
      val response    = service.postVerifyPersonal(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "INVALID_ACCOUNT_NUMBER"
      actual.desc mustBe "9901100: invalid account number"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with an invalid account number" taggedAs (LocalTests, ZapTests) in {
      val requestBody = PersonalRequest(
        Account(Some("679880"), Some("1A110005")),
        Subject(name = generateRandomName)
      )
      val response    = service.postVerifyPersonal(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "INVALID_ACCOUNT_NUMBER"
      actual.desc mustBe "1A110005: invalid account number"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with an invalid json" taggedAs (LocalTests, ZapTests) in {
      val requestBody =
        """{
          |"account":
          |{"sortCode" :
          |""".stripMargin
      val response    = service.postVerifyPersonal(requestBody, "")
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "MALFORMED_JSON"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with HMRC account details" taggedAs (LocalTests, ZapTests) in {
      val requestBody = PersonalRequest(
        Account(Some(HMRC_ACCOUNT.sortCode.get), Some(HMRC_ACCOUNT.accountNumber.get)),
        Subject(name = generateRandomName)
      )
      val response    = service.postVerifyPersonal(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "SORT_CODE_ON_DENY_LIST"
      actual.desc mustBe s"${HMRC_ACCOUNT.sortCode.get}: sort code is on deny list. This usually means that it is an HMRC sort code."
      response.status mustBe 400
    }

    "should receive a forbidden request when calling the assess endpoint with default account details" taggedAs (LocalTests, ZapTests) in {
      val requestBody = PersonalRequest(
        Account(Some(DEFAULT_ACCOUNT.sortCode.get), Some(DEFAULT_ACCOUNT.accountNumber.get)),
        Subject(name = generateRandomName)
      )
      val response    = service.postVerifyPersonalWithUnknownUserAgent(requestBody)
      val actual      = Json.parse(response.body).as[Forbidden]

      response.status mustBe 403
      actual.code mustBe 403
      actual.description mustBe "'unknown' is not authorized to use the requested BARS endpoint. Please complete 'https://forms.office.com/Pages/ResponsePage.aspx?id=PPdSrBr9mkqOekokjzE54cRTj_GCzpRJqsT4amG0JK1UMkpBS1NUVDhWR041NjJWU0lCMVZUNk5NTi4u' to request access."
    }
  }
}
