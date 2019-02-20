package com.ringoid.common;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.model.PutRecordRequest;
import com.google.gson.Gson;
import com.ringoid.PersonProperties;
import com.ringoid.Relationships;
import com.ringoid.api.ProfileResponse;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.TransactionWork;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.PersonProperties.LAST_ACTION_TIME;
import static com.ringoid.PersonProperties.USER_ID;

public class Utils {
    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    private static final String NUM = "num";
    private static final String OWN_LAST_ACTION_TIME = "lastActionTime";

    private static final String GET_LAST_ACTION_TIME_QUERY =
            String.format(
                    "MATCH (sourceUser:%s {%s:$sourceUserId}) RETURN sourceUser.%s AS %s",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), LAST_ACTION_TIME.getPropertyName(), OWN_LAST_ACTION_TIME
            );

    public static long lastActionTime(Map<String, Object> parameters, Transaction tx) {
        log.debug("last action time fot parameters {}", parameters);
        StatementResult result = tx.run(GET_LAST_ACTION_TIME_QUERY, parameters);
        List<Record> list = result.list();
        long ownLastActionTime = 0L;
        for (Record each : list) {
            ownLastActionTime = each.get(OWN_LAST_ACTION_TIME).asLong();
        }
        return ownLastActionTime;
    }

    public static List<ProfileResponse> profileResponse(List<Map<String, List<String>>> receivedProfiles) {
        List<ProfileResponse> profiles = new ArrayList<>();

        for (Map<String, List<String>> eachProfileWithPhotos : receivedProfiles) {
            ProfileResponse profileResp = new ProfileResponse();
            for (Map.Entry<String, List<String>> eachEntry : eachProfileWithPhotos.entrySet()) {
                profileResp.setUserId(eachEntry.getKey());
                profileResp.setPhotoIds(eachEntry.getValue());
            }
            profiles.add(profileResp);
        }

        return profiles;
    }

    public static List<Map<String, List<String>>> llmRequest(Map<String, Object> parameters, String query,
                                                             String targetUserIdParamName, String targetPhotoIdParamName,
                                                             String logInfoRequestType,
                                                             Driver driver) {
        final List<Map<String, List<String>>> resultMap = new ArrayList<>();
        final List<String> orderList = new ArrayList<>();
        final Map<String, List<String>> tmpMap = new HashMap<>();

        try (Session session = driver.session()) {
            session.readTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    StatementResult result = tx.run(query, parameters);
                    List<Record> list = result.list();
                    int photoCounter = 0;
                    for (Record each : list) {
                        String targetUserId = each.get(targetUserIdParamName).asString();
                        String targetPhotoId = each.get(targetPhotoIdParamName).asString();
                        List<String> photos = tmpMap.get(targetUserId);
                        if (photos == null) {
                            orderList.add(targetUserId);
                            photos = new ArrayList<>();
                            tmpMap.put(targetUserId, photos);
                        }
                        photos.add(targetPhotoId);
                        photoCounter++;
                    }
                    log.info("{} photo were found for {} request {} for userId {}",
                            photoCounter, logInfoRequestType, parameters, parameters.get("sourceUserId"));
                    log.debug("{}", query);
                    return 1;
                }
            });
        } catch (Throwable throwable) {
            log.error("error {} request, request {} for userId {}", logInfoRequestType, parameters.get("sourceUserId"), throwable);
            throw throwable;
        }
        for (String eachUserId : orderList) {
            List<String> photos = tmpMap.get(eachUserId);
            Map<String, List<String>> eachProfileWithPhotos = new HashMap<>();
            eachProfileWithPhotos.put(eachUserId, photos);
            resultMap.add(eachProfileWithPhotos);
        }
        return resultMap;
    }

    public static long lastActionTime(Map<String, Object> parameters, Driver driver) {
        long lastActionTime;
        try (Session session = driver.session()) {
            lastActionTime = session.readTransaction(new TransactionWork<Long>() {
                @Override
                public Long execute(Transaction tx) {
                    return Utils.lastActionTime(parameters, tx);
                }
            });
        } catch (Throwable throwable) {
            log.error("error last action time request, request {} for userId {}", parameters.get("sourceUserId"), throwable);
            throw throwable;
        }
        return lastActionTime;
    }

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
