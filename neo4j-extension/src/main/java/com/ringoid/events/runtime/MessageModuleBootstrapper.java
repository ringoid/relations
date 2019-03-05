package com.ringoid.events.runtime;

import com.graphaware.runtime.module.RuntimeModule;
import com.graphaware.runtime.module.RuntimeModuleBootstrapper;
import org.neo4j.graphdb.GraphDatabaseService;

import java.util.Map;

public class MessageModuleBootstrapper implements RuntimeModuleBootstrapper {
    @Override
    public RuntimeModule bootstrapModule(String moduleId, Map<String, String> config, GraphDatabaseService database) {
        String internalStreamName = config.get("internal_stream_name");
        Boolean botsEnabled = Boolean.valueOf(config.get("bots_enable"));
        String botsSqsQueue = config.get("bots_sqs_queue");
        System.out.println("bootstrap block module with internal stream name = " + internalStreamName);
        System.out.println("bootstrap block module with bots sqs url = " + botsSqsQueue);
        System.out.println("bootstrap block module with bots enabled = " + botsEnabled);
        return new MessageModule(moduleId, internalStreamName, botsSqsQueue, botsEnabled);
    }
}