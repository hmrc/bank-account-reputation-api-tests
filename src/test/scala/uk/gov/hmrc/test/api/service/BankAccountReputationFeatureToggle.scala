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

import uk.gov.hmrc.test.api.client.HttpClient
import uk.gov.hmrc.test.api.conf.TestConfiguration

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

trait BankAccountReputationFeatureToggle extends HttpClient {

  lazy val featureToggleHost: String = TestConfiguration.url("bank-account-reputation")

  private def postToFeatureToggle(configName: String, isEnabled: Boolean = true): Unit =
    Await.result(
      post(
        s"$featureToggleHost/bank-account-reputation/test-only/api/feature-switches",
        s"""[{"configName": "$configName", "displayName": "", "isEnabled": $isEnabled}]""",
        "Content-Type" -> "application/json"
      ),
      10.seconds
    )

  def enableSurePay(): Unit               =
    postToFeatureToggle("microservice.services.surepay.enabled")
  def disableSurePay(): Unit              =
    postToFeatureToggle("microservice.services.surepay.enabled", isEnabled = false)
  def enableSurePayBusinessCache(): Unit  =
    postToFeatureToggle("microservice.services.surepay.business.cache.enabled")
  def disableSurePayBusinessCache(): Unit =
    postToFeatureToggle("microservice.services.surepay.business.cache.enabled", isEnabled = false)
  def enableSurePayPersonalCache(): Unit  =
    postToFeatureToggle("microservice.services.surepay.personal.cache.enabled")
  def disableSurePayPersonalCache(): Unit =
    postToFeatureToggle("microservice.services.surepay.personal.cache.enabled", isEnabled = false)

  def enableModulr(): Unit               =
    postToFeatureToggle("microservice.services.modulr.enabled")
  def disableModulr(): Unit              =
    postToFeatureToggle("microservice.services.modulr.enabled", isEnabled = false)
  def enableModulrBusinessCache(): Unit  =
    postToFeatureToggle("microservice.services.modulr.business.cache.enabled")
  def disableModulrBusinessCache(): Unit =
    postToFeatureToggle("microservice.services.modulr.business.cache.enabled", isEnabled = false)
  def enableModulrPersonalCache(): Unit  =
    postToFeatureToggle("microservice.services.modulr.personal.cache.enabled")
  def disableModulrPersonalCache(): Unit =
    postToFeatureToggle("microservice.services.modulr.personal.cache.enabled", isEnabled = false)

  def enableSurePayResponses(): Unit =
    postToFeatureToggle("microservice.services.modulr.returnResults", isEnabled = false)
  def enableModulrResponses(): Unit  =
    postToFeatureToggle("microservice.services.modulr.returnResults")
}
