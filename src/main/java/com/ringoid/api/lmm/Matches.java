package com.ringoid.api.lmm;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ringoid.Relationships;
import com.ringoid.UserStatus;
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

import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.PersonProperties.LAST_ACTION_TIME;
import static com.ringoid.PersonProperties.LAST_ONLINE_TIME;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.PersonProperties.USER_STATUS;
import static com.ringoid.PhotoProperties.PHOTO_ID;
import static com.ringoid.PhotoProperties.PHOTO_UPLOADED;

public class Matches {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Driver driver;

    private static final String TARGET_USER_ID = "targetUserId";
    private static final String TARGET_PHOTO_ID = "targetPhotoId";
    private static final String OWN_LAST_ACTION_TIME = "lastActionTime";

    //    match (sourceUser:Person {user_id:"56adeed94278b1f7fb5dc33a6153d8b86758aef4"})-[:MATCH]-(targetUser)-[:UPLOAD_PHOTO]->(ph:Photo)
//    where sourceUser.user_id <> targetUser.user_id
//
//    AND (NOT (sourceUser)-[:VIEW_IN_MATCHES]->(targetUser))
//    AND targetUser.user_status <> "hidden"
//    with sourceUser, targetUser, ph
//    optional match (ph)<-[sourceLike:LIKE]-(sourceUser)
//    with sourceUser, targetUser, count(sourceLike) as likesThatSourceMakes
//    match (sourceUser)-[:MATCH]-(targetUser)-[:UPLOAD_PHOTO]->(ph:Photo)
//    with sourceUser, targetUser,likesThatSourceMakes, ph
//
//    optional match (ph)<-[ll:LIKE]-(:Person) with sourceUser, targetUser, count(ll) as likesThatUserHas, likesThatSourceMakes
//    match (targetUser)-[upl:UPLOAD_PHOTO]->(photo:Photo) with sourceUser, targetUser, likesThatUserHas, count(upl) as uploads, likesThatSourceMakes
//    match (sourceUser)-[:UPLOAD_PHOTO]->(sPh:Photo)<-[rrr:LIKE]-(targetUser)
//    with sourceUser, targetUser, targetUser.was_online as onlineTime, likesThatUserHas, uploads, count(rrr) as howManyBackLikes, likesThatSourceMakes
//    match (targetUser)-[uplRel:UPLOAD_PHOTO]->(trPhoto:Photo) with sourceUser, targetUser, onlineTime, likesThatUserHas, howManyBackLikes, trPhoto, uplRel.photo_uploaded_at as photoUploadedTime, uploads, likesThatSourceMakes
//    optional match (trPhoto)<-[anyView]-(sourceUser) with sourceUser, targetUser, onlineTime, likesThatUserHas, howManyBackLikes, uploads, trPhoto, photoUploadedTime, count(anyView) as viewPhotoCountBySource, likesThatSourceMakes
//    optional match (trPhoto)<-[lrl:LIKE]-(:Person) return sourceUser.last_action_time as lastActionTime, targetUser.user_id, likesThatUserHas, uploads, howManyBackLikes, onlineTime, viewPhotoCountBySource, count(lrl) as targetPhotoWasLiked, photoUploadedTime, trPhoto.photo_id as photoId, likesThatSourceMakes
//    order by likesThatSourceMakes DESC, likesThatUserHas DESC, uploads DESC, howManyBackLikes DESC, onlineTime DESC, viewPhotoCountBySource ASC, targetPhotoWasLiked DESC, photoUploadedTime DESC
    private static final String MATCHES_NEW_PART =
            String.format(
                    "MATCH (sourceUser:%s {%s:$sourceUserId})-[:%s]-(targetUser)-[:%s]->(ph:%s) " +//1
                            "WHERE sourceUser.%s <> targetUser.%s " +//2

                            "AND (NOT (sourceUser)-[:%s]->(targetUser)) " +//3
                            "AND targetUser.%s <> $hiddenUserStatus " +//3.1

                            "WITH sourceUser, targetUser, ph " +//4

                            "OPTIONAL MATCH (ph)<-[sourceLike:%s]-(sourceUser) " +//4.1
                            "WITH sourceUser, targetUser, count(sourceLike) as likesThatSourceMakes " +//4.2
                            "MATCH (sourceUser)-[:%s]-(targetUser)-[:%s]->(ph:Photo) " +//4.3
                            "WITH sourceUser, targetUser,likesThatSourceMakes, ph " +//4.4

                            "OPTIONAL MATCH (ph)<-[ll:%s]-(:%s) WITH sourceUser, targetUser, count(ll) as likesThatUserHas, likesThatSourceMakes " +//5
                            "MATCH (targetUser)-[upl:%s]->(photo:%s) WITH sourceUser, targetUser, likesThatUserHas, count(upl) as uploads, likesThatSourceMakes " +//6
                            "MATCH (sourceUser)-[:%s]->(sPh:%s)<-[rrr:%s]-(targetUser) " +//7
                            "WITH sourceUser, targetUser, targetUser.%s as onlineTime, likesThatUserHas, uploads, count(rrr) as howManyBackLikes, likesThatSourceMakes " +//8
                            "MATCH (targetUser)-[uplRel:%s]->(trPhoto:%s) WITH sourceUser, targetUser, onlineTime, likesThatUserHas, howManyBackLikes, trPhoto, uplRel.%s as photoUploadedTime, uploads, likesThatSourceMakes " +//9
                            "OPTIONAL MATCH (trPhoto)<-[anyView]-(sourceUser) WITH sourceUser, targetUser, onlineTime, likesThatUserHas, howManyBackLikes, uploads, trPhoto, photoUploadedTime, count(anyView) as viewPhotoCountBySource, likesThatSourceMakes " +//10
                            "OPTIONAL MATCH (trPhoto)<-[lrl:%s]-(:%s) RETURN sourceUser.%s AS %s, targetUser.%s AS %s, likesThatUserHas, uploads, howManyBackLikes, onlineTime, viewPhotoCountBySource, count(lrl) as targetPhotoWasLiked, photoUploadedTime, trPhoto.%s AS %s, likesThatSourceMakes " +//11
                            "ORDER BY likesThatSourceMakes DESC, likesThatUserHas DESC, uploads DESC, howManyBackLikes DESC, onlineTime DESC, viewPhotoCountBySource ASC, targetPhotoWasLiked DESC, photoUploadedTime DESC",//12

                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.MATCH.name(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//1
                    USER_ID.getPropertyName(), USER_ID.getPropertyName(),//2

                    Relationships.VIEW_IN_MATCHES.name(),//3
                    USER_STATUS.getPropertyName(),//3.1

                    Relationships.LIKE.name(), //4.1
                    Relationships.MATCH.name(), Relationships.UPLOAD_PHOTO.name(), //4.3

                    Relationships.LIKE.name(), PERSON.getLabelName(),//5
                    Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//6
                    Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), Relationships.LIKE.name(),//7
                    LAST_ONLINE_TIME.getPropertyName(),//8
                    Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), PHOTO_UPLOADED.getPropertyName(),//9
                    Relationships.LIKE.name(), PERSON.getLabelName(), LAST_ACTION_TIME.getPropertyName(), OWN_LAST_ACTION_TIME, USER_ID.getPropertyName(), TARGET_USER_ID, PHOTO_ID.getPropertyName(), TARGET_PHOTO_ID //11
            );


    private static final String MATCHES_OLD_PART =
            String.format(
                    "MATCH (sourceUser:%s {%s:$sourceUserId})-[:%s]-(targetUser)-[:%s]->(ph:%s) " +//1
                            "WHERE sourceUser.%s <> targetUser.%s " +//2

                            "AND (sourceUser)-[:%s]->(targetUser) " +//3
                            "AND targetUser.%s <> $hiddenUserStatus " +//3.1

                            "WITH sourceUser, targetUser, ph " +//4

                            "OPTIONAL MATCH (ph)<-[sourceLike:%s]-(sourceUser) " +//4.1
                            "WITH sourceUser, targetUser, count(sourceLike) as likesThatSourceMakes " +//4.2
                            "MATCH (sourceUser)-[:%s]-(targetUser)-[:%s]->(ph:Photo) " +//4.3
                            "WITH sourceUser, targetUser,likesThatSourceMakes, ph " +//4.4

                            "OPTIONAL MATCH (ph)<-[ll:%s]-(:%s) WITH sourceUser, targetUser, count(ll) as likesThatUserHas, likesThatSourceMakes " +//5
                            "MATCH (targetUser)-[upl:%s]->(photo:%s) WITH sourceUser, targetUser, likesThatUserHas, count(upl) as uploads, likesThatSourceMakes " +//6
                            "MATCH (sourceUser)-[:%s]->(sPh:%s)<-[rrr:%s]-(targetUser) " +//7
                            "WITH sourceUser, targetUser, targetUser.%s as onlineTime, likesThatUserHas, uploads, count(rrr) as howManyBackLikes, likesThatSourceMakes " +//8
                            "MATCH (targetUser)-[uplRel:%s]->(trPhoto:%s) WITH sourceUser, targetUser, onlineTime, likesThatUserHas, howManyBackLikes, trPhoto, uplRel.%s as photoUploadedTime, uploads, likesThatSourceMakes " +//9
                            "OPTIONAL MATCH (trPhoto)<-[anyView]-(sourceUser) WITH sourceUser, targetUser, onlineTime, likesThatUserHas, howManyBackLikes, uploads, trPhoto, photoUploadedTime, count(anyView) as viewPhotoCountBySource, likesThatSourceMakes " +//10
                            "OPTIONAL MATCH (trPhoto)<-[lrl:%s]-(:%s) RETURN sourceUser.%s AS %s, targetUser.%s AS %s, likesThatUserHas, uploads, howManyBackLikes, onlineTime, viewPhotoCountBySource, count(lrl) as targetPhotoWasLiked, photoUploadedTime, trPhoto.%s AS %s, likesThatSourceMakes " +//11
                            "ORDER BY likesThatSourceMakes DESC, likesThatUserHas DESC, uploads DESC, howManyBackLikes DESC, onlineTime DESC, viewPhotoCountBySource ASC, targetPhotoWasLiked DESC, photoUploadedTime DESC",//12

                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.MATCH.name(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//1
                    USER_ID.getPropertyName(), USER_ID.getPropertyName(),//2

                    Relationships.VIEW_IN_MATCHES.name(),//3
                    USER_STATUS.getPropertyName(),//3.1

                    Relationships.LIKE.name(), //4.1
                    Relationships.MATCH.name(), Relationships.UPLOAD_PHOTO.name(), //4.3

                    Relationships.LIKE.name(), PERSON.getLabelName(),//5
                    Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//6
                    Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), Relationships.LIKE.name(),//7
                    LAST_ONLINE_TIME.getPropertyName(),//8
                    Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), PHOTO_UPLOADED.getPropertyName(),//9
                    Relationships.LIKE.name(), PERSON.getLabelName(), LAST_ACTION_TIME.getPropertyName(), OWN_LAST_ACTION_TIME, USER_ID.getPropertyName(), TARGET_USER_ID, PHOTO_ID.getPropertyName(), TARGET_PHOTO_ID //11
            );

    public Matches() {
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
        log.debug("handle matches you request {}", request);
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("sourceUserId", request.getUserId());
        parameters.put("hiddenUserStatus", UserStatus.HIDDEN.getValue());

        long lastActionTime = Utils.lastActionTime(parameters, driver);
        LMMResponse response = new LMMResponse();
        response.setLastActionTime(lastActionTime);

        if (request.getRequestedLastActionTime() > lastActionTime) {
            log.debug("requested last action time > actual, return empty response");
            return response;
        }

        String query = MATCHES_OLD_PART;
        if (request.isRequestNewPart()) {
            query = MATCHES_NEW_PART;
        }

        List<Map<String, List<String>>> receivedProfiles = Utils.llmRequest(parameters, query,
                TARGET_USER_ID, TARGET_PHOTO_ID, "matches", driver);

        List<ProfileResponse> profiles = Utils.profileResponse(receivedProfiles);
        response.setProfiles(profiles);
        response.setLastActionTime(lastActionTime);

        log.debug("successfully handle matches request for userId {} with {} profiles", request.getUserId(), receivedProfiles.size());
        return response;
    }

}
