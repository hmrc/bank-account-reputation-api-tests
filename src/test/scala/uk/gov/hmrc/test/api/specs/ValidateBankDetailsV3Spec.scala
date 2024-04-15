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

import org.mockserver.model.{HttpRequest, JsonPathBody}
import org.mockserver.verify.VerificationTimes
import play.api.libs.json.Json
import uk.gov.hmrc.api.BaseSpec
import uk.gov.hmrc.test.api.model.request.BankAccountRequest
import uk.gov.hmrc.test.api.model.request.components.Account
import uk.gov.hmrc.test.api.model.response.{BadRequest, Forbidden, ValidateBankDetailsV3}
import uk.gov.hmrc.test.api.tags.{LocalTests, ZapTests}
import uk.gov.hmrc.test.api.utils.MockServer

import scala.concurrent.duration.DurationInt

class ValidateBankDetailsV3Spec extends BaseSpec with MockServer {

  val HMRC_ACCOUNT: Account         = Account(Some("083210"), Some("12001039"))
  val NO_CR_ACCOUNT: Account        = Account(Some("209057"), Some("44355655"))
  val NO_DR_ACCOUNT: Account        = Account(Some("203007"), Some("44355655"))
  val NO_AU_ACCOUNT: Account        = Account(Some("235262"), Some("98675767"))
  val DEFAULT_ACCOUNT: Account      = Account(Some("404784"), Some("70872490"))
  val SUREPAY_TEST_ACCOUNT: Account = Account(Some("999999"), Some("00000001"))

  "Should receive a valid response when using valid sort code and account number" taggedAs (LocalTests, ZapTests) in {
    val requestBody = BankAccountRequest(Account(Some("110010"), Some("29250496")))
    val response    = service.postValidateBankDetailsV3(requestBody)
    val actual      = Json.parse(response.body).as[ValidateBankDetailsV3]

    actual.accountNumberIsWellFormatted mustBe "yes"
    actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
    actual.sortCodeIsPresentOnEISCD mustBe "yes"
    actual.sortCodeSupportsDirectDebit.get mustBe "yes"
    actual.sortCodeSupportsDirectCredit.get mustBe "yes"
    actual.iban.get mustBe "GB60 HLFX 1100 1029 2504 96"
    actual.sortCodeBankName.get mustBe "HALIFAX (A TRADING NAME OF BANK OF SCOTLAND PLC)"
    response.status mustBe 200

    mockServer.verify(500.millis)(
      HttpRequest
        .request()
        .withPath("/write/audit")
        .withBody(
          JsonPathBody.jsonPath(
            "$[?(" +
              "@.auditType=='validateBankDetails' " +
              "&& @.detail.length()==4" +
              s"&& @.tags.path=='/${service.BarsEndpoints.VALIDATE_BANK_DETAILS_V3}'" +
              ")]"
          )
        ),
      VerificationTimes.atLeast(1)
    )
  }

  "Should receive indeterminate if sort code fails mod check " taggedAs (LocalTests, ZapTests) in {
    val requestBody = BankAccountRequest(Account(Some("000000"), Some("29250496")))
    val response    = service.postValidateBankDetailsV3(requestBody)
    val actual      = Json.parse(response.body).as[ValidateBankDetailsV3]

    actual.accountNumberIsWellFormatted mustBe "indeterminate"
    actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
    actual.sortCodeIsPresentOnEISCD mustBe "no"
    response.status mustBe 200

    mockServer.verify(500.millis)(
      HttpRequest
        .request()
        .withPath("/write/audit")
        .withBody(
          JsonPathBody.jsonPath(
            "$[?(" +
              "@.auditType=='validateBankDetails' " +
              "&& @.detail.length()==4" +
              ")]"
          )
        ),
      VerificationTimes.atLeast(1)
    )
  }

  "should not accept Surepay test credentials with default config" taggedAs (LocalTests, ZapTests) in {
    val requestBody = BankAccountRequest(SUREPAY_TEST_ACCOUNT)
    val response    = service.postValidateBankDetailsV3(requestBody)
    val actual      = Json.parse(response.body).as[ValidateBankDetailsV3]

    actual.accountNumberIsWellFormatted mustBe "indeterminate"
    actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
    actual.sortCodeIsPresentOnEISCD mustBe "no"
    response.status mustBe 200
  }

  "should receive accountNumberIsWellFormatted no if sort code is valid but account number fails mod check" taggedAs (LocalTests, ZapTests) in {
    val requestBody = BankAccountRequest(Account(Some("110010"), Some("29250490")))
    val response    = service.postValidateBankDetailsV3(requestBody)
    val actual      = Json.parse(response.body).as[ValidateBankDetailsV3]

    actual.accountNumberIsWellFormatted mustBe "no"
    actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
    actual.sortCodeIsPresentOnEISCD mustBe "yes"
    actual.sortCodeSupportsDirectDebit.get mustBe "yes"
    actual.sortCodeSupportsDirectCredit.get mustBe "yes"
    actual.sortCodeBankName.get mustBe "HALIFAX (A TRADING NAME OF BANK OF SCOTLAND PLC)"
    response.status mustBe 200

    mockServer.verify(500.millis)(
      HttpRequest
        .request()
        .withPath("/write/audit")
        .withBody(
          JsonPathBody.jsonPath(
            "$[?(" +
              "@.auditType=='validateBankDetails' " +
              "&& @.detail.length()==4" +
              ")]"
          )
        ),
      VerificationTimes.atLeast(1)
    )
  }

  "should receive a bad request when calling the validate endpoint with HMRC account details" taggedAs (LocalTests, ZapTests) in {
    val requestBody = BankAccountRequest(HMRC_ACCOUNT)
    val response    = service.postValidateBankDetailsV3(requestBody)
    val actual      = Json.parse(response.body).as[BadRequest]

    actual.code mustBe "SORT_CODE_ON_DENY_LIST"
    actual.desc mustBe s"${HMRC_ACCOUNT.sortCode.get}: sort code is on deny list. This usually means that it is an HMRC sort code."
    response.status mustBe 400
  }

  "Should return direct credit not supported if disallowed transactions contains CR" taggedAs (LocalTests, ZapTests) in {
    val requestBody = BankAccountRequest(NO_CR_ACCOUNT)
    val response    = service.postValidateBankDetailsV3(requestBody)
    val actual      = Json.parse(response.body).as[ValidateBankDetailsV3]

    actual.accountNumberIsWellFormatted mustBe "yes"
    actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
    actual.sortCodeIsPresentOnEISCD mustBe "yes"
    actual.sortCodeSupportsDirectDebit.get mustBe "yes"
    actual.sortCodeSupportsDirectCredit.get mustBe "no"
    actual.sortCodeBankName.get mustBe "BARCLAYS BANK PLC"
    response.status mustBe 200

    mockServer.verify(500.millis)(
      HttpRequest
        .request()
        .withPath("/write/audit")
        .withBody(
          JsonPathBody.jsonPath(
            "$[?(" +
              "@.auditType=='validateBankDetails' " +
              "&& @.detail.length()==4" +
              ")]"
          )
        ),
      VerificationTimes.atLeast(1)
    )
  }

  "Should return direct debit not supported if disallowed transactions contains AU" taggedAs (LocalTests, ZapTests) in {
    val requestBody = BankAccountRequest(NO_AU_ACCOUNT)
    val response    = service.postValidateBankDetailsV3(requestBody)
    val actual      = Json.parse(response.body).as[ValidateBankDetailsV3]

    actual.accountNumberIsWellFormatted mustBe "yes"
    actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
    actual.sortCodeIsPresentOnEISCD mustBe "yes"
    actual.sortCodeSupportsDirectDebit.get mustBe "no"
    actual.sortCodeSupportsDirectCredit.get mustBe "yes"
    actual.sortCodeBankName.get mustBe "BARCLAYS BANK PLC"
    response.status mustBe 200

    mockServer.verify(500.millis)(
      HttpRequest
        .request()
        .withPath("/write/audit")
        .withBody(
          JsonPathBody.jsonPath(
            "$[?(" +
              "@.auditType=='validateBankDetails' " +
              "&& @.detail.length()==4" +
              ")]"
          )
        ),
      VerificationTimes.atLeast(1)
    )
  }

  "Should return direct debit not supported if disallowed transactions contains DR" taggedAs (LocalTests, ZapTests) in {
    val requestBody = BankAccountRequest(NO_DR_ACCOUNT)
    val response    = service.postValidateBankDetailsV3(requestBody)
    val actual      = Json.parse(response.body).as[ValidateBankDetailsV3]

    actual.accountNumberIsWellFormatted mustBe "yes"
    actual.nonStandardAccountDetailsRequiredForBacs mustBe "no"
    actual.sortCodeIsPresentOnEISCD mustBe "yes"
    actual.sortCodeSupportsDirectDebit.get mustBe "no"
    actual.sortCodeSupportsDirectCredit.get mustBe "yes"
    actual.sortCodeBankName.get mustBe "BARCLAYS BANK PLC"
    response.status mustBe 200

    mockServer.verify(500.millis)(
      HttpRequest
        .request()
        .withPath("/write/audit")
        .withBody(
          JsonPathBody.jsonPath(
            "$[?(" +
              "@.auditType=='validateBankDetails' " +
              "&& @.detail.length()==4" +
              ")]"
          )
        ),
      VerificationTimes.atLeast(1)
    )
  }

  "should receive a forbidden request when calling the validate endpoint with HMRC account details" taggedAs (LocalTests, ZapTests) in {
    val requestBody = BankAccountRequest(Account(Some("110010"), Some("29250496")))
    val response    = service.postValidateBankDetailsV3WithUnkownUserAgent(requestBody)
    val actual      = Json.parse(response.body).as[Forbidden]

    response.status mustBe 403
    actual.code mustBe 403
    actual.description mustBe "'unknown' is not authorized to use the requested BARS endpoint. Please complete 'https://forms.office.com/Pages/ResponsePage.aspx?id=PPdSrBr9mkqOekokjzE54cRTj_GCzpRJqsT4amG0JK1UMkpBS1NUVDhWR041NjJWU0lCMVZUNk5NTi4u' to request access."
  }

}
