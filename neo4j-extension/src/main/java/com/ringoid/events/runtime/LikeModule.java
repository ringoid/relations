package com.ringoid.events.runtime;

import com.graphaware.common.policy.inclusion.RelationshipInclusionPolicy;
import com.graphaware.runtime.config.FluentTxDrivenModuleConfiguration;
import com.graphaware.runtime.config.TxDrivenModuleConfiguration;
import com.graphaware.runtime.module.BaseTxDrivenModule;
import com.graphaware.runtime.module.DeliberateTransactionRollbackException;
import com.graphaware.tx.event.improved.api.ImprovedTransactionData;
import com.ringoid.Labels;
import com.ringoid.PersonProperties;
import com.ringoid.PhotoProperties;
import com.ringoid.Relationships;
import com.ringoid.events.internal.events.PhotoLikeEvent;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class LikeModule extends BaseTxDrivenModule<List<PhotoLikeEvent>> {

    private final TxDrivenModuleConfiguration configuration;
    private final GraphDatabaseService database;

    private final Sender sender;
    private final boolean botEnabled;

    public LikeModule(GraphDatabaseService database, String moduleId, String internalStreamName, String botSqsQueueUrl, boolean botEnabled, String botKinesis) {
        super(moduleId);
        this.database = database;
        this.sender = new Sender(internalStreamName, botSqsQueueUrl, botKinesis);
        this.botEnabled = botEnabled;
        this.configuration = FluentTxDrivenModuleConfiguration
                .defaultConfiguration()
                .with(
                        new RelationshipInclusionPolicy.Adapter() {
                            @Override
                            public boolean include(Relationship relationship) {
                                return relationship.isType(RelationshipType.withName(Relationships.LIKE.name()));
                            }
                        }
                );
    }

    @Override
    public TxDrivenModuleConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void afterCommit(List<PhotoLikeEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        sender.sendLikeEvents(events, botEnabled);
    }

    @Override
    public List<PhotoLikeEvent> beforeCommit(ImprovedTransactionData transactionData) throws DeliberateTransactionRollbackException {
        List<PhotoLikeEvent> events = new ArrayList<>();
        for (Relationship like : transactionData.getAllCreatedRelationships()) {
            if (like.isType(RelationshipType.withName(Relationships.LIKE.name()))) {
                Node startNode = like.getStartNode();
                Node endNode = like.getEndNode();
                if (startNode.hasLabel(Label.label(Labels.PERSON.getLabelName())) &&
                        endNode.hasLabel(Label.label(Labels.PHOTO.getLabelName()))) {

                    String sourceOfLikeUserId = (String) startNode.getProperty(PersonProperties.USER_ID.getPropertyName());
                    String originPhotoId = (String) endNode.getProperty(PhotoProperties.PHOTO_ID.getPropertyName());
                    Optional<String> targetUserIdOpt = getPhotoOwnerUserId(originPhotoId);
                    if (targetUserIdOpt.isPresent()) {
                        PhotoLikeEvent event = new PhotoLikeEvent(targetUserIdOpt.get(), originPhotoId);
                        event.setSourceOfLikeUserId(sourceOfLikeUserId);
                        events.add(event);
                    }
                }
            }
        }
        return events;
    }

    private Optional<String> getPhotoOwnerUserId(String photoId) {
        String result = null;
        try (Transaction tx = database.beginTx()) {
            Node photo = database.findNode(Label.label(Labels.PHOTO.getLabelName()),
                    PhotoProperties.PHOTO_ID.getPropertyName(), photoId);
            if (Objects.nonNull(photo)) {
                Relationship upload = photo.getSingleRelationship(RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()), Direction.INCOMING);
                if (Objects.nonNull(upload)) {
                    Node other = upload.getOtherNode(photo);
                    if (other.hasLabel(Label.label(Labels.PERSON.getLabelName()))) {
                        String userId = (String) other.getProperty(PersonProperties.USER_ID.getPropertyName());
                        result = userId;
                    }
                }
            }
            tx.success();
        }
        return Optional.ofNullable(result);
    }
}
