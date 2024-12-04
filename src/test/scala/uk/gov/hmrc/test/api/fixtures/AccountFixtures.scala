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

package uk.gov.hmrc.test.api.fixtures

import uk.gov.hmrc.test.api.model.request.components.Account

trait AccountFixtures {
  val DEFAULT_ACCOUNT: Account      = Account(Some("404784"), Some("70872490"))
  val HMRC_ACCOUNT: Account         = Account(Some("083210"), Some("12001039"))
  val SUREPAY_TEST_ACCOUNT: Account = Account(Some("999999"), Some("00000001"))

  val DEFAULT_COMPANY_REGISTRATION_NUMBER: Option[String] = Some("UK27318156")
}
