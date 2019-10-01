package com.ringoid.events.push;

import com.graphaware.common.log.LoggerFactory;
import com.ringoid.Labels;
import com.ringoid.PhotoProperties;
import com.ringoid.Relationships;
import com.ringoid.ViewProperties;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.logging.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.PersonProperties.PUSH_WAS_SENT_AT;
import static com.ringoid.PersonProperties.USER_ID;

public class PushUtils {
    private final static Log log = LoggerFactory.getLogger(PushUtils.class);

    public static void pushWasSent(PushWasSentEvent event, GraphDatabaseService database) {
        Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), event.getUserId());
        if (Objects.isNull(sourceUser)) {
            return;
        }
        sourceUser.setProperty(PUSH_WAS_SENT_AT.getPropertyName(), event.getUnixTime());
    }

    public static String mostViewPhotoThumbnail(Node whoWillReceivePush, Node whoSentPush) {
        Iterable<Relationship> upls = whoSentPush.getRelationships(RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()), Direction.OUTGOING);
        List<PhotoWrapper> photos = new ArrayList<>();
        for (Relationship eachUpls : upls) {

            Node photo = eachUpls.getOtherNode(whoSentPush);
            if (photo.hasLabel(Label.label(PHOTO.getLabelName())) &&
                    !((Boolean) photo.getProperty(PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), false))) {

                String thumbnailPhotoLink = "n/a";
                Iterable<Relationship> resized = photo.getRelationships(RelationshipType.withName(Relationships.RESIZED.name()), Direction.OUTGOING);
                for (Relationship eachResized : resized) {
                    Node other = eachResized.getOtherNode(photo);
                    if (other.hasLabel(Label.label(Labels.RESIZED_PHOTO.getLabelName()))) {
                        String res = (String) other.getProperty(PhotoProperties.RESOLUTION.getPropertyName(), "n/a");
                        if (res.endsWith("_thumbnail")) {
                            thumbnailPhotoLink = (String) other.getProperty(PhotoProperties.PHOTO_LINK.getPropertyName(), "n/a");
                            break;
                        }
                    }
                }

                if (!Objects.equals("n/a", thumbnailPhotoLink)) {
                    Long viewTime = 0L;
                    for (Relationship eachView : photo.getRelationships(RelationshipType.withName(Relationships.VIEW.name()), Direction.INCOMING)) {
                        Node person = eachView.getOtherNode(photo);
                        if (person.hasLabel(Label.label(PERSON.getLabelName())) &&
                                person.getId() == whoWillReceivePush.getId()) {
                            viewTime = (Long) eachView.getProperty(ViewProperties.VIEW_TIME.getPropertyName(), 0L);
                            break;
                        }
                    }

                    PhotoWrapper pw = new PhotoWrapper();
                    pw.thumbnail = thumbnailPhotoLink;
                    pw.viewTime = viewTime;
                    photos.add(pw);
                }

            }//end if it's a photo
        }//end upload rels

        Collections.sort(photos, new Comparator<PhotoWrapper>() {
            @Override
            public int compare(PhotoWrapper o1, PhotoWrapper o2) {
                return (-1) * o1.viewTime.compareTo(o2.viewTime);
            }
        });

        if (photos.isEmpty()) {
            return "n/a";
        }

        return photos.get(0).thumbnail;
    }

    static class PhotoWrapper {
        String thumbnail;
        Long viewTime;
    }
}
