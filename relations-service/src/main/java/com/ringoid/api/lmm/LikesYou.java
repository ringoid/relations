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
import static com.ringoid.PersonProperties.LAST_ACTION_TIME;
import static com.ringoid.PersonProperties.LAST_ONLINE_TIME;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.PhotoProperties.PHOTO_ID;
import static com.ringoid.PhotoProperties.PHOTO_UPLOADED;

public class LikesYou {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Driver driver;

    private static final String TARGET_USER_ID = "targetUserId";
    private static final String TARGET_PHOTO_ID = "targetPhotoId";
    private static final String OWN_LAST_ACTION_TIME = "lastActionTime";

    private static final String LIKES_YOU_NEW_PART =
            String.format(
                    "MATCH (sourceUser:%s {%s:$sourceUserId})<-[:%s]-(targetUser)-[:%s]->(ph:%s) " +//1
                            "WHERE sourceUser.%s <> targetUser.%s " +//2

                            "AND (NOT (sourceUser)-[:%s]->(targetUser)) " +//3
                            "AND (NOT '%s' in labels(targetUser)) " +//3.1
                            "WITH sourceUser, targetUser, ph " +//4

                            "OPTIONAL MATCH (ph)<-[ll:%s]-(:%s) WITH sourceUser, targetUser, count(ll) as likesThatUserHas " +//5
                            "MATCH (targetUser)-[upl:%s]->(photo:%s) WITH sourceUser, targetUser, likesThatUserHas, count(upl) as uploads " +//6
                            "MATCH (sourceUser)-[:%s]->(sPh:%s)<-[rrr:%s]-(targetUser) " +//7
                            "WITH sourceUser, targetUser, targetUser.%s as onlineTime, likesThatUserHas, uploads, count(rrr) as howManyBackLikes " +//8
                            "MATCH (targetUser)-[uplRel:%s]->(trPhoto:%s) WITH sourceUser, targetUser, onlineTime, likesThatUserHas, howManyBackLikes, trPhoto, uplRel.%s as photoUploadedTime, uploads " +//9
                            "OPTIONAL MATCH (trPhoto)<-[anyView]-(sourceUser) WITH sourceUser, targetUser, onlineTime, likesThatUserHas, howManyBackLikes, uploads, trPhoto, photoUploadedTime, count(anyView) as viewPhotoCountBySource " +//10
                            "OPTIONAL MATCH (trPhoto)<-[lrl:%s]-(:%s) RETURN sourceUser.%s AS %s, targetUser.%s AS %s, likesThatUserHas, uploads, howManyBackLikes, onlineTime, viewPhotoCountBySource, count(lrl) as targetPhotoWasLiked, photoUploadedTime, trPhoto.%s AS %s " +//11
                            "ORDER BY likesThatUserHas DESC, uploads DESC, howManyBackLikes DESC, onlineTime DESC, viewPhotoCountBySource ASC, targetPhotoWasLiked DESC, photoUploadedTime DESC",//12

                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.LIKE.name(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//1
                    USER_ID.getPropertyName(), USER_ID.getPropertyName(),//2

                    Relationships.VIEW_IN_LIKES_YOU.name(),//3
                    HIDDEN.getLabelName(),//3.1
                    Relationships.LIKE.name(), PERSON.getLabelName(),//5
                    Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//6
                    Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), Relationships.LIKE.name(),//7
                    LAST_ONLINE_TIME.getPropertyName(),//8
                    Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), PHOTO_UPLOADED.getPropertyName(),//9
                    Relationships.LIKE.name(), PERSON.getLabelName(), LAST_ACTION_TIME.getPropertyName(), OWN_LAST_ACTION_TIME, USER_ID.getPropertyName(), TARGET_USER_ID, PHOTO_ID.getPropertyName(), TARGET_PHOTO_ID //11
            );


    private static final String LIKES_YOU_OLD_PART =
            String.format(
                    "MATCH (sourceUser:%s {%s:$sourceUserId})<-[:%s]-(targetUser)-[:%s]->(ph:%s) " +//1
                            "WHERE sourceUser.%s <> targetUser.%s " +//2

                            "AND (sourceUser)-[:%s]->(targetUser) " +//3
                            "AND (NOT '%s' in labels(targetUser)) " +//3.1

                            "WITH sourceUser, targetUser, ph " +//4

                            "OPTIONAL MATCH (ph)<-[ll:%s]-(:%s) WITH sourceUser, targetUser, count(ll) as likesThatUserHas " +//5
                            "MATCH (targetUser)-[upl:%s]->(photo:%s) WITH sourceUser, targetUser, likesThatUserHas, count(upl) as uploads " +//6
                            "MATCH (sourceUser)-[:%s]->(sPh:%s)<-[rrr:%s]-(targetUser) " +//7
                            "WITH sourceUser, targetUser, targetUser.%s as onlineTime, likesThatUserHas, uploads, count(rrr) as howManyBackLikes " +//8
                            "MATCH (targetUser)-[uplRel:%s]->(trPhoto:%s) WITH sourceUser, targetUser, onlineTime, likesThatUserHas, howManyBackLikes, trPhoto, uplRel.%s as photoUploadedTime, uploads " +//9
                            "OPTIONAL MATCH (trPhoto)<-[anyView]-(sourceUser) WITH sourceUser, targetUser, onlineTime, likesThatUserHas, howManyBackLikes, uploads, trPhoto, photoUploadedTime, count(anyView) as viewPhotoCountBySource " +//10
                            "OPTIONAL MATCH (trPhoto)<-[lrl:%s]-(:%s) RETURN sourceUser.%s AS %s, targetUser.%s AS %s, likesThatUserHas, uploads, howManyBackLikes, onlineTime, viewPhotoCountBySource, count(lrl) as targetPhotoWasLiked, photoUploadedTime, trPhoto.%s AS %s " +//11
                            "ORDER BY likesThatUserHas DESC, uploads DESC, howManyBackLikes DESC, onlineTime DESC, viewPhotoCountBySource ASC, targetPhotoWasLiked DESC, photoUploadedTime DESC",//12

                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.LIKE.name(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//1
                    USER_ID.getPropertyName(), USER_ID.getPropertyName(),//2

                    Relationships.VIEW_IN_LIKES_YOU.name(),//3
                    HIDDEN.getLabelName(),//3.1

                    Relationships.LIKE.name(), PERSON.getLabelName(),//5
                    Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//6
                    Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), Relationships.LIKE.name(),//7
                    LAST_ONLINE_TIME.getPropertyName(),//8
                    Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), PHOTO_UPLOADED.getPropertyName(),//9
                    Relationships.LIKE.name(), PERSON.getLabelName(), LAST_ACTION_TIME.getPropertyName(), OWN_LAST_ACTION_TIME, USER_ID.getPropertyName(), TARGET_USER_ID, PHOTO_ID.getPropertyName(), TARGET_PHOTO_ID //11
            );

    public LikesYou() {
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
        log.debug("handle likes you request {}", request);
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("sourceUserId", request.getUserId());

        long lastActionTime = Utils.lastActionTime(parameters, driver);
        LMMResponse response = new LMMResponse();
        response.setLastActionTime(lastActionTime);

        if (request.getRequestedLastActionTime() > lastActionTime) {
            log.debug("requested last action time > actual, return empty response");
            return response;
        }

        String query = LIKES_YOU_OLD_PART;
        if (request.isRequestNewPart()) {
            query = LIKES_YOU_NEW_PART;
        }

        List<Map<String, List<String>>> receivedProfiles = Utils.llmRequest(parameters, query,
                TARGET_USER_ID, TARGET_PHOTO_ID, "likesYou", driver);

        List<ProfileResponse> profiles = Utils.profileResponse(receivedProfiles);
        response.setProfiles(profiles);
        response.setLastActionTime(lastActionTime);

        log.debug("successfully handle likes you request for userId {} with {} profiles", request.getUserId(), receivedProfiles.size());
        return response;
    }

}
