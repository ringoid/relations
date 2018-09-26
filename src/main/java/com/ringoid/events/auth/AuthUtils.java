package com.ringoid.events.auth;

import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.TransactionWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.PersonProperties.CREATED;
import static com.ringoid.PersonProperties.LAST_ONLINE_TIME;
import static com.ringoid.PersonProperties.SAFE_DISTANCE_IN_METER;
import static com.ringoid.PersonProperties.SEX;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.PersonProperties.WHO_CAN_SEE_PHOTO;
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
                            "n.%s = $whoCanSeePhotoValue, " +
                            "n.%s = $safeDistanceInMeterValue " +
                            "ON MATCH SET " +
                            "n.%s = $whoCanSeePhotoValue, " +
                            "n.%s = $safeDistanceInMeterValue",
                    PERSON.getLabelName(), USER_ID.getPropertyName(),
                    WHO_CAN_SEE_PHOTO.getPropertyName(), SAFE_DISTANCE_IN_METER.getPropertyName(),
                    WHO_CAN_SEE_PHOTO.getPropertyName(), SAFE_DISTANCE_IN_METER.getPropertyName());


    private static final String UPDATE_USER_ONLINE_TIME =
            String.format("MERGE (n:%s {%s: $userIdValue}) " +
                            "ON MATCH SET " +
                            "n.%s = $onlineUserTime",
                    PERSON.getLabelName(), USER_ID.getPropertyName(),
                    LAST_ONLINE_TIME.getPropertyName());

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
        parameters.put("whoCanSeePhotoValue", event.getWhoCanSeePhoto());
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
