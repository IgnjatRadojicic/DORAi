package dorai.server.collect.aws

import aws.sdk.kotlin.services.dynamodb.DynamoDbClient
import aws.sdk.kotlin.services.dynamodb.describeTable
import aws.sdk.kotlin.services.dynamodb.listTagsOfResource
import aws.sdk.kotlin.services.dynamodb.paginators.listTablesPaginated
import aws.sdk.kotlin.services.lambda.LambdaClient
import aws.sdk.kotlin.services.lambda.listTags
import aws.sdk.kotlin.services.lambda.paginators.listFunctionsPaginated
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.getBucketLocation
import aws.sdk.kotlin.services.s3.getBucketTagging
import aws.sdk.kotlin.services.s3.listBuckets
import aws.sdk.kotlin.services.s3.model.S3Exception
import aws.sdk.kotlin.services.sqs.SqsClient
import aws.sdk.kotlin.services.sqs.getQueueAttributes
import aws.sdk.kotlin.services.sqs.listQueueTags
import aws.sdk.kotlin.services.sqs.model.QueueAttributeName
import aws.sdk.kotlin.services.sqs.paginators.listQueuesPaginated
import aws.smithy.kotlin.runtime.net.url.Url
import dorai.server.collect.LiveConfig
import dorai.server.core.CloudCollector
import dorai.server.core.CollectResult
import dorai.shared.model.Resource
import dorai.shared.model.ScanMode
import dorai.shared.rules.ServiceTypeTable
import kotlinx.coroutines.flow.toList

/**
 * Demo only: LocalStack (DORAI_AWS_ENDPOINT). Free LocalStack returns nothing from the Tagging API, so the
 * services the FinPay demo uses (S3, DynamoDB, Lambda, SQS) are listed one by one with their own tag calls.
 */
class LocalStackCollector(config: LiveConfig) : CloudCollector {
    override val provider = ServiceTypeTable.AWS

    private val endpoint = Url.parse(requireNotNull(config.awsEndpoint) { "LocalStackCollector needs DORAI_AWS_ENDPOINT." })
    private val region = config.awsRegions.first()

    override suspend fun collect() = CollectResult(s3() + dynamoDb() + lambda() + sqs(), ScanMode.Live)

    private suspend fun s3(): List<Resource> =
        S3Client.fromEnvironment { region = this@LocalStackCollector.region; endpointUrl = endpoint; forcePathStyle = true }.use { s3 ->
            s3.listBuckets {}.buckets.orEmpty().mapNotNull { it.name }.map { bucket ->
                // An empty location constraint means us-east-1, S3's historical default.
                val location = s3.getBucketLocation { this.bucket = bucket }.locationConstraint?.value?.ifEmpty { null } ?: "us-east-1"
                val tags = try {
                    s3.getBucketTagging { this.bucket = bucket }.tagSet.associate { it.key to it.value }
                } catch (e: S3Exception) {
                    if (e.sdkErrorMetadata.errorCode == "NoSuchTagSet") emptyMap() else throw e
                }
                AwsArns.toResource("arn:aws:s3:::$bucket", tags, location)
            }
        }

    private suspend fun dynamoDb(): List<Resource> =
        DynamoDbClient.fromEnvironment { region = this@LocalStackCollector.region; endpointUrl = endpoint }.use { ddb ->
            ddb.listTablesPaginated {}.toList().flatMap { it.tableNames.orEmpty() }.mapNotNull { name ->
                val arn = ddb.describeTable { tableName = name }.table?.tableArn ?: return@mapNotNull null
                val tags = ddb.listTagsOfResource { resourceArn = arn }.tags.orEmpty().associate { it.key to it.value }
                AwsArns.toResource(arn, tags, region)
            }
        }

    private suspend fun lambda(): List<Resource> =
        LambdaClient.fromEnvironment { region = this@LocalStackCollector.region; endpointUrl = endpoint }.use { lambda ->
            lambda.listFunctionsPaginated {}.toList().flatMap { it.functions.orEmpty() }.mapNotNull { f ->
                val arn = f.functionArn ?: return@mapNotNull null
                AwsArns.toResource(arn, lambda.listTags { resource = arn }.tags.orEmpty(), region)
            }
        }

    private suspend fun sqs(): List<Resource> =
        SqsClient.fromEnvironment { region = this@LocalStackCollector.region; endpointUrl = endpoint }.use { sqs ->
            sqs.listQueuesPaginated {}.toList().flatMap { it.queueUrls.orEmpty() }.mapNotNull { url ->
                val arn = sqs.getQueueAttributes { queueUrl = url; attributeNames = listOf(QueueAttributeName.QueueArn) }
                    .attributes?.get(QueueAttributeName.QueueArn) ?: return@mapNotNull null
                AwsArns.toResource(arn, sqs.listQueueTags { queueUrl = url }.tags.orEmpty(), region)
            }
        }
}
