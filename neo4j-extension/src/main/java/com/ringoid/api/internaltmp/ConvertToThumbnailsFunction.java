package com.ringoid.api.internaltmp;

import com.graphaware.common.log.LoggerFactory;
import com.ringoid.PhotoProperties;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.logging.Log;

import java.util.HashMap;
import java.util.Map;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.Labels.RESIZED_PHOTO;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.Relationships.RESIZED;
import static com.ringoid.Relationships.UPLOAD_PHOTO;

public class ConvertToThumbnailsFunction {

    private final static Log log = LoggerFactory.getLogger(ConvertToThumbnailsFunction.class);

    private final static String CONVERT_QUERY =
            String.format(
                    "MATCH (n:%s)-[:%s]->(origin:%s)-[:%s]->(p:%s) " +
                            "RETURN n.%s as userId, origin.%s as s3Key, count(p) as countP " +
                            "ORDER BY userId SKIP $skipParam LIMIT $limitParam",
                    PERSON.getLabelName(), UPLOAD_PHOTO.name(), PHOTO.getLabelName(), RESIZED.name(), RESIZED_PHOTO.getLabelName(),
                    USER_ID.getPropertyName(), PhotoProperties.PHOTO_S3_KEY.getPropertyName()
            );

    public static ConvertResponse fetchForConvertion(ConvertRequest request, GraphDatabaseService database) {
        Map<String, Object> params = new HashMap<>();
        params.put("skipParam", request.getSkip());
        params.put("limitParam", request.getLimit());
        ConvertResponse response = new ConvertResponse();
        try (Transaction tx = database.beginTx()) {
            Result result = database.execute(CONVERT_QUERY, params);
            while (result.hasNext()) {
                Map<String, Object> mapResult = result.next();
                String userId = (String) mapResult.get("userId");
                String s3Key = (String) mapResult.get("s3Key");
                ConverObject obj = new ConverObject();
                obj.setObjectKey(s3Key);
                obj.setUserId(userId);
                response.getObjects().add(obj);
            }
            tx.success();
        }
        return response;
    }
}
