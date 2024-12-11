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
import uk.gov.hmrc.test.api.model.request.PersonalRequest
import uk.gov.hmrc.test.api.model.request.components.{Account, Subject}
import uk.gov.hmrc.test.api.model.response.{AssessV4, BadRequest, Forbidden}
import uk.gov.hmrc.test.api.service.BankAccountReputationFeatureToggle
import uk.gov.hmrc.test.api.tags.{LocalTests, ZapTests}
import uk.gov.hmrc.test.api.utils.MockServer

import java.util.UUID

class PersonalAccountV4CheckSpec
    extends BaseSpec
    with MockServer
    with AccountFixtures
    with ModulrFixtures
    with BankAccountReputationFeatureToggle {

  override def beforeAll: Unit = {
    disableSurePay() // disable surepay API call

    enableModulr() // enables modulr API call
    enableModulrPersonalCache() // enables caching of modulr responses for personal bank account checks

    enableModulrResponses() // returns modulr responses not surepay responses

    super.beforeAll
  }

  "/verify/personal" should {
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

      val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(name = generateRandomName))

      service.postVerifyPersonal(requestBody, xRequestId)

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

      val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(name = generateRandomName))

      service.postVerifyPersonal(requestBody, xRequestId)

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

      val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(name = generateRandomName))

      // initial request for personal account check should not
      // find a cache hit so should call the Modulr API then cache the response
      service.postVerifyPersonal(requestBody, xRequestId).status mustBe 200

      // second request for the same personal account check should find a cache hit
      // so should not call the Modulr API
      service.postVerifyPersonal(requestBody, xRequestId).status mustBe 200

      // verify that we called the Modulr API only once
      mockServer.verify(request().withPath(MODULR_PATH), VerificationTimes.exactly(1))

      // verify TxSucceeded audit event was written once with a call credit from the API
      verifyTxSucceededAuditEvent("modulr_personal_fromapi", numberOfTimes = 1)

      // verify TxSucceeded audit event was written once with a call credit from the cache
      verifyTxSucceededAuditEvent("modulr_personal_fromcache", numberOfTimes = 1)
    }

    "not make a Module API call when the sort code is not on EISCD" in {
      val requestBody = PersonalRequest(Account(Some("401003"), Some("71201948")), Subject(name = generateRandomName))

      val response = service.postVerifyPersonal(requestBody, xRequestId)
      val actual   = Json.parse(response.body).as[AssessV4]

      actual.sortCodeIsPresentOnEISCD mustBe "no"

      // verify that we have not called the Modulr API
      mockServer.verify(request().withPath(MODULR_PATH), VerificationTimes.never())

      // verify TxSucceeded audit event was written once explicitly stating it was not found in EISCD
      verifyTxSucceededAuditEvent("notfoundineiscd", numberOfTimes = 1)
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

        val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(name = generateRandomName))

        val response     = service.postVerifyPersonal(requestBody, xRequestId)
        val responseBody = Json.parse(response.body).as[AssessV4]

        responseBody.accountExists mustBe "yes"
        responseBody.nameMatches mustBe "yes"
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

        val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(name = generateRandomName))

        val response     = service.postVerifyPersonal(requestBody, xRequestId)
        val responseBody = Json.parse(response.body).as[AssessV4]

        responseBody.nonStandardAccountDetailsRequiredForBacs mustBe "no"
        responseBody.sortCodeBankName.get mustBe "Lloyds"
        responseBody.accountExists mustBe "yes"
        responseBody.nameMatches mustBe "partial"
        responseBody.accountName.getOrElse("") mustBe "Patrick O'Conner-Smith"

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

        val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(name = generateRandomName))

        val response     = service.postVerifyPersonal(requestBody, xRequestId)
        val responseBody = Json.parse(response.body).as[AssessV4]

        responseBody.accountExists mustBe "yes"
        responseBody.nameMatches mustBe "no"
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

        val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(name = generateRandomName))

        val response     = service.postVerifyPersonal(requestBody, xRequestId)
        val responseBody = Json.parse(response.body).as[AssessV4]

        responseBody.accountExists mustBe "no"
        responseBody.nameMatches mustBe "yes"
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

        val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(name = generateRandomName))

        val response     = service.postVerifyPersonal(requestBody, xRequestId)
        val responseBody = Json.parse(response.body).as[AssessV4]

        responseBody.accountExists mustBe "yes"
        // AssessV4 changes `partial` to `yes` for the nameMatches field, AccessV4 would leave it as `partial`
        responseBody.nameMatches mustBe "yes"
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

        val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(name = generateRandomName))

        val response     = service.postVerifyPersonal(requestBody, xRequestId)
        val responseBody = Json.parse(response.body).as[AssessV4]

        responseBody.accountExists mustBe "no"
        responseBody.nameMatches mustBe "partial"
        responseBody.accountName mustBe None
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

        val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(name = generateRandomName))

        val response     = service.postVerifyPersonal(requestBody, xRequestId)
        val responseBody = Json.parse(response.body).as[AssessV4]

        responseBody.accountExists mustBe "yes"
        responseBody.nameMatches mustBe "partial"
        responseBody.sortCodeIsPresentOnEISCD mustBe "yes"
        responseBody.accountName.getOrElse("") mustBe "Patrick O'Conner-Smith"
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

        val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(name = generateRandomName))

        val response     = service.postVerifyPersonal(requestBody, xRequestId)
        val responseBody = Json.parse(response.body).as[AssessV4]

        responseBody.accountExists mustBe "no"
        responseBody.nameMatches mustBe "indeterminate"
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

        val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(name = generateRandomName))

        val response     = service.postVerifyPersonal(requestBody, xRequestId)
        val responseBody = Json.parse(response.body).as[AssessV4]

        responseBody.accountExists mustBe "indeterminate"
        responseBody.nameMatches mustBe "indeterminate"
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

        val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(name = generateRandomName))

        val response     = service.postVerifyPersonal(requestBody, xRequestId)
        val responseBody = Json.parse(response.body).as[AssessV4]

        responseBody.accountExists mustBe "indeterminate"
        responseBody.nameMatches mustBe "indeterminate"
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

        val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(name = generateRandomName))

        val response     = service.postVerifyPersonal(requestBody, xRequestId)
        val responseBody = Json.parse(response.body).as[AssessV4]

        responseBody.accountExists mustBe "no"
        responseBody.nameMatches mustBe "indeterminate"
        responseBody.sortCodeIsPresentOnEISCD mustBe "yes"
        response.status mustBe 200
      }

    }

    "return a BAD_REQUEST (400) response" when {

      "the request has a missing name" taggedAs (LocalTests, ZapTests) in {
        val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject())
        val response    = service.postVerifyPersonal(requestBody, xRequestId)
        val actual      = Json.parse(response.body).as[BadRequest]

        actual.code mustBe "BAD_NAME"
        actual.desc mustBe "Either the name or the firstName/lastName pair is required (but not both)"
        response.status mustBe 400
      }

      "the request has both a name and last name" taggedAs (LocalTests, ZapTests) in {
        val requestBody = PersonalRequest(
          DEFAULT_ACCOUNT,
          Subject(lastName = Some("Smith"), name = generateRandomName)
        )
        val response    = service.postVerifyPersonal(requestBody, xRequestId)
        val actual      = Json.parse(response.body).as[BadRequest]

        actual.code mustBe "BAD_NAME"
        actual.desc mustBe "Either the name or the firstName/lastName pair is required (but not both)"
        response.status mustBe 400
      }

      "the request has both a name and first name" taggedAs (LocalTests, ZapTests) in {
        val requestBody = PersonalRequest(
          DEFAULT_ACCOUNT,
          Subject(firstName = Some("Nathan"), name = generateRandomName)
        )
        val response    = service.postVerifyPersonal(requestBody, xRequestId)
        val actual      = Json.parse(response.body).as[BadRequest]

        actual.code mustBe "BAD_NAME"
        actual.desc mustBe "Either the name or the firstName/lastName pair is required (but not both)"
        response.status mustBe 400
      }

      "the request has an empty first name" taggedAs (LocalTests, ZapTests) in {
        val requestBody = PersonalRequest(
          DEFAULT_ACCOUNT,
          Subject(firstName = Some(""), lastName = Some("Smith"))
        )
        val response    = service.postVerifyPersonal(requestBody, xRequestId)
        val actual      = Json.parse(response.body).as[BadRequest]

        actual.code mustBe "BLANK_FIRST_NAME"
        actual.desc mustBe "firstName length is blank"
        response.status mustBe 400
      }

      "the request has a missing first name" taggedAs (LocalTests, ZapTests) in {
        val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(lastName = Some("Smith")))
        val response    = service.postVerifyPersonal(requestBody, xRequestId)
        val actual      = Json.parse(response.body).as[BadRequest]

        actual.code mustBe "BAD_NAME"
        actual.desc mustBe "Either the name or the firstName/lastName pair is required (but not both)"
        response.status mustBe 400
      }

      "the request has an empty last name" taggedAs (LocalTests, ZapTests) in {
        val requestBody = PersonalRequest(
          DEFAULT_ACCOUNT,
          Subject(firstName = Some("Nathan"), lastName = Some(""))
        )
        val response    = service.postVerifyPersonal(requestBody, xRequestId)
        val actual      = Json.parse(response.body).as[BadRequest]

        actual.code mustBe "BLANK_LAST_NAME"
        actual.desc mustBe "lastName length is blank"
        response.status mustBe 400
      }

      "the request has a missing last name" taggedAs (LocalTests, ZapTests) in {
        val requestBody = PersonalRequest(DEFAULT_ACCOUNT, Subject(firstName = Some("Nathan")))
        val response    = service.postVerifyPersonal(requestBody, xRequestId)
        val actual      = Json.parse(response.body).as[BadRequest]

        actual.code mustBe "BAD_NAME"
        actual.desc mustBe "Either the name or the firstName/lastName pair is required (but not both)"
        response.status mustBe 400
      }

      "the request has a too short sort code" taggedAs (LocalTests, ZapTests) in {
        val requestBody = PersonalRequest(
          Account(Some("79880"), Some("99901100")),
          Subject(name = generateRandomName)
        )
        val response    = service.postVerifyPersonal(requestBody, xRequestId)
        val actual      = Json.parse(response.body).as[BadRequest]

        actual.code mustBe "INVALID_SORTCODE"
        actual.desc mustBe "79880: invalid sortcode"
        response.status mustBe 400
      }

      "the request has a missing sort code" taggedAs (LocalTests, ZapTests) in {
        val requestBody = PersonalRequest(
          Account(accountNumber = Some("99901100")),
          Subject(name = generateRandomName)
        )
        val response    = service.postVerifyPersonal(requestBody, xRequestId)
        val actual      = Json.parse(response.body).as[BadRequest]

        actual.code mustBe "MALFORMED_JSON"
        response.status mustBe 400
      }

      "the request has a missing account number" taggedAs (LocalTests, ZapTests) in {
        val requestBody = PersonalRequest(
          Account(sortCode = Some("679880")),
          Subject(name = generateRandomName)
        )
        val response    = service.postVerifyPersonal(requestBody, xRequestId)
        val actual      = Json.parse(response.body).as[BadRequest]

        actual.code mustBe "MALFORMED_JSON"
        response.status mustBe 400
      }

      "the request has a missing sort code and account number" taggedAs (LocalTests, ZapTests) in {
        val requestBody = PersonalRequest(Account(), Subject(name = generateRandomName))
        val response    = service.postVerifyPersonal(requestBody, xRequestId)
        val actual      = Json.parse(response.body).as[BadRequest]

        actual.code mustBe "MALFORMED_JSON"
        response.status mustBe 400
      }

      "the request has a too long sort code" taggedAs (LocalTests, ZapTests) in {
        val requestBody = PersonalRequest(
          Account(Some("6679880"), Some("99901100")),
          Subject(name = generateRandomName)
        )
        val response    = service.postVerifyPersonal(requestBody, xRequestId)
        val actual      = Json.parse(response.body).as[BadRequest]

        actual.code mustBe "INVALID_SORTCODE"
        actual.desc mustBe "6679880: invalid sortcode"
        response.status mustBe 400
      }

      "the request has an invalid sort code" taggedAs (LocalTests, ZapTests) in {
        val requestBody = PersonalRequest(
          Account(Some("9999A7"), Some("99901100")),
          Subject(name = generateRandomName)
        )
        val response    = service.postVerifyPersonal(requestBody, xRequestId)
        val actual      = Json.parse(response.body).as[BadRequest]

        actual.code mustBe "INVALID_SORTCODE"
        actual.desc mustBe "9999A7: invalid sortcode"
        response.status mustBe 400
      }

      "the request has a too long account number" taggedAs (LocalTests, ZapTests) in {
        val requestBody = PersonalRequest(
          Account(Some("679880"), Some("999901100")),
          Subject(name = generateRandomName)
        )
        val response    = service.postVerifyPersonal(requestBody, xRequestId)
        val actual      = Json.parse(response.body).as[BadRequest]

        actual.code mustBe "INVALID_ACCOUNT_NUMBER"
        actual.desc mustBe "999901100: invalid account number"
        response.status mustBe 400
      }

      "the request has a too short account number" taggedAs (LocalTests, ZapTests) in {
        val requestBody = PersonalRequest(
          Account(Some("679880"), Some("9901100")),
          Subject(name = generateRandomName)
        )
        val response    = service.postVerifyPersonal(requestBody, xRequestId)
        val actual      = Json.parse(response.body).as[BadRequest]

        actual.code mustBe "INVALID_ACCOUNT_NUMBER"
        actual.desc mustBe "9901100: invalid account number"
        response.status mustBe 400
      }

      "the request has an invalid account number" taggedAs (LocalTests, ZapTests) in {
        val requestBody = PersonalRequest(
          Account(Some("679880"), Some("1A110005")),
          Subject(name = generateRandomName)
        )
        val response    = service.postVerifyPersonal(requestBody, xRequestId)
        val actual      = Json.parse(response.body).as[BadRequest]

        actual.code mustBe "INVALID_ACCOUNT_NUMBER"
        actual.desc mustBe "1A110005: invalid account number"
        response.status mustBe 400
      }

      "the request has an invalid json body" taggedAs (LocalTests, ZapTests) in {
        val requestBody =
          """{
            |"account":
            |{"sortCode" :
            |""".stripMargin
        val response    = service.postVerifyPersonal(requestBody, xRequestId)
        val actual      = Json.parse(response.body).as[BadRequest]

        actual.code mustBe "MALFORMED_JSON"
        response.status mustBe 400
      }

      "the request has  HMRC account details" taggedAs (LocalTests, ZapTests) in {
        val requestBody = PersonalRequest(
          Account(Some(HMRC_ACCOUNT.sortCode.get), Some(HMRC_ACCOUNT.accountNumber.get)),
          Subject(name = generateRandomName)
        )
        val response    = service.postVerifyPersonal(requestBody, xRequestId)
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
        val response    = service.postVerifyPersonalWithUnknownUserAgent(requestBody, xRequestId)
        val actual      = Json.parse(response.body).as[Forbidden]

        response.status mustBe 403
        actual.code mustBe 403
        actual.description mustBe "'unknown' is not authorized to use the requested BARS endpoint. Please complete 'https://forms.office.com/Pages/ResponsePage.aspx?id=PPdSrBr9mkqOekokjzE54cRTj_GCzpRJqsT4amG0JK1UMkpBS1NUVDhWR041NjJWU0lCMVZUNk5NTi4u' to request access."
      }

    }

  }

}
