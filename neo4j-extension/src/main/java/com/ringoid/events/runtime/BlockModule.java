package com.ringoid.events.runtime;

import com.graphaware.common.policy.inclusion.RelationshipInclusionPolicy;
import com.graphaware.runtime.config.FluentTxDrivenModuleConfiguration;
import com.graphaware.runtime.config.TxDrivenModuleConfiguration;
import com.graphaware.runtime.module.BaseTxDrivenModule;
import com.graphaware.runtime.module.DeliberateTransactionRollbackException;
import com.graphaware.tx.event.improved.api.ImprovedTransactionData;
import com.ringoid.Labels;
import com.ringoid.PersonProperties;
import com.ringoid.Relationships;
import com.ringoid.events.actions.UserBlockOtherEvent;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;

import java.util.ArrayList;
import java.util.List;

import static com.ringoid.BlockProperties.BLOCK_REASON_NUM;
import static com.ringoid.events.EventTypes.ACTION_USER_BLOCK_OTHER;

public class BlockModule extends BaseTxDrivenModule<List<UserBlockOtherEvent>> {
    public static final long REPORT_REASON_TRESHOLD = 9L;

    private final TxDrivenModuleConfiguration configuration;
    private final Sender sender;

    public BlockModule(String moduleId, String internalStreamName, String botSqsQueueUrl, String botKinesis) {
        super(moduleId);
        this.sender = new Sender(internalStreamName, botSqsQueueUrl, botKinesis);
        this.configuration = FluentTxDrivenModuleConfiguration
                .defaultConfiguration()
                .with(
                        new RelationshipInclusionPolicy.Adapter() {
                            @Override
                            public boolean include(Relationship relationship) {
                                return relationship.isType(RelationshipType.withName(Relationships.BLOCK.name()));
                            }
                        }
                );
    }

    @Override
    public TxDrivenModuleConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void afterCommit(List<UserBlockOtherEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        sender.sendBlockEvents(events, REPORT_REASON_TRESHOLD);
    }

    @Override
    public List<UserBlockOtherEvent> beforeCommit(ImprovedTransactionData transactionData) throws DeliberateTransactionRollbackException {
        List<UserBlockOtherEvent> events = new ArrayList<>();
        for (Relationship block : transactionData.getAllCreatedRelationships()) {
            if (block.isType(RelationshipType.withName(Relationships.BLOCK.name()))) {
                Node startNode = block.getStartNode();
                Node endNode = block.getEndNode();
                if (startNode.hasLabel(Label.label(Labels.PERSON.getLabelName())) &&
                        endNode.hasLabel(Label.label(Labels.PERSON.getLabelName()))) {
                    long blockReason = (Long) block.getProperty(BLOCK_REASON_NUM.getPropertyName(), 0);
                    String userId = (String) startNode.getProperty(PersonProperties.USER_ID.getPropertyName());
                    String targetUserId = (String) endNode.getProperty(PersonProperties.USER_ID.getPropertyName());
                    UserBlockOtherEvent event = new UserBlockOtherEvent();
                    event.setEventType(ACTION_USER_BLOCK_OTHER.name());
                    event.setBlockReasonNum(blockReason);
                    event.setUserId(userId);
                    event.setTargetUserId(targetUserId);
                    events.add(event);
                }
            }

        }
        return events;
    }
}
