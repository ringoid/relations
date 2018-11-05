package com.ringoid.common;

import com.ringoid.Relationships;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.PersonProperties.USER_ID;

public class Utils {
    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    private static final String NUM = "num";

    private static final String DO_WE_HAVE_BLOCK_QUERY =
            String.format("MATCH (sourceUser:%s {%s: $sourceUserId})-[r:%s]-(target:%s {%s: $targetUserId}) " +
                            "WHERE sourceUser.%s <> target.%s " +
                            "RETURN count(r) as %s",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.BLOCK.name(), PERSON.getLabelName(), USER_ID.getPropertyName(),
                    USER_ID.getPropertyName(), USER_ID.getPropertyName(),
                    NUM
            );


    public static boolean doWeHaveBlock(String userId, String otherUserId, Transaction tx) {
        log.debug("do we have a block between userId {} and other userId {}", userId, otherUserId);
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("sourceUserId", userId);
        parameters.put("targetUserId", otherUserId);
        StatementResult result = tx.run(DO_WE_HAVE_BLOCK_QUERY, parameters);
        List<Record> recordList = result.list();
        Record record = recordList.get(0);
        int num = record.get(NUM).asInt();
        log.debug("{} block relationships exist between userId {} and other userId {}",
                num, userId, otherUserId);
        return num > 0;
    }
}
