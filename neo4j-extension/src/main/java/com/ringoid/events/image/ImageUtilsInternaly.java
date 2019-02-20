package com.ringoid.events.image;

import com.ringoid.PhotoProperties;
import com.ringoid.Relationships;
import org.neo4j.graphdb.GraphDatabaseService;

import java.util.HashMap;
import java.util.Map;

import static com.ringoid.DeletePhotoProperties.HIDE_AT;
import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.PhotoProperties.PHOTO_ID;
import static com.ringoid.PhotoProperties.PHOTO_S3_KEY;
import static com.ringoid.PhotoProperties.PHOTO_UPLOADED;

public class ImageUtilsInternaly {

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

    public static void uploadPhotoInternaly(UserUploadedPhotoEvent event, GraphDatabaseService database) {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("userIdValue", event.getUserId());
        parameters.put("uploadedAtValue", event.getUnixTime());
        parameters.put("photoIdValue", event.getPhotoId());
        parameters.put("photoKey", event.getPhotoKey());
        database.execute(UPLOAD_PHOTO, parameters);
    }

    public static void deletePhotoInternaly(UserDeletePhotoEvent event, GraphDatabaseService database) {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("photoIdValue", event.getPhotoId());
        parameters.put("userIdValue", event.getUserId());
        parameters.put("hideAtValue", event.getUnixTime());
        String query = deletePhotoQuery(event.isUserTakePartInReport());
        database.execute(query, parameters);
    }
}
