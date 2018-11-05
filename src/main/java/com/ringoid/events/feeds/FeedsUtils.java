package com.ringoid.events.feeds;

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
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.WasReturnedToNewFacesProperties.TIME_TO_DEL;

public class FeedsUtils {
    private static final Logger log = LoggerFactory.getLogger(FeedsUtils.class);

    private static final String DELETE_OLD_VIEW =
            String.format(
                    "MATCH (sourceUser:%s {%s:$sourceUserId})-[r:%s]->(:%s) " +//1
                            "WHERE r.%s <= $currentTime " +//2
                            "DELETE r",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.WAS_RETURN_TO_NEW_FACES.name(), PERSON.getLabelName(),//1
                    TIME_TO_DEL.getPropertyName()//2

            );

    private static final String MARK_PROFILES =
            String.format(
                    "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                            "MATCH (targetUser:%s) WHERE targetUser.%s IN $userIdList " +//2
                            "AND (NOT (targetUser)-[:%s]-(sourceUser)) " +//2.5
                            "MERGE (sourceUser)-[r:%s]->(targetUser) " +//3
                            "ON CREATE SET r.%s = $timeToDelete",//4
                    PERSON.getLabelName(), USER_ID.getPropertyName(),//1
                    PERSON.getLabelName(), USER_ID.getPropertyName(),//2
                    Relationships.BLOCK.name(),//2.5
                    Relationships.WAS_RETURN_TO_NEW_FACES.name(),//3
                    TIME_TO_DEL.getPropertyName()//4
            );

    public static void markAlreadySeenProfiles(ProfileWasReturnToNewFacesEvent event, Driver driver) {
        int currentUnixTime = (int) (System.currentTimeMillis() / 1000);
        log.debug("mark already seen profiles, event {} for userId {} with currentUnixTime {}",
                event, event.getUserId(), currentUnixTime);
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("sourceUserId", event.getUserId());
        parameters.put("userIdList", event.getTargetUserIds());
        parameters.put("timeToDelete", event.getTimeToDelete());
        parameters.put("currentTime", currentUnixTime);

        try (Session session = driver.session()) {
            session.writeTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    StatementResult result = tx.run(DELETE_OLD_VIEW, parameters);
                    SummaryCounters counters = result.summary().counters();
                    log.info("{} old was return to new faces relationships were deleted from userId {}",
                            counters.relationshipsDeleted(), event.getUserId());
                    result = tx.run(MARK_PROFILES, parameters);
                    counters = result.summary().counters();
                    log.info("{} was return to new faces relationships were created from userId {}",
                            counters.relationshipsCreated(), event.getUserId());
                    return 1;
                }
            });
        } catch (Throwable throwable) {
            log.error("error mark already seen profiles, event {} for userId {}", event, event.getUserId(), throwable);
            throw throwable;
        }

        log.debug("successfully mark already seen profiles, event {} for userId {} with currentUnixTime {}",
                event, event.getUserId(), currentUnixTime);
    }
}
