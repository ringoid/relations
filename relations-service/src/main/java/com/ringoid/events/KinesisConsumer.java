package com.ringoid.events;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class KinesisConsumer {
    private static final String EXTENSION_SUFIX = ":7474/graphaware/actions";
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final List<String> hosts;
    private final String userName;
    private final String password;

    public KinesisConsumer() {
        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();

        String env = System.getenv("ENV");
        String neo4jUris = System.getenv("NEO4J_URIS");
        userName = System.getenv("NEO4J_USER");

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
        password = map.get("password");

        log.info("its a given ips {}", neo4jUris);
        String[] arr = neo4jUris.split("&");

        hosts = new ArrayList<>();
        for (String eachIp : arr) {
            hosts.add("http://" + eachIp + EXTENSION_SUFIX);
        }
        log.info("its a  final list of hosts {}", hosts);
    }

    public void handler(KinesisEvent event, Context context) throws IOException {
        log.info("start handle {} records from the stream", event.getRecords().size());
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
        String json = builder.toString();

        CredentialsProvider provider = new BasicCredentialsProvider();
        UsernamePasswordCredentials credentials
                = new UsernamePasswordCredentials(userName, password);
        provider.setCredentials(AuthScope.ANY, credentials);

//        CloseableHttpClient httpClient = HttpClientBuilder.create()
//                .setDefaultCredentialsProvider(provider)
//                .build();

        Iterator<String> it = hosts.iterator();
        while (it.hasNext()) {
            String eachUrl = it.next();
            HttpPost httppost = new HttpPost(eachUrl);
            StringEntity entity = new StringEntity(json, ContentType.APPLICATION_JSON);
            httppost.setEntity(entity);

            CloseableHttpClient httpClient = HttpClientBuilder.create()
                    .setConnectionReuseStrategy(new NoConnectionReuseStrategy())
//                    .setDefaultCredentialsProvider(provider)
                    .build();
            try {
                CloseableHttpResponse response = httpClient.execute(httppost);
                try {
                    if (response.getStatusLine().getStatusCode() != 200) {
                        log.error("receive non OK ({}) response from {}, remove this url from a list",
                                response.getStatusLine().getStatusCode(), eachUrl);
                        //todo:remove
                        it.remove();
                    } else {
                        log.info("successfully handle {} records from the stream in {}", event.getRecords().size(), System.currentTimeMillis() - start);
                        return;
                    }
                } finally {
                    response.close();
                }
            } finally {
                httpClient.close();
            }
        }
        throw new IllegalStateException("there is no life neo4j host in a list, reboot");
    }

}
