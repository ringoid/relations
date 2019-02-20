package com.ringoid.events;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class KinesisConsumer {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final String NEO_URL = "http://54.194.22.99:7474/graphaware/actions";

    public KinesisConsumer() {
        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();

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
    }

    public void handler(KinesisEvent event, Context context) throws IOException {
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
