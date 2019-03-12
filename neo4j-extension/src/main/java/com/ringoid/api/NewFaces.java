package com.ringoid.api;

import com.google.common.collect.Lists;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.ringoid.Labels.HIDDEN;
import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.PersonProperties.LAST_ACTION_TIME;
import static com.ringoid.PersonProperties.LAST_ONLINE_TIME;
import static com.ringoid.PersonProperties.LIKE_COUNTER;
import static com.ringoid.PersonProperties.SEX;
import static com.ringoid.PersonProperties.USER_ID;

public class NewFaces {

    private static final int MAX_LOOP_NUM = 4;

    private static final String NEW_FACES_SEEN_QUERY = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                    "MATCH (target:%s) " +//2
                    "WHERE NOT (target)-[:%s|%s|%s|%s]-(sourceUser) " +//2.1
                    "RETURN DISTINCT target.%s AS userId, target.%s AS likes ORDER BY likes DESC SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(),//1
            PERSON.getLabelName(), //2
            Relationships.BLOCK, Relationships.LIKE, Relationships.MESSAGE, Relationships.MATCH,
            USER_ID.getPropertyName(), LIKE_COUNTER.getPropertyName()//3
    );

    private static final String NEW_FACES_QUERY = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                    "MATCH (target:%s) " +//2
                    "WHERE NOT (target)<-[]-(sourceUser) " +//2.1
                    "RETURN DISTINCT target.%s AS userId, target.%s AS likes ORDER BY likes DESC SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(),//1
            PERSON.getLabelName(), //2
            USER_ID.getPropertyName(), LIKE_COUNTER.getPropertyName()//3
    );

    private static List<Node> unknowns(NewFacesRequest request, String useQuery, int skip, GraphDatabaseService database) {
        if (Objects.equals(NEW_FACES_QUERY, useQuery)) {
            return unknownPersons(request.getUserId(), skip, request.getLimit(), database);
        } else if (Objects.equals(NEW_FACES_SEEN_QUERY, useQuery)) {
            return moreProfilesForNewFaces(request.getUserId(), skip, 20, database);
        }
        return Lists.newArrayList();
    }

    public static NewFacesResponse newFaces(NewFacesRequest request, GraphDatabaseService database) {
        NewFacesResponse response = new NewFacesResponse();
        try (Transaction tx = database.beginTx()) {
            Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), request.getUserId());
            long actionTime = (Long) sourceUser.getProperty(LAST_ACTION_TIME.getPropertyName(), 0L);
            response.setLastActionTime(actionTime);
            if (request.getRequestedLastActionTime() <= actionTime) {
                String sex = (String) sourceUser.getProperty(SEX.getPropertyName(), "male");
                String targetSex = "female";
                if (Objects.equals("female", sex)) {
                    targetSex = "male";
                }

                int skip = 0;
                int loopCounter = 0;
                List<Node> tmpResult = new ArrayList<>();
                while (tmpResult.size() < request.getLimit() && loopCounter < MAX_LOOP_NUM) {
                    List<Node> unknowns = unknowns(request, NEW_FACES_QUERY, skip, database);
                    if (unknowns.size() < request.getLimit()) {
                        //there is no more users, exit from the loop
                        loopCounter = MAX_LOOP_NUM;
                    } else {
                        skip += unknowns.size();
                        loopCounter++;
                    }

                    for (Node eachUnknown : unknowns) {
                        if (sourceUser.getId() == eachUnknown.getId()) {
                            continue;
                        }
                        if (eachUnknown.hasLabel(Label.label(HIDDEN.getLabelName()))) {
                            continue;
                        }
                        String nodeSex = (String) eachUnknown.getProperty(SEX.getPropertyName());
                        if (!Objects.equals(targetSex, nodeSex)) {
                            continue;
                        }
                        if (!eachUnknown.hasRelationship(RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()), Direction.OUTGOING)) {
                            continue;
                        }

                        Iterable<Relationship> relationshipIterable = eachUnknown.getRelationships(
                                Direction.OUTGOING,
                                RelationshipType.withName(Relationships.LIKE.name()),
                                RelationshipType.withName(Relationships.BLOCK.name())
                        );
                        int hasRelWithSource = 0;
                        for (Relationship eachRel : relationshipIterable) {
                            if (eachRel.getOtherNode(eachUnknown).getId() == sourceUser.getId()) {
                                hasRelWithSource++;
                            }
                        }
                        if (hasRelWithSource == 0) {
                            tmpResult.add(eachUnknown);
                        }

                    }
                }//tmpResult is ready
                tmpResult = sortProfiles(tmpResult);
                //now check that result <= limit
                if (tmpResult.size() > request.getLimit()) {
                    tmpResult = tmpResult.subList(0, request.getLimit() - 1);
                }
                List<Profile> profileList = new ArrayList<>(tmpResult.size());
                for (Node eachProfile : tmpResult) {
                    Profile prof = new Profile();
                    prof.setUserId((String) eachProfile.getProperty(USER_ID.getPropertyName()));
                    prof.setPhotoIds(sortPhotos(eachProfile));
                    profileList.add(prof);
                }

                //now fill profiles with 20 users with highest rank
                if (profileList.isEmpty()) {
                    tmpResult = new ArrayList<>();
                    skip = 0;
                    loopCounter = 0;
                    while (tmpResult.size() < request.getLimit() && loopCounter < MAX_LOOP_NUM) {
                        List<Node> unknowns = unknowns(request, NEW_FACES_SEEN_QUERY, skip, database);
                        if (unknowns.size() < request.getLimit()) {
                            //there is no more users, exit from the loop
                            loopCounter = MAX_LOOP_NUM;
                        } else {
                            skip += unknowns.size();
                            loopCounter++;
                        }

                        for (Node eachUnknown : unknowns) {
                            if (sourceUser.getId() == eachUnknown.getId()) {
                                continue;
                            }
                            if (eachUnknown.hasLabel(Label.label(HIDDEN.getLabelName()))) {
                                continue;
                            }
                            String nodeSex = (String) eachUnknown.getProperty(SEX.getPropertyName());
                            if (!Objects.equals(targetSex, nodeSex)) {
                                continue;
                            }
                            if (!eachUnknown.hasRelationship(RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()), Direction.OUTGOING)) {
                                continue;
                            }

                            tmpResult.add(eachUnknown);
                        }
                    }//tmpResult is ready

                    //now check that result <= limit
                    if (tmpResult.size() > request.getLimit()) {
                        tmpResult = tmpResult.subList(0, request.getLimit() - 1);
                    }
                    for (Node eachProfile : tmpResult) {
                        Profile prof = new Profile();
                        prof.setUserId((String) eachProfile.getProperty(USER_ID.getPropertyName()));
                        prof.setPhotoIds(sortPhotos(eachProfile));
                        profileList.add(prof);
                    }
                }//end block for seen top profiles

                response.setNewFaces(profileList);
            }
            tx.success();
        }
        return response;
    }

    private static List<Node> sortProfiles(List<Node> tmpResult) {
        Collections.sort(tmpResult, new Comparator<Node>() {
            @Override
            public int compare(Node node1, Node node2) {
                long likeCounter1 = (Long) node1.getProperty(LIKE_COUNTER.getPropertyName(), 0L);
                long likeCounter2 = (Long) node2.getProperty(LIKE_COUNTER.getPropertyName(), 0L);
                if (likeCounter1 > likeCounter2) {
                    return -1;
                } else if (likeCounter1 < likeCounter2) {
                    return 1;
                }

                //now count photos
                int photoCounter1 = 0;
                for (Relationship each : node1.getRelationships(Direction.OUTGOING, RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()))) {
                    photoCounter1++;
                }
                int photoCounter2 = 0;
                for (Relationship each : node2.getRelationships(Direction.OUTGOING, RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()))) {
                    photoCounter2++;
                }

                if (photoCounter1 > photoCounter2) {
                    return -1;
                } else if (photoCounter1 < photoCounter2) {
                    return 1;
                }

                //compare last online time
                long lastOnline1 = (Long) node1.getProperty(LAST_ONLINE_TIME.getPropertyName(), 0L);
                long lastOnline2 = (Long) node2.getProperty(LAST_ONLINE_TIME.getPropertyName(), 0L);
                if (lastOnline1 > lastOnline2) {
                    return -1;
                } else if (lastOnline1 < lastOnline2) {
                    return 1;
                }
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

    private static List<Node> unknownPersons(String sourceUserId, int skip, int limit, GraphDatabaseService database) {
        List<Node> nodes = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        params.put("sourceUserId", sourceUserId);
        params.put("skipParam", skip);
        params.put("limitParam", limit);
        Result result = database.execute(NEW_FACES_QUERY, params);
        while (result.hasNext()) {
            Map<String, Object> mapResult = result.next();
            String userId = (String) mapResult.get("userId");
            Node node = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), userId);
            nodes.add(node);
        }
        return nodes;
    }

    private static List<Node> moreProfilesForNewFaces(String sourceUserId, int skip, int limit, GraphDatabaseService database) {
        List<Node> nodes = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        params.put("sourceUserId", sourceUserId);
        params.put("skipParam", skip);
        params.put("limitParam", limit);
        Result result = database.execute(NEW_FACES_SEEN_QUERY, params);
        while (result.hasNext()) {
            Map<String, Object> mapResult = result.next();
            String userId = (String) mapResult.get("userId");
            Node node = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), userId);
            nodes.add(node);
        }
        return nodes;
    }
}
