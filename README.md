# bank-account-reputation-api-tests
API test suite for the `bank-account-reputation` service using ScalaTest and [play-ws](https://github.com/playframework/play-ws) client.  

## Running the tests

Prior to executing the tests ensure you have:
 - Installed [MongoDB](https://docs.mongodb.com/manual/installation/) 
 - Installed/configured [service manager](https://github.com/hmrc/service-manager).  

Run the following commands to start services locally:

    docker run --rm -d -p 27017:27017 --name mongo percona/percona-server-mongodb:5.0

    sm2 --start BANK_ACCOUNT_REPUTATION_FRONTEND_SERVICES --appendArgs '{
      "BANK_ACCOUNT_REPUTATION": [
        "-Dplay.http.router=testOnlyDoNotUseInAppConf.Routes",
        "-Dmicroservice.services.modulr.protocol=http",
        "-Dmicroservice.services.modulr.host=localhost",
        "-Dmicroservice.services.modulr.port=6001",
        "-Dmicroservice.services.modulr.enabled=true",
        "-Dmicroservice.services.modulr.business.cache.enabled=false",
        "-Dmicroservice.services.modulr.personal.cache.enabled=false",
        "-Dauditing.consumer.baseUri.port=6001",
        "-Dauditing.consumer.baseUri.host=localhost",
        "-Dauditing.enabled=true",
        "-Dproxy.proxyRequiredForThisEnvironment=false",
        "-Dmicroservice.services.eiscd.aws.endpoint=http://0.0.0.0:6002",
        "-Dmicroservice.services.eiscd.aws.bucket=txm-dev-bacs-eiscd",
        "-Dmicroservice.services.eiscd.cache-schedule.initial-delay=86400",
        "-Dmicroservice.services.modcheck.cache-schedule.initial-delay=86400",
        "-Dmicroservice.services.thirdPartyCache.endpoint=http://localhost:9899/cache",
        "-Dmicroservice.services.access-control.endpoint.verify.enabled=true",
        "-Dmicroservice.services.access-control.endpoint.verify.allow-list.0=bars-acceptance-tests",
        "-Dmicroservice.services.access-control.endpoint.verify.allow-list.1=some-upstream-service",
        "-Dmicroservice.services.access-control.endpoint.verify.allow-list.2=bank-account-reputation-frontend",
        "-Dmicroservice.services.access-control.endpoint.verify.allow-list.3=bank-account-verification-frontend",
        "-Dmicroservice.services.access-control.endpoint.validate.enabled=true",
        "-Dmicroservice.services.access-control.endpoint.validate.allow-list.0=bars-acceptance-tests",
        "-Dmicroservice.services.access-control.endpoint.validate.allow-list.1=some-upstream-service",
        "-Dmicroservice.services.access-control.endpoint.validate.allow-list.2=bank-account-reputation-frontend",
        "-Dmicroservice.services.access-control.endpoint.validate.allow-list.3=bank-account-verification-frontend",
        "-Dmicroservice.services.modcheck.useLocal=true"
      ],
      "BANK_ACCOUNT_REPUTATION_THIRD_PARTY_CACHE": [
        "-Dcontrollers.confidenceLevel.uk.gov.hmrc.bankaccountreputationthirdpartycache.controllers.CacheController.needsLogging=true"
      ],
      "BANK_ACCOUNT_VERIFICATION_FRONTEND": [
        "-Dmicroservice.hosts.allowList.1=localhost",
        "-Dauditing.consumer.baseUri.port=6001",
        "-Dauditing.consumer.baseUri.host=localhost",
        "-Dauditing.enabled=true",
        "-Dmicroservice.services.access-control.enabled=true",
        "-Dmicroservice.services.access-control.allow-list.0=bavfe-acceptance-tests"
      ],
      "BANK_ACCOUNT_REPUTATION_FRONTEND": [
        "-Dauditing.enabled=true",
        "-Dauditing.consumer.baseUri.port=6001",
        "-Dauditing.consumer.baseUri.host=localhost"
      ]
    }'

There is also a `.start_services.sh` script that you can run to start the services with the required configuration.

Then execute the `run_tests.sh` script:

`./run_tests.sh <environment>`

The tests default to the `local` environment.  For a complete list of supported param values, see:
 - `src/test/resources/application.conf` for **environment** 

#### Running the tests against a test environment

To run the tests against an environment set the corresponding `host` environment property as specified under
 `<env>.host.services` in the [application.conf](src/test/resources/application.conf). 

## Scalafmt

Check all project files are formatted as expected as follows:

```bash
sbt scalafmtCheckAll scalafmtCheck
```

Format `*.sbt` and `project/*.scala` files as follows:

```bash
sbt scalafmtSbt
```

Format all project files as follows:

```bash
sbt scalafmtAll
```

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
