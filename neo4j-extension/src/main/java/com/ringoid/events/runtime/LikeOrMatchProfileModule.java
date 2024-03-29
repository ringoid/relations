package com.ringoid.events.runtime;

import com.graphaware.common.log.LoggerFactory;
import com.graphaware.common.policy.inclusion.RelationshipInclusionPolicy;
import com.graphaware.runtime.config.FluentTxDrivenModuleConfiguration;
import com.graphaware.runtime.config.TxDrivenModuleConfiguration;
import com.graphaware.runtime.module.BaseTxDrivenModule;
import com.graphaware.runtime.module.DeliberateTransactionRollbackException;
import com.graphaware.tx.event.improved.api.ImprovedTransactionData;
import com.ringoid.Labels;
import com.ringoid.LikeProperties;
import com.ringoid.MatchProperties;
import com.ringoid.PersonProperties;
import com.ringoid.Relationships;
import com.ringoid.events.internal.events.PushObjectEvent;
import com.ringoid.events.internal.events.PushTypes;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.logging.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.ringoid.PersonProperties.NAME;
import static com.ringoid.events.push.PushUtils.mostViewPhotoThumbnail;

//for pushes
public class LikeOrMatchProfileModule extends BaseTxDrivenModule<List<PushObjectEvent>> {

    private final Log log = LoggerFactory.getLogger(getClass());

    private final TxDrivenModuleConfiguration configuration;
    private final Sender sender;
    private final GraphDatabaseService database;

    public LikeOrMatchProfileModule(String moduleId, String internalStreamName, String botSqsQueueUrl, String botStream, GraphDatabaseService database) {
        super(moduleId);
        this.sender = new Sender(internalStreamName, botSqsQueueUrl, botStream);
        this.configuration = FluentTxDrivenModuleConfiguration
                .defaultConfiguration()
                .with(
                        new RelationshipInclusionPolicy.Adapter() {
                            @Override
                            public boolean include(Relationship relationship) {
                                return relationship.isType(RelationshipType.withName(Relationships.LIKE.name())) ||
                                        relationship.isType(RelationshipType.withName(Relationships.MATCH.name()));
                            }
                        }
                );
        this.database = database;
    }

    @Override
    public TxDrivenModuleConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void afterCommit(List<PushObjectEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        this.sender.sendPushObjectEvents(events);
    }

    @Override
    public List<PushObjectEvent> beforeCommit(ImprovedTransactionData transactionData) throws DeliberateTransactionRollbackException {
        List<PushObjectEvent> list = new ArrayList<>();
        for (Relationship createdRel : transactionData.getAllCreatedRelationships()) {
            if (createdRel.isType(RelationshipType.withName(Relationships.LIKE.name())) ||
                    createdRel.isType(RelationshipType.withName(Relationships.MATCH.name()))) {
                Node source = createdRel.getStartNode();
                Node target = createdRel.getEndNode();
                if (source.hasLabel(Label.label(Labels.PERSON.getLabelName())) &&
                        target.hasLabel(Label.label(Labels.PERSON.getLabelName()))) {
                    String userId = (String) target.getProperty(PersonProperties.USER_ID.getPropertyName(), "n/a");
                    String sex = (String) target.getProperty(PersonProperties.SEX.getPropertyName(), "n/a");
                    long lastOnlineTime = (Long) target.getProperty(PersonProperties.LAST_ONLINE_TIME.getPropertyName(), 0L);
                    String locale = (String) target.getProperty(PersonProperties.SETTINGS_LOCALE.getPropertyName(), "n/a");
                    long newMessageCount = 0L;
                    long newProfiles = 0L;
                    long newMatchCount = 0L;
                    long newLikeCount = 0L;
                    String pushType = null;
                    if (createdRel.isType(RelationshipType.withName(Relationships.LIKE.name()))) {
                        newLikeCount = 1L;
                        pushType = PushTypes.NewLikeInternalEventType.getName();
                    } else {
                        newMatchCount = 1L;
                        pushType = PushTypes.NewMatchInternalEventType.getName();
                    }

                    boolean newLikeEnabled = (Boolean) target.getProperty(PersonProperties.SETTINGS_NEW_LIKE_PUSH.getPropertyName(), false);
                    boolean newMatchEnabled = (Boolean) target.getProperty(PersonProperties.SETTINGS_NEW_MATCH_PUSH.getPropertyName(), false);
                    boolean newMessageEnabled = (Boolean) target.getProperty(PersonProperties.SETTINGS_NEW_MESSAGE_PUSH.getPropertyName(), false);

                    String targetUserId = (String) target.getProperty(PersonProperties.USER_ID.getPropertyName(), "n/a");
                    String oppositeUserId = (String) source.getProperty(PersonProperties.USER_ID.getPropertyName(), "n/a");

                    PushObjectEvent event = new PushObjectEvent();
                    event.setUserId(userId);
                    event.setSex(sex);
                    event.setLastOnlineTime(lastOnlineTime);
                    event.setLocale(locale);
                    event.setNewMessageCount(newMessageCount);
                    event.setNewProfiles(newProfiles);
                    event.setNewMatchCount(newMatchCount);
                    event.setNewLikeCount(newLikeCount);
                    event.setPushType(pushType);
                    event.setEventType(pushType);
                    event.setNewLikeEnabled(newLikeEnabled);
                    event.setNewMatchEnabled(newMatchEnabled);
                    event.setNewMessageEnabled(newMessageEnabled);
                    event.setOppositeUserId(oppositeUserId);

                    String name = (String) source.getProperty(NAME.getPropertyName(), "unknown");
                    Long likedAt = (Long) createdRel.getProperty(LikeProperties.LIKE_AT.getPropertyName(), 0L);
                    Long matchAt = (Long) createdRel.getProperty(MatchProperties.MATCH_AT.getPropertyName(), 0L);
                    Long time = 0L;
                    if (likedAt != 0L) {
                        time = likedAt;
                    } else {
                        time = matchAt;
                    }

                    String thumb = mostViewPhotoThumbnail(targetUserId, oppositeUserId, false, database);
                    List<String> thumbs = new ArrayList<>(1);
                    if (!Objects.equals("n/a", thumb)) {
                        thumbs.add(thumb);
                    }

                    event.setName(name);
                    event.setTs(time);
                    event.setThumbnails(thumbs);

                    if (!thumbs.isEmpty()) {
                        list.add(event);
                    }
                }
            }
        }
        return list;
    }
}
