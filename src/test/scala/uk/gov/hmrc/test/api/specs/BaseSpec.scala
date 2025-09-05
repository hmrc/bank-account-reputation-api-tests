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

package uk.gov.hmrc.api

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import uk.gov.hmrc.test.api.service.BankAccountReputationService
import uk.gov.hmrc.test.api.utils.S3Helper

trait BaseSpec extends AnyWordSpec with BeforeAndAfterEach with Matchers with BeforeAndAfterAll with S3Helper {

  val service     = new BankAccountReputationService
  val MODULR_PATH = "/api-sandbox-token/account-name-check"

  private val random = new java.util.Random()

  override def beforeAll(): Unit = {
    uploadFilesToS3()
    service.postRefreshEiscdCache()
    service.postRefreshModcheckCache()
  }

  def randomAlphaChar(): Char = {
    val low  = 97
    val high = 122
    (random.nextInt(high - low) + low).toChar
  }

  def randomString(length: Int = 5): String =
    (0 until length).map(_ => randomAlphaChar()).mkString

  def generateRandomName: Option[String] =
    Some(s"Mr Na${randomString()} ${randomString()} ${randomString(8)}-Smith")

  def generateRandomBusinessName: Option[String] =
    Some(s"O'C${randomString()} ${randomString(8)} ${randomString(4)} LLC")
}
