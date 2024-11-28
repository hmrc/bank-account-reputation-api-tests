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

package uk.gov.hmrc.test.api.service

import com.typesafe.config.{Config, ConfigFactory}
import uk.gov.hmrc.test.api.client.HttpClient
import uk.gov.hmrc.test.api.conf.TestConfiguration
import uk.gov.hmrc.test.api.model.request.{BankAccountRequest, BusinessRequest, PersonalRequest}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class BankAccountReputationService extends HttpClient {

  val config: Config   = ConfigFactory.load()
  val defaultUserAgent = "bars-acceptance-tests"
  val host: String     = TestConfiguration.url("bank-account-reputation")

  object BarsEndpoints {
    val PERSONAL_ASSESS_V1         = "assess"
    val PERSONAL_ASSESS_V2         = "v2/assess"
    val PERSONAL_ASSESS_V3         = "personal/v3/assess"
    val BUSINESS_ASSESS_V1         = "business/v1/assess"
    val BUSINESS_ASSESS_V2         = "business/v2/assess"
    val VALIDATE_BANK_DETAILS_V3   = "validate/bank-details"
    val REFRESH_EISCD_CACHE        = "refresh/cache/eiscd"
    val REFRESH_MODCHECK_CACHE     = "refresh/cache/modcheck"
    val VERIFY_BUSINESS            = "verify/business"
    val VERIFY_PERSONAL            = "verify/personal"
    val CACHE_MIGRATION_TEST_SETUP = "test-only/cache-migration-test/setup"
  }

  def withHost(endpoint: String) = s"$host/$endpoint"

  object HeaderNames {
    val xRequestId        = "X-Request-ID"
    val trueCallingClient = "True-Calling-Client"
    val userAgent         = "User-Agent"
    val contentType       = "Content-Type"
  }

  val applicationJson = "application/json"

  def postPersonalAssessV3(requestBody: PersonalRequest, xRequestId: String) =
    Await.result(
      post(
        withHost(BarsEndpoints.PERSONAL_ASSESS_V3),
        requestBody.asJsonString(),
        HeaderNames.contentType -> applicationJson,
        HeaderNames.xRequestId  -> xRequestId,
        HeaderNames.userAgent   -> defaultUserAgent
      ),
      10.seconds
    )

  def postPersonalAssessV3(requestBody: String, xRequestId: String) =
    Await.result(
      post(
        withHost(BarsEndpoints.PERSONAL_ASSESS_V3),
        requestBody,
        HeaderNames.contentType -> applicationJson,
        HeaderNames.xRequestId  -> xRequestId,
        HeaderNames.userAgent   -> defaultUserAgent
      ),
      10.seconds
    )

  def postPersonalAssessV3WithTrueCallingClient(
    requestBody: PersonalRequest,
    xRequestId: String,
    trueCallingClient: String
  ) =
    Await.result(
      post(
        withHost(BarsEndpoints.PERSONAL_ASSESS_V3),
        requestBody.asJsonString(),
        HeaderNames.contentType       -> applicationJson,
        HeaderNames.xRequestId        -> xRequestId,
        HeaderNames.userAgent         -> defaultUserAgent,
        HeaderNames.trueCallingClient -> trueCallingClient
      ),
      10.seconds
    )

  def postPersonalAssessV3WithUnkownUserAgent(requestBody: PersonalRequest, xRequestId: String) =
    Await.result(
      post(
        withHost(BarsEndpoints.PERSONAL_ASSESS_V3),
        requestBody.asJsonString(),
        HeaderNames.contentType -> applicationJson,
        HeaderNames.xRequestId  -> xRequestId,
        HeaderNames.userAgent   -> "unknown"
      ),
      10.seconds
    )

  def postBusinessAssessV2(requestBody: BusinessRequest, xRequestId: String = "") =
    Await.result(
      post(
        withHost(BarsEndpoints.BUSINESS_ASSESS_V2),
        requestBody.asJsonString(),
        HeaderNames.contentType -> applicationJson,
        HeaderNames.xRequestId  -> xRequestId,
        HeaderNames.userAgent   -> defaultUserAgent
      ),
      10.seconds
    )

  def postBusinessAssessV2WithTrueCallingClient(
    requestBody: BusinessRequest,
    xRequestId: String,
    trueCallingClient: String
  ) =
    Await.result(
      post(
        withHost(BarsEndpoints.BUSINESS_ASSESS_V2),
        requestBody.asJsonString(),
        HeaderNames.contentType       -> applicationJson,
        HeaderNames.xRequestId        -> xRequestId,
        HeaderNames.userAgent         -> defaultUserAgent,
        HeaderNames.trueCallingClient -> trueCallingClient
      ),
      10.seconds
    )

  def postBusinessAssessV2WithUnkownUserAgent(requestBody: BusinessRequest, xRequestId: String = "") =
    Await.result(
      post(
        withHost(BarsEndpoints.BUSINESS_ASSESS_V2),
        requestBody.asJsonString(),
        HeaderNames.contentType -> applicationJson,
        HeaderNames.xRequestId  -> xRequestId,
        HeaderNames.userAgent   -> "unknown"
      ),
      10.seconds
    )

  def postValidateBankDetailsV3(requestBody: BankAccountRequest, xRequestId: String = "") =
    Await.result(
      post(
        withHost(BarsEndpoints.VALIDATE_BANK_DETAILS_V3),
        requestBody.asJsonString(),
        HeaderNames.contentType -> applicationJson,
        HeaderNames.xRequestId  -> xRequestId,
        HeaderNames.userAgent   -> defaultUserAgent
      ),
      10.seconds
    )

  def postValidateBankDetailsV3WithUnkownUserAgent(requestBody: BankAccountRequest, xRequestId: String = "") =
    Await.result(
      post(
        withHost(BarsEndpoints.VALIDATE_BANK_DETAILS_V3),
        requestBody.asJsonString(),
        HeaderNames.contentType -> applicationJson,
        HeaderNames.xRequestId  -> xRequestId,
        HeaderNames.userAgent   -> "unknown"
      ),
      10.seconds
    )

  def postVerifyPersonal(request: PersonalRequest, xRequestId: String = "") =
    Await.result(
      post(
        withHost(BarsEndpoints.VERIFY_PERSONAL),
        request.asJsonString(),
        HeaderNames.contentType -> applicationJson,
        HeaderNames.xRequestId  -> xRequestId,
        HeaderNames.userAgent   -> defaultUserAgent
      ),
      10.seconds
    )

  def postVerifyPersonal(request: String, xRequestId: String) =
    Await.result(
      post(
        withHost(BarsEndpoints.VERIFY_PERSONAL),
        request,
        HeaderNames.contentType -> applicationJson,
        HeaderNames.xRequestId  -> xRequestId,
        HeaderNames.userAgent   -> defaultUserAgent
      ),
      10.seconds
    )

  def postVerifyPersonalWithTrueCallingClient(request: PersonalRequest, xRequestId: String, trueCallingClient: String) =
    Await.result(
      post(
        withHost(BarsEndpoints.VERIFY_PERSONAL),
        request.asJsonString(),
        HeaderNames.contentType       -> applicationJson,
        HeaderNames.xRequestId        -> xRequestId,
        HeaderNames.userAgent         -> defaultUserAgent,
        HeaderNames.trueCallingClient -> trueCallingClient
      ),
      10.seconds
    )

  def postVerifyPersonalWithUnknownUserAgent(request: PersonalRequest, xRequestId: String = "") =
    Await.result(
      post(
        withHost(BarsEndpoints.VERIFY_PERSONAL),
        request.asJsonString(),
        HeaderNames.contentType -> applicationJson,
        HeaderNames.xRequestId  -> xRequestId,
        HeaderNames.userAgent   -> "unknown"
      ),
      10.seconds
    )

  def postVerifyBusiness(request: BusinessRequest, xRequestId: String = "") =
    Await.result(
      post(
        withHost(BarsEndpoints.VERIFY_BUSINESS),
        request.asJsonString(),
        HeaderNames.contentType -> applicationJson,
        HeaderNames.xRequestId  -> xRequestId,
        HeaderNames.userAgent   -> defaultUserAgent
      ),
      10.seconds
    )

  def postVerifyBusiness(request: String, xRequestId: String) =
    Await.result(
      post(
        withHost(BarsEndpoints.VERIFY_BUSINESS),
        request,
        HeaderNames.contentType -> applicationJson,
        HeaderNames.xRequestId  -> xRequestId,
        HeaderNames.userAgent   -> defaultUserAgent
      ),
      10.seconds
    )

  def postVerifyBusinessWithTrueCallingClient(request: BusinessRequest, xRequestId: String, trueCallingClient: String) =
    Await.result(
      post(
        withHost(BarsEndpoints.VERIFY_BUSINESS),
        request.asJsonString(),
        HeaderNames.contentType       -> applicationJson,
        HeaderNames.xRequestId        -> xRequestId,
        HeaderNames.userAgent         -> defaultUserAgent,
        HeaderNames.trueCallingClient -> trueCallingClient
      ),
      10.seconds
    )

  def postVerifyBusinessWithUnknownUserAgent(request: BusinessRequest, xRequestId: String = "") =
    Await.result(
      post(
        withHost(BarsEndpoints.VERIFY_BUSINESS),
        request.asJsonString(),
        HeaderNames.contentType -> applicationJson,
        HeaderNames.xRequestId  -> xRequestId,
        HeaderNames.userAgent   -> "unknown"
      ),
      10.seconds
    )

  def postCacheMigrationtestSetup(xRequestId: String) =
    Await.result(
      post(
        withHost(BarsEndpoints.CACHE_MIGRATION_TEST_SETUP),
        "",
        HeaderNames.contentType -> applicationJson,
        HeaderNames.xRequestId  -> xRequestId
      ),
      10.seconds
    )

  def postRefreshEiscdCache() =
    Await.result(
      post(withHost(BarsEndpoints.REFRESH_EISCD_CACHE), "", HeaderNames.contentType -> applicationJson),
      60.seconds
    )

  def postRefreshModcheckCache() =
    Await.result(
      post(withHost(BarsEndpoints.REFRESH_MODCHECK_CACHE), "", HeaderNames.contentType -> applicationJson),
      60.seconds
    )

  //
//  def sendRequest(
//    endpoint: String,
//    payload: String,
//    mediaType: MediaType = MediaType.parse("application/json"),
//    httpVerb: String = HttpVerbs.POST,
//    xRequestId: Option[String] = None,
//    xTrackingId: Option[String] = None,
//    trueCallingClient: Option[String] = None,
//    userAgent: String = defaultUserAgent
//  ): Result = {
//    val environmentUrl: String = TestConfiguration.url(Service.BARS)
//    val request                = new Request.Builder()
//    request.url(s"$environmentUrl/$endpoint")
//    request.method(httpVerb, RequestBody.create(mediaType, payload))
//    request.addHeader("User-Agent", userAgent)
//    if (xRequestId.isDefined) {
//      request.addHeader("X-Request-ID", xRequestId.get)
//    }
//    if (xTrackingId.isDefined) {
//      request.addHeader("X-Tracking-ID", xTrackingId.get)
//    }
//    if (trueCallingClient.isDefined) {
//      request.addHeader("True-Calling-Client", trueCallingClient.get)
//    }
//
//    val response = client.newCall(request.build()).execute()
//    val result   = Result(response.body().string(), response.code())
//    response.close()
//    result
//  }

}
