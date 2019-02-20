package com.ringoid.api.lmm;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ringoid.Relationships;
import com.ringoid.api.LMMRequest;
import com.ringoid.api.LMMResponse;
import com.ringoid.api.ProfileResponse;
import com.ringoid.common.Utils;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Config;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.ringoid.Labels.HIDDEN;
import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.MessageProperties.MSG_AT;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.PhotoProperties.PHOTO_ID;
import static com.ringoid.PhotoProperties.PHOTO_UPLOADED;

public class Messages {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Driver driver;

    private static final String TARGET_USER_ID = "targetUserId";
    private static final String TARGET_PHOTO_ID = "targetPhotoId";

    private static final String MESSAGES_QUERY =
            String.format(
                    "MATCH (sourceUser:%s {%s:$sourceUserId})-[msg:%s]-(targetUser)-[uplRel:%s]->(trPhoto:%s) " +//1

                            "WHERE sourceUser.%s <> targetUser.%s " +//2
                            "AND (NOT '%s' in labels(targetUser)) " +//3
                            "WITH sourceUser, targetUser, msg.%s as lastMessageTime, uplRel, uplRel.%s as photoUploadedTime, trPhoto " +//4

                            "OPTIONAL MATCH (trPhoto)<-[anyView]-(sourceUser) WITH sourceUser, targetUser, lastMessageTime, trPhoto, photoUploadedTime, count(anyView) as viewPhotoCountBySource " +//5
                            "OPTIONAL MATCH (trPhoto)<-[lrl:%s]-(:%s) RETURN targetUser.%s AS %s, trPhoto.%s AS %s, lastMessageTime, viewPhotoCountBySource, count(lrl) as targetPhotoWasLiked, photoUploadedTime " +//6
                            "ORDER BY lastMessageTime DESC, viewPhotoCountBySource ASC, targetPhotoWasLiked DESC, photoUploadedTime DESC",//7

                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.MESSAGE.name(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//1

                    USER_ID.getPropertyName(), USER_ID.getPropertyName(),//2
                    HIDDEN.getLabelName(),//3
                    MSG_AT.getPropertyName(), PHOTO_UPLOADED.getPropertyName(),//4

                    Relationships.LIKE.name(), PERSON.getLabelName(), USER_ID.getPropertyName(), TARGET_USER_ID, PHOTO_ID.getPropertyName(), TARGET_PHOTO_ID //6
            );


    public Messages() {
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
    }

    public LMMResponse handler(LMMRequest request, Context context) {
        log.debug("handle messages you request {}", request);
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("sourceUserId", request.getUserId());

        long lastActionTime = Utils.lastActionTime(parameters, driver);
        LMMResponse response = new LMMResponse();
        response.setLastActionTime(lastActionTime);

        if (request.getRequestedLastActionTime() > lastActionTime) {
            log.debug("requested last action time > actual, return empty response");
            return response;
        }

        List<Map<String, List<String>>> receivedProfiles = Utils.llmRequest(parameters, MESSAGES_QUERY,
                TARGET_USER_ID, TARGET_PHOTO_ID, "messages", driver);

        List<ProfileResponse> profiles = Utils.profileResponse(receivedProfiles);
        response.setProfiles(profiles);
        response.setLastActionTime(lastActionTime);

        log.debug("successfully handle messages request for userId {} with {} profiles", request.getUserId(), receivedProfiles.size());
        return response;
    }

}

