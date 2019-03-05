package com.ringoid.common;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.model.PutRecordRequest;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class Utils {
    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    public static void sendEventIntoInternalQueue(Object event,
                                                  AmazonKinesis kinesis, String streamName, String partitionKey,
                                                  Gson gson) {
        log.debug("send event {} into internal kinesis queue", event);
        PutRecordRequest putRecordRequest = new PutRecordRequest();
        putRecordRequest.setStreamName(streamName);
        String strRep = gson.toJson(event);
        putRecordRequest.setData(ByteBuffer.wrap(strRep.getBytes()));
        putRecordRequest.setPartitionKey(partitionKey);
        kinesis.putRecord(putRecordRequest);
        log.debug("successfully send event {} into internal queue", event);
    }
}
