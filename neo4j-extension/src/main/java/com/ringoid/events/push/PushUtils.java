package com.ringoid.events.push;

import com.graphaware.common.log.LoggerFactory;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.logging.Log;

import java.util.Objects;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.PersonProperties.PUSH_WAS_SENT_AT;
import static com.ringoid.PersonProperties.USER_ID;

public class PushUtils {
    private final static Log log = LoggerFactory.getLogger(PushUtils.class);

    public static void pushWasSent(PushWasSentEvent event, GraphDatabaseService database) {
        Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), event.getUserId());
        if (Objects.isNull(sourceUser)) {
            return;
        }
        sourceUser.setProperty(PUSH_WAS_SENT_AT.getPropertyName(), event.getUnixTime());
    }
}
