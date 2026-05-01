/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.test.api.utils

import org.scalatest.{BeforeAndAfterAll, TestSuite}
import org.slf4j.{Logger, LoggerFactory}
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{BucketAlreadyExistsException, BucketAlreadyOwnedByYouException, CreateBucketRequest, PutObjectRequest}
import uk.gov.hmrc.test.api.conf.TestConfiguration

import java.io.File
import java.net.URI
import scala.util.{Failure, Success, Try}

trait S3Helper extends BeforeAndAfterAll { self: TestSuite =>

  lazy val logger: Logger = LoggerFactory.getLogger(getClass.getName)

  lazy val s3Port: Int         = TestConfiguration.config.getInt("aws.s3.port")
  lazy val accessKeyId: String = TestConfiguration.config.getString("aws.s3.accessKeyId")
  lazy val secretKey: String   = TestConfiguration.config.getString("aws.s3.secretKey")
  lazy val region: String      = TestConfiguration.config.getString("aws.s3.region")

  lazy val s3Client: S3Client = S3Client
    .builder()
    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretKey)))
    .region(Region.of(region))
    .endpointOverride(URI.create(s"http://localhost:$s3Port"))
    .forcePathStyle(true)
    .build()

  def uploadFilesToS3(): Unit = {

    val resourcePath = getClass.getResource("/sThreeBucket").getFile
    new File(resourcePath).listFiles.filter(_.isDirectory).foreach { subDir =>
      val bucketName = subDir.getName

      Try(s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build())) match {
        case Success(_)                                                                              => ()
        case Failure(_: BucketAlreadyOwnedByYouException) | Failure(_: BucketAlreadyExistsException) =>
          logger.info(s"Bucket '$bucketName' already exists.")
        case Failure(e)                                                                              =>
          logger.error(s"Failed to create bucket '$bucketName': [${e.getClass.getSimpleName}]${e.getMessage}")
      }

      subDir.listFiles.filter(_.isFile).foreach { file =>
        val putObjectRequest = PutObjectRequest
          .builder()
          .bucket(bucketName)
          .key(file.getName)
          .build()

        s3Client.putObject(putObjectRequest, RequestBody.fromFile(file))
      }
    }
  }
}
