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
     "-J-Dapplication.router=testOnlyDoNotUseInAppConf.Routes",
     "-J-Dmicroservice.services.callvalidate.endpoint=http://localhost:6001/callvalidateapi",
     "-J-Dmicroservice.services.surepay.hostname=http://localhost:6001/surepay/",
     "-J-Dmicroservice.services.surepay.enabled=true",
     "-J-Dauditing.consumer.baseUri.port=6001",
     "-J-Dauditing.consumer.baseUri.host=localhost",
     "-J-Dauditing.enabled=true",
     "-J-Dproxy.proxyRequiredForThisEnvironment=false",
     "-J-Dmicroservice.services.eiscd.aws.endpoint=http://localhost:6002",
     "-J-Dmicroservice.services.eiscd.aws.bucket=txm-dev-bacs-eiscd",
     "-J-Dmicroservice.services.eiscd.aws.accesskeyid=AKIAIOSFODNN7EXAMPLE",
     "-J-Dmicroservice.services.eiscd.aws.secretkey=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
     "-J-Dmicroservice.services.modcheck.aws.endpoint=http://localhost:6002",
     "-J-Dmicroservice.services.modcheck.aws.bucket=txm-dev-bacs-modcheck",
     "-J-Dmicroservice.services.modcheck.aws.accesskeyid=AKIAIOSFODNN7EXAMPLE",
     "-J-Dmicroservice.services.modcheck.aws.secretkey=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
     "-J-Dmicroservice.services.thirdPartyCache.endpoint=http://localhost:9899/cache",
     "-J-Dmicroservice.services.surepay.cache.enabled=true",
     "-J-Dmicroservice.services.access-control.endpoint.verify.enabled=true",
     "-J-Dmicroservice.services.access-control.endpoint.verify.allow-list.0=bars-acceptance-tests",
     "-J-Dmicroservice.services.access-control.endpoint.verify.allow-list.1=some-upstream-service",
     "-J-Dmicroservice.services.access-control.endpoint.verify.allow-list.2=bank-account-reputation-frontend",
     "-J-Dmicroservice.services.access-control.endpoint.validate.enabled=true",
     "-J-Dmicroservice.services.access-control.endpoint.validate.allow-list.0=bars-acceptance-tests",
     "-J-Dmicroservice.services.access-control.endpoint.validate.allow-list.1=some-upstream-service",
     "-J-Dmicroservice.services.access-control.endpoint.validate.allow-list.2=bank-account-reputation-frontend"
     ],
     "BANK_ACCOUNT_REPUTATION_THIRD_PARTY_CACHE": [
     "-J-Dcontrollers.confidenceLevel.uk.gov.hmrc.bankaccountreputationthirdpartycache.controllers.CacheController.needsLogging=true"
     ],
     "BANK_ACCOUNT_REPUTATION_FRONTEND": [
     "-J-Dauditing.enabled=true",
     "-J-Dauditing.consumer.baseUri.port=6001",
     "-J-Dauditing.consumer.baseUri.host=localhost"
     ]
     }'

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
