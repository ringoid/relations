package com.ringoid.api;

import com.codahale.metrics.MetricRegistry;
import com.graphaware.common.log.LoggerFactory;
import com.ringoid.Labels;
import com.ringoid.MatchProperties;
import com.ringoid.MessageRelationshipProperties;
import com.ringoid.PersonProperties;
import com.ringoid.PhotoProperties;
import com.ringoid.Relationships;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Result;
import org.neo4j.logging.Log;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.PersonProperties.LAST_ONLINE_TIME;
import static com.ringoid.PersonProperties.LIKE_COUNTER;
import static com.ringoid.PersonProperties.LOCATION;
import static com.ringoid.PersonProperties.SEX;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.PersonProperties.YEAR;
import static com.ringoid.PhotoProperties.ONLY_OWNER_CAN_SEE;
import static com.ringoid.api.Utils.enrichProfile;
import static com.ringoid.api.Utils.sortLMHISPhotos;
import static com.ringoid.api.Utils.sortNewFacesUnseenPhotos;

public class QueryUtils {

    private static final Log log = LoggerFactory.getLogger(QueryUtils.class);

    //$sourceUserId, $targetSex, $limitParam
    static final String DISCOVER_GEO_NOT_SEEN_SORTED_BY_ONLINE_TIME_DESC = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                    "MATCH (target:%s) WHERE target.%s = $targetSex" +//2
                    //!!!dynamic part
                    "AGE_FILTER DISTANCE_FILTER" +
                    //!!!
                    "WITH sourceUser, target " +
                    "WHERE NOT (target)<-[:%s|%s|%s|%s|%s|%s|%s|%s]-(sourceUser) " +//2.1
                    "AND (target)-[:%s]->(:%s) AND NOT('%s' in labels(target)) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, target.%s AS onlineTime ORDER BY onlineTime DESC LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(),//1
            PERSON.getLabelName(), SEX.getPropertyName(),//2
            Relationships.VIEW, Relationships.VIEW_IN_LIKES_YOU, Relationships.VIEW_IN_MATCHES, Relationships.VIEW_IN_MESSAGES, Relationships.BLOCK, Relationships.LIKE, Relationships.MESSAGE, Relationships.MATCH,//2.1
            Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), Labels.HIDDEN.getLabelName(),//2.2
            USER_ID.getPropertyName(), LAST_ONLINE_TIME.getPropertyName()//3
    );

    //$sourceUserId, $targetSex, $skipParam, $limitParam
    static final String DISCOVER_GEO_SEEN_SORTED_BY_ONLINE_TIME_DESC = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId})-[:%s]->(target:%s)-[:%s]->(:%s) " +//1
                    "WHERE target.%s = $targetSex" +//2
                    //!!!dynamic part
                    "AGE_FILTER DISTANCE_FILTER" +
                    //!!!
                    "AND NOT('%s' in labels(target)) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, target.%s AS onlineTime ORDER BY onlineTime DESC SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.VIEW.name(), PERSON.getLabelName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//1
            SEX.getPropertyName(),//2
            Labels.HIDDEN.getLabelName(),//2.2
            USER_ID.getPropertyName(), LAST_ONLINE_TIME.getPropertyName()//3
    );

    //$sourceUserId, $targetSex, $skipParam, $limitParam
    static final String GET_LC_LIKES_GEO_UNSEEN_SORTED_BY_USER_ID = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId})<-[:%s]-(target:%s)-[:%s]->(photo:%s) " +//1
                    "WHERE (NOT exists(photo.%s) OR photo.%s = false)" +//2
                    //!!!dynamic part
                    "AGE_FILTER DISTANCE_FILTER" +
                    //!!!
                    "AND NOT (sourceUser)-[:%s]->(target) " +//2.1
                    "AND NOT('%s' in labels(target)) " +//2.2
                    "RETURN DISTINCT target.%s AS userId ORDER BY userId SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.LIKE.name(), PERSON.getLabelName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//1
            PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(),//2
            Relationships.VIEW_IN_LIKES_YOU.name(),//2.1
            Labels.HIDDEN.getLabelName(),//2.2
            USER_ID.getPropertyName()//3
    );

    //$sourceUserId, $targetSex, $skipParam, $limitParam
    static final String GET_LC_LIKES_GEO_SEEN_SORTED_BY_USER_ID = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId})<-[:%s]-(target:%s)-[:%s]->(photo:%s) " +//1
                    "WHERE (NOT exists(photo.%s) OR photo.%s = false)" +//2
                    //!!!dynamic part
                    "AGE_FILTER DISTANCE_FILTER" +
                    //!!!
                    "AND (sourceUser)-[:%s]->(target) " +//2.1
                    "AND NOT('%s' in labels(target)) " +//2.2
                    "RETURN DISTINCT target.%s AS userId ORDER BY userId SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.LIKE.name(), PERSON.getLabelName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//1
            PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(),//2
            Relationships.VIEW_IN_LIKES_YOU.name(),//2.1
            Labels.HIDDEN.getLabelName(),//2.2
            USER_ID.getPropertyName()//3
    );

    //$sourceUserId
    static final String GET_LC_LIKES_NUM = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId})<-[:%s]-(target:%s)-[:%s]->(photo:%s) " +//1
                    "WHERE (NOT exists(photo.%s) OR photo.%s = false)" +//2
                    "AND NOT('%s' in labels(target)) " +//2.2
                    "RETURN count(DISTINCT target.%s) as num",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.LIKE.name(), PERSON.getLabelName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//1
            PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(),//2
            Labels.HIDDEN.getLabelName(),//2.2
            USER_ID.getPropertyName()//3
    );

    //$sourceUserId
    static final String GET_LC_MESSAGES_AND_MATCHES_GEO = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId})-[:%s|%s]-(target:%s)-[:%s]->(photo:%s) " +//1
                    "WHERE (NOT exists(photo.%s) OR photo.%s = false)" +//2
                    //!!!dynamic part
                    "AGE_FILTER DISTANCE_FILTER" +
                    //!!!
                    "AND NOT('%s' in labels(target)) " +//2.2
                    "RETURN DISTINCT target.%s AS userId",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.MATCH.name(), Relationships.MESSAGE.name(), PERSON.getLabelName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//1
            PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(),//2
            Labels.HIDDEN.getLabelName(),//2.2
            USER_ID.getPropertyName()//3
    );

    //$sourceUserId
    static final String GET_LC_MESSAGES_NUM = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId})<-[:%s|%s]-(target:%s)-[:%s]->(photo:%s) " +//1
                    "WHERE (NOT exists(photo.%s) OR photo.%s = false)" +//2
                    "AND NOT('%s' in labels(target)) " +//2.2
                    "RETURN count(DISTINCT target.%s) as num",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.MATCH.name(), Relationships.MESSAGE, PERSON.getLabelName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//1
            PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(),//2
            Labels.HIDDEN.getLabelName(),//2.2
            USER_ID.getPropertyName()//3
    );

    public static String constructFilteredQuery(String baseQuery, Filter filter) {
        if (Objects.isNull(filter)) {
            return baseQuery.replaceFirst("AGE_FILTER DISTANCE_FILTER", " ");
        } else {
            String agePart = "";
            if (Objects.nonNull(filter.getMinAge())) {
                int minAge = LocalDate.now().getYear() - filter.getMinAge();
                agePart += String.format(" AND target.%s <= %s", YEAR.getPropertyName(), Integer.toString(minAge));
            }
            if (Objects.nonNull(filter.getMaxAge())) {
                int maxAge = LocalDate.now().getYear() - filter.getMaxAge();
                agePart += String.format(" AND target.%s >= %s", YEAR.getPropertyName(), Integer.toString(maxAge));
            }

            String distancePart = "";
            if (Objects.nonNull(filter.getMaxDistance())) {
                distancePart += String.format(" AND distance(sourceUser.%s, target.%s) <= %s",
                        LOCATION.getPropertyName(), LOCATION.getPropertyName(), filter.getMaxDistance().toString());
            }

            //return final result
            if (Objects.equals("", agePart) && Objects.equals("", distancePart)) {
                return baseQuery.replaceFirst("AGE_FILTER DISTANCE_FILTER", " ");
            } else if (!Objects.equals("", agePart) && Objects.equals("", distancePart)) {
                String tmpStr = baseQuery.replaceFirst(" DISTANCE_FILTER", "");
                return tmpStr.replaceFirst("AGE_FILTER", agePart + " ");
            } else if (Objects.equals("", agePart) && !Objects.equals("", distancePart)) {
                String tmpStr = baseQuery.replaceFirst("AGE_FILTER ", "");
                return tmpStr.replaceFirst("DISTANCE_FILTER", distancePart + " ");
            } else {
                String tmpStr = baseQuery.replaceFirst("AGE_FILTER", agePart);
                return tmpStr.replaceFirst("DISTANCE_FILTER", distancePart + " ");
            }
        }
    }

    public static int count(String query, String sourceUserId, GraphDatabaseService database, MetricRegistry metrics) {
        Map<String, Object> params = new HashMap<>();
        params.put("sourceUserId", sourceUserId);
        Result queryResult = database.execute(query, params, 10L, TimeUnit.SECONDS);
        Long result = 0L;
        while (queryResult.hasNext()) {
            result = (Long) queryResult.next().get("num");
        }
        return result.intValue();
    }

    public static List<Node> execute(String query, String sourceUserId, String targetSex, int skip, int limit,
                                     GraphDatabaseService database, MetricRegistry metrics) {
        log.info("execute query : %s", query);
        log.info("with params : sourceUserId : %s, targetSex : %s, skipParam : %s, limitParam : %s",
                sourceUserId, targetSex, Integer.toString(skip), Integer.toString(limit));
        List<Node> result = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        params.put("sourceUserId", sourceUserId);
        params.put("targetSex", targetSex);
        params.put("skipParam", skip);
        params.put("limitParam", limit);
        Result queryResult = database.execute(query, params, 10L, TimeUnit.SECONDS);

        while (queryResult.hasNext()) {
            Node node = database.findNode(Label.label(Labels.PERSON.getLabelName()), USER_ID.getPropertyName(), queryResult.next().get("userId"));
            if (Objects.nonNull(node)) {
                result.add(node);
            }
        }
        return result;
    }

    public static List<Node> filterNodesByVisiblePhotos(String sourceUserId, List<Node> source) {
        Iterator<Node> it = source.iterator();
        while (it.hasNext()) {
            Node each = it.next();
            String userId = (String) each.getProperty(USER_ID.getPropertyName(), "n/a");
            if (Objects.equals(sourceUserId, userId)) {
                it.remove();
            } else {
                boolean dontHaveVisiblePhotos = true;
                Iterable<Relationship> uploaded =
                        each.getRelationships(RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()), Direction.OUTGOING);
                for (Relationship upls : uploaded) {
                    Node photo = upls.getOtherNode(each);

                    if (photo.hasLabel(Label.label(Labels.PHOTO.getLabelName()))) {
                        boolean onlyOwnerCanSee = (Boolean) photo.getProperty(PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), false);
                        if (!onlyOwnerCanSee) {
                            dontHaveVisiblePhotos = false;
                            break;
                        }
                    }
                }
                if (dontHaveVisiblePhotos) {
                    it.remove();
                }
            }
        }
        return source;
    }

    public static List<Profile> createProfileListWithResizedAndSortedPhotos(String resolution, List<Node> source,
                                                                            boolean isItUnseenPart, Node sourceUser, GraphDatabaseService database,
                                                                            MetricRegistry metrics) {
        long start = System.currentTimeMillis();
        List<Profile> profileList = new ArrayList<>(source.size());
        for (Node eachProfile : source) {
            Profile prof = new Profile();
            prof.setUserId((String) eachProfile.getProperty(USER_ID.getPropertyName()));
            prof.setUnseen(isItUnseenPart);
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
        metrics.histogram("discover/lc_createProfileListWithResizedAndSortedPhotos").update(fullTime);
        return profileList;
    }

    public static List<Node> sortDiscoverUnseenPartProfiles(List<Node> tmpResult) {
        if (tmpResult.isEmpty()) {
            return tmpResult;
        }

        Collections.sort(tmpResult, new Comparator<Node>() {
            @Override
            public int compare(Node node1, Node node2) {
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

                long likeCounter1 = (Long) node1.getProperty(LIKE_COUNTER.getPropertyName(), 0L);
                long likeCounter2 = (Long) node2.getProperty(LIKE_COUNTER.getPropertyName(), 0L);
                if (likeCounter1 > likeCounter2) {
                    return -1;
                } else if (likeCounter1 < likeCounter2) {
                    return 1;
                }

                return 0;
            }
        });
        return tmpResult;
    }

    public static List<Node> discoverSortProfilesSeenPart(Node sourceUser, List<Node> sourceList) {
        List<Utils.NodeWrapper> wrapperList = new ArrayList<>(sourceList.size());
        for (Node each : sourceList) {
            int photoCounter = 0;
            int likeCounter = 0;
            int viewedFromSource = 0;
            Iterable<Relationship> uploads = each.getRelationships(RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()), Direction.OUTGOING);
            for (Relationship eachUpload : uploads) {
                Node photo = eachUpload.getOtherNode(each);
                if (photo.hasLabel(Label.label(Labels.PHOTO.getLabelName()))) {
                    Boolean onlyOwnerCanSee = (Boolean) photo.getProperty(PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), false);
                    if (!onlyOwnerCanSee) {
                        photoCounter++;
                        Iterable<Relationship> photoLikes = photo.getRelationships(RelationshipType.withName(Relationships.LIKE.name()), Direction.INCOMING);
                        //todo:here we can check who liked this photo - hidden user or not, but mb later
                        for (Relationship eachLike : photoLikes) {
                            likeCounter++;
                        }
                        Iterable<Relationship> viewRels = photo.getRelationships(RelationshipType.withName(Relationships.VIEW.name()), Direction.INCOMING);
                        for (Relationship eachView : viewRels) {
                            Node other = eachView.getOtherNode(photo);
                            if (other.hasLabel(Label.label(Labels.PERSON.getLabelName())) &&
                                    other.getId() == sourceUser.getId()) {
                                viewedFromSource++;
                                break;
                            }
                        }
                    }
                }//end each single photo
            }//end loop by all photos
            Utils.NodeWrapper nodeWrapper = new Utils.NodeWrapper();
            nodeWrapper.node = each;
            nodeWrapper.allPhotoCount = photoCounter;
            nodeWrapper.likes = likeCounter;
            nodeWrapper.unseenPhotos = photoCounter - viewedFromSource;
            wrapperList.add(nodeWrapper);
        }//end
        Collections.sort(wrapperList, new Comparator<Utils.NodeWrapper>() {
            @Override
            public int compare(Utils.NodeWrapper node1, Utils.NodeWrapper node2) {
                if (node1.unseenPhotos > node2.unseenPhotos) {
                    return -1;
                } else if (node1.unseenPhotos < node2.unseenPhotos) {
                    return 1;
                }

                if (node1.allPhotoCount > node2.allPhotoCount) {
                    return -1;
                } else if (node1.allPhotoCount < node2.allPhotoCount) {
                    return 1;
                }

                if (node1.likes > node2.likes) {
                    return -1;
                } else if (node1.likes < node2.likes) {
                    return 1;
                }
                return 0;
            }
        });
        List<Node> result = new ArrayList<>(wrapperList.size());
        for (Utils.NodeWrapper eachWrapper : wrapperList) {
            result.add(eachWrapper.node);
        }
        return result;
    }

    public static List<Node> sortGetLCUnseenPartProfiles(Node sourceUser, List<Node> tmpResult) {
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
                    Node eachPhoto = each.getOtherNode(node1);
                    if (Objects.nonNull(eachPhoto) &&
                            !((Boolean) eachPhoto.getProperty(PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), false))) {
                        photoCounter1++;
                    }
                }
                int photoCounter2 = 0;
                for (Relationship each : node2.getRelationships(Direction.OUTGOING, RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()))) {
                    Node eachPhoto = each.getOtherNode(node2);
                    if (Objects.nonNull(eachPhoto) &&
                            !((Boolean) eachPhoto.getProperty(PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), false))) {
                        photoCounter2++;
                    }
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

    public static List<Node> sortGetLCSeenPartProfiles(Node sourceUser, List<Node> sourceList) {
        List<Utils.NodeWrapper> wrapperList = new ArrayList<>(sourceList.size());
        for (Node each : sourceList) {
            long lastOnlineTime = (Long) each.getProperty(PersonProperties.LAST_ONLINE_TIME.getPropertyName(), 0L);
            int photoCounter = 0;
            int likeCounter = 0;
            int viewedFromSource = 0;
            Iterable<Relationship> uploads = each.getRelationships(RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()), Direction.OUTGOING);
            for (Relationship eachUpload : uploads) {
                Node photo = eachUpload.getOtherNode(each);
                if (photo.hasLabel(Label.label(Labels.PHOTO.getLabelName()))) {
                    Boolean onlyOwnerCanSee = (Boolean) photo.getProperty(PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), false);
                    if (!onlyOwnerCanSee) {
                        photoCounter++;
                        Iterable<Relationship> photoLikes = photo.getRelationships(RelationshipType.withName(Relationships.LIKE.name()), Direction.INCOMING);
                        //todo:here we can check who liked this photo - hidden user or not, but mb later
                        for (Relationship eachLike : photoLikes) {
                            likeCounter++;
                        }
                        Iterable<Relationship> viewRels = photo.getRelationships(RelationshipType.withName(Relationships.VIEW.name()), Direction.INCOMING);
                        for (Relationship eachView : viewRels) {
                            Node other = eachView.getOtherNode(photo);
                            if (other.hasLabel(Label.label(Labels.PERSON.getLabelName())) &&
                                    other.getId() == sourceUser.getId()) {
                                viewedFromSource++;
                                break;
                            }
                        }
                    }
                }//end each single photo
            }//end loop by all photos

            Utils.NodeWrapper nodeWrapper = new Utils.NodeWrapper();
            nodeWrapper.node = each;
            nodeWrapper.allPhotoCount = photoCounter;
            nodeWrapper.likes = likeCounter;
            nodeWrapper.unseenPhotos = photoCounter - viewedFromSource;
            nodeWrapper.onlineTime = lastOnlineTime;

            LocalDateTime timeOnline =
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(lastOnlineTime),
                            TimeZone.getDefault().toZoneId());
            LocalDateTime timeNow = LocalDateTime.now();
            int days = Long.valueOf(ChronoUnit.DAYS.between(timeOnline, timeNow)).intValue();
            if (days < 0) {
                days = days * (-1);
            }
            nodeWrapper.dayNum = days;

            wrapperList.add(nodeWrapper);
        }//end
        Collections.sort(wrapperList, new Comparator<Utils.NodeWrapper>() {
            @Override
            public int compare(Utils.NodeWrapper node1, Utils.NodeWrapper node2) {
                if (node1.dayNum > node2.dayNum) {
                    return 1;
                } else if (node1.dayNum < node2.dayNum) {
                    return -1;
                }

                if (node1.unseenPhotos > node2.unseenPhotos) {
                    return -1;
                } else if (node1.unseenPhotos < node2.unseenPhotos) {
                    return 1;
                }

                if (node1.allPhotoCount > node2.allPhotoCount) {
                    return -1;
                } else if (node1.allPhotoCount < node2.allPhotoCount) {
                    return 1;
                }

                if (node1.likes > node2.likes) {
                    return -1;
                } else if (node1.likes < node2.likes) {
                    return 1;
                }

                if (node1.onlineTime > node2.onlineTime) {
                    return -1;
                } else if (node1.onlineTime < node2.onlineTime) {
                    return 1;
                }
                return 0;
            }
        });
        List<Node> result = new ArrayList<>(wrapperList.size());
        for (Utils.NodeWrapper eachWrapper : wrapperList) {
            result.add(eachWrapper.node);
        }
        return result;
    }

    public static List<Node> sortProfilesByLastMessageAtOrMatchTime(Node sourceUser, List<Node> tmpResult) {
        Collections.sort(tmpResult, new Comparator<Node>() {
            @Override
            public int compare(Node node1, Node node2) {
                long lastMessageAt1 = 0;
                Iterable<Relationship> messages1 = node1.getRelationships(Direction.BOTH, RelationshipType.withName(Relationships.MESSAGE.name()));
                for (Relationship each : messages1) {
                    Node other = each.getOtherNode(node1);
                    if (other.hasLabel(Label.label(PERSON.getLabelName())) && other.getId() == sourceUser.getId()) {
                        lastMessageAt1 = (Long) each.getProperty(MessageRelationshipProperties.MSG_AT.getPropertyName(), 0L);
                        break;
                    }
                }
                long time1 = lastMessageAt1;
                if (time1 == 0L) {
                    Iterable<Relationship> matches1 = node1.getRelationships(Direction.BOTH, RelationshipType.withName(Relationships.MATCH.name()));
                    for (Relationship each : matches1) {
                        Node other = each.getOtherNode(node1);
                        if (other.hasLabel(Label.label(PERSON.getLabelName())) && other.getId() == sourceUser.getId()) {
                            long matchTime1 = (Long) each.getProperty(MatchProperties.MATCH_AT.getPropertyName(), 0L);
                            time1 = matchTime1;
                            break;
                        }
                    }
                }

                long lastMessageAt2 = 0;
                Iterable<Relationship> messages2 = node2.getRelationships(Direction.BOTH, RelationshipType.withName(Relationships.MESSAGE.name()));
                for (Relationship each : messages2) {
                    Node other = each.getOtherNode(node2);
                    if (other.hasLabel(Label.label(PERSON.getLabelName())) && other.getId() == sourceUser.getId()) {
                        lastMessageAt2 = (Long) each.getProperty(MessageRelationshipProperties.MSG_AT.getPropertyName(), 0L);
                        break;
                    }
                }
                long time2 = lastMessageAt2;
                if (time2 == 0L) {
                    Iterable<Relationship> matches2 = node2.getRelationships(Direction.BOTH, RelationshipType.withName(Relationships.MATCH.name()));
                    for (Relationship each : matches2) {
                        Node other = each.getOtherNode(node2);
                        if (other.hasLabel(Label.label(PERSON.getLabelName())) && other.getId() == sourceUser.getId()) {
                            long matchTime2 = (Long) each.getProperty(MatchProperties.MATCH_AT.getPropertyName(), 0L);
                            time2 = matchTime2;
                            break;
                        }
                    }
                }

                if (time1 > time2) {
                    return -1;
                } else if (time1 < time2) {
                    return 1;
                }

                return 0;
            }
        });
        return tmpResult;
    }
}
