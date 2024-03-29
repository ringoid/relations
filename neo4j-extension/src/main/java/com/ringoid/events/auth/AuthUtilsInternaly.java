package com.ringoid.events.auth;

import com.ringoid.Labels;
import com.ringoid.PersonProperties;
import com.ringoid.Relationships;
import com.ringoid.UserStatus;
import com.ringoid.common.UtilsInternaly;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.Labels.RESIZED_PHOTO;
import static com.ringoid.PersonProperties.ABOUT;
import static com.ringoid.PersonProperties.CHILDREN;
import static com.ringoid.PersonProperties.COMPANY;
import static com.ringoid.PersonProperties.CREATED;
import static com.ringoid.PersonProperties.EDUCATION_TEXT;
import static com.ringoid.PersonProperties.EDU_LEVEL;
import static com.ringoid.PersonProperties.HAIR_COLOR;
import static com.ringoid.PersonProperties.HEIGHT;
import static com.ringoid.PersonProperties.INCOME;
import static com.ringoid.PersonProperties.INSTAGRAM;
import static com.ringoid.PersonProperties.JOB_TITLE;
import static com.ringoid.PersonProperties.LAST_ACTION_TIME;
import static com.ringoid.PersonProperties.LAST_ONLINE_TIME;
import static com.ringoid.PersonProperties.LIKE_COUNTER;
import static com.ringoid.PersonProperties.NAME;
import static com.ringoid.PersonProperties.PRIVATE_KEY;
import static com.ringoid.PersonProperties.PROPERTY;
import static com.ringoid.PersonProperties.REFERRAL_ID;
import static com.ringoid.PersonProperties.SETTINGS_LOCALE;
import static com.ringoid.PersonProperties.SETTINGS_NEW_LIKE_PUSH;
import static com.ringoid.PersonProperties.SETTINGS_NEW_MATCH_PUSH;
import static com.ringoid.PersonProperties.SETTINGS_NEW_MESSAGE_PUSH;
import static com.ringoid.PersonProperties.SETTINGS_PUSH;
import static com.ringoid.PersonProperties.SETTINGS_TIMEZONE;
import static com.ringoid.PersonProperties.SEX;
import static com.ringoid.PersonProperties.STATUS_TEXT;
import static com.ringoid.PersonProperties.TIKTOK;
import static com.ringoid.PersonProperties.TRANSPORT;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.PersonProperties.WHERE_I_FROM;
import static com.ringoid.PersonProperties.WHERE_I_LIVE;
import static com.ringoid.PersonProperties.YEAR;
import static com.ringoid.UserStatus.ACTIVE;
import static com.ringoid.UserStatus.HIDDEN;

public class AuthUtilsInternaly {
    private static final String USER_ID_PROPERTY = "userIdProperty";

    private final static String UPDATE_PROFILE =
            String.format(
                    "MATCH (n:%s {%s: $userIdValue}) " +
                            "SET n.%s = $property, n.%s = $transport, n.%s = $income, n.%s = $height, n.%s = $educationLevel, n.%s = $hairColor, n.%s = $children, " +
                            "n.%s = $name, n.%s = $jobTitle, n.%s = $company, n.%s = $education, n.%s = $about, n.%s = $instagram, n.%s = $tikTok, n.%s = $whereLive, n.%s = $whereFrom, n.%s = $statusText",
                    PERSON.getLabelName(), USER_ID.getPropertyName(),
                    PROPERTY.getPropertyName(), TRANSPORT.getPropertyName(), INCOME.getPropertyName(), HEIGHT.getPropertyName(), EDU_LEVEL.getPropertyName(), HAIR_COLOR.getPropertyName(), CHILDREN.getPropertyName(),
                    NAME.getPropertyName(), JOB_TITLE.getPropertyName(), COMPANY.getPropertyName(), EDUCATION_TEXT.getPropertyName(), ABOUT.getPropertyName(), INSTAGRAM.getPropertyName(), TIKTOK.getPropertyName(), WHERE_I_LIVE.getPropertyName(), WHERE_I_FROM.getPropertyName(), STATUS_TEXT.getPropertyName()
            );

    private final static String HIDE_PROFILE =
            String.format(
                    "MATCH (n:%s {%s: $userIdValue}) SET n:%s",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), Labels.HIDDEN.getLabelName()
            );


    private static final String CREATE_PROFILE =
            String.format("MERGE (n:%s {%s: $userIdValue}) " +
                            "ON CREATE SET " +
                            "n.%s = $sexValue, " +
                            "n.%s = $yearValue, " +
                            "n.%s = $createdValue, " +
                            "n.%s = 0, " +
                            "n.%s = 0, " +
                            "n.%s = $referral, " +
                            "n.%s = $privateKey, " +
                            "n.%s = $onlineUserTime",
                    PERSON.getLabelName(), USER_ID.getPropertyName(),
                    SEX.getPropertyName(), YEAR.getPropertyName(), CREATED.getPropertyName(), LAST_ACTION_TIME.getPropertyName(), LIKE_COUNTER.getPropertyName(), REFERRAL_ID.getPropertyName(), PRIVATE_KEY.getPropertyName(), LAST_ONLINE_TIME.getPropertyName());

    private static final String CLAIM_REFERRAL_CODE =
            String.format("MATCH (n:%s {%s: $userIdValue}) " +
                            "SET n.%s = $referral",
                    PERSON.getLabelName(), USER_ID.getPropertyName(),
                    REFERRAL_ID.getPropertyName());

    private static final String UPDATE_USER_ONLINE_TIME =
            String.format("MATCH (n:%s {%s: $userIdValue}) " +
                            "SET n.%s = $onlineUserTime",
                    PERSON.getLabelName(), USER_ID.getPropertyName(),
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
                                "WITH n, ph " +//3
                                "OPTIONAL MATCH (ph)-[:%s]->(resP:%s) " +//4
                                "DETACH DELETE n, ph, resP",
                        PERSON.getLabelName(), USER_ID.getPropertyName(),//1
                        Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//2
                        Relationships.RESIZED.name(), RESIZED_PHOTO.getLabelName()
                );
            }
            case HIDDEN: {
                //match (p:Person {user_id:1000}) SET p:Hidden
                return String.format(
                        "MATCH (n:%s {%s: $userIdValue}) SET n:%s WITH n " +//1
                                "OPTIONAL MATCH (n)-[:%s]->(ph:%s) " +//2
                                "WITH n, ph " +//3
                                "OPTIONAL MATCH (ph)-[:%s]->(resP:%s) " +//4
                                "DETACH DELETE resP",
                        PERSON.getLabelName(), USER_ID.getPropertyName(), Labels.HIDDEN.getLabelName(),//1
                        Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//2
                        Relationships.RESIZED.name(), RESIZED_PHOTO.getLabelName()//4
                );
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
            //first delete user conversations
            Node sourceUser = database.findNode(Label.label(Labels.PERSON.getLabelName()),
                    PersonProperties.USER_ID.getPropertyName(),
                    event.getUserId());
            UtilsInternaly.deleteUserConversations(sourceUser);
            //then delete user
            query = deleteQuery(ACTIVE, parameters);
        }
        database.execute(query, parameters);
    }

    public static void createProfileInternaly(UserProfileCreatedEvent event, GraphDatabaseService database) {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("userIdValue", event.getUserId());
        parameters.put("sexValue", event.getSex());
        parameters.put("yearValue", event.getYearOfBirth());
        parameters.put("referral", event.getReferralId());
        parameters.put("privateKey", event.getPrivateKey());
        parameters.put("createdValue", event.getUnixTime());
        parameters.put("onlineUserTime", event.getUnixTime());
        database.execute(CREATE_PROFILE, parameters);

        LocalDate localDate = LocalDate.now();
        int minYear = localDate.getYear() - 18;
        if (event.getYearOfBirth() > minYear) {
            database.execute(HIDE_PROFILE, parameters);
        }
    }

    public static void claimReferralCodeInternaly(UserClaimReferralCodeEvent event, GraphDatabaseService database) {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("userIdValue", event.getUserId());
        parameters.put("referral", event.getReferralId());
        database.execute(CLAIM_REFERRAL_CODE, parameters);
    }

    public static void updateLastOnlineTimeInternaly(UserOnlineEvent event, GraphDatabaseService database) {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("userIdValue", event.getUserId());
        parameters.put("onlineUserTime", event.getUnixTime());
        database.execute(UPDATE_USER_ONLINE_TIME, parameters);
    }

    public static void updateProfile(UserUpdateProfileEvent event, GraphDatabaseService database) {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("userIdValue", event.getUserId());
        parameters.put("property", event.getProperty());
        parameters.put("transport", event.getTransport());
        parameters.put("income", event.getIncome());
        parameters.put("height", event.getHeight());
        parameters.put("educationLevel", event.getEducationLevel());
        parameters.put("hairColor", event.getHairColor());
        parameters.put("children", event.getChildren());

        parameters.put("name", event.getName());
        parameters.put("jobTitle", event.getJobTitle());
        parameters.put("company", event.getCompany());
        parameters.put("education", event.getEducation());
        parameters.put("about", event.getAbout());
        parameters.put("instagram", event.getInstagram());
        parameters.put("tikTok", event.getTikTok());
        parameters.put("whereLive", event.getWhereLive());
        parameters.put("whereFrom", event.getWhereFrom());
        parameters.put("statusText", event.getStatusText());

        database.execute(UPDATE_PROFILE, parameters);
    }

    public static void updateSettings(UserSettingsUpdatedEvent event, GraphDatabaseService database) {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("userIdValue", event.getUserId());
        if (Objects.nonNull(event.getWasLocaleChanged()) && event.getWasLocaleChanged()) {
            parameters.put("locale", event.getLocale());
            String query = String.format(
                    "MATCH (n:%s {%s: $userIdValue}) SET n.%s = $locale",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), SETTINGS_LOCALE.getPropertyName()
            );
            database.execute(query, parameters);
        }
        if (Objects.nonNull(event.getWasPushChanged()) && event.getWasPushChanged()) {
            parameters.put("push", event.getPush());
            String query = String.format(
                    "MATCH (n:%s {%s: $userIdValue}) SET n.%s = $push",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), SETTINGS_PUSH.getPropertyName()
            );
            database.execute(query, parameters);
        }

        if (Objects.nonNull(event.getWasPushNewLikeChanged()) && event.getWasPushNewLikeChanged()) {
            parameters.put("pushNewLike", event.getPushNewLike());
            String query = String.format(
                    "MATCH (n:%s {%s: $userIdValue}) SET n.%s = $pushNewLike",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), SETTINGS_NEW_LIKE_PUSH.getPropertyName()
            );
            database.execute(query, parameters);
        }

        if (Objects.nonNull(event.getWasPushNewMatchChanged()) && event.getWasPushNewMatchChanged()) {
            parameters.put("pushNewMatch", event.getPushNewMatch());
            String query = String.format(
                    "MATCH (n:%s {%s: $userIdValue}) SET n.%s = $pushNewMatch",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), SETTINGS_NEW_MATCH_PUSH.getPropertyName()
            );
            database.execute(query, parameters);
        }

        if (Objects.nonNull(event.getWasPushNewMessageChanged()) && event.getWasPushNewMessageChanged()) {
            parameters.put("pushNewMessage", event.getPushNewMessage());
            String query = String.format(
                    "MATCH (n:%s {%s: $userIdValue}) SET n.%s = $pushNewMessage",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), SETTINGS_NEW_MESSAGE_PUSH.getPropertyName()
            );
            database.execute(query, parameters);
        }
        if (Objects.nonNull(event.getWasTimeZoneChanged()) && event.getWasTimeZoneChanged()) {
            parameters.put("timezone", event.getTimeZone());
            String query = String.format(
                    "MATCH (n:%s {%s: $userIdValue}) SET n.%s = $timezone",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), SETTINGS_TIMEZONE.getPropertyName()
            );
            database.execute(query, parameters);
        }
    }
}
