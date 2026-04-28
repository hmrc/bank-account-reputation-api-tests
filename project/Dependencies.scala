import sbt.*

object Dependencies {

  private val bouncyCastleVersion = "1.81"

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"           %% "api-test-runner"          % "0.10.0",
    "org.slf4j"              % "slf4j-api"                % "2.0.17",
    "software.amazon.awssdk" % "s3"                       % "2.32.20",
    "org.mock-server"        % "mockserver-netty"         % "5.15.0",
    "io.swagger.parser.v3"   % "swagger-parser"           % "2.1.31",
    "org.openapi4j"          % "openapi-schema-validator" % "1.0.7",
    "org.bouncycastle"       % "bcprov-jdk18on"           % bouncyCastleVersion,
    "org.bouncycastle"       % "bcpkix-jdk18on"           % bouncyCastleVersion
  ).map(_ % Test)
}
