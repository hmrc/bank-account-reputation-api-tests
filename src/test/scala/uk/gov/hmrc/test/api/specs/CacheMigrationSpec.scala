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

import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import play.api.libs.json.Json
import uk.gov.hmrc.api.BaseSpec
import uk.gov.hmrc.test.api.model.request.PersonalRequest
import uk.gov.hmrc.test.api.model.request.components.{Account, Address, Subject}
import uk.gov.hmrc.test.api.model.response.AssessV4
import uk.gov.hmrc.test.api.tags.LocalTests
import uk.gov.hmrc.test.api.utils.MockServer

import java.util.UUID

class CacheMigrationSpec extends BaseSpec with MockServer {

  "Confirmation of Payee third party cache" must {

    "Handle a mix of legacy and new cache entries" taggedAs LocalTests in {
      val xRequestId: String = UUID.randomUUID().toString
      val address            = Address(Some(Array("7 Skyline Avenue")), Some("X9 9AG"))

      service.postCacheMigrationtestSetup(xRequestId)

      val request1 = PersonalRequest(
        Account(Some("404784"), Some("70872490")),
        Subject(name = Some("Bob Smith"), address = Some(address))
      )
      val request2 = PersonalRequest(
        Account(Some("404784"), Some("70872490")),
        Subject(name = Some("Jan Smith"), address = Some(address))
      )

      val response1 = service.postVerifyPersonal(request1, xRequestId)
      val response2 = service.postVerifyPersonal(request2, xRequestId)

      val actual1 = Json.parse(response1.body).as[AssessV4]
      val actual2 = Json.parse(response2.body).as[AssessV4]

      actual1.accountExists shouldBe "yes"
      actual2.accountExists shouldBe "yes"
    }
  }
}
