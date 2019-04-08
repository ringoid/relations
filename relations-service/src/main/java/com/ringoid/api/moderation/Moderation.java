package com.ringoid.api.moderation;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ringoid.HideProperties;
import com.ringoid.PersonProperties;
import com.ringoid.PhotoProperties;
import com.ringoid.Relationships;
import com.ringoid.common.Utils;
import com.ringoid.events.internal.events.HidePhotoEvent;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Config;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.TransactionWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.ringoid.BlockProperties.BLOCK_REASON_NUM;
import static com.ringoid.Labels.HIDDEN;
import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.Labels.RESIZED_PHOTO;
import static com.ringoid.PersonProperties.MODERATION_STARTED_AT;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.PhotoProperties.PHOTO_ID;
import static com.ringoid.PhotoProperties.PHOTO_S3_KEY;

public class Moderation {
    private static final String USER_ID_PROPERY = "userId";
    private static final String PHOTO_ID_PROPERTY = "photoId";
    private static final String IS_IT_HIDDEN_PROPERTY = "isItHidden";
    private static final String HOW_MANY_BLOCK_PROPERTY = "howManyBlocks";
    private static final String HOW_MANY_LIKE_PROPERTY = "howManyLikes";
    private static final String LIST_BLOCK_REASONS = "reasons";
    private static final String NEW_ONE_PROPERTY = "newOne";
    private static final String S3_PHOTO_KEY = "s3PhotoKey";
    private final static String WITHOUT_REPORTED =
            String.format(
                    "MATCH (ph:%s)<-[relP:%s|%s]-(targetUser:%s) " +//1
                            "WHERE targetUser.%s = true AND (NOT '%s' in labels(targetUser)) " +//2
                            "AND NOT ( (targetUser)<-[:%s]-(:%s) ) " +//3
                            "AND (NOT exists(targetUser.%s) OR targetUser.%s < $lastModerationTime) " +//4
                            "WITH DISTINCT targetUser LIMIT $limit " +//5
                            "MATCH (ph:%s)<-[relP:%s|%s]-(targetUser) WITH targetUser, relP, ph " +//5.1
                            "OPTIONAL MATCH (ph)<-[ll:%s]-() " +//8
                            "RETURN targetUser.%s AS %s, ph.%s AS %s, ph.%s AS %s, type(relP)='%s' AS %s, count(ll) AS %s, ph.%s as %s " +//9
                            "ORDER BY %s DESC, %s DESC",

                    PHOTO.getLabelName(), Relationships.UPLOAD_PHOTO.name(), Relationships.HIDE_PHOTO.name(), PERSON.getLabelName(),//1
                    PersonProperties.NEED_TO_MODERATE.getPropertyName(), HIDDEN.getLabelName(),//2
                    Relationships.BLOCK.name(), PERSON.getLabelName(),//3
                    MODERATION_STARTED_AT.getPropertyName(), MODERATION_STARTED_AT.getPropertyName(),//4
                    PHOTO.getLabelName(), Relationships.UPLOAD_PHOTO.name(), Relationships.HIDE_PHOTO.name(),//5.1
                    Relationships.LIKE.name(),//8
                    USER_ID.getPropertyName(), USER_ID_PROPERY, PHOTO_ID.getPropertyName(), PHOTO_ID_PROPERTY, PHOTO_S3_KEY.getPropertyName(), S3_PHOTO_KEY, Relationships.HIDE_PHOTO.name(), IS_IT_HIDDEN_PROPERTY, HOW_MANY_LIKE_PROPERTY, PhotoProperties.NEED_TO_MODERATE.getPropertyName(), NEW_ONE_PROPERTY,//9
                    IS_IT_HIDDEN_PROPERTY, HOW_MANY_LIKE_PROPERTY
            );
    private final static String REPORTED =
            String.format(
                    "MATCH (ph:%s)<-[relP:%s|%s]-(targetUser:%s)<-[r:%s]-(n:%s) " +//1
                            "WHERE targetUser.%s = true AND (NOT '%s' in labels(targetUser)) " +//2
                            "AND r.%s > 9 " +//3
                            "AND (NOT exists(targetUser.%s) OR targetUser.%s < $lastModerationTime) " +//4
                            "WITH DISTINCT targetUser LIMIT $limit " +//5
                            "MATCH (ph:%s)<-[relP:%s|%s]-(targetUser) WITH targetUser, relP, ph " +//5.1
                            "OPTIONAL MATCH (ph)<-[br:%s]-() " +//6
                            "WITH targetUser, ph, relP, count(br) AS %s, collect(br.%s) as %s " +//7
                            "OPTIONAL MATCH (ph)<-[ll:%s]-() " +//8
                            "RETURN targetUser.%s AS %s, ph.%s AS %s, ph.%s AS %s, type(relP)='%s' AS %s, count(ll) AS %s, ph.%s as %s, %s, %s " +//9
                            "ORDER BY %s DESC, %s DESC, %s DESC",

                    PHOTO.getLabelName(), Relationships.UPLOAD_PHOTO.name(), Relationships.HIDE_PHOTO.name(), PERSON.getLabelName(), Relationships.BLOCK.name(), PERSON.getLabelName(),//1
                    PersonProperties.NEED_TO_MODERATE.getPropertyName(), HIDDEN.getLabelName(),//2
                    BLOCK_REASON_NUM.getPropertyName(),//3
                    MODERATION_STARTED_AT.getPropertyName(), MODERATION_STARTED_AT.getPropertyName(),//4
                    PHOTO.getLabelName(), Relationships.UPLOAD_PHOTO.name(), Relationships.HIDE_PHOTO.name(),//5.1
                    Relationships.BLOCK.name(),//6
                    HOW_MANY_BLOCK_PROPERTY, BLOCK_REASON_NUM.getPropertyName(), LIST_BLOCK_REASONS,//7
                    Relationships.LIKE.name(),//8
                    USER_ID.getPropertyName(), USER_ID_PROPERY, PHOTO_ID.getPropertyName(), PHOTO_ID_PROPERTY, PHOTO_S3_KEY.getPropertyName(), S3_PHOTO_KEY, Relationships.HIDE_PHOTO.name(), IS_IT_HIDDEN_PROPERTY, HOW_MANY_LIKE_PROPERTY, PhotoProperties.NEED_TO_MODERATE.getPropertyName(), NEW_ONE_PROPERTY, HOW_MANY_BLOCK_PROPERTY, LIST_BLOCK_REASONS,//9
                    IS_IT_HIDDEN_PROPERTY, HOW_MANY_BLOCK_PROPERTY, HOW_MANY_LIKE_PROPERTY
            );
    private final static String MARK_THAT_MODERATION_IN_PROGRESS =
            String.format(
                    "MATCH (n:%s {%s:$targetUserId}) SET n.%s = $startModerationTime",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), MODERATION_STARTED_AT.getPropertyName()
            );

    private final static String HIDE_PHOTO =
            String.format(
                    "MATCH (n:%s {%s:$targetUserId})-[upl:%s]->(ph:%s {%s:$targetPhotoId}) " +//1
                            "DELETE upl " +//2
                            "MERGE (n)-[h:%s]->(ph) " +//3
                            "ON CREATE SET h.%s = $time, h.%s = $moderator, ph.%s = false " +//4
                            "ON MATCH SET h.%s = $time, h.%s = $moderator, ph.%s = false " +//5
                            "WITH ph " +//6
                            "OPTIONAL MATCH (ph)-[:%s]->(rs:%s) " +//7
                            "DETACH DELETE rs",//8
                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), PHOTO_ID.getPropertyName(),//1
                    Relationships.HIDE_PHOTO.name(),//3
                    HideProperties.HIDE_AT.getPropertyName(), HideProperties.HIDE_REASON.getPropertyName(), PhotoProperties.NEED_TO_MODERATE.getPropertyName(),//4
                    HideProperties.HIDE_AT.getPropertyName(), HideProperties.HIDE_REASON.getPropertyName(), PhotoProperties.NEED_TO_MODERATE.getPropertyName(),//5
                    Relationships.RESIZED.name(), RESIZED_PHOTO.getLabelName()//7
            );

    private final static String UNHIDE_PHOTO =
            String.format(
                    "MATCH (n:%s {%s:$targetUserId})-[upl:%s]->(ph:%s {%s:$targetPhotoId}) " +//1
                            "DELETE upl " +//2
                            "MERGE (n)-[h:%s]->(ph) " +//3
                            "ON CREATE SET h.%s = $time, ph.%s = false " +//4
                            "ON MATCH SET h.%s = $time, ph.%s = false",//5
                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.HIDE_PHOTO.name(), PHOTO.getLabelName(), PHOTO_ID.getPropertyName(),//1
                    Relationships.UPLOAD_PHOTO.name(),//3
                    PhotoProperties.PHOTO_UPLOADED_AT.getPropertyName(), PhotoProperties.NEED_TO_MODERATE.getPropertyName(),//4
                    PhotoProperties.PHOTO_UPLOADED_AT.getPropertyName(), PhotoProperties.NEED_TO_MODERATE.getPropertyName()//5
            );
    private final static String COMPLETE =
            String.format(
                    "MATCH (n:%s {%s:$targetUserId})-[:%s|%s]->(ph:%s) " +//1
                            "SET n.%s = false, ph.%s = false",//2
                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), Relationships.HIDE_PHOTO.name(), PHOTO.getLabelName(),//1
                    PersonProperties.NEED_TO_MODERATE.getPropertyName(), PhotoProperties.NEED_TO_MODERATE.getPropertyName()
            );
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Driver driver;
    private final AmazonKinesis kinesis;
    private final String internalStreamName;
    private final Gson gson;

    public Moderation() {
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

        AmazonKinesisClientBuilder clientBuilder = AmazonKinesisClientBuilder.standard().withRegion(Regions.EU_WEST_1);
        kinesis = clientBuilder.build();
    }

    public ModerationResponse handler(ModerationRequest request, Context context) {
        log.debug("handle moderation request {}", request);
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("lastModerationTime", System.currentTimeMillis() - 15 * 60 * 1000);
        parameters.put("limit", request.getLimit());

        ModerationResponse response = new ModerationResponse();
        switch (request.getQueryType()) {
            case "reported": {
//                {
//                    "queryType": "reported",
//                    "limit": 10
//                }

                List<ProfileObj> result = reported(parameters);
                markPersonInModerationProccess(result);
                response.setProfiles(result);
                return response;
            }
            case "unReported": {
//                {
//                    "queryType": "unReported",
//                    "limit": 10
//                }

                List<ProfileObj> result = unReported(parameters);
                markPersonInModerationProccess(result);
                response.setProfiles(result);
                return response;
            }
            case "hide": {
//                {
//                    "queryType": "hide",
//                        "limit": 100,
//                        "profilePhotoMap":{
//                    "3162d6f0d83f36032d6e8d6c1e9455aad7ef91e0": "origin_4531f21d90c4c6e4fa933f3333aa41bc2a3c2692photo_s3_key"
//                }
//                }
                hidePhoto(request.getProfilePhotoMap());
                return response;
            }
            //todo:mb later, now it's too complicated
//            case "unhide": {
//                unHidePhoto(request.getProfilePhotoMap());
//                return response;
//            }
            case "complete": {
//                {
//                    "queryType": "complete",
//                        "limit": 100,
//                        "profilePhotoMap":{
//                    "3162d6f0d83f36032d6e8d6c1e9455aad7ef91e0": "origin_4531f21d90c4c6e4fa933f3333aa41bc2a3c2692photo_s3_key"
//                }
//                }

                complete(request.getProfilePhotoMap());
                return response;
            }
            default: {
                log.error("unsupported query type {}", request.getQueryType());
                return response;
            }
        }
    }

    private void complete(Map<String, String> photos) {
        try (Session session = driver.session()) {
            session.writeTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    for (String eachUserId : photos.keySet()) {
                        Map<String, Object> parameters = new HashMap<>();
                        parameters.put("targetUserId", eachUserId);
                        tx.run(COMPLETE, parameters);
                    }
                    return 1;
                }
            });
        } catch (Throwable throwable) {
            log.error("error complete request", throwable);
            throw throwable;
        }
    }

    private void markPersonInModerationProccess(List<ProfileObj> source) {
        long moderationStarted = System.currentTimeMillis();
        try (Session session = driver.session()) {
            session.writeTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    for (ProfileObj each : source) {
                        Map<String, Object> parameters = new HashMap<>();
                        parameters.put("targetUserId", each.getUserId());
                        parameters.put("startModerationTime", moderationStarted);
                        tx.run(MARK_THAT_MODERATION_IN_PROGRESS, parameters);
                    }
                    return 1;
                }
            });
        } catch (Throwable throwable) {
            log.error("error mark moderation request", throwable);
            throw throwable;
        }
    }

    private void hidePhoto(Map<String, String> photos) {
        execute(HIDE_PHOTO, photos);
    }

    private void execute(String query, Map<String, String> photos) {
        try (Session session = driver.session()) {
            session.writeTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    for (String eachUserId : photos.keySet()) {
                        String photoId = photos.get(eachUserId);
                        Map<String, Object> parameters = new HashMap<>();
                        parameters.put("time", System.currentTimeMillis());
                        parameters.put("targetUserId", eachUserId);
                        parameters.put("targetPhotoId", photoId);
                        parameters.put("moderator", "moderator");
                        tx.run(query, parameters);
                        //send events to internal queue

                        HidePhotoEvent event = new HidePhotoEvent(eachUserId, photoId);
                        Utils.sendEventIntoInternalQueue(event, kinesis, internalStreamName, event.getUserId(), gson);
                    }
                    return 1;
                }
            });
        } catch (Throwable throwable) {
            log.error("error hide/unhide request", throwable);
            throw throwable;
        }
    }

//    private void unHidePhoto(Map<String, String> photos) {
//        execute(UNHIDE_PHOTO, photos);
//    }

    private List<ProfileObj> reported(Map<String, Object> parameters) {
        Map<String, List<PhotoObj>> listMap = new HashMap<>();
        List<String> userOrder = new ArrayList<>();
        try (Session session = driver.session()) {
            session.readTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    StatementResult result = tx.run(REPORTED, parameters);
                    List<Record> list = result.list();
                    for (Record each : list) {
                        String userId = each.get(USER_ID_PROPERY).asString();
                        List<PhotoObj> photos = listMap.get(userId);
                        if (Objects.isNull(photos)) {
                            userOrder.add(userId);
                            photos = new ArrayList<>();
                        }
                        PhotoObj photoObj = new PhotoObj();
                        photoObj.setPhotoId(each.get(PHOTO_ID_PROPERTY).asString());
                        photoObj.setPhotoHidden(each.get(IS_IT_HIDDEN_PROPERTY).asBoolean());
                        photoObj.setPhotoReported(each.get(HOW_MANY_BLOCK_PROPERTY).asInt() > 0);
                        photoObj.setLikes(each.get(HOW_MANY_LIKE_PROPERTY).asInt());
                        photoObj.setWasModeratedBefore(!each.get(NEW_ONE_PROPERTY).asBoolean());
                        photoObj.setBlockReasons(each.get(LIST_BLOCK_REASONS).asList());
                        photoObj.setS3Key(each.get(S3_PHOTO_KEY).asString());
                        photos.add(photoObj);
                        listMap.put(userId, photos);
                    }

                    log.info("{} profiles were found for reported moderation request", userOrder.size());
                    log.info("{}", REPORTED);
                    return 1;
                }
            });
        } catch (Throwable throwable) {
            log.error("error reported request", throwable);
            throw throwable;
        }

        List<ProfileObj> finalResult = new ArrayList<>();
        for (String each : userOrder) {
            List<PhotoObj> photoObjList = listMap.get(each);
            ProfileObj profileObj = new ProfileObj();
            profileObj.setUserId(each);
            profileObj.setPhotos(photoObjList);
            finalResult.add(profileObj);
        }

        return finalResult;
    }

    private List<ProfileObj> unReported(Map<String, Object> parameters) {
        Map<String, List<PhotoObj>> listMap = new HashMap<>();
        List<String> userOrder = new ArrayList<>();
        try (Session session = driver.session()) {
            session.readTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    StatementResult result = tx.run(WITHOUT_REPORTED, parameters);
                    List<Record> list = result.list();
                    for (Record each : list) {
                        String userId = each.get(USER_ID_PROPERY).asString();
                        List<PhotoObj> photos = listMap.get(userId);
                        if (Objects.isNull(photos)) {
                            userOrder.add(userId);
                            photos = new ArrayList<>();
                        }
                        PhotoObj photoObj = new PhotoObj();
                        photoObj.setPhotoId(each.get(PHOTO_ID_PROPERTY).asString());
                        photoObj.setPhotoHidden(each.get(IS_IT_HIDDEN_PROPERTY).asBoolean());
                        photoObj.setLikes(each.get(HOW_MANY_LIKE_PROPERTY).asInt());
                        photoObj.setWasModeratedBefore(!each.get(NEW_ONE_PROPERTY).asBoolean());
                        photoObj.setS3Key(each.get(S3_PHOTO_KEY).asString());
                        photos.add(photoObj);
                        listMap.put(userId, photos);
                    }

                    log.info("{} profiles were found for without reported moderation request", userOrder.size());
                    log.debug("{}", WITHOUT_REPORTED);
                    return 1;
                }
            });
        } catch (Throwable throwable) {
            log.error("error reported request", throwable);
            throw throwable;
        }

        List<ProfileObj> finalResult = new ArrayList<>();
        for (String each : userOrder) {
            List<PhotoObj> photoObjList = listMap.get(each);
            ProfileObj profileObj = new ProfileObj();
            profileObj.setUserId(each);
            profileObj.setPhotos(photoObjList);
            finalResult.add(profileObj);
        }

        return finalResult;
    }

}
