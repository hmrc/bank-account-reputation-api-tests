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

package uk.gov.hmrc.test.api.model.response

import play.api.libs.json.{Json, OFormat}

case class BusinessV2(
  accountNumberWithSortCodeIsValid: String,
  sortCodeIsPresentOnEISCD: String,
  sortCodeBankName: Option[String] = None,
  nonStandardAccountDetailsRequiredForBacs: String,
  accountExists: String,
  companyNameMatches: String,
  companyPostCodeMatches: String,
  companyRegistrationNumberMatches: String,
  sortCodeSupportsDirectDebit: String,
  sortCodeSupportsDirectCredit: String
)

object BusinessV2 {
  implicit val responseJsonFormat: OFormat[BusinessV2] = Json.format[BusinessV2]
}
