package com.ringoid.events.preparenf;

import com.graphaware.common.log.LoggerFactory;
import com.ringoid.PrepareNFRelationshipProperties;
import com.ringoid.Relationships;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.logging.Log;

import java.util.List;
import java.util.Objects;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.PersonProperties.PREPARED_NF_UNIX_TIME_IN_MILLIS;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.common.UtilsInternaly.getAllRelationshipBetweenNodes;
import static com.ringoid.common.UtilsInternaly.getOrCreateRelationship;

public class PrepareNFUtils {
    private static final Log log = LoggerFactory.getLogger(PrepareNFUtils.class);

    public static void deletePreparedNF(DeletePreviousPreparedNFEvent event, GraphDatabaseService database) {
        Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), event.getUserId());
        if (Objects.isNull(sourceUser)) {
            log.warn("delete prepared new faces event for not existing userId [%s]", event.getUserId());
            return;
        }
        Iterable<Relationship> allPrepared = sourceUser.getRelationships(Direction.OUTGOING, RelationshipType.withName(Relationships.PREPARE_NF.name()));
        int delCounter = 0;
        for (Relationship each : allPrepared) {
            each.delete();
            delCounter++;
        }
        log.info("delete [%s] old prepared new faces for userId [%s]", delCounter, event.getUserId());
    }

    public static void prepareNF(PrepareNFEvent event, GraphDatabaseService database) {
        Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), event.getUserId());
        if (Objects.isNull(sourceUser)) {
            log.warn("prepare new faces event for not existing userId [%s]", event.getUserId());
            return;
        }
        Node targetUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), event.getTargetUserId());
        if (Objects.isNull(sourceUser)) {
            log.warn("prepare new faces event for not existing targetUserId [%s]", event.getTargetUserId());
            return;
        }
        List<Relationship> allRels = getAllRelationshipBetweenNodes(sourceUser, targetUser);

        for (Relationship each : allRels) {
            if (each.isType(RelationshipType.withName(Relationships.LIKE.name())) ||
                    each.isType(RelationshipType.withName(Relationships.MATCH.name())) ||
                    each.isType(RelationshipType.withName(Relationships.MESSAGE.name())) ||
                    each.isType(RelationshipType.withName(Relationships.BLOCK.name()))) {
                log.warn("cann't prepare new faces for users who already like/match/message or block each other, " +
                        "userId [%s], targetUserId [%s]", event.getUserId(), event.getTargetUserId());
                return;
            }
            if ((each.isType(RelationshipType.withName(Relationships.VIEW_IN_LIKES_YOU.name())) ||
                    each.isType(RelationshipType.withName(Relationships.VIEW_IN_MATCHES.name())) ||
                    each.isType(RelationshipType.withName(Relationships.VIEW_IN_MESSAGES.name()))) &&
                    each.getStartNode().getId() == sourceUser.getId()) {
                log.warn("cann't prepare new faces, already seen profile, userId [%s], targetUserId [%s]", event.getUserId(), event.getTargetUserId());
                return;
            }

            if (!event.getAlreadySeen() &&
                    each.isType(RelationshipType.withName(Relationships.VIEW.name())) &&
                    each.getStartNode().getId() == sourceUser.getId()) {
                log.warn("cann't prepare new faces, already seen profile, userId [%s], targetUserId [%s]", event.getUserId(), event.getTargetUserId());
                return;
            }
        }

        Relationship prRel = getOrCreateRelationship(sourceUser, targetUser, Direction.OUTGOING, Relationships.PREPARE_NF.name());
        prRel.setProperty(PrepareNFRelationshipProperties.INDEX.getPropertyName(), event.getIndex());
        sourceUser.setProperty(PREPARED_NF_UNIX_TIME_IN_MILLIS.getPropertyName(), System.currentTimeMillis());
        log.debug("prepare node for new faces, userId [%s]", event.getUserId());
    }
}