package com.ringoid.common;

import com.ringoid.BlockProperties;
import com.ringoid.Labels;
import com.ringoid.Relationships;
import com.ringoid.events.runtime.BlockModule;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.ringoid.PersonProperties.LAST_ACTION_TIME;
import static com.ringoid.PhotoProperties.PHOTO_ID;

public class UtilsInternaly {

    public static void deleteUserConversations(Node sourceNode) {
        if (Objects.isNull(sourceNode)) {
            return;
        }
        Iterable<Relationship> takeParts = sourceNode.getRelationships(
                RelationshipType.withName(Relationships.TAKE_PART_IN_CONVERSATION.name()),
                Direction.OUTGOING);
        for (Relationship takePart : takeParts) {
            Node conversationNode = takePart.getOtherNode(sourceNode);
            if (conversationNode.hasLabel(Label.label(Labels.CONVERSATION.getLabelName()))) {
                boolean wasReported = false;
                //check that nobody in this conversation was not reported
                for (Relationship eachRel : conversationNode.getRelationships(
                        RelationshipType.withName(Relationships.TAKE_PART_IN_CONVERSATION.name()),
                        Direction.INCOMING
                )) {
                    Node other = eachRel.getOtherNode(conversationNode);
                    if (other.hasLabel(Label.label(Labels.PERSON.getLabelName()))) {
                        for (Relationship eachBlockRel : other.getRelationships(
                                RelationshipType.withName(Relationships.BLOCK.name()),
                                Direction.BOTH
                        )) {
                            long blockReasonNum = (Long) eachBlockRel.getProperty(BlockProperties.BLOCK_REASON_NUM.getPropertyName(), 0L);
                            if (blockReasonNum > BlockModule.REPORT_REASON_TRESHOLD) {
                                wasReported = true;
                            }
                        }
                    }
                }

                if (wasReported) {
                    break;
                }
                List<Node> messages = new ArrayList<>();
                messages = getFullConversation(conversationNode, messages);
                for (Node eachMessage : messages) {
                    for (Relationship eachRel : eachMessage.getRelationships()) {
                        eachRel.delete();
                    }
                    eachMessage.delete();
                }
                for (Relationship eachRel : conversationNode.getRelationships()) {
                    eachRel.delete();
                }
                conversationNode.delete();
            }
        }
    }

    public static List<Node> getFullConversation(Node startFrom, List<Node> sourceList) {
        Relationship msgPass = startFrom.getSingleRelationship(
                RelationshipType.withName(Relationships.PASS_MESSAGE.name()),
                Direction.OUTGOING
        );
        if (Objects.isNull(msgPass)) {
            return sourceList;
        }
        Node other = msgPass.getOtherNode(startFrom);
        if (other.hasLabel(Label.label(Labels.MESSAGE.getLabelName()))) {
            sourceList.add(other);
        }
        sourceList = getFullConversation(other, sourceList);
        return sourceList;
    }

    public static Relationship getOrCreateRelationship(Node source, Node target, Direction direction, String relType) {
        if (Objects.isNull(source) || Objects.isNull(target)) {
            return null;
        }
        Iterable<Relationship> rels = source.getRelationships(direction, RelationshipType.withName(relType));
        for (Relationship relationship : rels) {
            Node other = relationship.getOtherNode(source);
            if (other.getId() == target.getId()) {
                return relationship;
            }
        }
        return source.createRelationshipTo(target, RelationshipType.withName(relType));
    }

    public static Optional<Node> getUploadedPhoto(Node targetUser, String originPhotoId) {
        Iterable<Relationship> uploads = targetUser.getRelationships(Direction.OUTGOING, RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()));
        Node targetPhoto = null;
        for (Relationship uploadRel : uploads) {
            Node photoNode = uploadRel.getOtherNode(targetUser);
            if (photoNode.hasLabel(Label.label(Labels.PHOTO.getLabelName()))) {
                String photoId = (String) photoNode.getProperty(PHOTO_ID.getPropertyName());
                if (Objects.equals(photoId, originPhotoId)) {
                    targetPhoto = photoNode;
                    break;
                }
            }
        }
        return Optional.ofNullable(targetPhoto);
    }

    public static Set<Relationships> getAllRelationshipTypes(Node source, Node target, Direction direction) {
        Set<Relationships> result = new HashSet<>();
        Iterable<Relationship> rels = source.getRelationships(direction);
        for (Relationship rel : rels) {
            if (rel.getOtherNode(source).getId() == target.getId()) {
                result.add(Relationships.fromString(rel.getType().name()));
            }
        }
        result.remove(Relationships.UNSUPPORTED.name());
        return result;
    }

    public static List<Relationship> getAllRelationshipBetweenNodes(Node source, Node target) {
        Iterable<Relationship> rels = source.getRelationships();
        List<Relationship> result = new ArrayList<>();
        if (Objects.isNull(source) || Objects.isNull(target)) {
            return result;
        }
        for (Relationship each : rels) {
            if (each.getOtherNode(source).getId() == target.getId()) {
                result.add(each);
            }
        }
        return result;
    }

    public static void updateLastActionTime(Node sourceNode, long lastActionTime, GraphDatabaseService database) {
        long actualLastActionTime = (Long) sourceNode.getProperty(LAST_ACTION_TIME.getPropertyName(), 0L);
        if (lastActionTime >= actualLastActionTime) {
            sourceNode.setProperty(LAST_ACTION_TIME.getPropertyName(), lastActionTime);
        }
    }

    public static boolean doWeHaveBlockInternaly(Node sourceNode, Node targetNode, GraphDatabaseService database) {
        if (!sourceNode.hasRelationship(RelationshipType.withName(Relationships.BLOCK.name()))) {
            return false;
        }
        Iterable<Relationship> blocks = sourceNode.getRelationships(RelationshipType.withName(Relationships.BLOCK.name()));
        for (Relationship eachBlock : blocks) {
            Node other = eachBlock.getOtherNode(sourceNode);
            if (targetNode.getId() == other.getId()) {
                return true;
            }
        }
        return false;
    }
}
