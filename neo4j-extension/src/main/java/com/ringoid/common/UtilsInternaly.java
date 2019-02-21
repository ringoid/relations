package com.ringoid.common;

import com.ringoid.Labels;
import com.ringoid.Relationships;
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

    public static Relationship getOrCreate(Node source, Node target, Direction direction, String relType) {
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
        for (Relationship each : rels) {
            if (each.getOtherNode(source).getId() == target.getId()) {
                result.add(each);
            }
        }
        return result;
    }

    public static void updateLastActionTime(Node sourceNode, long lastActionTime, GraphDatabaseService database) {
        sourceNode.setProperty(LAST_ACTION_TIME.getPropertyName(), lastActionTime);
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
