package com.ringoid.events.runtime;

import com.graphaware.common.log.LoggerFactory;
import com.graphaware.runtime.module.RuntimeModule;
import com.graphaware.runtime.module.RuntimeModuleBootstrapper;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.logging.Log;

import java.util.Map;

public class MessageProfileModuleBootstrapper implements RuntimeModuleBootstrapper {

    private final Log log = LoggerFactory.getLogger(getClass());

    @Override
    public RuntimeModule bootstrapModule(String moduleId, Map<String, String> config, GraphDatabaseService database) {
        String internalStreamName = config.get("internal_stream_name");
        String botsSqsQueue = config.get("bots_sqs_queue");
        String botStream = "fake";
        //String botStream = config.get("bots_kinesis_queue");
        log.info("bootstrap module with internal stream name [%s] and bots sqs url [%s], bot stream [%s]",
                internalStreamName, botsSqsQueue, botStream);
        return new MessageProfileModule(database, moduleId, internalStreamName, botsSqsQueue, botStream);
    }
}
