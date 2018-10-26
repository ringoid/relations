package com.ringoid.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.ringoid.Relationships;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Config;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.TransactionWork;
import org.neo4j.driver.v1.summary.SummaryCounters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.PhotoProperties.PHOTO_ID;

public class NewFaces {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Driver driver;

    private static final String TARGET_USER_ID = "targetUserId";
    private static final String TARGET_PHOTO_ID = "targetPhotoId";

    private final static String NEW_FACES_REQUEST =
            String.format("MATCH (n:%s)-[:%s]->(ph:%s) " +
                            "WHERE n.%s <> $sourceUserId " +

                            "AND (NOT (n)-[]-(:%s {%s: $sourceUserId})) " +
                            "RETURN n.%s as %s, ph.%s as %s",
                    PERSON.getLabelName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),
                    USER_ID.getPropertyName(),

                    PERSON.getLabelName(), USER_ID.getPropertyName(),
                    USER_ID.getPropertyName(), TARGET_USER_ID, PHOTO_ID.getPropertyName(), TARGET_PHOTO_ID
            );


    public NewFaces() {
        String neo4jUri = System.getenv("NEO4J_URI");
        //todo:read these value from Security Storage
        String userName = System.getenv("NEO4J_USER");
        String password = System.getenv("NEO4J_PASSWORD");

        driver = GraphDatabase.driver(neo4jUri, AuthTokens.basic(userName, password),
                Config.build().withMaxTransactionRetryTime(10, TimeUnit.SECONDS).toConfig());
    }

//    public void handler(NewFacesRequest request, Context context) {
//        log.debug("handle new face request {}", request);
//        final Map<String, Object> parameters = new HashMap<>();
//        parameters.put("sourceUserId", request.getUserId());
//
//        parameters.put("targetUserId", event.getTargetUserId());
//        parameters.put("blockedAt", event.getBlockedAt());
//    }

//    public static void newFaces(NewFacesRequest request) {
//        log.debug("block user event {} for userId {}", event, event.getUserId());
//        final Map<String, Object> parameters = new HashMap<>();
//        parameters.put("sourceUserId", event.getUserId());
//        parameters.put("targetUserId", event.getTargetUserId());
//        parameters.put("blockedAt", event.getBlockedAt());
//
//        try (Session session = driver.session()) {
//            session.writeTransaction(new TransactionWork<Integer>() {
//                @Override
//                public Integer execute(Transaction tx) {
//
//                    if (doWeHaveBlock(event.getUserId(), event.getTargetUserId(), tx)) {
//                        log.warn("BLOCK already exist between source userId {} and target userId {}, can not block",
//                                event.getUserId(), event.getTargetUserId());
//                        return 1;
//                    }
//
//                    StatementResult result = tx.run(DELETE_ALL_INCOMING_PHOTO_RELATIONSHIPS_FROM_BLOCKED_PROFILE_QUERY, parameters);
//                    SummaryCounters counters = result.summary().counters();
//                    log.info("{} relationships were deleted between source user's photo with userId {} and blocked userId {}",
//                            counters.relationshipsDeleted(), event.getUserId(), event.getTargetUserId());
//                    result = tx.run(DELETE_ALL_OUTGOUING_PHOTO_RELATIONSHIPS_WITH_BLOCKED_PROFILE_QUERY, parameters);
//                    counters = result.summary().counters();
//                    log.info("{} relationships were deleted between blocked user's photo with userId {} and source userId {}",
//                            counters.relationshipsDeleted(), event.getTargetUserId(), event.getUserId());
//                    result = tx.run(DELETE_ALL_RELATIONSHIPS_BETWEEN_PROFILES_QUERY, parameters);
//                    counters = result.summary().counters();
//                    log.info("{} relationships were deleted between source profile userId {} and target profile userId {}",
//                            counters.relationshipsDeleted(), event.getUserId(), event.getTargetUserId());
//                    result = tx.run(CREATE_BLOCK_QUERY, parameters);
//                    counters = result.summary().counters();
//                    log.info("{} block relationships were created between source profile userId {} and target profile userId {}",
//                            counters.relationshipsCreated(), event.getUserId(), event.getTargetUserId());
//                    return 1;
//                }
//            });
//        } catch (Throwable throwable) {
//            log.error("error block user event {} for userId {}", event, event.getUserId(), throwable);
//            throw throwable;
//        }
//    }
}
