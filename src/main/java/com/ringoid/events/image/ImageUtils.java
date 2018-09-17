package com.ringoid.events.image;

import com.ringoid.Relationships;
import com.ringoid.events.auth.AuthUtils;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.TransactionWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.PhotoProperties.PHOTO_ID;
import static com.ringoid.PhotoProperties.PHOTO_UPLOADED;


public class ImageUtils {
    private static final Logger log = LoggerFactory.getLogger(AuthUtils.class);

    private static final String UPLOAD_PHOTO =
            String.format("MATCH (n:%s {%s: $userIdValue}) " +
                            "MERGE (n)-[:%s {%s: $uploadedAtValue}]->(p:%s {%s: $photoIdValue})",
                    PERSON.getLabelName(), USER_ID.getPropertyName(),
                    Relationships.UPLOAD_PHOTO.name(), PHOTO_UPLOADED.getPropertyName(),
                    PHOTO.getLabelName(), PHOTO_ID.getPropertyName());

    private static final String DELETE_PHOTO =
            String.format("MATCH (p:%s {%s: $photoIdValue}) " +
                            "DETACH DELETE p",
                    PHOTO.getLabelName(), PHOTO_ID.getPropertyName());

    public static void uploadPhoto(UserUploadedPhotoEvent event, Driver driver) {
        log.debug("upload photo {}", event);

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("userIdValue", event.getUserId());
        parameters.put("uploadedAtValue", event.getUnixTime());
        parameters.put("photoIdValue", event.getPhotoId());

        try (Session session = driver.session()) {
            session.writeTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    tx.run(UPLOAD_PHOTO, parameters);
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

        try (Session session = driver.session()) {
            session.writeTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    tx.run(DELETE_PHOTO, parameters);
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
