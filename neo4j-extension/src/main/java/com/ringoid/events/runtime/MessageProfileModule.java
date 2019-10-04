package com.ringoid.events.runtime;

import com.graphaware.common.policy.inclusion.BaseNodeInclusionPolicy;
import com.graphaware.runtime.config.FluentTxDrivenModuleConfiguration;
import com.graphaware.runtime.config.TxDrivenModuleConfiguration;
import com.graphaware.runtime.module.BaseTxDrivenModule;
import com.graphaware.runtime.module.DeliberateTransactionRollbackException;
import com.graphaware.tx.event.improved.api.ImprovedTransactionData;
import com.ringoid.Labels;
import com.ringoid.MessageProperties;
import com.ringoid.PersonProperties;
import com.ringoid.events.internal.events.PushObjectEvent;
import com.ringoid.events.internal.events.PushTypes;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.ringoid.PersonProperties.NAME;
import static com.ringoid.events.push.PushUtils.mostViewPhotoThumbnail;

public class MessageProfileModule extends BaseTxDrivenModule<List<PushObjectEvent>> {

    private final GraphDatabaseService database;
    private final TxDrivenModuleConfiguration configuration;
    private final Sender sender;

    public MessageProfileModule(GraphDatabaseService database, String moduleId, String internalStreamName, String botSqsQueueUrl, String botStream) {
        super(moduleId);
        this.database = database;
        this.sender = new Sender(internalStreamName, botSqsQueueUrl, botStream);
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
    public void afterCommit(List<PushObjectEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        sender.sendPushObjectEvents(events);
    }

    @Override
    public List<PushObjectEvent> beforeCommit(ImprovedTransactionData transactionData) throws DeliberateTransactionRollbackException {
        List<PushObjectEvent> result = new ArrayList<>();
        try (Transaction tx = database.beginTx()) {
            for (Node each : transactionData.getAllCreatedNodes()) {
                if (each.hasLabel(Label.label(Labels.MESSAGE.getLabelName()))) {
                    String targetUserId = (String) each.getProperty(MessageProperties.MSG_TARGET_USER_ID.getPropertyName());
                    String oppositeUserId = (String) each.getProperty(MessageProperties.MSG_SOURCE_USER_ID.getPropertyName());

                    if (Objects.nonNull(targetUserId) && Objects.nonNull(oppositeUserId)) {
                        Node targetUser = database.findNode(Label.label(Labels.PERSON.getLabelName()), PersonProperties.USER_ID.getPropertyName(), targetUserId);

                        if (Objects.nonNull(targetUser)) {

                            boolean newLikeEnabled = (Boolean) targetUser.getProperty(PersonProperties.SETTINGS_NEW_LIKE_PUSH.getPropertyName(), false);
                            boolean newMatchEnabled = (Boolean) targetUser.getProperty(PersonProperties.SETTINGS_NEW_MATCH_PUSH.getPropertyName(), false);
                            boolean newMessageEnabled = (Boolean) targetUser.getProperty(PersonProperties.SETTINGS_NEW_MESSAGE_PUSH.getPropertyName(), false);

                            String sex = (String) targetUser.getProperty(PersonProperties.SEX.getPropertyName(), "n/a");
                            long lastOnlineTime = (Long) targetUser.getProperty(PersonProperties.LAST_ONLINE_TIME.getPropertyName(), 0L);
                            String locale = (String) targetUser.getProperty(PersonProperties.SETTINGS_LOCALE.getPropertyName(), "n/a");
                            long newMessageCount = 1L;
                            long newProfiles = 0L;
                            long newMatchCount = 0L;
                            long newLikeCount = 0L;
                            String pushType = PushTypes.NewMessageInternalEventType.getName();

                            PushObjectEvent pushObjectEvent = new PushObjectEvent();
                            pushObjectEvent.setUserId(targetUserId);
                            pushObjectEvent.setSex(sex);
                            pushObjectEvent.setLastOnlineTime(lastOnlineTime);
                            pushObjectEvent.setLocale(locale);
                            pushObjectEvent.setNewMessageCount(newMessageCount);
                            pushObjectEvent.setNewProfiles(newProfiles);
                            pushObjectEvent.setNewLikeCount(newLikeCount);
                            pushObjectEvent.setNewMatchCount(newMatchCount);
                            pushObjectEvent.setPushType(pushType);
                            pushObjectEvent.setEventType(pushType);
                            pushObjectEvent.setNewLikeEnabled(newLikeEnabled);
                            pushObjectEvent.setNewMatchEnabled(newMatchEnabled);
                            pushObjectEvent.setNewMessageEnabled(newMessageEnabled);
                            pushObjectEvent.setOppositeUserId(oppositeUserId);

                            Node oppositeUser = database.findNode(Label.label(Labels.PERSON.getLabelName()), PersonProperties.USER_ID.getPropertyName(), oppositeUserId);
                            String name = (String) oppositeUser.getProperty(NAME.getPropertyName(), "unknown");
                            String msgText = (String) each.getProperty(MessageProperties.MSG_TEXT.getPropertyName(), "");
                            Long time = (Long) each.getProperty(MessageProperties.MSG_AT.getPropertyName(), 0L);

                            String thumb = mostViewPhotoThumbnail(targetUserId, oppositeUserId, true, database);
                            List<String> thumbs = new ArrayList<>(1);
                            if (!Objects.equals("n/a", thumb)) {
                                thumbs.add(thumb);
                            }
                            pushObjectEvent.setName(name);
                            pushObjectEvent.setText(msgText);
                            pushObjectEvent.setTs(time);
                            pushObjectEvent.setThumbnails(thumbs);

                            if (!thumbs.isEmpty()) {
                                result.add(pushObjectEvent);
                            }
                        }
                    }
                }
            }
            tx.success();
        }
        return result;
    }

    @Override
    public TxDrivenModuleConfiguration getConfiguration() {
        return configuration;
    }
}