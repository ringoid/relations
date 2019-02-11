package com.ringoid.events.image;

import com.ringoid.PhotoProperties;
import com.ringoid.Relationships;
import com.ringoid.common.Utils;
import com.ringoid.events.auth.AuthUtils;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.TransactionWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.ringoid.DeletePhotoProperties.HIDE_AT;
import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.PhotoProperties.PHOTO_ID;
import static com.ringoid.PhotoProperties.PHOTO_S3_KEY;
import static com.ringoid.PhotoProperties.PHOTO_UPLOADED;


public class ImageUtils {
    private static final Logger log = LoggerFactory.getLogger(AuthUtils.class);

    private static final String UPLOAD_PHOTO =
            String.format("MATCH (n:%s {%s: $userIdValue}) " +//1
                            "MERGE (p:%s {%s: $photoIdValue}) " +//2

                            "ON CREATE SET p.%s = $photoKey " +//2.1
                            "ON MATCH SET p.%s = $photoKey " +//2.2

                            "MERGE (n)-[rel:%s]->(p) " +//3
                            "ON CREATE SET rel.%s = $uploadedAtValue, p.%s = true " +//4
                            "ON MATCH SET rel.%s = $uploadedAtValue, p.%s = true",//5

                    PERSON.getLabelName(), USER_ID.getPropertyName(),//1
                    PHOTO.getLabelName(), PHOTO_ID.getPropertyName(),//2
                    PHOTO_S3_KEY.getPropertyName(),//2.1
                    PHOTO_S3_KEY.getPropertyName(),//2.2
                    Relationships.UPLOAD_PHOTO.name(),//3
                    PHOTO_UPLOADED.getPropertyName(), PhotoProperties.NEED_TO_MODERATE.getPropertyName(),//4
                    PHOTO_UPLOADED.getPropertyName(), PhotoProperties.NEED_TO_MODERATE.getPropertyName());//5

    private static String deletePhotoQuery(boolean userTakePartInReport) {
        if (!userTakePartInReport) {
            return String.format("MATCH (p:%s {%s: $photoIdValue}) " +
                            "DETACH DELETE p",
                    PHOTO.getLabelName(), PHOTO_ID.getPropertyName());
        }

        return String.format(
                "MATCH (n:%s {%s: $userIdValue})-[upl:%s]->(p:%s {%s: $photoIdValue}) " +
                        "DELETE upl WITH n, p " +
                        "MERGE (n)-[d:%s]->(p) " +
                        "ON CREATE SET d.%s = $hideAtValue",
                PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), PHOTO_ID.getPropertyName(),
                Relationships.HIDE_PHOTO.name(),
                HIDE_AT.getPropertyName()
        );
    }

    public static void uploadPhoto(UserUploadedPhotoEvent event, Driver driver) {
        log.debug("upload photo {}", event);

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("userIdValue", event.getUserId());
        parameters.put("uploadedAtValue", event.getUnixTime());
        parameters.put("photoIdValue", event.getPhotoId());
        parameters.put("photoKey", event.getPhotoKey());

        try (Session session = driver.session()) {
            session.writeTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    tx.run(UPLOAD_PHOTO, parameters);
                    Utils.markPersonForModeration(event.getUserId(), tx);
                    return 1;
                }
            });
        } catch (Throwable throwable) {
            log.error("error upload photo {}", event, throwable);
            throw throwable;
        }
        log.info("successfully upload photo {}", event);
    }

    public static void deletePhoto(UserDeletePhotoEvent event, Driver driver) {
        log.debug("delete photo {}", event);

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("photoIdValue", event.getPhotoId());
        parameters.put("userIdValue", event.getUserId());
        parameters.put("hideAtValue", event.getUnixTime());

        try (Session session = driver.session()) {
            session.writeTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    String query = deletePhotoQuery(event.isUserTakePartInReport());
                    tx.run(query, parameters);
                    return 1;
                }
            });
        } catch (Throwable throwable) {
            log.error("error delete photo {}", event, throwable);
            throw throwable;
        }
        log.info("successfully delete photo {}", event);
    }
}
