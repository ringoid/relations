package com.ringoid.api.newfaces;

import com.amazonaws.services.lambda.runtime.Context;
import com.ringoid.Relationships;
import com.ringoid.UserStatus;
import com.ringoid.api.ProfileResponse;
import com.ringoid.common.Utils;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.PersonProperties.LAST_ONLINE_TIME;
import static com.ringoid.PersonProperties.SEX;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.PersonProperties.USER_STATUS;

public class NewFaces {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Driver driver;

    private static final String TARGET_USER_ID = "targetUserId";
    private static final String TARGET_PHOTO_ID = "targetPhotoId";

    //original query (without sex):
    //match (sourceUser:Person {user_id:10}) with sourceUser
    //match (n:Person)-[upl:UPLOAD]->(ph:Photo)
    //where sourceUser.user_id <> n.user_id
    //and (not (sourceUser)-[]->(n)) and (not (n)-[:LIKE|VIEW_IN_LIKES_YOU|VIEW_IN_MATCHES|VIEW_IN_MESSAGES|BLOCK|MATCH|MESSAGE]->(sourceUser)) with n, ph, upl
    //optional match (ph)<-[ll:LIKE]-(:Person) with n, count(ll) as likes order by likes desc
    //match (n)-[uplRel:UPLOAD]->(photo:Photo) with n, likes, count(uplRel) as photos, n.was_online as wasOnline
    //order by likes desc, photos desc, wasOnline desc limit 10
    //match (n)-[uplRel:UPLOAD]->(photo:Photo) with n, photo, likes, photos, wasOnline
    //optional match (:Person)-[uploadRel:UPLOAD]->(photo)<-[l:LIKE]-(:Person) return n.user_id as userId, photo.photo_id as photoId, count(l) as eachPhotoLikes, likes, photos, wasOnline, uploadRel.photo_uploaded_at as photoUploadedAt order by likes desc, photos desc, wasOnline desc, eachPhotoLikes desc, photoUploadedAt desc
    private final static String NEW_FACES_REQUEST =
            String.format(
                    "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                            "MATCH (n:%s)-[upl:%s]->(ph:%s) " +//2
                            "WHERE sourceUser.%s <> n.%s " +//3
                            "AND sourceUser.%s <> n.%s " +//3.5
                            "AND n.%s <> $hiddenUserStatus " +//3.6
                            "AND (NOT (n)<-[]-(sourceUser)) " +//4
                            "AND (NOT (n)-[:%s|%s|%s|%s|%s|%s|%s]->(sourceUser)) WITH n, ph, upl " +//4.1
                            "OPTIONAL MATCH (ph)<-[ll:%s]-(:%s) WITH n, count(ll) AS likes ORDER BY likes DESC " +//5
                            "MATCH (n)-[uplRel:%s]->(photo:%s) WITH n, likes, count(uplRel) AS photos, n.%s AS wasOnline ORDER BY likes DESC, photos DESC, wasOnline DESC LIMIT $limit " +//6
                            "MATCH (n)-[uplRel:%s]->(photo:%s) WITH n, photo, likes, photos, wasOnline " +//7
                            "OPTIONAL MATCH (:%s)-[uploadRel:%s]->(photo)<-[l:%s]-(:%s) " +//8

                            "WITH n, photo, likes, photos, wasOnline, count(l) AS eachPhotoLikes " +//8.1
                            "MATCH (n)-[uploadRel:%s]->(photo) " +//8.2

                            "RETURN n.user_id AS %s, photo.photo_id AS %s, likes, photos, wasOnline, uploadRel.photo_uploaded_at AS photoUploadedAt ORDER BY likes DESC, photos DESC, wasOnline DESC, eachPhotoLikes DESC, photoUploadedAt DESC",//9
                    PERSON.getLabelName(), USER_ID.getPropertyName(),//1
                    PERSON.getLabelName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//2
                    USER_ID.getPropertyName(), USER_ID.getPropertyName(),//3
                    SEX.getPropertyName(), SEX.getPropertyName(),//3.5
                    USER_STATUS.getPropertyName(), //3.6
                    Relationships.LIKE.name(), Relationships.VIEW_IN_LIKES_YOU.name(), Relationships.VIEW_IN_MATCHES.name(), Relationships.VIEW_IN_MESSAGES.name(), Relationships.BLOCK.name(), Relationships.MATCH.name(), Relationships.MESSAGE.name(),//4.1
                    Relationships.LIKE.name(), PERSON.getLabelName(),//5
                    Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), LAST_ONLINE_TIME.getPropertyName(),//6
                    Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//7
                    PERSON.getLabelName(), Relationships.UPLOAD_PHOTO.name(), Relationships.LIKE.name(), PERSON.getLabelName(),//8
                    Relationships.UPLOAD_PHOTO.name(),//8.2
                    TARGET_USER_ID, TARGET_PHOTO_ID//9
            );

    public NewFaces() {
        String neo4jUri = System.getenv("NEO4J_URI");
        //todo:read these value from Security Storage
        String userName = System.getenv("NEO4J_USER");
        String password = System.getenv("NEO4J_PASSWORD");

        driver = GraphDatabase.driver(neo4jUri, AuthTokens.basic(userName, password),
                Config.build().withMaxTransactionRetryTime(10, TimeUnit.SECONDS).toConfig());
    }

    public NewFacesResponse handler(NewFacesRequest request, Context context) {
        log.debug("handle new face request {}", request);
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("sourceUserId", request.getUserId());
        parameters.put("limit", request.getLimit());
        parameters.put("hiddenUserStatus", UserStatus.HIDDEN.getValue());

        NewFacesResponse response = new NewFacesResponse();

        long lastActionTime = Utils.lastActionTime(parameters, driver);
        response.setLastActionTime(lastActionTime);

        if (request.getRequestedLastActionTime() > lastActionTime) {
            log.debug("requested last action time > actual, return empty response");
            return response;
        }

        List<Map<String, List<String>>> faces = newFaces(parameters);

        List<ProfileResponse> newFaces = new ArrayList<>();
        response.setNewFaces(newFaces);

        for (Map<String, List<String>> eachProfileWithPhotos : faces) {
            ProfileResponse profileResp = new ProfileResponse();
            for (Map.Entry<String, List<String>> eachEntry : eachProfileWithPhotos.entrySet()) {
                profileResp.setUserId(eachEntry.getKey());
                profileResp.setPhotoIds(eachEntry.getValue());
            }
            newFaces.add(profileResp);
        }

        log.debug("successfully handle new faces request for userId {} with {} profiles", request.getUserId(), faces.size());
        return response;
    }

    public List<Map<String, List<String>>> newFaces(Map<String, Object> parameters) {
        final List<Map<String, List<String>>> resultMap = new ArrayList<>();
        final List<String> orderList = new ArrayList<>();
        final Map<String, List<String>> tmpMap = new HashMap<>();
        try (Session session = driver.session()) {
            session.readTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    StatementResult result = tx.run(NEW_FACES_REQUEST, parameters);
                    List<Record> list = result.list();
                    int photoCounter = 0;
                    for (Record each : list) {
                        String targetUserId = each.get(TARGET_USER_ID).asString();
                        String targetPhotoId = each.get(TARGET_PHOTO_ID).asString();
                        List<String> photos = tmpMap.get(targetUserId);
                        if (photos == null) {
                            orderList.add(targetUserId);
                            photos = new ArrayList<>();
                            tmpMap.put(targetUserId, photos);
                        }
                        photos.add(targetPhotoId);
                        photoCounter++;
                    }
                    log.info("{} photo were found for new faces request {} for userId {}",
                            photoCounter, parameters, parameters.get("sourceUserId"));
                    log.debug("{}",NEW_FACES_REQUEST);
                    return 1;
                }
            });
        } catch (Throwable throwable) {
            log.error("error new faces request, request {} for userId {}", parameters.get("sourceUserId"), throwable);
            throw throwable;
        }
        for (String eachUserId : orderList) {
            List<String> photos = tmpMap.get(eachUserId);
            Map<String, List<String>> eachProfileWithPhotos = new HashMap<>();
            eachProfileWithPhotos.put(eachUserId, photos);
            resultMap.add(eachProfileWithPhotos);
        }
        return resultMap;
    }
}
