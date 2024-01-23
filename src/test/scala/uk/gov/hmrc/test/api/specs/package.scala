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

package uk.gov.hmrc.test.api

import org.mockserver.client.MockServerClient
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.RequestDefinition
import org.mockserver.verify.VerificationTimes

import scala.concurrent.duration.{DurationInt, FiniteDuration}

package object specs {
  implicit class ClientAndServerExt(clientServer: ClientAndServer) {
    def verify(
      duration: FiniteDuration = 500.millis
    )(requestDefinition: RequestDefinition, times: VerificationTimes): MockServerClient =
      delayedFunction(duration)(clientServer.verify(requestDefinition, times))
    private def delayedFunction[T](duration: FiniteDuration)(f: => T): T = {
      Thread.sleep(duration.toMillis)
      f
    }
  }
}
