import sbt.*

object Dependencies {

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "api-test-runner"          % "0.10.0",
    "org.slf4j"               % "slf4j-api"                 % "2.0.17",
    "software.amazon.awssdk"  % "s3"                        % "2.32.10",
    "org.mock-server"         % "mockserver-netty"          % "5.12.0",
    "io.swagger.parser.v3"    % "swagger-parser"            % "2.1.18",
    "org.openapi4j"           % "openapi-schema-validator"  % "1.0.7",
    "org.bouncycastle"        % "bcprov-jdk18on"            % "1.81",
    "org.bouncycastle"        % "bcpkix-jdk18on"            % "1.81"
  ).map(_ % Test)
}
