package com.ringoid.api;

import com.ringoid.Labels;
import com.ringoid.MessageProperties;
import com.ringoid.MessageRelationshipProperties;
import com.ringoid.PersonProperties;
import com.ringoid.PhotoProperties;
import com.ringoid.Relationships;
import com.ringoid.common.UtilsInternaly;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.spatial.Point;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;

import static com.ringoid.Labels.HIDDEN;
import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.PersonProperties.ABOUT;
import static com.ringoid.PersonProperties.CHILDREN;
import static com.ringoid.PersonProperties.COMPANY;
import static com.ringoid.PersonProperties.EDUCATION_TEXT;
import static com.ringoid.PersonProperties.EDU_LEVEL;
import static com.ringoid.PersonProperties.HAIR_COLOR;
import static com.ringoid.PersonProperties.HEIGHT;
import static com.ringoid.PersonProperties.INCOME;
import static com.ringoid.PersonProperties.INSTAGRAM;
import static com.ringoid.PersonProperties.JOB_TITLE;
import static com.ringoid.PersonProperties.LAST_ONLINE_TIME;
import static com.ringoid.PersonProperties.LIKE_COUNTER;
import static com.ringoid.PersonProperties.LOCATION;
import static com.ringoid.PersonProperties.NAME;
import static com.ringoid.PersonProperties.PROPERTY;
import static com.ringoid.PersonProperties.SETTINGS_LOCALE;
import static com.ringoid.PersonProperties.SEX;
import static com.ringoid.PersonProperties.STATUS_TEXT;
import static com.ringoid.PersonProperties.TIKTOK;
import static com.ringoid.PersonProperties.TRANSPORT;
import static com.ringoid.PersonProperties.WHERE_I_FROM;
import static com.ringoid.PersonProperties.WHERE_I_LIVE;
import static com.ringoid.PersonProperties.YEAR;
import static com.ringoid.PhotoProperties.ONLY_OWNER_CAN_SEE;

public class Utils {

    public static Profile enrichProfile(Node node, Node sourceNode, Profile prof) {
        if (Objects.isNull(node.getProperty(LOCATION.getPropertyName(), null)) ||
                Objects.isNull(sourceNode.getProperty(LOCATION.getPropertyName(), null))) {
            prof.setLocationExist(false);
        } else {
            prof.setLocationExist(true);
            Point point = (Point) node.getProperty(LOCATION.getPropertyName());
            List<Double> coords = point.getCoordinate().getCoordinate();
            prof.setLat(coords.get(1));
            prof.setLon(coords.get(0));
            point = (Point) sourceNode.getProperty(LOCATION.getPropertyName());
            coords = point.getCoordinate().getCoordinate();
            prof.setSlon(coords.get(0));
            prof.setSlat(coords.get(1));
        }

        long lastOnline = (Long) node.getProperty(LAST_ONLINE_TIME.getPropertyName(), 0L);
        prof.setLastOnlineTime(lastOnline);

        String sourceLocale = (String) sourceNode.getProperty(SETTINGS_LOCALE.getPropertyName(), "en");
        prof.setSlocale(sourceLocale);

        int yearOfBirth = (Integer) node.getProperty(YEAR.getPropertyName());
        int age = LocalDate.now().getYear() - yearOfBirth;
        prof.setAge(age);

        String sex = (String) node.getProperty(SEX.getPropertyName(), "unknown");
        prof.setSex(sex);

        int property = (Integer) node.getProperty(PROPERTY.getPropertyName(), 0);
        prof.setProperty(property);

        int transport = (Integer) node.getProperty(TRANSPORT.getPropertyName(), 0);
        prof.setTransport(transport);

        int income = (Integer) node.getProperty(INCOME.getPropertyName(), 0);
        prof.setIncome(income);

        int height = (Integer) node.getProperty(HEIGHT.getPropertyName(), 0);
        prof.setHeight(height);

        int eduLevel = (Integer) node.getProperty(EDU_LEVEL.getPropertyName(), 0);
        prof.setEducationLevel(eduLevel);

        int hairColor = (Integer) node.getProperty(HAIR_COLOR.getPropertyName(), 0);
        prof.setHairColor(hairColor);

        int children = (Integer) node.getProperty(CHILDREN.getPropertyName(), 0);
        prof.setChildren(children);

        String name = (String) node.getProperty(NAME.getPropertyName(), "unknown");
        prof.setName(name);

        String jobTitle = (String) node.getProperty(JOB_TITLE.getPropertyName(), "unknown");
        prof.setJobTitle(jobTitle);

        String company = (String) node.getProperty(COMPANY.getPropertyName(), "unknown");
        prof.setCompany(company);

        String educationText = (String) node.getProperty(EDUCATION_TEXT.getPropertyName(), "unknown");
        prof.setEducation(educationText);

        String about = (String) node.getProperty(ABOUT.getPropertyName(), "unknown");
        prof.setAbout(about);

        String instagram = (String) node.getProperty(INSTAGRAM.getPropertyName(), "unknown");
        prof.setInstagram(instagram);

        String tiktok = (String) node.getProperty(TIKTOK.getPropertyName(), "unknown");
        prof.setTikTok(tiktok);

        String whereILive = (String) node.getProperty(WHERE_I_LIVE.getPropertyName(), "unknown");
        prof.setWhereLive(whereILive);

        String whereIFrom = (String) node.getProperty(WHERE_I_FROM.getPropertyName(), "unknown");
        prof.setWhereFrom(whereIFrom);

        String statusText = (String) node.getProperty(STATUS_TEXT.getPropertyName(), "unknown");
        prof.setStatusText(statusText);

        Long allLikeCounter = (Long) node.getProperty(LIKE_COUNTER.getPropertyName(), 0L);
        prof.setTotalLikes(allLikeCounter);

        return prof;
    }

    public static List<Photo> resizedAndVisibleToEveryOnePhotos(List<String> origins, String resolution, GraphDatabaseService database) {
        List<Photo> result = new ArrayList<>();
        for (String eachOrigin : origins) {
            Photo photo = new Photo();
            photo.setOriginPhotoId(eachOrigin);
            photo.setResolution(resolution);

            Node originNode = database.findNode(Label.label(Labels.PHOTO.getLabelName()), PhotoProperties.PHOTO_ID.getPropertyName(), eachOrigin);
            String resizedPhotoId = null;
            String thumbnailPhotoLink = null;
            String link = null;
            if (Objects.nonNull(originNode) &&
                    !((Boolean) originNode.getProperty(PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), false))) {

                Iterable<Relationship> resized = originNode.getRelationships(RelationshipType.withName(Relationships.RESIZED.name()), Direction.OUTGOING);
                for (Relationship eachResized : resized) {
                    Node other = eachResized.getOtherNode(originNode);
                    if (other.hasLabel(Label.label(Labels.RESIZED_PHOTO.getLabelName()))) {
                        String res = (String) other.getProperty(PhotoProperties.RESOLUTION.getPropertyName(), "n/a");
                        if (Objects.equals(resolution, res)) {
                            String rPhotoId = (String) other.getProperty(PhotoProperties.PHOTO_ID.getPropertyName(), "n/a");
                            String rLink = (String) other.getProperty(PhotoProperties.PHOTO_LINK.getPropertyName(), "n/a");
                            if (!Objects.equals("n/a", rPhotoId) && !Objects.equals("n/a", rLink)) {
                                resizedPhotoId = rPhotoId;
                                link = rLink;
                            }
                        }
                        if (res.endsWith("_thumbnail")) {
                            String rLink = (String) other.getProperty(PhotoProperties.PHOTO_LINK.getPropertyName(), "n/a");
                            if (!Objects.equals("n/a", rLink)) {
                                thumbnailPhotoLink = rLink;
                            }
                        }
                    }
                }
                if (Objects.nonNull(resizedPhotoId) && Objects.nonNull(link)) {
                    photo.setResizedPhotoId(resizedPhotoId);
                    photo.setLink(link);
                    photo.setThumbnailLink(thumbnailPhotoLink);
                    result.add(photo);
                }
            }
        }
        return result;
    }

    public static List<Node> filterUsers(Node sourceUser, List<Node> list, RelationshipType type, boolean onlyNew) {
        List<Node> result = new ArrayList<>();
        for (Node each : list) {
            boolean hasSeenRel = false;
            Iterable<Relationship> seenInLikesIt = each.getRelationships(Direction.INCOMING, type);
            for (Relationship rel : seenInLikesIt) {
                Node other = rel.getOtherNode(each);
                if (other.hasLabel(Label.label(PERSON.getLabelName())) &&
                        other.getId() == sourceUser.getId()) {
                    hasSeenRel = true;
                    break;
                }
            }

            if (!hasSeenRel && onlyNew) {
                result.add(each);
            } else if (hasSeenRel && !onlyNew) {
                result.add(each);
            }
        }
        return result;
    }

    public static List<Node> whoHasLikeMatchOrMessageWithMe(Node sourceUser, RelationshipType type, Direction direction) {
        List<Node> result = new ArrayList<>();
        Iterable<Relationship> whoLikedMeIt = sourceUser.getRelationships(direction, type);
        for (Relationship eachRel : whoLikedMeIt) {
            Node other = eachRel.getOtherNode(sourceUser);
            if (other.hasLabel(Label.label(HIDDEN.getLabelName()))) {
                continue;
            }
            if (other.hasLabel(Label.label(PERSON.getLabelName())) &&
                    other.getId() != sourceUser.getId() ||
                    other.hasRelationship(Direction.OUTGOING, RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()))) {

                result.add(other);
            }
        }
        return result;
    }

    public static Profile markProfileWithLastMessageAt(Node sourceUser, Node profileNode, Profile profile) {
        long lastMessageAt = 0L;
        Iterable<Relationship> msgRels = profileNode.getRelationships(Direction.BOTH, RelationshipType.withName(Relationships.MESSAGE.name()));
        for (Relationship eachMsgRel : msgRels) {
            Node other = eachMsgRel.getOtherNode(profileNode);
            if (other.hasLabel(Label.label(PERSON.getLabelName())) && other.getId() == sourceUser.getId()) {
                lastMessageAt = (Long) eachMsgRel.getProperty(MessageRelationshipProperties.MSG_AT.getPropertyName(), 0L);
                break;
            }
        }
        profile.setLastMessageAt(lastMessageAt);
        return profile;
    }

    public static List<Node> commonSortProfilesSeenPart(Node sourceUser, List<Node> sourceList) {
        String sex = (String) sourceUser.getProperty(SEX.getPropertyName(), "male");
        String targetSex = "female";
        if (Objects.equals("female", sex)) {
            targetSex = "male";
        }

        int sourceYearOfBirth = (Integer) sourceUser.getProperty(YEAR.getPropertyName(), 0);

        List<NodeWrapper> wrapperList = new ArrayList<>(sourceList.size());
        for (Node each : sourceList) {

            int targetYearMarker = 0;
            int yearOfBirth = (Integer) each.getProperty(YEAR.getPropertyName(), 0);
            if (Objects.equals("male", targetSex)) {
                if (yearOfBirth >= sourceYearOfBirth - 5 && yearOfBirth <= sourceYearOfBirth) {
                    targetYearMarker++;
                }
            } else if (Objects.equals("female", targetSex)) {
                if (yearOfBirth >= sourceYearOfBirth && yearOfBirth <= sourceYearOfBirth + 7) {
                    targetYearMarker++;
                }
            }

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
            NodeWrapper nodeWrapper = new NodeWrapper();
            nodeWrapper.node = each;
            nodeWrapper.allPhotoCount = photoCounter;
            nodeWrapper.likes = likeCounter;
            nodeWrapper.unseenPhotos = photoCounter - viewedFromSource;
            nodeWrapper.onlineTime = lastOnlineTime;
            nodeWrapper.targetYear = targetYearMarker;

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
        Collections.sort(wrapperList, new Comparator<NodeWrapper>() {
            @Override
            public int compare(NodeWrapper node1, NodeWrapper node2) {

                if (node1.targetYear > node2.targetYear) {
                    return -1;
                } else if (node1.targetYear < node2.targetYear) {
                    return 1;
                }

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
        for (NodeWrapper eachWrapper : wrapperList) {
            result.add(eachWrapper.node);
        }
        return result;
    }

    public static List<Profile> sortLMHISProfilesForInbox(List<Profile> profiles) {
        Collections.sort(profiles, new Comparator<Profile>() {
            @Override
            public int compare(Profile profile1, Profile profile2) {
                long lastMessageToMeAt1 = 0L;
                for (Message msg : profile1.getMessages()) {
                    if (!msg.isWasYouSender() && msg.getWasSentAt() > lastMessageToMeAt1) {
                        lastMessageToMeAt1 = msg.getWasSentAt();
                    }
                }

                long lastMessageToMeAt2 = 0L;
                for (Message msg : profile2.getMessages()) {
                    if (!msg.isWasYouSender() && msg.getWasSentAt() > lastMessageToMeAt2) {
                        lastMessageToMeAt2 = msg.getWasSentAt();
                    }
                }

                if (lastMessageToMeAt1 > lastMessageToMeAt2) {
                    //put profile1 before profile2
                    return -1;
                } else if (lastMessageToMeAt1 < lastMessageToMeAt2) {
                    return 1;
                }

                //if we don't have time in a messages then use usual sorting order
                if (profile1.getLastMessageAt() > profile2.getLastMessageAt()) {
                    return -1;
                } else if (profile1.getLastMessageAt() < profile2.getLastMessageAt()) {
                    return 1;
                }
                return 0;
            }
        });
        return profiles;
    }

    public static List<Profile> sortLMHISProfilesForSent(List<Profile> profiles) {
        Collections.sort(profiles, new Comparator<Profile>() {
            @Override
            public int compare(Profile profile1, Profile profile2) {
                long lastMessageToMeAt1 = 0L;
                for (Message msg : profile1.getMessages()) {
                    if (msg.isWasYouSender() && msg.getWasSentAt() > lastMessageToMeAt1) {
                        lastMessageToMeAt1 = msg.getWasSentAt();
                    }
                }

                long lastMessageToMeAt2 = 0L;
                for (Message msg : profile2.getMessages()) {
                    if (msg.isWasYouSender() && msg.getWasSentAt() > lastMessageToMeAt2) {
                        lastMessageToMeAt2 = msg.getWasSentAt();
                    }
                }

                if (lastMessageToMeAt1 > lastMessageToMeAt2) {
                    //put profile1 before profile2
                    return -1;
                } else if (lastMessageToMeAt1 < lastMessageToMeAt2) {
                    return 1;
                }

                //if we don't have time in a messages then use usual sorting order
                if (profile1.getLastMessageAt() > profile2.getLastMessageAt()) {
                    return -1;
                } else if (profile1.getLastMessageAt() < profile2.getLastMessageAt()) {
                    return 1;
                }
                return 0;
            }
        });
        return profiles;
    }

    public static List<Node> sortProfilesByLastMessageAt(Node sourceUser, List<Node> tmpResult) {
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

                long lastMessageAt2 = 0;
                Iterable<Relationship> messages2 = node2.getRelationships(Direction.BOTH, RelationshipType.withName(Relationships.MESSAGE.name()));
                for (Relationship each : messages2) {
                    Node other = each.getOtherNode(node2);
                    if (other.hasLabel(Label.label(PERSON.getLabelName())) && other.getId() == sourceUser.getId()) {
                        lastMessageAt2 = (Long) each.getProperty(MessageRelationshipProperties.MSG_AT.getPropertyName(), 0L);
                        break;
                    }
                }

                if (lastMessageAt1 > lastMessageAt2) {
                    return -1;
                } else if (lastMessageAt1 < lastMessageAt2) {
                    return 1;
                }

                return 0;
            }
        });
        return tmpResult;
    }

    public static List<Node> sortNewFacesUnseenPartProfiles(List<Node> tmpResult,
                                                            String targetSex,
                                                            int sourceYearOfBirth) {
        if (tmpResult.isEmpty()) {
            return tmpResult;
        }

        Collections.sort(tmpResult, new Comparator<Node>() {
            @Override
            public int compare(Node node1, Node node2) {
                int yearIn1 = 0;
                int yearIn2 = 0;
                int yearOfBirth1 = (Integer) node1.getProperty(YEAR.getPropertyName(), 0);
                int yearOfBirth2 = (Integer) node2.getProperty(YEAR.getPropertyName(), 0);
                if (Objects.equals("male", targetSex)) {
                    if (yearOfBirth1 >= sourceYearOfBirth - 5 && yearOfBirth1 <= sourceYearOfBirth) {
                        yearIn1++;
                    }
                    if (yearOfBirth2 >= sourceYearOfBirth - 5 && yearOfBirth2 <= sourceYearOfBirth) {
                        yearIn2++;
                    }
                } else if (Objects.equals("female", targetSex)) {
                    if (yearOfBirth1 >= sourceYearOfBirth && yearOfBirth1 <= sourceYearOfBirth + 7) {
                        yearIn1++;
                    }
                    if (yearOfBirth2 >= sourceYearOfBirth && yearOfBirth2 <= sourceYearOfBirth + 7) {
                        yearIn2++;
                    }
                }

                if (yearIn1 > yearIn2) {
                    return -1;
                } else if (yearIn1 < yearIn2) {
                    return 1;
                }

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

    public static List<Node> sortLMHISUnseenPartProfiles(Node sourceUser, List<Node> tmpResult) {
        Collections.sort(tmpResult, new Comparator<Node>() {
            @Override
            public int compare(Node node1, Node node2) {
                //comment this coz in new product's model - one like -> one profile
//                if (isItMatchFeed) {
//                    //special sorting step
//                    int likesThatSourceMakes1 = 0;
//                    Iterable<Relationship> uploads1 = node1.getRelationships(Direction.OUTGOING, RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()));
//                    for (Relationship each : uploads1) {
//                        Node photo = each.getOtherNode(node1);
//                        if (photo.hasLabel(Label.label(PHOTO.getLabelName())) &&
//                                !((Boolean) photo.getProperty(PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), false))) {
//
//                            Iterable<Relationship> likes = photo.getRelationships(Direction.INCOMING, RelationshipType.withName(Relationships.LIKE.name()));
//                            for (Relationship rel : likes) {
//                                Node other = rel.getOtherNode(photo);
//                                if (other.hasLabel(Label.label(PERSON.getLabelName())) && other.getId() == sourceUser.getId()) {
//                                    likesThatSourceMakes1++;
//                                }
//                            }
//                        }
//                    }
//
//                    int likesThatSourceMakes2 = 0;
//                    Iterable<Relationship> uploads2 = node2.getRelationships(Direction.OUTGOING, RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()));
//                    for (Relationship each : uploads2) {
//                        Node photo = each.getOtherNode(node2);
//                        if (photo.hasLabel(Label.label(PHOTO.getLabelName())) &&
//                                !((Boolean) photo.getProperty(PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), false))) {
//                            Iterable<Relationship> likes = photo.getRelationships(Direction.INCOMING, RelationshipType.withName(Relationships.LIKE.name()));
//                            for (Relationship rel : likes) {
//                                Node other = rel.getOtherNode(photo);
//                                if (other.hasLabel(Label.label(PERSON.getLabelName())) && other.getId() == sourceUser.getId()) {
//                                    likesThatSourceMakes2++;
//                                }
//                            }
//                        }
//                    }
//
//                    if (likesThatSourceMakes1 > likesThatSourceMakes2) {
//                        return -1;
//                    } else if (likesThatSourceMakes1 < likesThatSourceMakes2) {
//                        return 1;
//                    }
//                }

                String sex = (String) sourceUser.getProperty(SEX.getPropertyName(), "male");
                String targetSex = "female";
                if (Objects.equals("female", sex)) {
                    targetSex = "male";
                }

                int sourceYearOfBirth = (Integer) sourceUser.getProperty(YEAR.getPropertyName(), 0);

                int yearIn1 = 0;
                int yearIn2 = 0;
                int yearOfBirth1 = (Integer) node1.getProperty(YEAR.getPropertyName(), 0);
                int yearOfBirth2 = (Integer) node2.getProperty(YEAR.getPropertyName(), 0);
                if (Objects.equals("male", targetSex)) {
                    if (yearOfBirth1 >= sourceYearOfBirth - 5 && yearOfBirth1 <= sourceYearOfBirth) {
                        yearIn1++;
                    }
                    if (yearOfBirth2 >= sourceYearOfBirth - 5 && yearOfBirth2 <= sourceYearOfBirth) {
                        yearIn2++;
                    }
                } else if (Objects.equals("female", targetSex)) {
                    if (yearOfBirth1 >= sourceYearOfBirth && yearOfBirth1 <= sourceYearOfBirth + 7) {
                        yearIn1++;
                    }
                    if (yearOfBirth2 >= sourceYearOfBirth && yearOfBirth2 <= sourceYearOfBirth + 7) {
                        yearIn2++;
                    }
                }

                if (yearIn1 > yearIn2) {
                    return -1;
                } else if (yearIn1 < yearIn2) {
                    return 1;
                }

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

                //comment this coz in new product's model - one like -> one profile
                //how many back likes
//                Set<Long> sourcePhotoIds = new HashSet<>();
//                Iterable<Relationship> uploads = sourceUser.getRelationships(Direction.OUTGOING, RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()));
//                for (Relationship rel : uploads) {
//                    Node other = rel.getOtherNode(sourceUser);
//                    if (other.hasLabel(Label.label(PHOTO.getLabelName()))) {
//                        sourcePhotoIds.add(other.getId());
//                    }
//                }

//                int howManyBackLikes1 = 0;
//                Iterable<Relationship> likes1 = node1.getRelationships(Direction.OUTGOING, RelationshipType.withName(Relationships.LIKE.name()));
//                for (Relationship each : likes1) {
//                    Node other = each.getOtherNode(node1);
//                    if (other.hasLabel(Label.label(PHOTO.getLabelName())) && sourcePhotoIds.contains(other.getId())) {
//                        howManyBackLikes1++;
//                    }
//                }
//
//                int howManyBackLikes2 = 0;
//                Iterable<Relationship> likes2 = node2.getRelationships(Direction.OUTGOING, RelationshipType.withName(Relationships.LIKE.name()));
//                for (Relationship each : likes2) {
//                    Node other = each.getOtherNode(node2);
//                    if (other.hasLabel(Label.label(PHOTO.getLabelName())) && sourcePhotoIds.contains(other.getId())) {
//                        howManyBackLikes2++;
//                    }
//                }
//
//                if (howManyBackLikes1 > howManyBackLikes2) {
//                    return -1;
//                } else if (howManyBackLikes1 < howManyBackLikes2) {
//                    return 1;
//                }

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

    public static List<String> sortLMHISPhotos(Node sourceUser, Node each) {
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

                Iterable<Relationship> whoSeeItRel1 = node1.getRelationships(Direction.INCOMING);
                int seenCounter1 = 0;
                int wasLikedCounter1 = 0;
                long photoUploadedAt1 = 0L;
                for (Relationship each : whoSeeItRel1) {
                    Node other = each.getOtherNode(node1);
                    if (Objects.equals(each.getType().name(), Relationships.VIEW.name()) &&
                            other.hasLabel(Label.label(PERSON.getLabelName())) && other.getId() == sourceUser.getId()) {
                        seenCounter1++;
                    }
                    if (Objects.equals(each.getType().name(), Relationships.LIKE.name())) {
                        wasLikedCounter1++;
                    }
                    if (Objects.equals(each.getType().name(), Relationships.UPLOAD_PHOTO.name())) {
                        photoUploadedAt1 = (Long) each.getProperty(PhotoProperties.PHOTO_UPLOADED_AT.getPropertyName(), 0L);
                    }
                }

                Iterable<Relationship> whoSeeItRel2 = node2.getRelationships(Direction.INCOMING);
                int seenCounter2 = 0;
                int wasLikedCounter2 = 0;
                long photoUploadedAt2 = 0L;
                for (Relationship each : whoSeeItRel2) {
                    Node other = each.getOtherNode(node2);
                    if (Objects.equals(each.getType().name(), Relationships.VIEW.name()) &&
                            other.hasLabel(Label.label(PERSON.getLabelName())) && other.getId() == sourceUser.getId()) {
                        seenCounter2++;
                    }
                    if (Objects.equals(each.getType().name(), Relationships.LIKE.name())) {
                        wasLikedCounter2++;
                    }
                    if (Objects.equals(each.getType().name(), Relationships.UPLOAD_PHOTO.name())) {
                        photoUploadedAt2 = (Long) each.getProperty(PhotoProperties.PHOTO_UPLOADED_AT.getPropertyName(), 0L);
                    }
                }

                if (seenCounter1 > seenCounter2) {
                    return 1;
                } else if (seenCounter1 < seenCounter2) {
                    return -1;
                }

                if (wasLikedCounter1 > wasLikedCounter2) {
                    return -1;
                } else if (wasLikedCounter1 < wasLikedCounter2) {
                    return 1;
                }

                if (photoUploadedAt1 > photoUploadedAt2) {
                    return -1;
                } else if (photoUploadedAt1 < photoUploadedAt2) {
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

    public static List<String> sortNewFacesUnseenPhotos(Node each) {
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

    public static int countConversationWithMessagesMoreThan(Node sourceNode, int limit) {
        int result = 0;
        Iterable<Relationship> takePart = sourceNode.getRelationships(
                RelationshipType.withName(Relationships.TAKE_PART_IN_CONVERSATION.name()),
                Direction.OUTGOING);
        for (Relationship out : takePart) {
            Node conversationNode = out.getOtherNode(sourceNode);
            if (conversationNode.hasLabel(Label.label(Labels.CONVERSATION.getLabelName()))) {
                List<Node> fullConversation = new ArrayList<>();
                fullConversation = UtilsInternaly.getFullConversation(conversationNode, fullConversation);
                if (fullConversation.size() >= limit) {
                    result++;
                }
            }
        }
        return result;
    }

    public static int countMatchesAndChats(Node sourceNode) {
        int result = 0;
        Iterable<Relationship> matches = sourceNode.getRelationships(RelationshipType.withName(Relationships.MATCH.name()));
        Iterable<Relationship> chats = sourceNode.getRelationships(
                RelationshipType.withName(Relationships.TAKE_PART_IN_CONVERSATION.name()),
                Direction.OUTGOING);
        for (Relationship each : matches) {
            result++;
        }
        for (Relationship each : chats) {
            result++;
        }
        return result;
    }

    public static int countPhotos(Node sourceNode) {
        int result = 0;
        for (Relationship each : sourceNode.getRelationships(Direction.OUTGOING, RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()))) {
            Node eachPhoto = each.getOtherNode(sourceNode);
            if (Objects.nonNull(eachPhoto) &&
                    !((Boolean) eachPhoto.getProperty(PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), false))) {
                result++;
            }
        }
        return result;
    }

    public static List<Message> messages(Node sourceNode, Node withHim, int maxLastMessages) {
        List<Message> result = new ArrayList<>();

        Iterable<Relationship> takePart = sourceNode.getRelationships(
                RelationshipType.withName(Relationships.TAKE_PART_IN_CONVERSATION.name()),
                Direction.OUTGOING);
        Node targetConversation = null;
        for (Relationship out : takePart) {
            Node conversationNode = out.getOtherNode(sourceNode);
            if (conversationNode.hasLabel(Label.label(Labels.CONVERSATION.getLabelName()))) {
                Iterable<Relationship> takePartIn = conversationNode.getRelationships(
                        RelationshipType.withName(Relationships.TAKE_PART_IN_CONVERSATION.name()),
                        Direction.INCOMING);
                for (Relationship in : takePartIn) {
                    Node otherProf = in.getOtherNode(conversationNode);
                    if (otherProf.hasLabel(Label.label(Labels.PERSON.getLabelName())) && otherProf.getId() == withHim.getId()) {
                        targetConversation = conversationNode;
                        break;
                    }
                }
                if (Objects.nonNull(targetConversation)) {
                    break;
                }
            }
        }

        if (Objects.isNull(targetConversation)) {
            return result;
        }

        List<Node> fullConversation = new ArrayList<>();
        fullConversation = UtilsInternaly.getFullConversation(targetConversation, fullConversation);

        if (maxLastMessages > 0 && fullConversation.size() > maxLastMessages) {
            fullConversation = fullConversation.subList(fullConversation.size() - maxLastMessages, fullConversation.size());
        }

        for (Node each : fullConversation) {
            String originSenderId = (String) sourceNode.getProperty(PersonProperties.USER_ID.getPropertyName());
            String msgSourceUser = (String) each.getProperty(MessageProperties.MSG_SOURCE_USER_ID.getPropertyName());
            boolean wasYouSender = Objects.equals(originSenderId, msgSourceUser);
            String text = (String) each.getProperty(MessageProperties.MSG_TEXT.getPropertyName());
            long sentAt = (Long) each.getProperty(MessageProperties.MSG_AT.getPropertyName(), 0L);
            String msgId = (String) each.getProperty(MessageProperties.MSG_ID.getPropertyName(), "");
            String clientMsgId = (String) each.getProperty(MessageProperties.CLIENT_MSG_ID.getPropertyName(), "");
            Message msg = new Message();
            msg.setMsgId(msgId);
            msg.setClientMsgId(clientMsgId);
            msg.setWasYouSender(wasYouSender);
            msg.setText(text);
            msg.setMsgAt(sentAt);
            msg.setWasSentAt(sentAt);

            boolean msgTargetUserRead = (Boolean) each.getProperty(MessageProperties.MSG_TARGET_USER_READ.getPropertyName(), false);

            msg.setHaveBeenRead(msgTargetUserRead);

            result.add(msg);
        }

        return result;
    }

    static class NodeWrapper {
        Node node;
        int dayNum;
        int unseenPhotos;
        int allPhotoCount;
        int likes;
        long onlineTime;
        int targetYear;
        DistanceWrapper distanceWrapper;
    }
}
