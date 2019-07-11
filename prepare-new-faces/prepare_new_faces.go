package main

import (
	"../apicommon"
	"context"
	"github.com/aws/aws-lambda-go/lambdacontext"
	basicLambda "github.com/aws/aws-lambda-go/lambda"
	"github.com/ringoid/commons"
	"net/http"
	"io/ioutil"
	"fmt"
	"encoding/json"
	"bytes"
	"errors"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/awserr"
	"strings"
)

var tmpNeo4jHosts []string

func init() {
	apimodel.InitLambdaVars("relationships-prepare-new-faces")
}

func handler(ctx context.Context, request commons.InternalPrepareNewFacesReq) (error) {
	lc, _ := lambdacontext.FromContext(ctx)

	apimodel.Anlogger.Debugf(lc, "prepare_new_faces.go : start handle prepare new faces request %v for userId [%s]", request, request.UserId)

	retry := true
	canStart := false
	for retry {
		var ok bool
		var errStr string
		canStart, ok, retry, errStr = canWeStartPrepareProcess(request.UserId, lc)
		if !retry && !ok {
			return errors.New(errStr)
		}
	}

	if !canStart {
		return nil
	}

	prepared, err := fetchRecommended(request.UserId, lc)
	if err != nil {
		return err
	}

	deleteEvent := apimodel.NewInternalDeletePreparedEvent(request.UserId)
	ok, errStr := commons.SendCommonEvent(deleteEvent, request.UserId, apimodel.CommonStreamName, "", apimodel.AwsKinesisStreamClient, apimodel.Anlogger, lc)
	if !ok {
		return errors.New(errStr)
	}
	for key, value := range prepared.TargetUserIndexMap {
		recoId := key
		alreadySeen := false
		if strings.HasSuffix(recoId, "_ALREADY_SEEN") {
			recoId = strings.Replace(recoId, "_ALREADY_SEEN", "", 1)
			alreadySeen = true
		}
		prepareEvent := apimodel.NewInternalPrepareNewFaces(request.UserId, recoId, value, alreadySeen)
		commons.SendCommonEvent(prepareEvent, request.UserId, apimodel.CommonStreamName, "", apimodel.AwsKinesisStreamClient, apimodel.Anlogger, lc)
	}

	apimodel.Anlogger.Infof(lc, "prepare_new_faces.go : successfully send update prepared new faces with [%d] profiles for userId [%s]",
		len(prepared.TargetUserIndexMap), request.UserId)

	return nil
}

func fetchRecommended(userId string, lc *lambdacontext.LambdaContext) (*apimodel.InternalPrepareNewFacesResponse, error) {
	if len(tmpNeo4jHosts) == 0 {
		tmpNeo4jHosts = make([]string, len(apimodel.Neo4jHosts))
		copy(tmpNeo4jHosts, apimodel.Neo4jHosts)
	}

	request := apimodel.InternalPrepareNewFacesRequest{
		UserId: userId,
		Limit:  commons.PrepareLimitNum,
	}

	start := commons.UnixTimeInMillis()

	body, err := json.Marshal(request)
	if err != nil {
		apimodel.Anlogger.Errorf(lc, "prepare_new_faces.go : error marshaling request %v into json for userId [%s] : %v", request, request.UserId, err)
		return nil, fmt.Errorf("error marshaling request %v into json : %v", request, err)
	}

	apimodel.Anlogger.Debugf(lc, "prepare_new_faces.go : start iterate on hosts %v for userId [%s]", tmpNeo4jHosts, request.UserId)
	for index, eachHost := range tmpNeo4jHosts {
		url := fmt.Sprintf("%s%s", eachHost, apimodel.PrepareNewFacesExtensionSuffix)
		apimodel.Anlogger.Debugf(lc, "prepare_new_faces.go : try this url [%s]", url)
		httpRequest, err := http.NewRequest("GET", url, bytes.NewReader(body))
		if err != nil {
			apimodel.Anlogger.Errorf(lc, "prepare_new_faces.go : error construction the httpRequest to host [%s] with body [%s] for userId [%s] : %v", eachHost, string(body), request.UserId, err)
			return nil, fmt.Errorf("error construction the httpRequest to host [%s] with body [%s] : %v", eachHost, string(body), err)
		}
		httpRequest.SetBasicAuth(apimodel.Neo4jUser, apimodel.Neo4jPassword)
		client := &http.Client{}
		httpResponse, err := client.Do(httpRequest)
		if err != nil {
			tmpNeo4jHosts = append(tmpNeo4jHosts[:index], tmpNeo4jHosts[index+1:]...)
			apimodel.Anlogger.Errorf(lc, "prepare_new_faces.go : error making httpRequest to host [%s], result neo4j hosts %v for userId [%s] : %v", eachHost, tmpNeo4jHosts, request.UserId, err)
			continue
		}
		defer httpResponse.Body.Close()

		respBody, err := ioutil.ReadAll(httpResponse.Body)
		if err != nil {
			apimodel.Anlogger.Errorf(lc, "prepare_new_faces.go : error reading response body for userId [%s] : %v", request.UserId, err)
			return nil, fmt.Errorf("error reading response body : %v", err)
		}

		apimodel.Anlogger.Debugf(lc, "prepare_new_faces.go : response body %s for userId [%s]", string(respBody), request.UserId)

		if httpResponse.StatusCode != 200 {
			tmpNeo4jHosts = append(tmpNeo4jHosts[:index], tmpNeo4jHosts[index+1:]...)
			apimodel.Anlogger.Errorf(lc, "prepare_new_faces.go : response from Neo4j NOT OK, requested host [%s], status code [%d] and status [%s], result neo4j hosts %v for userId [%s]",
				eachHost, httpResponse.StatusCode, httpResponse.Status, tmpNeo4jHosts, request.UserId)
		} else {
			var response apimodel.InternalPrepareNewFacesResponse
			err := json.Unmarshal(respBody, &response)
			if err != nil {
				apimodel.Anlogger.Errorf(lc, "prepare_new_faces.go : error unmarshaling response body [%s] in apimodel.InternalPrepareNewFacesResponse for userId [%s] : %v", string(respBody), request.UserId, err)
				return nil, fmt.Errorf("error unmarshaling response body [%s] in apimodel.InternalPrepareNewFacesResponse : %v", string(respBody), err)
			}
			apimodel.Anlogger.Debugf(lc, "prepare_new_faces.go : successfully handle prepare_new_faces httpRequest in %v millis for userId [%s]", commons.UnixTimeInMillis()-start, request.UserId)

			//round robin load balancer
			tmpNeo4jHosts = append(tmpNeo4jHosts[:index], tmpNeo4jHosts[index+1:]...)
			apimodel.Anlogger.Debugf(lc, "prepare_new_faces.go : result neo4j hosts %v for userId [%s]", tmpNeo4jHosts, request.UserId)
			return &response, nil
		}
	}

	apimodel.Anlogger.Errorf(lc, "prepare_new_faces.go : there is no alive hosts in %v for userId [%s]", apimodel.Neo4jHosts, request.UserId)
	return nil, fmt.Errorf("new_faces : there is no alive hosts in %v", apimodel.Neo4jHosts)
}

//return can we start, was request ok, need to retry, and error string
func canWeStartPrepareProcess(userId string, lc *lambdacontext.LambdaContext) (bool, bool, bool, string) {
	apimodel.Anlogger.Debugf(lc, "prepare_new_faces.go : check can we start prepare new faces process for  userId [%s]", userId)
	updateTime := commons.UnixTimeInMillis()
	oldestPossibleTimeForPreviousPrepareProcessStarted := updateTime - 1000*10 //10 sec

	input := &dynamodb.UpdateItemInput{
		ExpressionAttributeNames: map[string]*string{
			"#updateTime": aws.String(commons.UpdatedTimeColumnName),
		},
		ExpressionAttributeValues: map[string]*dynamodb.AttributeValue{
			":oldestTimeV": {
				N: aws.String(fmt.Sprintf("%v", oldestPossibleTimeForPreviousPrepareProcessStarted)),
			},
			":updateTimeV": {
				N: aws.String(fmt.Sprintf("%v", updateTime)),
			},
		},
		Key: map[string]*dynamodb.AttributeValue{
			commons.UserIdColumnName: {
				S: aws.String(userId),
			},
		},
		ConditionExpression: aws.String(fmt.Sprintf("attribute_not_exists(%s) OR %s < :oldestTimeV",
			commons.UpdatedTimeColumnName, commons.UpdatedTimeColumnName)),
		TableName:        aws.String(apimodel.AlreadyStartedPreparedProcessTable),
		UpdateExpression: aws.String("SET #updateTime = :updateTimeV"),
	}

	_, err := apimodel.AwsDynamoDbClient.UpdateItem(input)
	if err != nil {
		if aerr, ok := err.(awserr.Error); ok {
			switch aerr.Code() {
			case dynamodb.ErrCodeConditionalCheckFailedException:
				apimodel.Anlogger.Debugf(lc, "prepare_new_faces.go : try to start prepare new faces process too often for userId [%s]", userId)
				return false, true, false, ""
			case dynamodb.ErrCodeProvisionedThroughputExceededException:
				apimodel.Anlogger.Warnf(lc, "prepare_new_faces.go : warning, when try to start prepare new faces process for userId [%s], need to retry : %v", userId, aerr)
				return false, false, true, ""
			default:
				apimodel.Anlogger.Errorf(lc, "prepare_new_faces.go : error, try to start prepare new faces process for userId [%s] : %v", userId, aerr)
				return false, false, false, commons.InternalServerError
			}
		}
		apimodel.Anlogger.Errorf(lc, "prepare_new_faces.go : error, try to start prepare new faces process for userId [%s] : %v", userId, err)
		return false, false, false, commons.InternalServerError
	}

	apimodel.Anlogger.Debugf(lc, "service_common.go : successfully check that we can start prepare new faces process for userId [%s]", userId)
	return true, true, false, ""
}

func main() {
	basicLambda.Start(handler)
}
