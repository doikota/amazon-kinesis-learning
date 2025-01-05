/*
 * Copyright 2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Amazon Software License (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 * http://aws.amazon.com/asl/
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazonaws.services.kinesis.samples.stocktrades.processor;

import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.kinesis.common.ConfigsBuilder;
import software.amazon.kinesis.coordinator.Scheduler;

/**
 * Uses the Kinesis Client Library (KCL) 2.2.9 to continuously consume and process stock trade
 * records from the stock trades stream. KCL monitors the number of shards and creates
 * record processor instances to read and process records from each shard. KCL also
 * load balances shards across all the instances of this processor.
 *
 */
public class StockTradesProcessor {

	private static final Logger LOG = LoggerFactory.getLogger(StockTradesProcessor.class);

    private static void checkUsage(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: " + StockTradesProcessor.class.getSimpleName()
                    + " <application name> <stream name> <region>");
            System.exit(1);
        }
    }

    public static void main(String[] args) throws Exception {
        checkUsage(args);
        String applicationName = args[0];
        String streamName = args[1];
        Region region = Region.of(args[2]);

        if (region == null) {
            System.err.println(args[2] + " is not a valid AWS region.");
            System.exit(1);
        }

        // Netty HTTP クライアントのカスタム設定
        SdkAsyncHttpClient httpClient = NettyNioAsyncHttpClient.builder()
                .maxConcurrency(100)  // 最大同時接続数を設定
                .connectionAcquisitionTimeout(Duration.ofSeconds(10))  // 接続取得待ち時間
                .connectionTimeout(Duration.ofSeconds(10))  // 接続確立の最大待機時間
                .readTimeout(Duration.ofSeconds(60))  // 読み込みタイムアウト
                .writeTimeout(Duration.ofSeconds(30))  // 書き込みタイムアウト
                .build();        
        // Kinesis クライアントの作成
        KinesisAsyncClient kinesisClient = KinesisAsyncClient.builder()
                .region(Region.AP_NORTHEAST_1)
                .httpClient(httpClient)  // これを指定
                .build();
        
		DynamoDbAsyncClient dynamoClient = DynamoDbAsyncClient.builder().region(region).build();
		CloudWatchAsyncClient cloudWatchClient = CloudWatchAsyncClient.builder().region(region).build();
		StockTradeRecordProcessorFactory shardRecordProcessor = new StockTradeRecordProcessorFactory();
		ConfigsBuilder configsBuilder = new ConfigsBuilder(streamName, applicationName, kinesisClient, dynamoClient,
				cloudWatchClient, UUID.randomUUID().toString(), shardRecordProcessor);

        Scheduler scheduler = new Scheduler(
                configsBuilder.checkpointConfig(),
                configsBuilder.coordinatorConfig(),
                configsBuilder.leaseManagementConfig(),
                configsBuilder.lifecycleConfig(),
                configsBuilder.metricsConfig(),
                configsBuilder.processorConfig(),
                configsBuilder.retrievalConfig()
        );
        int exitCode = 0;
        try {
            scheduler.run();
        } catch (Throwable t) {
            LOG.error("Caught throwable while processing data.", t);
            exitCode = 1;
        }
        System.exit(exitCode);

    }

}
