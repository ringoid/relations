package com.ringoid.api;

import com.codahale.metrics.MetricRegistry;
import com.graphaware.common.log.LoggerFactory;
import com.ringoid.Labels;
import com.ringoid.PersonProperties;
import com.ringoid.PhotoProperties;
import com.ringoid.PrepareNFRelationshipProperties;
import com.ringoid.Relationships;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.logging.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.ringoid.Labels.HIDDEN;
import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.PersonProperties.LAST_ACTION_TIME;
import static com.ringoid.PersonProperties.LAST_ONLINE_TIME;
import static com.ringoid.PersonProperties.LIKE_COUNTER;
import static com.ringoid.PersonProperties.LOCATION;
import static com.ringoid.PersonProperties.PREPARED_NF_UNIX_TIME_IN_MILLIS;
import static com.ringoid.PersonProperties.SEX;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.PersonProperties.YEAR;
import static com.ringoid.PhotoProperties.ONLY_OWNER_CAN_SEE;
import static com.ringoid.api.Utils.commonSortProfilesSeenPart;
import static com.ringoid.api.Utils.enrichProfile;
import static com.ringoid.api.Utils.sortLMHISPhotos;
import static com.ringoid.api.Utils.sortNewFacesUnseenPartProfiles;
import static com.ringoid.api.Utils.sortNewFacesUnseenPhotos;

public class NewFaces {

    private final static Log log = LoggerFactory.getLogger(NewFaces.class);

    private static final int MAX_LOOP_NUM = 4;

    private static final long MAX_AGE_FOR_PREPARED_NF = 1000 * 60 * 10;//10 mins

    private static final String SEEN_SORTED_BY_LIKES_QUERY = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                    "MATCH (target:%s {%s:$sex})-[:%s]->(pp:%s) " +//2
                    "WHERE NOT (target)-[:%s|%s|%s|%s]-(sourceUser) AND target.%s >= $onlineTime " +//2.1
                    "AND (NOT exists(pp.%s) OR pp.%s = false) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, target.%s AS likes ORDER BY likes DESC, userId SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(),//1
            PERSON.getLabelName(), SEX.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), //2
            Relationships.BLOCK, Relationships.LIKE, Relationships.MESSAGE, Relationships.MATCH, LAST_ONLINE_TIME.getPropertyName(),//2.1
            ONLY_OWNER_CAN_SEE.getPropertyName(), ONLY_OWNER_CAN_SEE.getPropertyName(),//2.2
            USER_ID.getPropertyName(), LIKE_COUNTER.getPropertyName()//3
    );

    private static final String GEO_SEEN_SORTED_BY_LIKES_QUERY = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                    "MATCH (target:%s {%s:$sex}) " +//2
                    "WHERE distance(sourceUser.%s, target.%s) <= $distance AND target.%s >= $onlineTime " +//2.01
                    "WITH sourceUser, target " +//2.01
                    "WHERE (NOT (target)<-[:%s|%s|%s|%s]-(sourceUser)) " +//2.1
                    "AND (target)-[:%s]->(:%s) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, target.%s AS likes ORDER BY likes DESC, userId SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(),//1
            PERSON.getLabelName(), SEX.getPropertyName(),//2
            LOCATION.getPropertyName(), LOCATION.getPropertyName(), LAST_ONLINE_TIME.getPropertyName(), //2.01
            Relationships.BLOCK, Relationships.LIKE, Relationships.MESSAGE, Relationships.MATCH,//2.1
            Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//2.2
            USER_ID.getPropertyName(), LIKE_COUNTER.getPropertyName()//3
    );

    private static final String SEEN_SORTED_BY_ONLINE_TIME_QUERY = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                    "MATCH (target:%s {%s:$sex})-[:%s]->(pp:%s) " +//2
                    "WHERE NOT (target)-[:%s|%s|%s|%s]-(sourceUser) AND target.%s >= $onlineTime " +//2.1
                    "AND (NOT exists(pp.%s) OR pp.%s = false) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, target.%s AS likes, target.%s AS onlineTime ORDER BY onlineTime DESC, userId SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(),//1
            PERSON.getLabelName(), SEX.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), //2
            Relationships.BLOCK, Relationships.LIKE, Relationships.MESSAGE, Relationships.MATCH, LAST_ONLINE_TIME.getPropertyName(),//2.1
            ONLY_OWNER_CAN_SEE.getPropertyName(), ONLY_OWNER_CAN_SEE.getPropertyName(),//2.2
            USER_ID.getPropertyName(), LIKE_COUNTER.getPropertyName(), LAST_ONLINE_TIME.getPropertyName()//3
    );

    private static final String GEO_SEEN_SORTED_BY_ONLINE_TIME_QUERY = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                    "MATCH (target:%s {%s:$sex}) " +//2
                    "WHERE distance(sourceUser.%s, target.%s) <= $distance AND target.%s >= $onlineTime " +//2.01
                    "WITH sourceUser, target " +//2.01
                    "WHERE (NOT (target)<-[:%s|%s|%s|%s]-(sourceUser)) " +//2.1
                    "AND (target)-[:%s]->(:%s) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, target.%s AS likes, target.%s AS onlineTime ORDER BY onlineTime DESC, userId SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(),//1
            PERSON.getLabelName(), SEX.getPropertyName(),//2
            LOCATION.getPropertyName(), LOCATION.getPropertyName(), LAST_ONLINE_TIME.getPropertyName(),//2.01
            Relationships.BLOCK, Relationships.LIKE, Relationships.MESSAGE, Relationships.MATCH,//2.1
            Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//2.2
            USER_ID.getPropertyName(), LIKE_COUNTER.getPropertyName(), LAST_ONLINE_TIME.getPropertyName()//3
    );

    private static final String NOT_SEEN_SORTED_BY_LIKES_QUERY = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                    "MATCH (target:%s {%s:$sex})-[:%s]->(pp:%s) " +//2
                    "WHERE NOT (target)<-[:%s|%s|%s|%s|%s|%s|%s|%s]-(sourceUser) AND target.%s >= $onlineTime " +//2.1
                    "AND (NOT exists(pp.%s) OR pp.%s = false) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, target.%s AS likes ORDER BY likes DESC, userId SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(),//1
            PERSON.getLabelName(), SEX.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), //2
            //we need this line to enable preparenf relationships take part in new preparation
            Relationships.VIEW, Relationships.VIEW_IN_LIKES_YOU, Relationships.VIEW_IN_MATCHES, Relationships.VIEW_IN_MESSAGES, Relationships.BLOCK, Relationships.LIKE, Relationships.MESSAGE, Relationships.MATCH, LAST_ONLINE_TIME.getPropertyName(),//2.1
            ONLY_OWNER_CAN_SEE.getPropertyName(), ONLY_OWNER_CAN_SEE.getPropertyName(),//2.2
            USER_ID.getPropertyName(), LIKE_COUNTER.getPropertyName()//3
    );

    private static final String GEO_NOT_SEEN_SORTED_BY_LIKES_QUERY = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                    "MATCH (target:%s {%s:$sex}) " +//2
                    "WHERE distance(sourceUser.%s, target.%s) <= $distance AND target.%s >= $onlineTime " +//2.01
                    "WITH sourceUser, target " +
                    "WHERE NOT (target)<-[:%s|%s|%s|%s|%s|%s|%s|%s]-(sourceUser) " +//2.1
                    "AND (target)-[:%s]->(:%s) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, target.%s AS likes ORDER BY likes DESC, userId SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(),//1
            PERSON.getLabelName(), SEX.getPropertyName(),//2
            LOCATION.getPropertyName(), LOCATION.getPropertyName(), LAST_ONLINE_TIME.getPropertyName(),//2.01
            //we need this line to enable preparenf relationships take part in new preparation
            Relationships.VIEW, Relationships.VIEW_IN_LIKES_YOU, Relationships.VIEW_IN_MATCHES, Relationships.VIEW_IN_MESSAGES, Relationships.BLOCK, Relationships.LIKE, Relationships.MESSAGE, Relationships.MATCH,//2.1
            Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//2.2
            USER_ID.getPropertyName(), LIKE_COUNTER.getPropertyName()//3
    );

    private static final String NOT_SEEN_SORTED_BY_ONLINE_TIME_QUERY = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                    "MATCH (target:%s {%s:$sex})-[:%s]->(pp:%s) " +//2
                    "WHERE NOT (target)<-[:%s|%s|%s|%s|%s|%s|%s|%s]-(sourceUser) AND target.%s >= $onlineTime " +//2.1
                    "AND (NOT exists(pp.%s) OR pp.%s = false) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, target.%s AS likes, target.%s AS onlineTime ORDER BY onlineTime DESC, userId SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(),//1
            PERSON.getLabelName(), SEX.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), //2
            //we need this line to enable preparenf relationships take part in new preparation
            Relationships.VIEW, Relationships.VIEW_IN_LIKES_YOU, Relationships.VIEW_IN_MATCHES, Relationships.VIEW_IN_MESSAGES, Relationships.BLOCK, Relationships.LIKE, Relationships.MESSAGE, Relationships.MATCH, LAST_ONLINE_TIME.getPropertyName(),//2.1
            ONLY_OWNER_CAN_SEE.getPropertyName(), ONLY_OWNER_CAN_SEE.getPropertyName(),//2.2
            USER_ID.getPropertyName(), LIKE_COUNTER.getPropertyName(), LAST_ONLINE_TIME.getPropertyName()//3
    );

    private static final String GEO_NOT_SEEN_SORTED_BY_ONLINE_TIME_QUERY = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                    "MATCH (target:%s {%s:$sex}) " +//2
                    "WHERE distance(sourceUser.%s, target.%s) <= $distance AND target.%s >= $onlineTime " +//2.01
                    "WITH sourceUser, target " +
                    "WHERE NOT (target)<-[:%s|%s|%s|%s|%s|%s|%s|%s]-(sourceUser) " +//2.1
                    "AND (target)-[:%s]->(:%s) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, target.%s AS likes, target.%s AS onlineTime ORDER BY onlineTime DESC, userId SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(),//1
            PERSON.getLabelName(), SEX.getPropertyName(),//2
            LOCATION.getPropertyName(), LOCATION.getPropertyName(), LAST_ONLINE_TIME.getPropertyName(),//2.01
            //we need this line to enable preparenf relationships take part in new preparation
            Relationships.VIEW, Relationships.VIEW_IN_LIKES_YOU, Relationships.VIEW_IN_MATCHES, Relationships.VIEW_IN_MESSAGES, Relationships.BLOCK, Relationships.LIKE, Relationships.MESSAGE, Relationships.MATCH,//2.1
            Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//2.2
            USER_ID.getPropertyName(), LIKE_COUNTER.getPropertyName(), LAST_ONLINE_TIME.getPropertyName()//3
    );

    private static final List<Integer> DISTANCES = new ArrayList<>();

    static {
//        DISTANCES.add(5_000);
//        DISTANCES.add(10_000);
        DISTANCES.add(25_000);
        DISTANCES.add(50_000);
//        DISTANCES.add(100_000);
        DISTANCES.add(100_000);
        DISTANCES.add(300_000);
    }

    public static PrepareNewFacesResponse prepareNewFaces(PrepareNewFacesRequest request, GraphDatabaseService database, MetricRegistry metrics) {
        PrepareNewFacesResponse response = new PrepareNewFacesResponse();
        try (Transaction tx = database.beginTx()) {
            Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), request.getUserId());
            if (Objects.isNull(sourceUser)) {
                log.warn("request prepare new_faces for user that not exist, userId [%s]", request.getUserId());
                tx.success();
                return response;
            }

            String sex = (String) sourceUser.getProperty(SEX.getPropertyName(), "male");
            String targetSex = "female";
            if (Objects.equals("female", sex)) {
                targetSex = "male";
            }

            int yearOfBirth = (Integer) sourceUser.getProperty(YEAR.getPropertyName(), 0);

            long startLoop = System.currentTimeMillis();
            List<Node> nodes = loopByUnseenPart(sourceUser.getId(), request.getUserId(), targetSex, request.getLimit(),
                    Collections.emptySet(), yearOfBirth, database, metrics);
            long loopTime = System.currentTimeMillis() - startLoop;
            metrics.histogram("prepare_new_faces_loopByUnseenPart").update(loopTime);

            Set<Long> unseenNodeIds = new HashSet<>(nodes.size());
            if (nodes.size() < request.getLimit()) {
                for (Node each : nodes) {
                    unseenNodeIds.add(each.getId());
                }
                startLoop = System.currentTimeMillis();
                List<Node> seenPart = loopBySeenPart(sourceUser.getId(), request.getUserId(), targetSex, request.getLimit(),
                        unseenNodeIds, database, metrics);
                seenPart = commonSortProfilesSeenPart(sourceUser, seenPart);
                nodes.addAll(seenPart);
                loopTime = System.currentTimeMillis() - startLoop;
                metrics.histogram("prepare_new_faces_loopBySeenPart").update(loopTime);
            }

            if (nodes.size() > request.getLimit()) {
                nodes = nodes.subList(0, request.getLimit());
            }
            log.info("prepare_new_faces (full) for userId [%s] size is [%s]", request.getUserId(), nodes.size());

            int index = 0;
            for (Node each : nodes) {
                String targetUserId = (String) each.getProperty(PersonProperties.USER_ID.getPropertyName());
                if (!unseenNodeIds.contains(each.getId())) {
                    response.getTargetUserIndexMap().put(targetUserId + "_ALREADY_SEEN", index++);
                } else {
                    response.getTargetUserIndexMap().put(targetUserId, index++);
                }
            }

            tx.success();
        }
        return response;
    }

    public static NewFacesResponse newFacesWithPreparedNodes(NewFacesRequest request, GraphDatabaseService database, MetricRegistry metrics) {
        NewFacesResponse response = new NewFacesResponse();
        List<PreparedNode> nodes = new ArrayList<>();
        try (Transaction tx = database.beginTx()) {
            Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), request.getUserId());
            if (Objects.isNull(sourceUser)) {
                log.warn("request new_faces_with_prepared_nodes for user that not exist, userId [%s]", request.getUserId());
                response.setLastActionTime(request.getRequestedLastActionTime() - 1);
                tx.success();
                return response;
            }
            long actionTime = (Long) sourceUser.getProperty(LAST_ACTION_TIME.getPropertyName(), 0L);
            response.setLastActionTime(actionTime);
            if (request.getRequestedLastActionTime() <= actionTime) {

                //check that recommendations not too old
                long lastPreparedNf = (Long) sourceUser.getProperty(PREPARED_NF_UNIX_TIME_IN_MILLIS.getPropertyName(), 0L);
                long preparedAgeInMillis = System.currentTimeMillis() - lastPreparedNf;
                if (preparedAgeInMillis > MAX_AGE_FOR_PREPARED_NF) {
                    log.debug("too old prepared new faces, return 0 instead [%s]", Long.toString(preparedAgeInMillis));
                    tx.success();
                    return response;
                }
                Iterable<Relationship> prepared = sourceUser.getRelationships(Direction.OUTGOING, RelationshipType.withName(Relationships.PREPARE_NF.name()));
                for (Relationship eachP : prepared) {
                    Node other = eachP.getOtherNode(sourceUser);
                    if (other.hasLabel(Label.label(PERSON.getLabelName()))) {
                        long index = (Long) eachP.getProperty(PrepareNFRelationshipProperties.INDEX.getPropertyName(), 1L);
                        PreparedNode preparedNode = new PreparedNode();
                        preparedNode.node = other;
                        preparedNode.index = index;
                        nodes.add(preparedNode);
                    }
                }
                Collections.sort(nodes, new Comparator<PreparedNode>() {
                    @Override
                    public int compare(PreparedNode o1, PreparedNode o2) {
                        return Long.compare(o1.index, o2.index);
                    }
                });
                List<Node> resultList = new ArrayList<>(nodes.size());
                for (PreparedNode each : nodes) {
                    resultList.add(each.node);
                }
                int howMuchPreparedWeHave = resultList.size();

                if (resultList.size() > request.getLimit()) {
                    resultList = resultList.subList(0, request.getLimit());
                }
                howMuchPreparedWeHave -= resultList.size();

                List<Profile> profileList = new ArrayList<>();
                profileList.addAll(createProfileListWithResizedAndSortedPhotos(request.getResolution(), resultList, true, sourceUser, database, metrics));
                response.setNewFaces(profileList);
                response.setHowMuchPrepared(howMuchPreparedWeHave);
                log.info("new_faces_with_prepared_nodes (full) for userId [%s] size is [%s]", request.getUserId(), profileList.size());
            }
            tx.success();
        }
        return response;
    }

    public static NewFacesResponse newFaces(NewFacesRequest request, GraphDatabaseService database, MetricRegistry metrics) {
        NewFacesResponse response = new NewFacesResponse();
        try (Transaction tx = database.beginTx()) {
            Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), request.getUserId());
            if (Objects.isNull(sourceUser)) {
                log.warn("request new_faces for user that not exist, userId [%s]", request.getUserId());
                response.setLastActionTime(request.getRequestedLastActionTime() - 1);
                tx.success();
                return response;
            }
            long actionTime = (Long) sourceUser.getProperty(LAST_ACTION_TIME.getPropertyName(), 0L);
            response.setLastActionTime(actionTime);
            if (request.getRequestedLastActionTime() <= actionTime) {
                String sex = (String) sourceUser.getProperty(SEX.getPropertyName(), "male");
                String targetSex = "female";
                if (Objects.equals("female", sex)) {
                    targetSex = "male";
                }

                int yearOfBirth = (Integer) sourceUser.getProperty(YEAR.getPropertyName(), 0);

                long startLoop = System.currentTimeMillis();
                List<Node> unseen = loopByUnseenPart(sourceUser.getId(), request.getUserId(), targetSex, request.getLimit(),
                        Collections.emptySet(), yearOfBirth, database, metrics);
                long loopTime = System.currentTimeMillis() - startLoop;
                metrics.histogram("new_faces_loopByUnseenPart").update(loopTime);

                //now check that result <= limit
                log.info("new_faces (not seen part) for userId [%s] size is [%s]", request.getUserId(), unseen.size());

                List<Profile> profileList = new ArrayList<>();
                profileList.addAll(createProfileListWithResizedAndSortedPhotos(request.getResolution(), unseen, true, sourceUser, database, metrics));

                if (profileList.size() < request.getLimit()) {
                    Set<Long> nodeIds = new HashSet<>(unseen.size());
                    for (Node each : unseen) {
                        nodeIds.add(each.getId());
                    }
                    startLoop = System.currentTimeMillis();
                    List<Node> seen = loopBySeenPart(sourceUser.getId(), request.getUserId(), targetSex, request.getLimit(),
                            nodeIds, database, metrics);
                    loopTime = System.currentTimeMillis() - startLoop;
                    metrics.histogram("new_faces_loopBySeenPart").update(loopTime);

                    long startSorting = System.currentTimeMillis();
                    seen = commonSortProfilesSeenPart(sourceUser, seen);
                    long sortingTime = System.currentTimeMillis() - startSorting;
                    metrics.histogram("new_faces_commonSortProfilesSeenPart").update(sortingTime);

                    log.info("new_faces (seen part) for userId [%s] size is [%s]", request.getUserId(), seen.size());
                    profileList.addAll(createProfileListWithResizedAndSortedPhotos(request.getResolution(), seen, false, sourceUser, database, metrics));
                }

                if (profileList.size() > request.getLimit()) {
                    profileList = profileList.subList(0, request.getLimit());
                }
                log.info("new_faces (full) for userId [%s] size is [%s]", request.getUserId(), profileList.size());

                Set<String> uniqueIds = new HashSet<>();
                Iterator<Profile> it = profileList.iterator();
                while (it.hasNext()) {
                    if (!uniqueIds.add(it.next().getUserId())) {
                        it.remove();
                    }
                }

                response.setNewFaces(profileList);
            }
            tx.success();
        }
        return response;
    }

    private static List<Profile> createProfileListWithResizedAndSortedPhotos(String resolution, List<Node> source,
                                                                             boolean isItUnseenPart, Node sourceUser, GraphDatabaseService database,
                                                                             MetricRegistry metrics) {
        long start = System.currentTimeMillis();
        List<Profile> profileList = new ArrayList<>(source.size());
        for (Node eachProfile : source) {
            Profile prof = new Profile();
            prof.setUserId((String) eachProfile.getProperty(USER_ID.getPropertyName()));
            if (isItUnseenPart) {
                prof.setPhotos(Utils.resizedAndVisibleToEveryOnePhotos(sortNewFacesUnseenPhotos(eachProfile), resolution, database));
            } else {
                prof.setPhotos(Utils.resizedAndVisibleToEveryOnePhotos(sortLMHISPhotos(sourceUser, eachProfile), resolution, database));
            }
            if (!prof.getPhotos().isEmpty()) {
                prof = enrichProfile(eachProfile, sourceUser, prof);
                profileList.add(prof);
            }
        }
        long fullTime = System.currentTimeMillis() - start;
        metrics.histogram("new_faces_createProfileListWithResizedAndSortedPhotos").update(fullTime);
        return profileList;
    }

    private static List<Node> loopBySeenPart(long sourceUserNodeId, String sourceUserId, String targetSex,
                                             int limit,
                                             Set<Long> nodeIdsToExclude,
                                             GraphDatabaseService database,
                                             MetricRegistry metrics) {
        long start = System.currentTimeMillis();

        List<Node> result = new ArrayList<>();
        int howManyStep = 0;
        for (int eachD : DISTANCES) {
            if (result.size() < limit) {
                result.clear();
            } else {
                break;
            }
            howManyStep++;
            if (Objects.equals("male", targetSex)) {
                //it means that you are woman, so we search most recent for seen part
                result.addAll(loopRequest(sourceUserNodeId, sourceUserId, targetSex, limit, eachD,
                        GEO_SEEN_SORTED_BY_ONLINE_TIME_QUERY, null,
                        nodeIdsToExclude,
                        false,
                        database));
            } else if (Objects.equals("female", targetSex)) {
                //it means that you are men so 75 popular, 25 recent
                result.addAll(loopRequest(sourceUserNodeId, sourceUserId, targetSex, limit, eachD,
                        GEO_SEEN_SORTED_BY_LIKES_QUERY, GEO_SEEN_SORTED_BY_ONLINE_TIME_QUERY,
                        nodeIdsToExclude,
                        true,
                        database));
            }
        }
        long fullTime = System.currentTimeMillis() - start;
        metrics.histogram("new_faces_loopBySeenPart_geo_full_target_" + targetSex).update(fullTime);
        metrics.histogram("new_faces_loopBySeenPart_geo_steps").update(howManyStep);

        log.info("loopBySeenPart : found [%s] profiles using location for userId [%s] in [%s] millis",
                result.size(), sourceUserId, System.currentTimeMillis() - start);

        //there is no reason for sorting here coz it's seen part
//        result = sortNewFacesUnseenProfiles(result);

        if (result.size() >= limit) {
            return result;
        }

        Set<Long> nodeIds = new HashSet<>(nodeIdsToExclude);
        for (Node each : result) {
            nodeIds.add(each.getId());
        }

        limit -= result.size();

        start = System.currentTimeMillis();

        List<Node> tmp = new ArrayList<>();
        if (Objects.equals("male", targetSex)) {
            //it means that you are women so most recent for you
            tmp.addAll(loopRequest(sourceUserNodeId, sourceUserId, targetSex, limit, 0,
                    SEEN_SORTED_BY_ONLINE_TIME_QUERY, null,
                    nodeIds,
                    false,
                    database));
        } else if (Objects.equals("female", targetSex)) {
            //it means that you are men so 75 popular, 25 recent
            tmp.addAll(loopRequest(sourceUserNodeId, sourceUserId, targetSex, limit, 0,
                    SEEN_SORTED_BY_LIKES_QUERY, SEEN_SORTED_BY_ONLINE_TIME_QUERY,
                    nodeIdsToExclude,
                    true,
                    database));
        }

        fullTime = System.currentTimeMillis() - start;
        metrics.histogram("new_faces_loopBySeenPart_without_geo_full_target_" + targetSex).update(fullTime);

        log.info("loopBySeenPart : found [%s] profiles without location for userId [%s] in [%s] millis",
                tmp.size(), sourceUserId, System.currentTimeMillis() - start);

        //there is no reason for sorting here coz it's seen part
//        tmp = sortNewFacesUnseenProfiles(tmp);

        result.addAll(tmp);

        return result;
    }

    private static List<Node> loopByUnseenPart(long sourceUserNodeId, String sourceUserId, String targetSex,
                                               int limit,
                                               Set<Long> nodeIdsToExclude,
                                               int yearOfBirth,
                                               GraphDatabaseService database,
                                               MetricRegistry metrics) {

        long start = System.currentTimeMillis();

        List<Node> result = new ArrayList<>();
        int howManyStep = 0;
        for (int eachD : DISTANCES) {
            if (result.size() < limit) {
                result.clear();
            } else {
                break;
            }
            howManyStep++;
            result.addAll(loopRequest(sourceUserNodeId, sourceUserId, targetSex, limit, eachD,
                    GEO_NOT_SEEN_SORTED_BY_ONLINE_TIME_QUERY, GEO_NOT_SEEN_SORTED_BY_LIKES_QUERY,
                    nodeIdsToExclude,
                    false,
                    database));
        }
        long fullTime = System.currentTimeMillis() - start;
        metrics.histogram("new_faces_loopByUnseenPart_geo_full").update(fullTime);
        metrics.histogram("new_faces_loopByUnseenPart_geo_steps").update(howManyStep);

        log.info("loopByUnseenPart : found [%s] profiles using location for userId [%s] in [%s] millis",
                result.size(), sourceUserId, System.currentTimeMillis() - start);

        result = sortNewFacesUnseenPartProfiles(result, targetSex, yearOfBirth);

        if (result.size() >= limit) {
            return result;
        }

        Set<Long> nodeIds = new HashSet<>(nodeIdsToExclude);
        for (Node each : result) {
            nodeIds.add(each.getId());
        }

        limit -= result.size();

        start = System.currentTimeMillis();
        List<Node> tmp = loopRequest(sourceUserNodeId, sourceUserId, targetSex, limit, 0,
                NOT_SEEN_SORTED_BY_ONLINE_TIME_QUERY, NOT_SEEN_SORTED_BY_LIKES_QUERY,
                nodeIds,
                false,
                database);

        fullTime = System.currentTimeMillis() - start;
        metrics.histogram("new_faces_loopByUnseenPart_without_geo_full").update(fullTime);

        log.info("loopByUnseenPart : found [%s] profiles without location for userId [%s] in [%s] millis",
                tmp.size(), sourceUserId, System.currentTimeMillis() - start);

        tmp = sortNewFacesUnseenPartProfiles(tmp, targetSex, yearOfBirth);

        result.addAll(tmp);

        return result;
    }

    private static List<Node> loopRequest(long sourceUserNodeId, String sourceUserId, String targetSex,
                                          int limit, int distance,
                                          String queryFirst, String querySecond,
                                          Set<Long> nodeIdsToExclude,
                                          boolean isItSeenForMen,
                                          GraphDatabaseService database) {

        int skip = 0;
        int loopCounter = 0;

        List<Node> result = new ArrayList<>();

        while (result.size() < limit && loopCounter < MAX_LOOP_NUM) {

            List<Node> unknowns = new ArrayList<>();
//            if (isItSeenForMen) {
//                unknowns.addAll(fetchProfilesForMenSeenPart(queryFirst, querySecond, sourceUserId, targetSex, skip, limit, distance, 75, 25, database));
//            } else {
//                unknowns.addAll(fetchProfiles(queryFirst, querySecond, sourceUserId, targetSex, skip, limit, distance, database));
//            }
            //try to add 2/3 active and 1/3 popular for everybody
            unknowns.addAll(fetchProfilesForMenSeenPart(queryFirst, querySecond, sourceUserId, targetSex, skip, limit, distance, 75, 25, database));

            if (unknowns.size() < limit) {
                //there is no more users, exit from the loop
                loopCounter = MAX_LOOP_NUM;
            } else {
                skip += limit;
                loopCounter++;
            }

            //delete possible duplicates
            Set<Long> ids = new HashSet<>(unknowns.size());
            Iterator<Node> it = unknowns.iterator();
            while (it.hasNext()) {
                if (!ids.add(it.next().getId())) {
                    it.remove();
                }
            }

            for (Node eachUnknown : unknowns) {
                if (nodeIdsToExclude.contains(eachUnknown.getId())) {
                    continue;
                }
                if (sourceUserNodeId == eachUnknown.getId()) {
                    continue;
                }
                if (eachUnknown.hasLabel(Label.label(HIDDEN.getLabelName()))) {
                    continue;
                }

                boolean dontHaveVisiblePhotos = true;
                Iterable<Relationship> uploaded =
                        eachUnknown.getRelationships(RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()), Direction.OUTGOING);
                for (Relationship upls : uploaded) {
                    Node photo = upls.getOtherNode(eachUnknown);
                    if (photo.hasLabel(Label.label(Labels.PHOTO.getLabelName()))) {
                        boolean onlyOwnerCanSee = (Boolean) photo.getProperty(PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), false);
                        if (!onlyOwnerCanSee) {
                            dontHaveVisiblePhotos = false;
                            break;
                        }
                    }
                }
                if (dontHaveVisiblePhotos) {
                    continue;
                }

                Iterable<Relationship> relationshipIterable = eachUnknown.getRelationships(
                        Direction.OUTGOING,
                        RelationshipType.withName(Relationships.LIKE.name()),
                        RelationshipType.withName(Relationships.BLOCK.name())
                );
                boolean dontHaveRelWithSource = true;
                for (Relationship eachRel : relationshipIterable) {
                    if (eachRel.getOtherNode(eachUnknown).getId() == sourceUserNodeId) {
                        dontHaveRelWithSource = false;
                        break;
                    }
                }

                if (dontHaveRelWithSource) {
                    result.add(eachUnknown);
                }
            }
        }
        return result;
    }

    private static List<Node> fetchProfiles(String queryFirstPart, String querySecondPart,
                                            String sourceUserId, String targetSex,
                                            int skip, int limit,
                                            int distance,
                                            GraphDatabaseService database) {
        List<Node> result = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        params.put("sourceUserId", sourceUserId);
        params.put("skipParam", skip);
        params.put("limitParam", limit);
        params.put("sex", targetSex);
        params.put("distance", distance);
        List<String> newUsers = executeQueryAndReturnUserIds(queryFirstPart, params, database);
        if (newUsers.size() < limit) {
            //it means that we can skip popular request
            return collectNodes(newUsers, database);
        }

        if (Objects.nonNull(querySecondPart)) {
            List<String> popularUseIds = executeQueryAndReturnUserIds(querySecondPart, params, database);
            newUsers.addAll(popularUseIds);
            Collections.shuffle(newUsers);
        }

        //todo:future place for optimization (we can ask only limit nodes)
        result.addAll(collectNodes(newUsers, database));

        return result;
    }

    private static List<Node> fetchProfilesForMenSeenPart(String queryFirstPart, String querySecondPart,
                                                          String sourceUserId, String targetSex,
                                                          int skip, int limit,
                                                          int distance,
                                                          int maxFirstPart, int maxSecondPart,
                                                          GraphDatabaseService database) {
        List<Node> result = new ArrayList<>();
        long onlineTime = System.currentTimeMillis() - 4 * 24 * 60 * 60 * 1000;//4days
        Map<String, Object> params = new HashMap<>();
        params.put("sourceUserId", sourceUserId);
        params.put("skipParam", skip);
        params.put("limitParam", limit);
        params.put("sex", targetSex);
        params.put("distance", distance);
        params.put("onlineTime", onlineTime);
        List<String> newUsers = executeQueryAndReturnUserIds(queryFirstPart, params, database);
        if (newUsers.size() < limit) {
            //it means that we can skip popular request
            return collectNodes(newUsers, database);
        }

        List<String> secondQueryUsers = new ArrayList<>();
        if (Objects.nonNull(querySecondPart)) {
            secondQueryUsers.addAll(executeQueryAndReturnUserIds(querySecondPart, params, database));
        }

        if (secondQueryUsers.size() > maxSecondPart) {
            secondQueryUsers = secondQueryUsers.subList(0, maxSecondPart);
        }

        if (newUsers.size() > maxFirstPart) {
            newUsers = newUsers.subList(0, maxFirstPart);
        }

        newUsers.addAll(secondQueryUsers);
        Collections.shuffle(newUsers);

        //todo:future place for optimization (we can ask only limit nodes)
        result.addAll(collectNodes(newUsers, database));

        return result;
    }

    private static List<String> executeQueryAndReturnUserIds(String query, Map<String, Object> params, GraphDatabaseService database) {
        log.info("execute query [%s] for userId [%s]", query, params.get("sourceUserId"));
        List<String> ids = new ArrayList<>();
        Result result = database.execute(query, params);
        while (result.hasNext()) {
            Map<String, Object> mapResult = result.next();
            String userId = (String) mapResult.get("userId");
            ids.add(userId);
        }
        return ids;
    }

    private static List<Node> collectNodes(Iterable<String> userIds, GraphDatabaseService database) {
        List<Node> result = new ArrayList<>();
        for (String each : userIds) {
            Node node = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), each);
            if (Objects.nonNull(node)) {
                result.add(node);
            }
        }
        return result;
    }

}

class PreparedNode {
    public Node node;
    public long index;
}