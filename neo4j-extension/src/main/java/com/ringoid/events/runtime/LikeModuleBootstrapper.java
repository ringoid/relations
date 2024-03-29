package com.ringoid.events.runtime;

import com.graphaware.common.log.LoggerFactory;
import com.graphaware.runtime.module.RuntimeModule;
import com.graphaware.runtime.module.RuntimeModuleBootstrapper;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.logging.Log;

import java.util.Map;

public class LikeModuleBootstrapper implements RuntimeModuleBootstrapper {

    private final Log log = LoggerFactory.getLogger(getClass());

    @Override
    public RuntimeModule bootstrapModule(String moduleId, Map<String, String> config, GraphDatabaseService database) {
        String internalStreamName = config.get("internal_stream_name");
        Boolean botsEnabled = Boolean.valueOf(config.get("bots_enable"));
        String botsSqsQueue = config.get("bots_sqs_queue");
        String botStream = "fake";
        //String botStream = config.get("bots_kinesis_queue");
        log.info("bootstrap module with internal stream name [%s], bots enabled [%s] and bots sqs url [%s] and botKinesis [%s]",
                internalStreamName, botsEnabled, botsSqsQueue, botStream);
        return new LikeModule(database, moduleId, internalStreamName, botsSqsQueue, botsEnabled, botStream);
    }
}
