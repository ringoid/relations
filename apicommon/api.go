package apimodel

import "fmt"

type InternalPrepareNewFacesRequest struct {
	UserId string `json:"userId"`
	Limit  int    `json:"limit"`
}

func (req InternalPrepareNewFacesRequest) String() string {
	return fmt.Sprintf("%#v", req)
}

type InternalPrepareNewFacesResponse struct {
	TargetUserIndexMap map[string]int `json:"targetUserIndexMap"`
}

func (req InternalPrepareNewFacesResponse) String() string {
	return fmt.Sprintf("%#v", req)
}

type InternalDeletePreparedEvent struct {
	UserId    string `json:"userId"`
	EventType string `json:"eventType"`
}

func (req InternalDeletePreparedEvent) String() string {
	return fmt.Sprintf("%#v", req)
}

func NewInternalDeletePreparedEvent(userId string) *InternalDeletePreparedEvent {
	return &InternalDeletePreparedEvent{
		UserId:    userId,
		EventType: "DELETE_PREPARED_NEW_FACES_EVENT",
	}
}

type InternalPrepareNewFaces struct {
	UserId       string `json:"userId"`
	TargetUserId string `json:"targetUserId"`
	Index        int    `json:"index"`
	EventType    string `json:"eventType"`
	AlreadySeen  bool   `json:"alreadySeen"`
}

func (req InternalPrepareNewFaces) String() string {
	return fmt.Sprintf("%#v", req)
}

func NewInternalPrepareNewFaces(userId, targetUserId string, index int, alreadySeen bool) *InternalPrepareNewFaces {
	return &InternalPrepareNewFaces{
		UserId:       userId,
		TargetUserId: targetUserId,
		Index:        index,
		AlreadySeen:  alreadySeen,
		EventType:    "PREPARE_NEW_FACES_EVENT",
	}
}
