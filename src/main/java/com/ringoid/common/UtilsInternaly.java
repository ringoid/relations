package com.ringoid.common;

import com.ringoid.Relationships;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;

import java.util.HashMap;
import java.util.Map;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.PersonProperties.USER_ID;

public class UtilsInternaly {
    private static final String NUM = "num";

    private static final String DO_WE_HAVE_BLOCK_QUERY =
            String.format("MATCH (sourceUser:%s {%s: $sourceUserId})-[r:%s]-(target:%s {%s: $targetUserId}) " +
                            "WHERE sourceUser.%s <> target.%s " +
                            "RETURN count(r) as %s",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.BLOCK.name(), PERSON.getLabelName(), USER_ID.getPropertyName(),
                    USER_ID.getPropertyName(), USER_ID.getPropertyName(),
                    NUM
            );

    public static boolean doWeHaveBlockInternaly(String userId, String otherUserId, GraphDatabaseService database) {
//        log.debug("do we have a block between userId {} and other userId {}", userId, otherUserId);
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("sourceUserId", userId);
        parameters.put("targetUserId", otherUserId);
        Result result = database.execute(DO_WE_HAVE_BLOCK_QUERY, parameters);
        int num = ((Long) result.next().get(NUM)).intValue();
        return num > 0;
    }

}
