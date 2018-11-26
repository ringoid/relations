package com.ringoid.events.auth;

import com.ringoid.Relationships;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.TransactionWork;
import org.neo4j.driver.v1.summary.SummaryCounters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.PersonProperties.CREATED;
import static com.ringoid.PersonProperties.LAST_ONLINE_TIME;
import static com.ringoid.PersonProperties.SAFE_DISTANCE_IN_METER;
import static com.ringoid.PersonProperties.SEX;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.PersonProperties.YEAR;

public class AuthUtils {
    private static final Logger log = LoggerFactory.getLogger(AuthUtils.class);

    private static final String CREATE_PROFILE =
            String.format("MERGE (n:%s {%s: $userIdValue}) " +
                            "ON CREATE SET " +
                            "n.%s = $sexValue, " +
                            "n.%s = $yearValue, " +
                            "n.%s = $createdValue " +
                            "ON MATCH SET " +
                            "n.%s = $sexValue, " +
                            "n.%s = $yearValue, " +
                            "n.%s = $createdValue, " +
                            "n.%s = $onlineUserTime",
                    PERSON.getLabelName(), USER_ID.getPropertyName(),
                    SEX.getPropertyName(), YEAR.getPropertyName(), CREATED.getPropertyName(),
                    SEX.getPropertyName(), YEAR.getPropertyName(), CREATED.getPropertyName(),
                    LAST_ONLINE_TIME.getPropertyName());

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
                            "ON MATCH SET " +
                            "n.%s = $onlineUserTime",
                    PERSON.getLabelName(), USER_ID.getPropertyName(),
                    LAST_ONLINE_TIME.getPropertyName());

    //match (p:Person {user_id:1000})-[:UPLOAD]->(ph:Photo) detach delete ph,p
    private static final String DELETE_USER =
            String.format(
                    "MATCH (n:%s {%s: $userIdValue})-[:%s]->(ph:%s) DETACH DELETE ph,n",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName());

    public static void deleteUser(UserCallDeleteHimselfEvent event, Driver driver) {
        log.debug("delete user {}", event);

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("userIdValue", event.getUserId());

        try (Session session = driver.session()) {
            session.writeTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    StatementResult result = tx.run(DELETE_USER, parameters);
                    SummaryCounters counters = result.summary().counters();
                    log.info("{} relationships were deleted, {} nodes where deleted where drop userId {}",
                            counters.relationshipsDeleted(), counters.nodesDeleted(), event.getUserId());
                    return 1;
                }
            });
        } catch (Throwable throwable) {
            log.error("error delete user {}", event, throwable);
            throw throwable;
        }
        log.info("successfully delete user {}", event);
    }

    public static void createProfile(UserProfileCreatedEvent event, Driver driver) {
        log.debug("create profile {}", event);

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("userIdValue", event.getUserId());
        parameters.put("sexValue", event.getSex());
        parameters.put("yearValue", event.getYearOfBirth());
        parameters.put("createdValue", event.getUnixTime());
        parameters.put("onlineUserTime", event.getUnixTime());

        try (Session session = driver.session()) {
            session.writeTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    tx.run(CREATE_PROFILE, parameters);
                    return 1;
                }
            });
        } catch (Throwable throwable) {
            log.error("error create profile {}", event, throwable);
            throw throwable;
        }
        log.info("successfully create profile {}", event);
    }

    public static void updateSettings(UserSettingsUpdatedEvent event, Driver driver) {
        log.debug("update profile settings {}", event);

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("userIdValue", event.getUserId());
        parameters.put("safeDistanceInMeterValue", event.getSafeDistanceInMeter());

        try (Session session = driver.session()) {
            session.writeTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    tx.run(UPDATE_SETTINGS, parameters);
                    return 1;
                }
            });
        } catch (Throwable throwable) {
            log.error("error update profile settings {}", event, throwable);
            throw throwable;
        }
        log.info("successfully update profile settings {}", event);
    }

    public static void updateLastOnlineTime(UserOnlineEvent event, Driver driver) {
        log.debug("update user online time {}", event);

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("userIdValue", event.getUserId());
        parameters.put("onlineUserTime", event.getUnixTime());

        try (Session session = driver.session()) {
            session.writeTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    tx.run(UPDATE_USER_ONLINE_TIME, parameters);
                    return 1;
                }
            });
        } catch (Throwable throwable) {
            log.error("error update user online time {}", event, throwable);
            throw throwable;
        }
        log.info("successfully update user online time {}", event);
    }
}
