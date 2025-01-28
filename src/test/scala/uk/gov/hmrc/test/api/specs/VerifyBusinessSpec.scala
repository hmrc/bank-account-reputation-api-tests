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

import org.mockserver.model.{HttpRequest, HttpResponse, JsonPathBody}
import org.mockserver.verify.VerificationTimes
import play.api.libs.json.Json
import uk.gov.hmrc.api.BaseSpec
import uk.gov.hmrc.test.api.model.request.BusinessRequest
import uk.gov.hmrc.test.api.model.request.components.{Account, Business}
import uk.gov.hmrc.test.api.model.response.{BadRequest, BusinessV3, Forbidden}
import uk.gov.hmrc.test.api.service.BankAccountReputationFeatureToggle
import uk.gov.hmrc.test.api.tags.{LocalTests, ZapTests}
import uk.gov.hmrc.test.api.utils.MockServer

import java.util.UUID
import scala.concurrent.duration.DurationInt

class VerifyBusinessSpec extends BaseSpec with MockServer with BankAccountReputationFeatureToggle {

  val DEFAULT_ACCOUNT: Account      = Account(Some("601613"), Some("26344696"))
  val HMRC_ACCOUNT: Account         = Account(Some("083210"), Some("12001039"))
  val SUREPAY_TEST_ACCOUNT: Account = Account(Some("999999"), Some("00000001"))

  override def beforeAll: Unit = {
    enableSurePay() // enables surepay API call
    disableModulr() // disables modulr API call

    enableSurePayBusinessCache() // enables caching of surepay responses for business bank account checks
    enableSurePayPersonalCache() // enables caching of surepay responses for personal bank account checks

    enableSurePayResponses() // returns surepay responses not modulr responses

    super.beforeAll
  }

  "Payload verification" when {

    "Should get an exact match when making call out to surepay" taggedAs (LocalTests, ZapTests) in {
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

      val requestBody =
        BusinessRequest(DEFAULT_ACCOUNT, Some(Business(generateRandomBusinessName)))
      val response    =
        service.postVerifyBusiness(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[BusinessV3]

      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeIsPresentOnEISCD mustBe "yes"
      actual.sortCodeBankName.get mustBe "HSBC UK BANK PLC"
      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountExists mustBe "yes"
      actual.nameMatches mustBe "yes"
      actual.iban.get mustBe "GB36 HBUK 6016 1326 3446 96"
      response.status mustBe 200

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='businessBankAccountCheck' " +
                "&& @.detail.length()==6" +
                "&& @.detail.request.length()==2" +
                "&& @.detail.response.length()==9" +
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

      val requestBody = BusinessRequest(
        Account(Some("401003"), Some("71201948")),
        Some(Business(generateRandomBusinessName))
      )
      val response    =
        service.postVerifyBusiness(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[BusinessV3]

      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeIsPresentOnEISCD mustBe "no"
      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountExists mustBe "inapplicable"
      actual.nameMatches mustBe "inapplicable"
      actual.sortCodeSupportsDirectDebit mustBe "no"
      actual.sortCodeSupportsDirectCredit mustBe "no"
      response.status mustBe 200

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='businessBankAccountCheck' " +
                "&& @.detail.length()==6" +
                "&& @.detail.request.length()==2" +
                "&& @.detail.response.length()==7" +
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
        VerificationTimes.atLeast(0)
      )
      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit/merged")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='OutboundCall' " +
                "&& @.request.tags.path=='http://localhost:6001/Match'" +
                s"&& @.request.tags.X-Request-ID=='$xRequestId'" +
                ")]"
            )
          ),
        VerificationTimes.atLeast(0)
      )
    }
  }

  "Valid requests" when {

    "successful Surepay calls are cached" taggedAs (LocalTests, ZapTests) in {
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

      val requestBody =
        BusinessRequest(DEFAULT_ACCOUNT, Some(Business(generateRandomBusinessName)))
      val response    =
        service.postVerifyBusiness(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[BusinessV3]

      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeIsPresentOnEISCD mustBe "yes"
      actual.sortCodeBankName.get mustBe "HSBC UK BANK PLC"
      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountExists mustBe "yes"
      actual.nameMatches mustBe "yes"
      response.status mustBe 200

      val secondResponse =
        service.postVerifyBusiness(requestBody, xRequestId)
      val cached         = Json.parse(secondResponse.body).as[BusinessV3]

      cached.accountNumberIsWellFormatted mustBe "yes"
      cached.sortCodeIsPresentOnEISCD mustBe "yes"
      cached.sortCodeBankName.get mustBe "HSBC UK BANK PLC"
      cached.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      cached.accountExists mustBe "yes"
      cached.nameMatches mustBe "yes"
      secondResponse.status mustBe 200

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='businessBankAccountCheck' " +
                "&& @.detail.length()==6" +
                "&& @.detail.context=='surepay_business_fromcache'" +
                "&& @.detail.request.length()==2" +
                "&& @.detail.response.length()==9" +
                ")]"
            )
          ),
        VerificationTimes.atLeast(1)
      )
    }

    "should receive account exists indeterminate, name matches indeterminate when the data does not exist with Surepay" taggedAs (LocalTests, ZapTests) in {
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

      val requestBody =
        BusinessRequest(DEFAULT_ACCOUNT, Some(Business(generateRandomBusinessName)))
      val response    =
        service.postVerifyBusiness(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[BusinessV3]

      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeIsPresentOnEISCD mustBe "yes"
      actual.sortCodeBankName.get mustBe "HSBC UK BANK PLC"
      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountExists mustBe "indeterminate"
      actual.nameMatches mustBe "indeterminate"
      response.status mustBe 200
    }

    "should receive sort code is not valid if sort code is in modcheck database but account number fails validation" taggedAs (LocalTests, ZapTests) in {
      val requestBody = BusinessRequest(
        Account(Some("401003"), Some("71201958")),
        Some(Business(generateRandomBusinessName))
      )
      val response    = service.postVerifyBusiness(requestBody)
      val actual      = Json.parse(response.body).as[BusinessV3]

      actual.accountNumberIsWellFormatted mustBe "no"
      actual.sortCodeIsPresentOnEISCD mustBe "no"
      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountExists mustBe "inapplicable"
      actual.nameMatches mustBe "inapplicable"
      response.status mustBe 200
    }

    "should receive accountNumberWithSortCodeIsValid is indeterminate if sort code is not found in in modcheck database" taggedAs (LocalTests, ZapTests) in {
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
            .withBody("""{"Matched": true}""".stripMargin)
            .withStatusCode(200)
        )

      val requestBody = BusinessRequest(
        Account(Some("679880"), Some("27505196")),
        Some(Business(generateRandomBusinessName))
      )
      val response    =
        service.postVerifyBusiness(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[BusinessV3]

      actual.accountNumberIsWellFormatted mustBe "indeterminate"
      actual.sortCodeIsPresentOnEISCD mustBe "yes"
      actual.sortCodeBankName.get mustBe "Lloyds"
      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountExists mustBe "yes"
      actual.nameMatches mustBe "yes"
      response.status mustBe 200
    }

    "should not accept Surepay test credentials with default config" taggedAs (LocalTests, ZapTests) in {
      val xRequestId: String = UUID.randomUUID().toString

      val requestBody = BusinessRequest(
        SUREPAY_TEST_ACCOUNT,
        Some(Business(generateRandomBusinessName))
      )
      val response    =
        service.postVerifyBusiness(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[BusinessV3]

      actual.accountNumberIsWellFormatted mustBe "indeterminate"
      actual.sortCodeIsPresentOnEISCD mustBe "no"
      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountExists mustBe "inapplicable"
      actual.nameMatches mustBe "inapplicable"
      response.status mustBe 200
    }

    "should not include EISCD status in account number validity check" taggedAs (LocalTests, ZapTests) in {
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

      val requestBody = BusinessRequest(
        Account(Some("401003"), Some("71201948")),
        Some(Business(generateRandomBusinessName))
      )
      val response    =
        service.postVerifyBusiness(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[BusinessV3]

      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeIsPresentOnEISCD mustBe "no"
      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountExists mustBe "inapplicable"
      actual.nameMatches mustBe "inapplicable"
      response.status mustBe 200
    }

    "should receive a match if the response is a close match on Surepay" taggedAs (LocalTests, ZapTests) in {
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
            .withBody(s"""{"Matched": false, "ReasonCode": "MBAM", "Name": "E-Corp"}""".stripMargin)
            .withStatusCode(200)
        )

      val requestBody =
        BusinessRequest(DEFAULT_ACCOUNT, Some(Business(generateRandomBusinessName)))
      val response    =
        service.postVerifyBusiness(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[BusinessV3]

      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeIsPresentOnEISCD mustBe "yes"
      actual.sortCodeBankName.get mustBe "HSBC UK BANK PLC"
      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountExists mustBe "yes"
      actual.nameMatches mustBe "partial"
      actual.accountName.get mustBe "E-Corp"
      response.status mustBe 200

      val secondResponse =
        service.postVerifyBusiness(requestBody, xRequestId)
      val cached         = Json.parse(secondResponse.body).as[BusinessV3]

      cached.accountNumberIsWellFormatted mustBe "yes"
      cached.sortCodeIsPresentOnEISCD mustBe "yes"
      cached.sortCodeBankName.get mustBe "HSBC UK BANK PLC"
      cached.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      cached.accountExists mustBe "yes"
      cached.nameMatches mustBe "partial"
      cached.accountName.get mustBe "E-Corp"
      secondResponse.status mustBe 200

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='businessBankAccountCheck' " +
                "&& @.detail.length()==6" +
                "&& @.detail.context=='surepay_business_succeeded'" +
                "&& @.detail.request.length()==2" +
                "&& @.detail.response.length()==10" +
                s"&& @.tags.path=='/${service.BarsEndpoints.VERIFY_BUSINESS}'" +
                ")]"
            )
          ),
        VerificationTimes.atLeast(1)
      )
    }

    "should receive a match if the response is a match but the account type is actually personal" taggedAs (LocalTests, ZapTests) in {
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
            .withBody(s"""{"Matched": false, "ReasonCode": "PANM"}""".stripMargin)
            .withStatusCode(200)
        )

      val requestBody =
        BusinessRequest(DEFAULT_ACCOUNT, Some(Business(generateRandomBusinessName)))
      val response    =
        service.postVerifyBusiness(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[BusinessV3]

      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeIsPresentOnEISCD mustBe "yes"
      actual.sortCodeBankName.get mustBe "HSBC UK BANK PLC"
      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountExists mustBe "yes"
      actual.nameMatches mustBe "yes"
      response.status mustBe 200

      val secondResponse =
        service.postVerifyBusiness(requestBody, xRequestId)
      val cached         = Json.parse(secondResponse.body).as[BusinessV3]

      cached.accountNumberIsWellFormatted mustBe "yes"
      cached.sortCodeIsPresentOnEISCD mustBe "yes"
      cached.sortCodeBankName.get mustBe "HSBC UK BANK PLC"
      cached.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      cached.accountExists mustBe "yes"
      cached.nameMatches mustBe "yes"
      secondResponse.status mustBe 200

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='businessBankAccountCheck' " +
                "&& @.detail.length()==6" +
                "&& @.detail.context=='surepay_business_succeeded'" +
                "&& @.detail.request.length()==2" +
                "&& @.detail.response.length()==9" +
                ")]"
            )
          ),
        VerificationTimes.atLeast(1)
      )
    }

    "should use useragent as trueCallingClient if True-Calling-Client header is not set" taggedAs (LocalTests, ZapTests) in {
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

      val requestBody =
        BusinessRequest(DEFAULT_ACCOUNT, Some(Business(generateRandomBusinessName)))
      val response    =
        service.postVerifyBusiness(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[BusinessV3]

      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeIsPresentOnEISCD mustBe "yes"
      actual.sortCodeBankName.get mustBe "HSBC UK BANK PLC"
      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountExists mustBe "yes"
      actual.nameMatches mustBe "yes"
      response.status mustBe 200

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='businessBankAccountCheck' " +
                "&& @.detail.length()==6" +
                s"&& @.detail.callingClient=='$defaultUserAgent'" +
                "&& @.detail.context=='surepay_business_succeeded'" +
                "&& @.detail.request.length()==2" +
                "&& @.detail.response.length()==9" +
                ")]"
            )
          ),
        VerificationTimes.atLeast(1)
      )
    }

    "should use True-Calling-Client header as trueCallingClient if set" taggedAs (LocalTests, ZapTests) in {
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

      val requestBody =
        BusinessRequest(DEFAULT_ACCOUNT, Some(Business(generateRandomBusinessName)))
      val response    = service.postVerifyBusinessWithTrueCallingClient(requestBody, xRequestId, "some-upstream-service")
      val actual      = Json.parse(response.body).as[BusinessV3]

      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeIsPresentOnEISCD mustBe "yes"
      actual.sortCodeBankName.get mustBe "HSBC UK BANK PLC"
      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountExists mustBe "yes"
      actual.nameMatches mustBe "yes"
      response.status mustBe 200

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='businessBankAccountCheck' " +
                "&& @.detail.length()==6" +
                "&& @.detail.callingClient=='some-upstream-service'" +
                s"&& @.detail.userAgent=='$defaultUserAgent'" +
                "&& @.detail.context=='surepay_business_succeeded'" +
                "&& @.detail.request.length()==2" +
                "&& @.detail.response.length()==9" +
                ")]"
            )
          ),
        VerificationTimes.atLeast(1)
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
            .withBody(s"""{"Matched": true, "ReasonCode": "XXXX", "Name": "Some Business"}""".stripMargin)
            .withStatusCode(200)
        )

      val requestBody =
        BusinessRequest(DEFAULT_ACCOUNT, Some(Business(generateRandomBusinessName)))
      val response    =
        service.postVerifyBusiness(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[BusinessV3]

      actual.accountNumberIsWellFormatted mustBe "yes"
      actual.sortCodeIsPresentOnEISCD mustBe "yes"
      actual.sortCodeBankName.get mustBe "HSBC UK BANK PLC"
      actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
      actual.accountExists mustBe "indeterminate"
      actual.nameMatches mustBe "indeterminate"
      response.status mustBe 200

      mockServer.verify(500.millis)(
        HttpRequest
          .request()
          .withPath("/write/audit")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='businessBankAccountCheck' " +
                "&& @.detail.length()==6" +
                "&& @.detail.context=='surepay_business_succeeded'" +
                "&& @.detail.request.length()==2" +
                "&& @.detail.response.length()==9" +
                ")]"
            )
          ),
        VerificationTimes.atLeast(1)
      )
    }

    "should receive a forbidden request when calling the assess endpoint with default account details" taggedAs (LocalTests, ZapTests) in {
      val requestBody =
        BusinessRequest(DEFAULT_ACCOUNT, Some(Business(generateRandomBusinessName)))
      val response    = service.postVerifyBusinessWithUnknownUserAgent(requestBody)
      val actual      = Json.parse(response.body).as[Forbidden]

      response.status mustBe 403
      actual.code mustBe 403
      actual.description mustBe "'unknown' is not authorized to use the requested BARS endpoint. Please complete 'https://forms.office.com/Pages/ResponsePage.aspx?id=PPdSrBr9mkqOekokjzE54cRTj_GCzpRJqsT4amG0JK1UMkpBS1NUVDhWR041NjJWU0lCMVZUNk5NTi4u' to request access."
    }
  }

  "Invalid requests" when {

    "should receive bad request when not submitting business name" taggedAs (LocalTests, ZapTests) in {
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
            .withBody("""{"Matched": true}""".stripMargin)
            .withStatusCode(200)
        )

      val requestBody = BusinessRequest(DEFAULT_ACCOUNT, Some(Business()))
      val response    =
        service.postVerifyBusiness(requestBody, xRequestId)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "MALFORMED_JSON"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with empty trading name" taggedAs (LocalTests, ZapTests) in {
      val requestBody =
        BusinessRequest(DEFAULT_ACCOUNT, Some(Business(companyName = Some(""))))
      val response    = service.postVerifyBusiness(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "BLANK_NAME"
      actual.desc mustBe "blank company name"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with too short sort code" taggedAs (LocalTests, ZapTests) in {
      val requestBody = BusinessRequest(
        Account(Some("79880"), Some("99901100")),
        Some(Business(generateRandomBusinessName))
      )
      val response    = service.postVerifyBusiness(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "INVALID_SORTCODE"
      actual.desc mustBe "79880: invalid sortcode"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with a missing sort code" taggedAs (LocalTests, ZapTests) in {
      val requestBody = BusinessRequest(
        Account(accountNumber = Some("99901100")),
        Some(Business(generateRandomBusinessName))
      )
      val response    = service.postVerifyBusiness(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "MALFORMED_JSON"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with a missing account number" taggedAs (LocalTests, ZapTests) in {
      val requestBody = BusinessRequest(
        Account(sortCode = Some("679880")),
        Some(Business(generateRandomBusinessName))
      )
      val response    = service.postVerifyBusiness(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "MALFORMED_JSON"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with a missing sort code and account number" taggedAs (LocalTests, ZapTests) in {
      val requestBody =
        BusinessRequest(Account(), Some(Business(generateRandomBusinessName)))
      val response    = service.postVerifyBusiness(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "MALFORMED_JSON"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with a too long sort code" taggedAs (LocalTests, ZapTests) in {
      val requestBody = BusinessRequest(
        Account(Some("6679880"), Some("99901100")),
        Some(Business(generateRandomBusinessName))
      )
      val response    = service.postVerifyBusiness(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "INVALID_SORTCODE"
      actual.desc mustBe "6679880: invalid sortcode"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with an invalid sort code" taggedAs (LocalTests, ZapTests) in {
      val requestBody = BusinessRequest(
        Account(Some("9999A7"), Some("99901100")),
        Some(Business(generateRandomBusinessName))
      )
      val response    = service.postVerifyBusiness(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "INVALID_SORTCODE"
      actual.desc mustBe "9999A7: invalid sortcode"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with a too long account number" taggedAs (LocalTests, ZapTests) in {
      val requestBody = BusinessRequest(
        Account(Some("679880"), Some("999901100")),
        Some(Business(generateRandomBusinessName))
      )
      val response    = service.postVerifyBusiness(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "INVALID_ACCOUNT_NUMBER"
      actual.desc mustBe "999901100: invalid account number"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with a too short account number" taggedAs (LocalTests, ZapTests) in {
      val requestBody = BusinessRequest(
        Account(Some("679880"), Some("9901100")),
        Some(Business(generateRandomBusinessName))
      )
      val response    = service.postVerifyBusiness(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "INVALID_ACCOUNT_NUMBER"
      actual.desc mustBe "9901100: invalid account number"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with an invalid account number" taggedAs (LocalTests, ZapTests) in {
      val requestBody = BusinessRequest(
        Account(Some("679880"), Some("1A110005")),
        Some(Business(generateRandomBusinessName))
      )
      val response    = service.postVerifyBusiness(requestBody)
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
      val response    = service.postVerifyBusiness(requestBody, "")
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "MALFORMED_JSON"
      response.status mustBe 400
    }

    "should receive a bad request when calling the assess endpoint with HMRC account details" taggedAs (LocalTests, ZapTests) in {
      val requestBody = BusinessRequest(
        Account(Some(HMRC_ACCOUNT.sortCode.get), Some(HMRC_ACCOUNT.accountNumber.get)),
        Some(Business(generateRandomBusinessName))
      )
      val response    = service.postVerifyBusiness(requestBody)
      val actual      = Json.parse(response.body).as[BadRequest]

      actual.code mustBe "SORT_CODE_ON_DENY_LIST"
      actual.desc mustBe s"${HMRC_ACCOUNT.sortCode.get}: sort code is on deny list. This usually means that it is an HMRC sort code."
      response.status mustBe 400
    }

    "should receive a forbidden request when calling the assess endpoint with default account details" taggedAs (LocalTests, ZapTests) in {
      val requestBody =
        BusinessRequest(DEFAULT_ACCOUNT, Some(Business(generateRandomBusinessName)))
      val response    = service.postVerifyBusinessWithUnknownUserAgent(requestBody)
      val actual      = Json.parse(response.body).as[Forbidden]

      response.status mustBe 403
      actual.code mustBe 403
      actual.description mustBe "'unknown' is not authorized to use the requested BARS endpoint. Please complete 'https://forms.office.com/Pages/ResponsePage.aspx?id=PPdSrBr9mkqOekokjzE54cRTj_GCzpRJqsT4amG0JK1UMkpBS1NUVDhWR041NjJWU0lCMVZUNk5NTi4u' to request access."
    }
  }
}
