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
)

var tmpNeo4jHosts []string

const (
	PrepareLimit = 10
)

func init() {
	apimodel.InitLambdaVars("relationships-prepare-new-faces")
}

func handler(ctx context.Context, request commons.InternalPrepareNewFacesReq) (error) {
	lc, _ := lambdacontext.FromContext(ctx)

	//todo:add rate limit

	apimodel.Anlogger.Debugf(lc, "prepare_new_faces.go : start handle new_faces request %v for userId [%s]", request, request.UserId)
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
		prepareEvent := apimodel.NewInternalPrepareNewFaces(request.UserId, key, value)
		commons.SendCommonEvent(prepareEvent, request.UserId, apimodel.CommonStreamName, "", apimodel.AwsKinesisStreamClient, apimodel.Anlogger, lc)
	}

	return nil
}

func fetchRecommended(userId string, lc *lambdacontext.LambdaContext) (*apimodel.InternalPrepareNewFacesResponse, error) {
	if len(tmpNeo4jHosts) == 0 {
		tmpNeo4jHosts = make([]string, len(apimodel.Neo4jHosts))
		copy(tmpNeo4jHosts, apimodel.Neo4jHosts)
	}

	request := apimodel.InternalPrepareNewFacesRequest{
		UserId: userId,
		Limit:  PrepareLimit,
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

func main() {
	basicLambda.Start(handler)
}
