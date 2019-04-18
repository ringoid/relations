package com.ringoid.api;

import com.graphaware.common.log.LoggerFactory;
import com.ringoid.Labels;
import com.ringoid.PhotoProperties;
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
import static com.ringoid.PersonProperties.SEX;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.PhotoProperties.ONLY_OWNER_CAN_SEE;

public class NewFaces {

    private final static Log log = LoggerFactory.getLogger(NewFaces.class);

    private static final int MAX_LOOP_NUM = 4;

    private static final String SEEN_SORTED_BY_LIKES_QUERY = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                    "MATCH (target:%s {%s:$sex})-[:%s]->(pp:%s) " +//2
                    "WHERE NOT (target)-[:%s|%s|%s|%s]-(sourceUser) " +//2.1
                    "AND (NOT exists(pp.%s) OR pp.%s = false) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, target.%s AS likes ORDER BY likes DESC, userId SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(),//1
            PERSON.getLabelName(), SEX.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), //2
            Relationships.BLOCK, Relationships.LIKE, Relationships.MESSAGE, Relationships.MATCH,//2.1
            ONLY_OWNER_CAN_SEE.getPropertyName(), ONLY_OWNER_CAN_SEE.getPropertyName(),//2.2
            USER_ID.getPropertyName(), LIKE_COUNTER.getPropertyName()//3
    );

    private static final String GEO_SEEN_SORTED_BY_LIKES_QUERY = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                    "MATCH (target:%s {%s:$sex})-[:%s]->(pp:%s) " +//2
                    "WHERE distance(sourceUser.%s, target.%s) <= $distance " +//2.01
                    "AND (NOT (target)-[:%s|%s|%s|%s]-(sourceUser)) " +//2.1
                    "AND (NOT exists(pp.%s) OR pp.%s = false) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, target.%s AS likes ORDER BY likes DESC, userId SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(),//1
            PERSON.getLabelName(), SEX.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), //2
            LOCATION.getPropertyName(), LOCATION.getPropertyName(), //2.01
            Relationships.BLOCK, Relationships.LIKE, Relationships.MESSAGE, Relationships.MATCH,//2.1
            ONLY_OWNER_CAN_SEE.getPropertyName(), ONLY_OWNER_CAN_SEE.getPropertyName(),//2.2
            USER_ID.getPropertyName(), LIKE_COUNTER.getPropertyName()//3
    );


    private static final String SEEN_SORTED_BY_ONLINE_TIME_QUERY = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                    "MATCH (target:%s {%s:$sex})-[:%s]->(pp:%s) " +//2
                    "WHERE NOT (target)-[:%s|%s|%s|%s]-(sourceUser) " +//2.1
                    "AND (NOT exists(pp.%s) OR pp.%s = false) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, target.%s AS likes, target.%s AS onlineTime ORDER BY onlineTime DESC, userId SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(),//1
            PERSON.getLabelName(), SEX.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), //2
            Relationships.BLOCK, Relationships.LIKE, Relationships.MESSAGE, Relationships.MATCH,//2.1
            ONLY_OWNER_CAN_SEE.getPropertyName(), ONLY_OWNER_CAN_SEE.getPropertyName(),//2.2
            USER_ID.getPropertyName(), LIKE_COUNTER.getPropertyName(), LAST_ONLINE_TIME.getPropertyName()//3
    );

    private static final String GEO_SEEN_SORTED_BY_ONLINE_TIME_QUERY = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                    "MATCH (target:%s {%s:$sex})-[:%s]->(pp:%s) " +//2
                    "WHERE distance(sourceUser.%s, target.%s) <= $distance " +//2.01
                    "AND (NOT (target)-[:%s|%s|%s|%s]-(sourceUser)) " +//2.1
                    "AND (NOT exists(pp.%s) OR pp.%s = false) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, target.%s AS likes, target.%s AS onlineTime ORDER BY onlineTime DESC, userId SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(),//1
            PERSON.getLabelName(), SEX.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), //2
            LOCATION.getPropertyName(), LOCATION.getPropertyName(), //2.01
            Relationships.BLOCK, Relationships.LIKE, Relationships.MESSAGE, Relationships.MATCH,//2.1
            ONLY_OWNER_CAN_SEE.getPropertyName(), ONLY_OWNER_CAN_SEE.getPropertyName(),//2.2
            USER_ID.getPropertyName(), LIKE_COUNTER.getPropertyName(), LAST_ONLINE_TIME.getPropertyName()//3
    );

    private static final String NOT_SEEN_SORTED_BY_LIKES_QUERY = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                    "MATCH (target:%s {%s:$sex})-[:%s]->(pp:%s) " +//2
                    "WHERE NOT (target)<-[]-(sourceUser) " +//2.1
                    "AND (NOT exists(pp.%s) OR pp.%s = false) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, target.%s AS likes ORDER BY likes DESC, userId SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(),//1
            PERSON.getLabelName(), SEX.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), //2
            ONLY_OWNER_CAN_SEE.getPropertyName(), ONLY_OWNER_CAN_SEE.getPropertyName(),//2.2
            USER_ID.getPropertyName(), LIKE_COUNTER.getPropertyName()//3
    );

    private static final String GEO_NOT_SEEN_SORTED_BY_LIKES_QUERY = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                    "MATCH (target:%s {%s:$sex})-[:%s]->(pp:%s) " +//2
                    "WHERE distance(sourceUser.%s, target.%s) <= $distance " +//2.01
                    "AND (NOT (target)<-[]-(sourceUser)) " +//2.1
                    "AND (NOT exists(pp.%s) OR pp.%s = false) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, target.%s AS likes ORDER BY likes DESC, userId SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(),//1
            PERSON.getLabelName(), SEX.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), //2
            LOCATION.getPropertyName(), LOCATION.getPropertyName(), //2.01
            ONLY_OWNER_CAN_SEE.getPropertyName(), ONLY_OWNER_CAN_SEE.getPropertyName(),//2.2
            USER_ID.getPropertyName(), LIKE_COUNTER.getPropertyName()//3
    );

    private static final String NOT_SEEN_SORTED_BY_ONLINE_TIME_QUERY = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                    "MATCH (target:%s {%s:$sex})-[:%s]->(pp:%s) " +//2
                    "WHERE NOT (target)<-[]-(sourceUser) " +//2.1
                    "AND (NOT exists(pp.%s) OR pp.%s = false) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, target.%s AS likes, target.%s AS onlineTime ORDER BY onlineTime DESC, userId SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(),//1
            PERSON.getLabelName(), SEX.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), //2
            ONLY_OWNER_CAN_SEE.getPropertyName(), ONLY_OWNER_CAN_SEE.getPropertyName(),//2.2
            USER_ID.getPropertyName(), LIKE_COUNTER.getPropertyName(), LAST_ONLINE_TIME.getPropertyName()//3
    );

    private static final String GEO_NOT_SEEN_SORTED_BY_ONLINE_TIME_QUERY = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                    "MATCH (target:%s {%s:$sex})-[:%s]->(pp:%s) " +//2
                    "WHERE distance(sourceUser.%s, target.%s) <= $distance " +//2.01
                    "AND (NOT (target)<-[]-(sourceUser)) " +//2.1
                    "AND (NOT exists(pp.%s) OR pp.%s = false) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, target.%s AS likes, target.%s AS onlineTime ORDER BY onlineTime DESC, userId SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(),//1
            PERSON.getLabelName(), SEX.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), //2
            LOCATION.getPropertyName(), LOCATION.getPropertyName(), //2.01
            ONLY_OWNER_CAN_SEE.getPropertyName(), ONLY_OWNER_CAN_SEE.getPropertyName(),//2.2
            USER_ID.getPropertyName(), LIKE_COUNTER.getPropertyName(), LAST_ONLINE_TIME.getPropertyName()//3
    );

    private static final List<Integer> DISTANCES = new ArrayList<>();

    static {
        DISTANCES.add(5_000);
        DISTANCES.add(10_000);
        DISTANCES.add(25_000);
        DISTANCES.add(100_000);
        DISTANCES.add(300_000);
    }

    public static NewFacesResponse newFaces(NewFacesRequest request, GraphDatabaseService database) {
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

                List<Node> respone = loopByUnseenPart(sourceUser.getId(), request.getUserId(), targetSex, request.getLimit(),
                        Collections.emptySet(), database);
                //now check that result <= limit
                log.info("new_faces (not seen part) for userId [%s] size is [%s]", request.getUserId(), respone.size());
                if (respone.size() < request.getLimit()) {
                    Set<Long> nodeIds = new HashSet<>(respone.size());
                    for (Node each : respone) {
                        nodeIds.add(each.getId());
                    }
                    List<Node> seen = loopBySeenPart(sourceUser.getId(), request.getUserId(), targetSex, request.getLimit(),
                            nodeIds, database);
                    log.info("new_faces (seen part) for userId [%s] size is [%s]", request.getUserId(), seen.size());
                    respone.addAll(seen);
                }

                List<Profile> profileList = new ArrayList<>(respone.size());
                for (Node eachProfile : respone) {
                    Profile prof = new Profile();
                    prof.setUserId((String) eachProfile.getProperty(USER_ID.getPropertyName()));
                    prof.setPhotos(Utils.resizedAndVisibleToEveryOnePhotos(sortPhotos(eachProfile), request.getResolution(), database));
                    if (!prof.getPhotos().isEmpty()) {
                        profileList.add(prof);
                    }
                }

                if (profileList.size() > request.getLimit()) {
                    profileList = profileList.subList(0, request.getLimit());
                }
                log.info("new_faces (full) for userId [%s] size is [%s]", request.getUserId(), profileList.size());
                response.setNewFaces(profileList);
            }
            tx.success();
        }
        return response;
    }

    private static List<Node> sortProfiles(List<Node> tmpResult) {
        if (tmpResult.isEmpty()) {
            return tmpResult;
        }

        Collections.sort(tmpResult, new Comparator<Node>() {
            @Override
            public int compare(Node node1, Node node2) {
//                long likeCounter1 = (Long) node1.getProperty(LIKE_COUNTER.getPropertyName(), 0L);
//                long likeCounter2 = (Long) node2.getProperty(LIKE_COUNTER.getPropertyName(), 0L);
//                if (likeCounter1 > likeCounter2) {
//                    return -1;
//                } else if (likeCounter1 < likeCounter2) {
//                    return 1;
//                }

                //now count photos
                int photoCounter1 = 0;
                for (Relationship each : node1.getRelationships(Direction.OUTGOING, RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()))) {
                    Node eachPhoto = each.getOtherNode(node1);
                    if (Objects.nonNull(eachPhoto) && eachPhoto.hasLabel(Label.label(Labels.PHOTO.getLabelName()))) {
                        if (!((Boolean) eachPhoto.getProperty(ONLY_OWNER_CAN_SEE.getPropertyName(), false))) {
                            photoCounter1++;
                        }
                    }
                }
                int photoCounter2 = 0;
                for (Relationship each : node2.getRelationships(Direction.OUTGOING, RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()))) {
                    Node eachPhoto = each.getOtherNode(node2);
                    if (Objects.nonNull(eachPhoto) && eachPhoto.hasLabel(Label.label(Labels.PHOTO.getLabelName()))) {
                        if (!((Boolean) eachPhoto.getProperty(ONLY_OWNER_CAN_SEE.getPropertyName(), false))) {
                            photoCounter2++;
                        }
                    }
                }

                if (photoCounter1 > photoCounter2) {
                    return -1;
                } else if (photoCounter1 < photoCounter2) {
                    return 1;
                }

                //compare last online time
//                long lastOnline1 = (Long) node1.getProperty(LAST_ONLINE_TIME.getPropertyName(), 0L);
//                long lastOnline2 = (Long) node2.getProperty(LAST_ONLINE_TIME.getPropertyName(), 0L);
//                if (lastOnline1 > lastOnline2) {
//                    return -1;
//                } else if (lastOnline1 < lastOnline2) {
//                    return 1;
//                }
                return 0;
            }
        });
        return tmpResult;
    }

    private static List<String> sortPhotos(Node each) {
        List<Node> photos = new ArrayList<>();
        Iterable<Relationship> uploads = each.getRelationships(RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()), Direction.OUTGOING);
        for (Relationship eachRel : uploads) {
            Node photo = eachRel.getOtherNode(each);
            if (photo.hasLabel(Label.label(PHOTO.getLabelName()))) {
                photos.add(photo);
            }
        }

        Collections.sort(photos, new Comparator<Node>() {
            @Override
            public int compare(Node node1, Node node2) {
                long photoCounter1 = (Long) node1.getProperty(PhotoProperties.LIKE_COUNTER.getPropertyName(), 0L);
                long photoCounter2 = (Long) node2.getProperty(PhotoProperties.LIKE_COUNTER.getPropertyName(), 0L);
                if (photoCounter1 > photoCounter2) {
                    return -1;
                } else if (photoCounter1 < photoCounter2) {
                    return 1;
                }

                long uploadedAt1 = (Long) node1.getProperty(PhotoProperties.PHOTO_UPLOADED_AT.getPropertyName(), 0L);
                long uploadedAt2 = (Long) node2.getProperty(PhotoProperties.PHOTO_UPLOADED_AT.getPropertyName(), 0L);
                if (uploadedAt1 > uploadedAt2) {
                    return -1;
                } else if (uploadedAt1 < uploadedAt2) {
                    return 1;
                }
                return 0;
            }
        });

        List<String> result = new ArrayList<>(photos.size());
        for (Node eachP : photos) {
            result.add((String) eachP.getProperty(PhotoProperties.PHOTO_ID.getPropertyName()));
        }
        return result;
    }

    private static List<Node> loopBySeenPart(long sourceUserNodeId, String sourceUserId, String targetSex,
                                             int limit,
                                             Set<Long> nodeIdsToExclude,
                                             GraphDatabaseService database) {
        long start = System.currentTimeMillis();

        List<Node> result = new ArrayList<>();
        for (int eachD : DISTANCES) {
            if (result.size() < limit) {
                result.clear();
            } else {
                break;
            }
            result.addAll(loopRequest(sourceUserNodeId, sourceUserId, targetSex, limit, eachD,
                    GEO_SEEN_SORTED_BY_ONLINE_TIME_QUERY, GEO_SEEN_SORTED_BY_LIKES_QUERY,
                    nodeIdsToExclude,
                    database));
        }

        log.info("loopBySeenPart : found [%s] profiles using location for userId [%s] in [%s] millis",
                result.size(), sourceUserId, System.currentTimeMillis() - start);

        result = sortProfiles(result);

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
                SEEN_SORTED_BY_ONLINE_TIME_QUERY, SEEN_SORTED_BY_LIKES_QUERY,
                nodeIds,
                database);

        log.info("loopBySeenPart : found [%s] profiles without location for userId [%s] in [%s] millis",
                tmp.size(), sourceUserId, System.currentTimeMillis() - start);

        tmp = sortProfiles(tmp);

        result.addAll(tmp);

        return result;
    }

    private static List<Node> loopByUnseenPart(long sourceUserNodeId, String sourceUserId, String targetSex,
                                               int limit,
                                               Set<Long> nodeIdsToExclude,
                                               GraphDatabaseService database) {

        long start = System.currentTimeMillis();

        List<Node> result = new ArrayList<>();
        for (int eachD : DISTANCES) {
            if (result.size() < limit) {
                result.clear();
            } else {
                break;
            }
            result.addAll(loopRequest(sourceUserNodeId, sourceUserId, targetSex, limit, eachD,
                    GEO_NOT_SEEN_SORTED_BY_ONLINE_TIME_QUERY, GEO_NOT_SEEN_SORTED_BY_LIKES_QUERY,
                    nodeIdsToExclude,
                    database));
        }

        log.info("loopByUnseenPart : found [%s] profiles using location for userId [%s] in [%s] millis",
                result.size(), sourceUserId, System.currentTimeMillis() - start);

        result = sortProfiles(result);

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
                database);

        log.info("loopByUnseenPart : found [%s] profiles without location for userId [%s] in [%s] millis",
                tmp.size(), sourceUserId, System.currentTimeMillis() - start);

        tmp = sortProfiles(tmp);

        result.addAll(tmp);

        return result;
    }

    private static List<Node> loopRequest(long sourceUserNodeId, String sourceUserId, String targetSex,
                                          int limit, int distance,
                                          String queryFirst, String querySecond,
                                          Set<Long> nodeIdsToExclude,
                                          GraphDatabaseService database) {

        int skip = 0;
        int loopCounter = 0;

        List<Node> result = new ArrayList<>();

        while (result.size() < limit && loopCounter < MAX_LOOP_NUM) {
            List<Node> unknowns = fetchProfiles(queryFirst, querySecond, sourceUserId, targetSex, skip, limit, distance, database);
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

        List<String> popularUseIds = executeQueryAndReturnUserIds(querySecondPart, params, database);
        newUsers.addAll(popularUseIds);

        //todo:future place for optimization (we can ask only limit nodes)
        result.addAll(collectNodes(newUsers, database));

        //todo:uncomments!!!
        Collections.shuffle(result);

        return result;
    }

    private static List<String> executeQueryAndReturnUserIds(String query, Map<String, Object> params, GraphDatabaseService database) {
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
