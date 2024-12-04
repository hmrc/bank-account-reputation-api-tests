package uk.gov.hmrc.test.api.fixtures

import uk.gov.hmrc.test.api.model.request.components.Account

trait AccountFixtures {
  val DEFAULT_ACCOUNT: Account      = Account(Some("404784"), Some("70872490"))
  val HMRC_ACCOUNT: Account         = Account(Some("083210"), Some("12001039"))
  val SUREPAY_TEST_ACCOUNT: Account = Account(Some("999999"), Some("00000001"))

  val DEFAULT_COMPANY_REGISTRATION_NUMBER: Option[String] = Some("UK27318156")
}
