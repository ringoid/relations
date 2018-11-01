package com.ringoid.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.ringoid.Relationships;
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
import static com.ringoid.PersonProperties.USER_ID;

public class NewFaces {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Driver driver;

    private static final String TARGET_USER_ID = "targetUserId";
    private static final String TARGET_PHOTO_ID = "targetPhotoId";

    //original query
    //match (sourceUser:Person {user_id:10}) with sourceUser match (n:Person)-[upl:UPLOAD]->(ph:Photo) where sourceUser.user_id <> n.user_id and (not (n)-[]-(sourceUser)) with n, ph, upl optional match patt=(ph)<-[:LIKE]-(:Person) with n.user_id as userId, count(relationships(patt)) as likes order by likes desc limit 10 match (n:Person {user_id:userId})-[uplRel:UPLOAD]->(photo:Photo) with n.user_id as userId, likes, count(uplRel) as photos, n.was_online as wasOnline match (n:Person {user_id:userId})-[uplRel:UPLOAD]->(photo:Photo) with n, photo, likes, photos, wasOnline optional match (:Person)-[uploadRel:UPLOAD]->(photo)<-[l:LIKE]-(:Person) return n.user_id as userId, photo.photo_id as photoId, count(l) as eachPhotoLikes, likes, photos, wasOnline, uploadRel.photo_uploaded_at as photoUploadedAt order by likes desc, photos desc, wasOnline desc, eachPhotoLikes desc, photoUploadedAt desc
    private final static String NEW_FACES_REQUEST =
            String.format(
                    "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +
                            "MATCH (n:%s)-[upl:%s]->(ph:%s) " +
                            "WHERE sourceUser.%s <> n.%s " +
                            "AND (NOT (n)-[]-(sourceUser)) WITH n, ph, upl " +
                            "OPTIONAL MATCH (ph)<-[ll:%s]-(:%s) WITH n.%s AS userId, count(ll) AS likes ORDER BY likes DESC LIMIT $limit " +
                            "MATCH (n:%s {%s:userId})-[uplRel:%s]->(photo:%s) WITH n.%s AS userId, likes, count(uplRel) AS photos, n.%s AS wasOnline " +
                            "MATCH (n:%s {%s:userId})-[uplRel:%s]->(photo:%s) WITH n, photo, likes, photos, wasOnline " +
                            "OPTIONAL MATCH (:%s)-[uploadRel:%s]->(photo)<-[l:%s]-(:%s) " +
                            "RETURN n.user_id AS %s, photo.photo_id AS %s, count(l) AS eachPhotoLikes, likes, photos, wasOnline, uploadRel.photo_uploaded_at AS photoUploadedAt ORDER BY likes DESC, photos DESC, wasOnline DESC, eachPhotoLikes DESC, photoUploadedAt DESC",
                    PERSON.getLabelName(), USER_ID.getPropertyName(),
                    PERSON.getLabelName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),
                    USER_ID.getPropertyName(), USER_ID.getPropertyName(),
                    Relationships.LIKE.name(), PERSON.getLabelName(), USER_ID.getPropertyName(),
                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), USER_ID.getPropertyName(), LAST_ONLINE_TIME.getPropertyName(),
                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),
                    PERSON.getLabelName(), Relationships.UPLOAD_PHOTO.name(), Relationships.LIKE.name(), PERSON.getLabelName(),
                    TARGET_USER_ID, TARGET_PHOTO_ID
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

        List<Map<String, List<String>>> faces = newFaces(parameters);

        NewFacesResponse response = new NewFacesResponse();
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
