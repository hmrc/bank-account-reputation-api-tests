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

package uk.gov.hmrc.test.api.specs.modulr

import org.mockserver.model.HttpRequest.request
import org.mockserver.model.{Header, HttpRequest, HttpResponse, JsonPathBody}
import org.mockserver.verify.VerificationTimes
import play.api.libs.json.Json
import uk.gov.hmrc.api.BaseSpec
import uk.gov.hmrc.test.api.fixtures.{AccountFixtures, ModulrFixtures}
import uk.gov.hmrc.test.api.model.request.BusinessRequest
import uk.gov.hmrc.test.api.model.request.components.{Account, Business}
import uk.gov.hmrc.test.api.model.response.{BadRequest, BusinessV2, Forbidden}
import uk.gov.hmrc.test.api.service.BankAccountReputationFeatureToggle
import uk.gov.hmrc.test.api.tags.{LocalTests, ZapTests}
import uk.gov.hmrc.test.api.utils.MockServer

import java.util.UUID

class BusinessAccountV2CheckSpec
    extends BaseSpec
    with MockServer
    with AccountFixtures
    with ModulrFixtures
    with BankAccountReputationFeatureToggle {

  override def beforeAll: Unit = {
    disableSurePay() // disable surepay API call

    enableModulr() // enables modulr API call
    enableModulrBusinessCache() // enables caching of modulr responses for business bank account checks

    enableModulrResponses() // returns modulr responses not surepay responses

    super.beforeAll
  }

  "/business/v2/assess" should {
    val xRequestId: String = UUID.randomUUID().toString

    "send correct headers to Modulr" in {
      mockServer
        .when(
          HttpRequest
            .request()
            .withMethod("POST")
            .withPath(MODULR_PATH)
        )
        .respond(
          HttpResponse
            .response()
            .withHeader("Content-Type", "application/json")
            .withBody(matchResponseAsJsonWith("MATCHED"))
            .withStatusCode(201)
        )

      val requestBody = BusinessRequest(
        DEFAULT_ACCOUNT,
        Some(Business(generateRandomBusinessName, DEFAULT_COMPANY_REGISTRATION_NUMBER))
      )

      service.postBusinessAssessV2(requestBody, xRequestId)

      mockServer.verify(
        request()
          .withPath(MODULR_PATH)
          .withHeaders(
            Header.header("Accept", "application/json"),
            Header.header("Content-Type", "application/json"),
            Header.header("User-Agent", "bank-account-reputation"),
            Header.header("Date"), // https://modulr.readme.io/docs/authentication
            Header.header("x-mod-nonce"), // https://modulr.readme.io/docs/authentication
            Header.header("Authorization") // https://modulr.readme.io/docs/authentication
          )
      )
    }

    "audit the outbound Modulr request" in {
      mockServer
        .when(
          HttpRequest
            .request()
            .withMethod("POST")
            .withPath(MODULR_PATH)
        )
        .respond(
          HttpResponse
            .response()
            .withHeader("Content-Type", "application/json")
            .withBody(matchResponseAsJsonWith("MATCHED"))
            .withStatusCode(201)
        )

      val requestBody = BusinessRequest(
        DEFAULT_ACCOUNT,
        Some(Business(generateRandomBusinessName, DEFAULT_COMPANY_REGISTRATION_NUMBER))
      )

      service.postBusinessAssessV2(requestBody, xRequestId)

      mockServer.verify(
        request()
          .withPath("/write/audit/merged")
          .withBody(
            JsonPathBody.jsonPath(
              "$[?(" +
                "@.auditType=='OutboundCall' " +
                "&& @.request.tags.path=='http://localhost:6001/api-sandbox/account-name-check'" +
                s"&& @.request.tags.X-Request-ID=='$xRequestId'" +
                ")]"
            )
          )
      )

    }

    "return a cached Modulr response for subsequent calls" in {
      mockServer
        .when(
          HttpRequest
            .request()
            .withMethod("POST")
            .withPath(MODULR_PATH)
        )
        .respond(
          HttpResponse
            .response()
            .withHeader("Content-Type", "application/json")
            .withBody(matchResponseAsJsonWith("MATCHED"))
            .withStatusCode(201)
        )

      val requestBody = BusinessRequest(
        DEFAULT_ACCOUNT,
        Some(Business(generateRandomBusinessName, DEFAULT_COMPANY_REGISTRATION_NUMBER))
      )

      // initial request for business account check should not
      // find a cache hit so should call the Modulr API then cache the response
      service.postBusinessAssessV2(requestBody, xRequestId).status mustBe 200

      // second request for the same business account check should find a cache hit
      // so should not call the Modulr API
      service.postBusinessAssessV2(requestBody, xRequestId).status mustBe 200

      // verify that we called the Modulr API only once
      mockServer.verify(request().withPath(MODULR_PATH), VerificationTimes.exactly(1))

      // verify `businessBankAccountCheck` auditType event was written once with a call credit from the API
      verifyBusinessBankAccountCheckAuditEvent("modulr_business_fromapi", numberOfTimes = 1)

      // verify `businessBankAccountCheck` auditType event was written once with a call credit from the cache
      verifyBusinessBankAccountCheckAuditEvent("modulr_business_fromcache", numberOfTimes = 1)
    }

    "not make a Module API call when the sort code is not on EISCD" in {
      val requestBody = BusinessRequest(
        Account(Some("401003"), Some("71201948")),
        Some(Business(generateRandomBusinessName, DEFAULT_COMPANY_REGISTRATION_NUMBER))
      )

      val response = service.postBusinessAssessV2(requestBody, xRequestId)
      val actual   = Json.parse(response.body).as[BusinessV2]

      actual.sortCodeIsPresentOnEISCD mustBe "no"

      // verify that we have not called the Modulr API
      mockServer.verify(request().withPath(MODULR_PATH), VerificationTimes.never())

      // verify businessBankAccountCheck audit event was written once
      verifyBusinessBankAccountCheckAuditEvent("business_modcheckfailed", numberOfTimes = 1)
    }

    "return a OK (200) response with the expected assessment results" when {

      "the Modulr API responds with MATCHED" in {
        mockServer
          .when(
            HttpRequest
              .request()
              .withMethod("POST")
              .withPath(MODULR_PATH)
          )
          .respond(
            HttpResponse
              .response()
              .withHeader("Content-Type", "application/json")
              .withBody(matchResponseAsJsonWith("MATCHED"))
              .withStatusCode(201)
          )

        val requestBody = BusinessRequest(
          DEFAULT_ACCOUNT,
          Some(Business(generateRandomBusinessName, DEFAULT_COMPANY_REGISTRATION_NUMBER))
        )

        val response     = service.postBusinessAssessV2(requestBody, xRequestId)
        val responseBody = Json.parse(response.body).as[BusinessV2]

        responseBody.accountExists mustBe "yes"
        responseBody.companyNameMatches mustBe "yes"
        responseBody.sortCodeIsPresentOnEISCD mustBe "yes"
        response.status mustBe 200
      }

      "the Modulr API responds with CLOSE_MATCH" in {
        mockServer
          .when(
            HttpRequest
              .request()
              .withMethod("POST")
              .withPath(MODULR_PATH)
          )
          .respond(
            HttpResponse
              .response()
              .withHeader("Content-Type", "application/json")
              .withBody(matchResponseAsJsonWith("CLOSE_MATCH", Some("Patrick O'Conner-Smith")))
              .withStatusCode(201)
          )

        val requestBody = BusinessRequest(
          DEFAULT_ACCOUNT,
          Some(Business(generateRandomBusinessName, DEFAULT_COMPANY_REGISTRATION_NUMBER))
        )

        val response     = service.postBusinessAssessV2(requestBody, xRequestId)
        val responseBody = Json.parse(response.body).as[BusinessV2]

        responseBody.nonStandardAccountDetailsRequiredForBacs mustBe "no"
        responseBody.sortCodeBankName.get mustBe "Lloyds"
        responseBody.accountExists mustBe "yes"
        responseBody.companyNameMatches mustBe "yes"
        responseBody.sortCodeIsPresentOnEISCD mustBe "yes"

        response.status mustBe 200
      }

      "the Modulr API responds with NOT_MATCHED" in {
        mockServer
          .when(
            HttpRequest
              .request()
              .withMethod("POST")
              .withPath(MODULR_PATH)
          )
          .respond(
            HttpResponse
              .response()
              .withHeader("Content-Type", "application/json")
              .withBody(matchResponseAsJsonWith("NOT_MATCHED"))
              .withStatusCode(201)
          )

        val requestBody = BusinessRequest(
          DEFAULT_ACCOUNT,
          Some(Business(generateRandomBusinessName, DEFAULT_COMPANY_REGISTRATION_NUMBER))
        )

        val response     = service.postBusinessAssessV2(requestBody, xRequestId)
        val responseBody = Json.parse(response.body).as[BusinessV2]

        responseBody.accountExists mustBe "yes"
        responseBody.companyNameMatches mustBe "no"
        responseBody.sortCodeIsPresentOnEISCD mustBe "yes"
        response.status mustBe 200
      }

      "the Modulr API responds with BUSINESS_ACCOUNT_NAME_MATCHED" in {
        mockServer
          .when(
            HttpRequest
              .request()
              .withMethod("POST")
              .withPath(MODULR_PATH)
          )
          .respond(
            HttpResponse
              .response()
              .withHeader("Content-Type", "application/json")
              .withBody(matchResponseAsJsonWith("BUSINESS_ACCOUNT_NAME_MATCHED"))
              .withStatusCode(201)
          )

        val requestBody = BusinessRequest(
          DEFAULT_ACCOUNT,
          Some(Business(generateRandomBusinessName, DEFAULT_COMPANY_REGISTRATION_NUMBER))
        )

        val response     = service.postBusinessAssessV2(requestBody, xRequestId)
        val responseBody = Json.parse(response.body).as[BusinessV2]

        responseBody.accountExists mustBe "no"
        responseBody.companyNameMatches mustBe "yes"
        responseBody.sortCodeIsPresentOnEISCD mustBe "yes"
        response.status mustBe 200
      }

      "the Modulr API responds with PERSONAL_ACCOUNT_NAME_MATCHED" in {
        mockServer
          .when(
            HttpRequest
              .request()
              .withMethod("POST")
              .withPath(MODULR_PATH)
          )
          .respond(
            HttpResponse
              .response()
              .withHeader("Content-Type", "application/json")
              .withBody(matchResponseAsJsonWith("PERSONAL_ACCOUNT_NAME_MATCHED"))
              .withStatusCode(201)
          )

        val requestBody = BusinessRequest(
          DEFAULT_ACCOUNT,
          Some(Business(generateRandomBusinessName, DEFAULT_COMPANY_REGISTRATION_NUMBER))
        )

        val response     = service.postBusinessAssessV2(requestBody, xRequestId)
        val responseBody = Json.parse(response.body).as[BusinessV2]

        responseBody.accountExists mustBe "yes"
        responseBody.companyNameMatches mustBe "yes"
        responseBody.sortCodeIsPresentOnEISCD mustBe "yes"
        response.status mustBe 200
      }

      "the Modulr API responds with BUSINESS_ACCOUNT_CLOSE_MATCH" in {
        mockServer
          .when(
            HttpRequest
              .request()
              .withMethod("POST")
              .withPath(MODULR_PATH)
          )
          .respond(
            HttpResponse
              .response()
              .withHeader("Content-Type", "application/json")
              .withBody(matchResponseAsJsonWith("BUSINESS_ACCOUNT_CLOSE_MATCH", Some("Patrick O'Conner-Smith")))
              .withStatusCode(201)
          )

        val requestBody = BusinessRequest(
          DEFAULT_ACCOUNT,
          Some(Business(generateRandomBusinessName, DEFAULT_COMPANY_REGISTRATION_NUMBER))
        )

        val response     = service.postBusinessAssessV2(requestBody, xRequestId)
        val responseBody = Json.parse(response.body).as[BusinessV2]

        responseBody.accountExists mustBe "no"
        responseBody.companyNameMatches mustBe "yes"
        responseBody.sortCodeIsPresentOnEISCD mustBe "yes"
        response.status mustBe 200
      }

      "the Modulr API responds with PERSONAL_ACCOUNT_CLOSE_MATCH" in {
        mockServer
          .when(
            HttpRequest
              .request()
              .withMethod("POST")
              .withPath(MODULR_PATH)
          )
          .respond(
            HttpResponse
              .response()
              .withHeader("Content-Type", "application/json")
              .withBody(matchResponseAsJsonWith("PERSONAL_ACCOUNT_CLOSE_MATCH", Some("Patrick O'Conner-Smith")))
              .withStatusCode(201)
          )

        val requestBody = BusinessRequest(
          DEFAULT_ACCOUNT,
          Some(Business(generateRandomBusinessName, DEFAULT_COMPANY_REGISTRATION_NUMBER))
        )

        val response     = service.postBusinessAssessV2(requestBody, xRequestId)
        val responseBody = Json.parse(response.body).as[BusinessV2]

        responseBody.accountExists mustBe "yes"
        responseBody.companyNameMatches mustBe "yes"
        responseBody.sortCodeIsPresentOnEISCD mustBe "yes"
        response.status mustBe 200
      }

      "the Modulr API responds with ACCOUNT_DOES_NOT_EXIST" in {
        mockServer
          .when(
            HttpRequest
              .request()
              .withMethod("POST")
              .withPath(MODULR_PATH)
          )
          .respond(
            HttpResponse
              .response()
              .withHeader("Content-Type", "application/json")
              .withBody(matchResponseAsJsonWith("ACCOUNT_DOES_NOT_EXIST"))
              .withStatusCode(201)
          )

        val requestBody = BusinessRequest(
          DEFAULT_ACCOUNT,
          Some(Business(generateRandomBusinessName, DEFAULT_COMPANY_REGISTRATION_NUMBER))
        )

        val response     = service.postBusinessAssessV2(requestBody, xRequestId)
        val responseBody = Json.parse(response.body).as[BusinessV2]

        responseBody.accountExists mustBe "no"
        responseBody.companyNameMatches mustBe "indeterminate"
        responseBody.sortCodeIsPresentOnEISCD mustBe "yes"
        response.status mustBe 200
      }

      "the Modulr API responds with SECONDARY_ACCOUNT_ID_NOT_FOUND" in {
        mockServer
          .when(
            HttpRequest
              .request()
              .withMethod("POST")
              .withPath(MODULR_PATH)
          )
          .respond(
            HttpResponse
              .response()
              .withHeader("Content-Type", "application/json")
              .withBody(matchResponseAsJsonWith("SECONDARY_ACCOUNT_ID_NOT_FOUND"))
              .withStatusCode(201)
          )

        val requestBody = BusinessRequest(
          DEFAULT_ACCOUNT,
          Some(Business(generateRandomBusinessName, DEFAULT_COMPANY_REGISTRATION_NUMBER))
        )

        val response     = service.postBusinessAssessV2(requestBody, xRequestId)
        val responseBody = Json.parse(response.body).as[BusinessV2]

        responseBody.accountExists mustBe "no"
        responseBody.companyNameMatches mustBe "yes"
        responseBody.sortCodeIsPresentOnEISCD mustBe "yes"
        response.status mustBe 200
      }

      "the Modulr API responds with ACCOUNT_NOT_SUPPORTED" in {
        mockServer
          .when(
            HttpRequest
              .request()
              .withMethod("POST")
              .withPath(MODULR_PATH)
          )
          .respond(
            HttpResponse
              .response()
              .withHeader("Content-Type", "application/json")
              .withBody(matchResponseAsJsonWith("ACCOUNT_NOT_SUPPORTED"))
              .withStatusCode(201)
          )

        val requestBody = BusinessRequest(
          DEFAULT_ACCOUNT,
          Some(Business(generateRandomBusinessName, DEFAULT_COMPANY_REGISTRATION_NUMBER))
        )

        val response     = service.postBusinessAssessV2(requestBody, xRequestId)
        val responseBody = Json.parse(response.body).as[BusinessV2]

        responseBody.accountExists mustBe "indeterminate"
        responseBody.companyNameMatches mustBe "indeterminate"
        responseBody.sortCodeIsPresentOnEISCD mustBe "yes"
        response.status mustBe 200
      }

      "the Modulr API responds with ACCOUNT_SWITCHED" in {
        mockServer
          .when(
            HttpRequest
              .request()
              .withMethod("POST")
              .withPath(MODULR_PATH)
          )
          .respond(
            HttpResponse
              .response()
              .withHeader("Content-Type", "application/json")
              .withBody(matchResponseAsJsonWith("ACCOUNT_SWITCHED"))
              .withStatusCode(201)
          )

        val requestBody = BusinessRequest(
          DEFAULT_ACCOUNT,
          Some(Business(generateRandomBusinessName, DEFAULT_COMPANY_REGISTRATION_NUMBER))
        )

        val response     = service.postBusinessAssessV2(requestBody, xRequestId)
        val responseBody = Json.parse(response.body).as[BusinessV2]

        responseBody.accountExists mustBe "no"
        responseBody.companyNameMatches mustBe "indeterminate"
        responseBody.sortCodeIsPresentOnEISCD mustBe "yes"
        response.status mustBe 200
      }

    }

    "return a BAD_REQUEST (400) response" when {

      "the request has a missing company name" taggedAs (LocalTests, ZapTests) in {
        val requestBody = BusinessRequest(
          DEFAULT_ACCOUNT,
          Some(Business(companyName = Some(""), DEFAULT_COMPANY_REGISTRATION_NUMBER))
        )
        val response    = service.postBusinessAssessV2(requestBody, xRequestId)
        val actual      = Json.parse(response.body).as[BadRequest]

        actual.code mustBe "BLANK_NAME"
        actual.desc mustBe "blank company name"
        response.status mustBe 400
      }

      "should receive a forbidden request when calling the assess endpoint with an unknown user agent" taggedAs (LocalTests, ZapTests) in {
        val requestBody = BusinessRequest(
          DEFAULT_ACCOUNT,
          Some(Business(generateRandomBusinessName, DEFAULT_COMPANY_REGISTRATION_NUMBER))
        )
        val response    = service.postBusinessAssessV2WithUnkownUserAgent(requestBody, xRequestId)
        val actual      = Json.parse(response.body).as[Forbidden]

        response.status mustBe 403
        actual.code mustBe 403
        actual.description mustBe "'unknown' is not authorized to use the requested BARS endpoint. Please complete 'https://forms.office.com/Pages/ResponsePage.aspx?id=PPdSrBr9mkqOekokjzE54cRTj_GCzpRJqsT4amG0JK1UMkpBS1NUVDhWR041NjJWU0lCMVZUNk5NTi4u' to request access."
      }

    }

  }

}
