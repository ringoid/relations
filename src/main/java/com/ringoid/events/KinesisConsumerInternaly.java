package com.ringoid.events;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ringoid.Labels;
import com.ringoid.PersonProperties;
import com.ringoid.PhotoProperties;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Config;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class KinesisConsumerInternaly {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Driver driver;
    private final Gson gson;
    private final AmazonKinesis kinesis;
    private final String internalStreamName;
    private final AmazonSQS sqs;
    private final String botSqsQueueUrl;
    private final boolean botEnabled;

    private static final String NEO_URL = "http://54.194.22.99:7474/graphaware/actions";

    public KinesisConsumerInternaly() {
        GsonBuilder builder = new GsonBuilder();
        gson = builder.create();

        String env = System.getenv("ENV");
        String userName = System.getenv("NEO4J_USER");
        String neo4jUris = System.getenv("NEO4J_URIS");

        // Create a Secrets Manager client
        AWSSecretsManager client = AWSSecretsManagerClientBuilder.standard()
                .withRegion("eu-west-1")
                .build();

        GetSecretValueRequest getSecretValueRequest = new GetSecretValueRequest()
                .withSecretId(env + "/Neo4j/Password");
        GetSecretValueResult getSecretValueResult = null;

        try {
            getSecretValueResult = client.getSecretValue(getSecretValueRequest);
        } catch (Exception e) {
            log.error("error fetching secret", e);
            throw e;
        }

        String secret = getSecretValueResult.getSecretString();
        HashMap<String, String> map = gson.fromJson(secret, (new HashMap<String, String>()).getClass());
        String password = map.get("password");

        String[] arr = neo4jUris.split("&");
        log.info("there is a list of ips {}", arr);
        if (arr.length > 1) {
            List<URI> uris = new ArrayList<>();
            for (String each : arr) {
                uris.add(URI.create("bolt+routing://" + each + ":7687"));
            }
            driver = GraphDatabase.routingDriver(uris, AuthTokens.basic(userName, password),
                    Config.build().withMaxTransactionRetryTime(10, TimeUnit.SECONDS).toConfig());
        } else {
            driver = GraphDatabase.driver("bolt://" + arr[0] + ":7687", AuthTokens.basic(userName, password),
                    Config.build().withMaxTransactionRetryTime(10, TimeUnit.SECONDS).toConfig());
        }

        internalStreamName = System.getenv("INTERNAL_STREAM_NAME");

        botSqsQueueUrl = System.getenv("BOT_SQS_QUEUE_URL");

        botEnabled = Boolean.valueOf(System.getenv("BOTS_ENABLED"));


        AmazonKinesisClientBuilder clientBuilder = AmazonKinesisClientBuilder.standard().withRegion(Regions.EU_WEST_1);
        kinesis = clientBuilder.build();

        AmazonSQSClientBuilder sqsClientBuilder = AmazonSQSClientBuilder.standard().withRegion(Regions.EU_WEST_1);
        sqs = sqsClientBuilder.build();
    }

    private void createIndexes(Driver driver) {
        String createIndexQuery = "CREATE INDEX ON :%s(%s)";
        String userIdPersonIndexQuery = String.format(
                createIndexQuery, Labels.PERSON.getLabelName(), PersonProperties.USER_ID.getPropertyName()
        );
        String sexPersonIndexQuery = String.format(
                createIndexQuery, Labels.PERSON.getLabelName(), PersonProperties.SEX.getPropertyName()
        );
        String moderatePersonIndexQuery = String.format(
                createIndexQuery, Labels.PERSON.getLabelName(), PersonProperties.NEED_TO_MODERATE.getPropertyName()
        );
        String photoIdPhotoIndexQuery = String.format(
                createIndexQuery, Labels.PHOTO.getLabelName(), PhotoProperties.PHOTO_ID.getPropertyName()
        );
        String moderatePhotoIndexQuery = String.format(
                createIndexQuery, Labels.PHOTO.getLabelName(), PhotoProperties.NEED_TO_MODERATE.getPropertyName()
        );
        try (Session session = driver.session()) {
            session.run(userIdPersonIndexQuery);
            session.run(sexPersonIndexQuery);
            session.run(moderatePersonIndexQuery);
            session.run(photoIdPhotoIndexQuery);
            session.run(moderatePhotoIndexQuery);
        }
    }

    public void handler(KinesisEvent event, Context context) throws IOException {
//        createIndexes(driver);
        long start = System.currentTimeMillis();
        StringBuilder builder = new StringBuilder("{\"events\":[");
        for (KinesisEvent.KinesisEventRecord each : event.getRecords()) {
            ByteBuffer buff = each.getKinesis().getData();
            String s = StandardCharsets.UTF_8.decode(buff).toString();
            builder.append(s);
            builder.append(",");
        }
        builder.replace(builder.lastIndexOf(","), builder.lastIndexOf(",") + 1, "");
        builder.append("]}");

        HttpClient httpclient = HttpClients.createDefault();
        HttpPost httppost = new HttpPost(NEO_URL);
        StringEntity entity = new StringEntity(builder.toString(), ContentType.APPLICATION_JSON);
        httppost.setEntity(entity);
        //Execute and get the response.
        HttpResponse response = httpclient.execute(httppost);
        if (response.getStatusLine().getStatusCode() != 200) {
            log.error("status code {}, reason {}", response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
        } else {
            log.debug("successfully handle {} records from the stream in {}", event.getRecords().size(), System.currentTimeMillis() - start);
        }
    }

}
