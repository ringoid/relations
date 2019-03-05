package com.ringoid.events.runtime;

import com.graphaware.common.policy.inclusion.BaseNodeInclusionPolicy;
import com.graphaware.runtime.config.FluentTxDrivenModuleConfiguration;
import com.graphaware.runtime.config.TxDrivenModuleConfiguration;
import com.graphaware.runtime.module.BaseTxDrivenModule;
import com.graphaware.runtime.module.DeliberateTransactionRollbackException;
import com.graphaware.tx.event.improved.api.ImprovedTransactionData;
import com.ringoid.Labels;
import com.ringoid.MessageProperties;
import com.ringoid.events.internal.events.MessageBotEvent;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;

import java.util.ArrayList;
import java.util.List;

public class MessageModule extends BaseTxDrivenModule<List<MessageBotEvent>> {

    private final TxDrivenModuleConfiguration configuration;
    private final Sender sender;
    private final boolean botEnabled;

    public MessageModule(String moduleId, String internalStreamName, String botSqsQueueUrl, boolean botEnabled) {
        super(moduleId);
        this.sender = new Sender(internalStreamName, botSqsQueueUrl);
        this.botEnabled = botEnabled;
        this.configuration = FluentTxDrivenModuleConfiguration
                .defaultConfiguration()
                .with(new BaseNodeInclusionPolicy() {
                          @Override
                          public boolean include(Node node) {
                              if (node.hasLabel(Label.label(Labels.MESSAGE.getLabelName()))) {
                                  return true;
                              }
                              return false;
                          }
                      }
                );
    }

    @Override
    public void afterCommit(List<MessageBotEvent> events) {
        //todo:place for optimization (using batch)
        for (MessageBotEvent each : events) {
            sender.sendBotEvent(each);
        }
    }

    @Override
    public List<MessageBotEvent> beforeCommit(ImprovedTransactionData transactionData) throws DeliberateTransactionRollbackException {
        List<MessageBotEvent> result = new ArrayList<>();
        if (!botEnabled) {
            return result;
        }
        for (Node each : transactionData.getAllCreatedNodes()) {
            if (each.hasLabel(Label.label(Labels.MESSAGE.getLabelName()))) {
                String userId = (String) each.getProperty(MessageProperties.MSG_SOURCE_USER_ID.getPropertyName());
                String targetUserId = (String) each.getProperty(MessageProperties.MSG_TARGET_USER_ID.getPropertyName());
                String text = (String) each.getProperty(MessageProperties.MSG_TEXT.getPropertyName());
                MessageBotEvent messageBotEvent = new MessageBotEvent(userId, targetUserId, text);
                result.add(messageBotEvent);
            }
        }
        return result;
    }

    @Override
    public TxDrivenModuleConfiguration getConfiguration() {
        return configuration;
    }

}
