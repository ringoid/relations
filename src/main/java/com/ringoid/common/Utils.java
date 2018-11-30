package com.ringoid.common;

import com.ringoid.Relationships;
import com.ringoid.api.ProfileResponse;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.PersonProperties.LAST_ACTION_TIME;
import static com.ringoid.PersonProperties.USER_ID;

public class Utils {
    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    private static final String NUM = "num";
    private static final String OWN_LAST_ACTION_TIME = "lastActionTime";

    private static final String DO_WE_HAVE_BLOCK_QUERY =
            String.format("MATCH (sourceUser:%s {%s: $sourceUserId})-[r:%s]-(target:%s {%s: $targetUserId}) " +
                            "WHERE sourceUser.%s <> target.%s " +
                            "RETURN count(r) as %s",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.BLOCK.name(), PERSON.getLabelName(), USER_ID.getPropertyName(),
                    USER_ID.getPropertyName(), USER_ID.getPropertyName(),
                    NUM
            );

    private static final String GET_LAST_ACTION_TIME_QUERY =
            String.format(
                    "MATCH (sourceUser:%s {%s:$sourceUserId}) RETURN sourceUser.%s AS %s",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), LAST_ACTION_TIME.getPropertyName(), OWN_LAST_ACTION_TIME
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

    public static int lastActionTime(Map<String, Object> parameters, Transaction tx) {
        log.debug("last action time fot parameters {}", parameters);
        StatementResult result = tx.run(GET_LAST_ACTION_TIME_QUERY, parameters);
        List<Record> list = result.list();
        int ownLastActionTime = 0;
        for (Record each : list) {
            ownLastActionTime = each.get(OWN_LAST_ACTION_TIME).asInt();
        }
        return ownLastActionTime;
    }

    public static List<ProfileResponse> profileResponse(List<Map<String, List<String>>> receivedProfiles) {
        List<ProfileResponse> profiles = new ArrayList<>();

        for (Map<String, List<String>> eachProfileWithPhotos : receivedProfiles) {
            ProfileResponse profileResp = new ProfileResponse();
            for (Map.Entry<String, List<String>> eachEntry : eachProfileWithPhotos.entrySet()) {
                profileResp.setUserId(eachEntry.getKey());
                profileResp.setPhotoIds(eachEntry.getValue());
            }
            profiles.add(profileResp);
        }

        return profiles;
    }
}
