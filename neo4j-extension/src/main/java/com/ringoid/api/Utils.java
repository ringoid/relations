package com.ringoid.api;

import com.ringoid.MessageProperties;
import com.ringoid.PhotoProperties;
import com.ringoid.Relationships;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.ringoid.Labels.HIDDEN;
import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.PersonProperties.LAST_ONLINE_TIME;
import static com.ringoid.PersonProperties.LIKE_COUNTER;

public class Utils {
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

    public static List<Node> sortMessagesProfiles(Node sourceUser, List<Node> tmpResult) {
        Collections.sort(tmpResult, new Comparator<Node>() {
            @Override
            public int compare(Node node1, Node node2) {
                long lastMessageAt1 = 0;
                Iterable<Relationship> messages1 = node1.getRelationships(Direction.BOTH, RelationshipType.withName(Relationships.MESSAGE.name()));
                for (Relationship each : messages1) {
                    Node other = each.getOtherNode(node1);
                    if (other.hasLabel(Label.label(PERSON.getLabelName())) && other.getId() == sourceUser.getId()) {
                        lastMessageAt1 = (Long) other.getProperty(MessageProperties.MSG_AT.getPropertyName(), 0L);
                        break;
                    }
                }

                long lastMessageAt2 = 0;
                Iterable<Relationship> messages2 = node2.getRelationships(Direction.BOTH, RelationshipType.withName(Relationships.MESSAGE.name()));
                for (Relationship each : messages2) {
                    Node other = each.getOtherNode(node2);
                    if (other.hasLabel(Label.label(PERSON.getLabelName())) && other.getId() == sourceUser.getId()) {
                        lastMessageAt2 = (Long) other.getProperty(MessageProperties.MSG_AT.getPropertyName(), 0L);
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

    public static List<Node> sortLmmProfiles(Node sourceUser, List<Node> tmpResult, boolean isItMatchFeed) {
        Collections.sort(tmpResult, new Comparator<Node>() {
            @Override
            public int compare(Node node1, Node node2) {
                if (isItMatchFeed) {
                    //special sorting step
                    int likesThatSourceMakes1 = 0;
                    Iterable<Relationship> uploads1 = node1.getRelationships(Direction.OUTGOING, RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()));
                    for (Relationship each : uploads1) {
                        Node photo = each.getOtherNode(node1);
                        if (photo.hasLabel(Label.label(PHOTO.getLabelName()))) {
                            Iterable<Relationship> likes = photo.getRelationships(Direction.INCOMING, RelationshipType.withName(Relationships.LIKE.name()));
                            for (Relationship rel : likes) {
                                Node other = rel.getOtherNode(photo);
                                if (other.hasLabel(Label.label(PERSON.getLabelName())) && other.getId() == sourceUser.getId()) {
                                    likesThatSourceMakes1++;
                                }
                            }
                        }
                    }

                    int likesThatSourceMakes2 = 0;
                    Iterable<Relationship> uploads2 = node2.getRelationships(Direction.OUTGOING, RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()));
                    for (Relationship each : uploads2) {
                        Node photo = each.getOtherNode(node2);
                        if (photo.hasLabel(Label.label(PHOTO.getLabelName()))) {
                            Iterable<Relationship> likes = photo.getRelationships(Direction.INCOMING, RelationshipType.withName(Relationships.LIKE.name()));
                            for (Relationship rel : likes) {
                                Node other = rel.getOtherNode(photo);
                                if (other.hasLabel(Label.label(PERSON.getLabelName())) && other.getId() == sourceUser.getId()) {
                                    likesThatSourceMakes2++;
                                }
                            }
                        }
                    }

                    if (likesThatSourceMakes1 > likesThatSourceMakes2) {
                        return -1;
                    } else if (likesThatSourceMakes1 < likesThatSourceMakes2) {
                        return 1;
                    }
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

                //how many back likes
                Set<Long> sourcePhotoIds = new HashSet<>();
                Iterable<Relationship> uploads = sourceUser.getRelationships(Direction.OUTGOING, RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()));
                for (Relationship rel : uploads) {
                    Node other = rel.getOtherNode(sourceUser);
                    if (other.hasLabel(Label.label(PHOTO.getLabelName()))) {
                        sourcePhotoIds.add(other.getId());
                    }
                }

                int howManyBackLikes1 = 0;
                Iterable<Relationship> likes1 = node1.getRelationships(Direction.OUTGOING, RelationshipType.withName(Relationships.LIKE.name()));
                for (Relationship each : likes1) {
                    Node other = each.getOtherNode(node1);
                    if (other.hasLabel(Label.label(PHOTO.getLabelName())) && sourcePhotoIds.contains(other.getId())) {
                        howManyBackLikes1++;
                    }
                }

                int howManyBackLikes2 = 0;
                Iterable<Relationship> likes2 = node2.getRelationships(Direction.OUTGOING, RelationshipType.withName(Relationships.LIKE.name()));
                for (Relationship each : likes2) {
                    Node other = each.getOtherNode(node2);
                    if (other.hasLabel(Label.label(PHOTO.getLabelName())) && sourcePhotoIds.contains(other.getId())) {
                        howManyBackLikes2++;
                    }
                }

                if (howManyBackLikes1 > howManyBackLikes2) {
                    return -1;
                } else if (howManyBackLikes1 < howManyBackLikes2) {
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

    public static List<String> sortLmmPhotos(Node sourceUser, Node each) {
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
                    if (other.hasLabel(Label.label(PERSON.getLabelName())) && other.getId() == sourceUser.getId()) {
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
                    if (other.hasLabel(Label.label(PERSON.getLabelName())) && other.getId() == sourceUser.getId()) {
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

}
