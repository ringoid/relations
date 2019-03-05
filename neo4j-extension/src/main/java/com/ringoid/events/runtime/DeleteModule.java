package com.ringoid.events.runtime;

import com.graphaware.common.policy.inclusion.BaseNodeInclusionPolicy;
import com.graphaware.common.util.Change;
import com.graphaware.runtime.config.FluentTxDrivenModuleConfiguration;
import com.graphaware.runtime.config.TxDrivenModuleConfiguration;
import com.graphaware.runtime.module.BaseTxDrivenModule;
import com.graphaware.runtime.module.DeliberateTransactionRollbackException;
import com.graphaware.tx.event.improved.api.ImprovedTransactionData;
import com.ringoid.Labels;
import com.ringoid.PersonProperties;
import com.ringoid.events.auth.UserCallDeleteHimselfEvent;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;

import java.util.ArrayList;
import java.util.List;

import static com.ringoid.events.EventTypes.AUTH_USER_CALL_DELETE_HIMSELF;

public class DeleteModule extends BaseTxDrivenModule<List<UserCallDeleteHimselfEvent>> {

    private final TxDrivenModuleConfiguration configuration;
    private final Sender sender;

    public DeleteModule(String moduleId, String internalStreamName, String botSqsQueueUrl) {
        super(moduleId);
        this.sender = new Sender(internalStreamName, botSqsQueueUrl);
        this.configuration = FluentTxDrivenModuleConfiguration
                .defaultConfiguration()
                .with(new BaseNodeInclusionPolicy() {
                          @Override
                          public boolean include(Node node) {
                              if (node.hasLabel(Label.label(Labels.PERSON.getLabelName()))) {
                                  return true;
                              }
                              return false;
                          }
                      }
                );
    }

    @Override
    public TxDrivenModuleConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void afterCommit(List<UserCallDeleteHimselfEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        sender.sendUserDeleteHimself(events);
    }

    @Override
    public List<UserCallDeleteHimselfEvent> beforeCommit(ImprovedTransactionData transactionData) throws DeliberateTransactionRollbackException {
        List<UserCallDeleteHimselfEvent> result = new ArrayList<>();
        for (Node each : transactionData.getAllDeletedNodes()) {
            if (each.hasLabel(Label.label(Labels.PERSON.getLabelName()))) {
                String userId = (String) each.getProperty(PersonProperties.USER_ID.getPropertyName());
                UserCallDeleteHimselfEvent event = new UserCallDeleteHimselfEvent();
                event.setEventType(AUTH_USER_CALL_DELETE_HIMSELF.name());
                event.setUserReportStatus("NA");
                event.setUserId(userId);
                result.add(event);
            }
        }
        for (Change<Node> each : transactionData.getAllChangedNodes()) {
            if (each.getCurrent().hasLabel(Label.label(Labels.PERSON.getLabelName())) &&
                    !each.getPrevious().hasLabel(Label.label(Labels.HIDDEN.getLabelName())) &&
                    each.getCurrent().hasLabel(Label.label(Labels.HIDDEN.getLabelName()))) {

                String userId = (String) each.getCurrent().getProperty(PersonProperties.USER_ID.getPropertyName());
                UserCallDeleteHimselfEvent event = new UserCallDeleteHimselfEvent();
                event.setEventType(AUTH_USER_CALL_DELETE_HIMSELF.name());
                event.setUserReportStatus("TAKE_PART_IN_REPORT");
                event.setUserId(userId);
                result.add(event);
            }
        }
        return result;
    }
}
