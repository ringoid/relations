package com.ringoid.events.runtime;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import com.amazonaws.services.kinesis.model.PutRecordRequest;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ringoid.events.actions.UserBlockOtherEvent;
import com.ringoid.events.actions.UserLikePhotoEvent;
import com.ringoid.events.auth.UserCallDeleteHimselfEvent;
import com.ringoid.events.internal.events.PhotoLikeEvent;

import java.nio.ByteBuffer;
import java.util.List;

import static com.ringoid.events.EventTypes.ACTION_USER_LIKE_PHOTO;

public class Sender {
    private final ObjectMapper objectMapper;
    private final String internalStreamName;
    private final String botSqsQueueUrl;
    private final AmazonKinesis kinesis;
    private final AmazonSQS sqs;

    public Sender(String internalStreamName, String botSqsQueueUrl) {
        this.objectMapper = new ObjectMapper();
        this.internalStreamName = internalStreamName;
        this.botSqsQueueUrl = botSqsQueueUrl;

        AmazonKinesisClientBuilder clientBuilder = AmazonKinesisClientBuilder.standard().withRegion(Regions.EU_WEST_1);
        kinesis = clientBuilder.build();

        AmazonSQSClientBuilder sqsClientBuilder = AmazonSQSClientBuilder.standard().withRegion(Regions.EU_WEST_1);
        sqs = sqsClientBuilder.build();
    }

    public void sendBlockEvents(List<UserBlockOtherEvent> events, long reportReasonMax) {
        long start = System.currentTimeMillis();
        for (UserBlockOtherEvent event : events) {
            if (event.getBlockReasonNum() > reportReasonMax) {
                //todo:place for optimization (using batch)
                sendEventIntoInternalQueue(event, internalStreamName, event.getUserId());
            }
        }
        //todo:implement normal logging
        System.out.println("(extension-report) successfully handle " + events.size() + " UserBlockOtherEvent events in " + (System.currentTimeMillis() - start) + " millis");
    }

    public void sendUserDeleteHimself(List<UserCallDeleteHimselfEvent> events) {
        long start = System.currentTimeMillis();
        for (UserCallDeleteHimselfEvent event : events) {
            //todo:place for optimization (using batch)
            sendEventIntoInternalQueue(event, internalStreamName, event.getUserId());
        }
        //todo:implement normal logging
        System.out.println("(extension-report) successfully handle " + events.size() + " UserCallDeleteHimselfEvent events in " + (System.currentTimeMillis() - start) + " millis");
    }

    public void sendLikeEvents(List<PhotoLikeEvent> events, boolean botEnabled) {
        long start = System.currentTimeMillis();
        for (PhotoLikeEvent event : events) {
            //todo:place for optimization (using batch)
            sendEventIntoInternalQueue(event, internalStreamName, event.getUserId());
            if (botEnabled) {
                UserLikePhotoEvent botEvent = new UserLikePhotoEvent();
                botEvent.setEventType("BOT_" + ACTION_USER_LIKE_PHOTO.name());
                botEvent.setUserId(event.getSourceOfLikeUserId());
                botEvent.setTargetUserId(event.getUserId());
                botEvent.setOriginPhotoId(event.getOriginPhotoId());
                //todo:place for optimization (using batch)
                sendBotEvent(botEvent);
            }
        }
        //todo:implement normal logging
        System.out.println("(extension-report) successfully handle " + events.size() + " PhotoLikeEvent events in " + (System.currentTimeMillis() - start) + " millis");
    }

    private void sendEventIntoInternalQueue(Object event, String streamName, String partitionKey) {
        try {
            PutRecordRequest putRecordRequest = new PutRecordRequest();
            putRecordRequest.setStreamName(streamName);
            String strRep = objectMapper.writeValueAsString(event);
            putRecordRequest.setData(ByteBuffer.wrap(strRep.getBytes()));
            putRecordRequest.setPartitionKey(partitionKey);
            kinesis.putRecord(putRecordRequest);
        } catch (JsonProcessingException e) {
            //todo:add normal logging
            e.printStackTrace();
        }
    }

    public void sendBotEvent(Object event) {
        try {
            String strRep = objectMapper.writeValueAsString(event);
            sqs.sendMessage(botSqsQueueUrl, strRep);
        } catch (JsonProcessingException e) {
            //todo:add normal logging
            e.printStackTrace();
        }
    }

}
