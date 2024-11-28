import sbt.*

object Dependencies {

  val test = Seq(
    "org.scalatest"       %% "scalatest"                % "3.2.15" % Test,
    "com.vladsch.flexmark" % "flexmark-all"             % "0.62.2" % Test,
    "com.typesafe"         % "config"                   % "1.3.2"  % Test,
    "com.typesafe.play"   %% "play-ahc-ws-standalone"   % "2.1.10" % Test,
    "org.slf4j"            % "slf4j-api"                % "2.0.16" % Test,
    "ch.qos.logback"       % "logback-classic"          % "1.5.12" % Test,
    "com.typesafe.play"   %% "play-ws-standalone-json"  % "2.1.10" % Test,
    "io.findify"          %% "s3mock"                   % "0.2.6"  % Test,
    "org.mock-server"      % "mockserver-netty"         % "5.12.0" % Test,
    "io.swagger.parser.v3" % "swagger-parser"           % "2.1.18" % Test,
    "org.openapi4j"        % "openapi-schema-validator" % "1.0.7"  % Test
  )
}
