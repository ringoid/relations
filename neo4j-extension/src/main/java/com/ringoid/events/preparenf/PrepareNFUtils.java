package com.ringoid.events.preparenf;

import com.graphaware.common.log.LoggerFactory;
import com.ringoid.Relationships;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.logging.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.PersonProperties.USER_ID;

public class PrepareNFUtils {
    private static final Log log = LoggerFactory.getLogger(PrepareNFUtils.class);

    private static final String DELETE_PREPARED_QUERY = String.format(
            "MATCH (source:%s {%s: $userIdValue})-[r:%s]->() " +
                    "DELETE r",
            PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.PREPARE_NF.name()
    );
    private static final String PREPARE_QUERY = String.format(
            "MATCH (source:%s {%s: $userIdValue}), (target:%s {%s: $targetUserIdValue}) " +
                    "MERGE source-[:%s]->target",
            PERSON.getLabelName(), USER_ID.getPropertyName(), PERSON.getLabelName(), USER_ID.getPropertyName(),
            Relationships.PREPARE_NF.name()
    );

    public static void prepareNF(PrepareNFEvent event, GraphDatabaseService database) {
        Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), event.getUserId());
        if (Objects.isNull(sourceUser)) {
            log.warn("prepare new faces event for not existing userId [%s]", event.getUserId());
        }
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("userIdValue", event.getUserId());
        Result result = database.execute(DELETE_PREPARED_QUERY, parameters);
        log.info("delete [%s] old prepared new faces for userId [%s]", Integer.toString(result.getQueryStatistics().getRelationshipsDeleted()), event.getUserId());

        int createdCounter = 0;
        for (String eachTarget : event.getTargetUserIds()) {
            parameters.put("targetUserIdValue", eachTarget);
            result = database.execute(PREPARE_QUERY, parameters);
            createdCounter += result.getQueryStatistics().getRelationshipsCreated();
        }
        log.info("prepare [%s] nodes for new faces, userId [%s]", Integer.toString(createdCounter), event.getUserId());
    }
}