package com.ringoid.events.auth;

import com.ringoid.Labels;
import com.ringoid.Relationships;
import com.ringoid.UserStatus;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.PersonProperties.CREATED;
import static com.ringoid.PersonProperties.LAST_ACTION_TIME;
import static com.ringoid.PersonProperties.LAST_ONLINE_TIME;
import static com.ringoid.PersonProperties.LIKE_COUNTER;
import static com.ringoid.PersonProperties.SAFE_DISTANCE_IN_METER;
import static com.ringoid.PersonProperties.SEX;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.PersonProperties.YEAR;
import static com.ringoid.UserStatus.ACTIVE;
import static com.ringoid.UserStatus.HIDDEN;

public class AuthUtilsInternaly {
    private static final String USER_ID_PROPERTY = "userIdProperty";

    private static final String CREATE_PROFILE =
            String.format("MERGE (n:%s {%s: $userIdValue}) " +
                            "ON CREATE SET " +
                            "n.%s = $sexValue, " +
                            "n.%s = $yearValue, " +
                            "n.%s = $createdValue, " +
                            "n.%s = 0, " +
                            "n.%s = 0, " +
                            "n.%s = $onlineUserTime " +
                            "ON MATCH SET " +
                            "n.%s = $sexValue, " +
                            "n.%s = $yearValue, " +
                            "n.%s = $createdValue, " +
                            "n.%s = 0, " +
                            "n.%s = $onlineUserTime",
                    PERSON.getLabelName(), USER_ID.getPropertyName(),
                    SEX.getPropertyName(), YEAR.getPropertyName(), CREATED.getPropertyName(), LAST_ACTION_TIME.getPropertyName(), LIKE_COUNTER.getPropertyName(), LAST_ONLINE_TIME.getPropertyName(),
                    SEX.getPropertyName(), YEAR.getPropertyName(), CREATED.getPropertyName(), LAST_ACTION_TIME.getPropertyName(), LAST_ONLINE_TIME.getPropertyName());

    private static final String UPDATE_SETTINGS =
            String.format("MERGE (n:%s {%s: $userIdValue}) " +
                            "ON CREATE SET " +
                            "n.%s = $safeDistanceInMeterValue " +
                            "ON MATCH SET " +
                            "n.%s = $safeDistanceInMeterValue",
                    PERSON.getLabelName(), USER_ID.getPropertyName(),
                    SAFE_DISTANCE_IN_METER.getPropertyName(),
                    SAFE_DISTANCE_IN_METER.getPropertyName());


    private static final String UPDATE_USER_ONLINE_TIME =
            String.format("MERGE (n:%s {%s: $userIdValue}) " +
                            "ON CREATE SET n.%s = $onlineUserTime " +
                            "ON MATCH SET n.%s = $onlineUserTime",
                    PERSON.getLabelName(), USER_ID.getPropertyName(),
                    LAST_ONLINE_TIME.getPropertyName(),
                    LAST_ONLINE_TIME.getPropertyName());

    private static final String GET_ALL_CONVERSATIONS =
            String.format("MATCH (source:%s {%s:$userIdValue})-[:%s]-(targetUser:%s) " +
                            "RETURN targetUser.%s AS %s",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.MESSAGE.name(), PERSON.getLabelName(),
                    USER_ID.getPropertyName(), USER_ID_PROPERTY
            );

    private static String deleteQuery(UserStatus userStatus, Map<String, Object> parameters) {
        switch (userStatus) {
            case ACTIVE: {
                //match (n:Person {user_id:"1"}) optional match (n)-[:UPLOAD_PHOTO]->(ph:Photo) detach delete n,ph
                return String.format(
                        "MATCH (n:%s {%s: $userIdValue}) " +//1
                                "OPTIONAL MATCH (n)-[:%s]->(ph:%s) " +//2
                                "DETACH DELETE n, ph",
                        PERSON.getLabelName(), USER_ID.getPropertyName(),//1
                        Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName()//2
                );
            }
            case HIDDEN: {
                //match (p:Person {user_id:1000}) SET p:Hidden
                return String.format(
                        "MATCH (n:%s {%s: $userIdValue}) SET n:%s",
                        PERSON.getLabelName(), USER_ID.getPropertyName(), Labels.HIDDEN.getLabelName());
            }
            default: {
//                log.error("unsupported user status {} with delete user request with params {}", userStatus, parameters);
                throw new IllegalArgumentException("unsupported user status " + userStatus);
            }
        }
    }

    public static void deleteUserInternaly(UserCallDeleteHimselfEvent event, GraphDatabaseService database) {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("userIdValue", event.getUserId());

        String query;
        List<String> targetIds = new ArrayList<>();
        if (Objects.equals(event.getUserReportStatus(), "TAKE_PART_IN_REPORT")) {
            query = deleteQuery(HIDDEN, parameters);
        } else {
            //we can collect all conversation ids for further deleting
            Result result = database.execute(GET_ALL_CONVERSATIONS, parameters);
            while (result.hasNext()) {
                String targetId = (String) result.next().get(USER_ID_PROPERTY);
                targetIds.add(targetId);
            }
            query = deleteQuery(ACTIVE, parameters);
        }
        database.execute(query, parameters);
        //todo:send notification
        //send events to internal queue
//        Utils.sendEventIntoInternalQueue(event, kinesis, streamName, event.getUserId(), gson);
//        for (String each : targetIds) {
//            DeleteUserConversationEvent deleteUserConversationEvent = new DeleteUserConversationEvent(event.getUserId(), each);
//            Utils.sendEventIntoInternalQueue(deleteUserConversationEvent, kinesis, streamName, event.getUserId(), gson);
//        }
    }

    public static void createProfileInternaly(UserProfileCreatedEvent event, GraphDatabaseService database) {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("userIdValue", event.getUserId());
        parameters.put("sexValue", event.getSex());
        parameters.put("yearValue", event.getYearOfBirth());
        parameters.put("createdValue", event.getUnixTime());
        parameters.put("onlineUserTime", event.getUnixTime());
        database.execute(CREATE_PROFILE, parameters);
    }

    public static void updateSettingsInternaly(UserSettingsUpdatedEvent event, GraphDatabaseService database) {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("userIdValue", event.getUserId());
        parameters.put("safeDistanceInMeterValue", event.getSafeDistanceInMeter());
        database.execute(UPDATE_SETTINGS, parameters);
    }

    public static void updateLastOnlineTimeInternaly(UserOnlineEvent event, GraphDatabaseService database) {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("userIdValue", event.getUserId());
        parameters.put("onlineUserTime", event.getUnixTime());
        database.execute(UPDATE_USER_ONLINE_TIME, parameters);
    }
}
