package uk.gov.hmrc.test.api.fixtures

trait ModulrFixtures {
  def matchResponseAsJsonWith(resultCode: String, resultName: Option[String] = None): String =
    if (resultName.isDefined) {
      s"""{
         |  "id": "C12001569Z",
         |  "result": {
         |    "code": "$resultCode",
         |    "name": "${resultName.get}"
         |  }
         |}""".stripMargin
    } else {
      s"""{
         |  "id": "C12001569Z",
         |  "result": {
         |    "code": "$resultCode"
         |  }
         |}""".stripMargin
    }
}
