package main

import (
	"../apicommon"
	"github.com/ringoid/commons"
	"net/http"
	"bytes"
	"io/ioutil"
	"github.com/aws/aws-lambda-go/lambdacontext"
	"context"
	"fmt"
	"encoding/json"
	basicLambda "github.com/aws/aws-lambda-go/lambda"
)

var tmpNeo4jHosts []string

func init() {
	apimodel.InitLambdaVars("relationships-ready-for-push")
}

func handler(ctx context.Context, request commons.PushRequest) (commons.PushResponse, error) {
	lc, _ := lambdacontext.FromContext(ctx)

	start := commons.UnixTimeInMillis()

	apimodel.Anlogger.Debugf(lc, "ready_for_push.go : start handle ready_for_push request [%v]", request)

	resp, err := sendRequest(&request, lc)
	if err != nil {
		return commons.PushResponse{}, err
	}

	apimodel.Anlogger.Infof(lc, "ready_for_push.go : successfully handle ready_for_push request in [%v] millis with [%d] push objects",
		commons.UnixTimeInMillis()-start, len(resp.Users))

	return *resp, nil
}

func sendRequest(request *commons.PushRequest, lc *lambdacontext.LambdaContext) (*commons.PushResponse, error) {
	start := commons.UnixTimeInMillis()

	if len(tmpNeo4jHosts) == 0 {
		tmpNeo4jHosts = make([]string, len(apimodel.Neo4jHosts))
		copy(tmpNeo4jHosts, apimodel.Neo4jHosts)
	}

	body, err := json.Marshal(request)
	if err != nil {
		apimodel.Anlogger.Errorf(lc, "ready_for_push : error marshaling request %v into json : %v", request, err)
		return nil, fmt.Errorf("error marshaling request %v into json : %v", request, err)
	}
	apimodel.Anlogger.Debugf(lc, "ready_for_push : start iterate on hosts %v ", tmpNeo4jHosts)

	for index, eachHost := range tmpNeo4jHosts {
		url := fmt.Sprintf("%s%s", eachHost, apimodel.ReadyForPushExtensionSuffix)
		apimodel.Anlogger.Debugf(lc, "ready_for_push : try this url [%s]", url)
		httpRequest, err := http.NewRequest("GET", url, bytes.NewReader(body))
		if err != nil {
			apimodel.Anlogger.Errorf(lc, "ready_for_push : error construction the httpRequest to host [%s] with body [%s] : %v", eachHost, string(body), err)
			return nil, fmt.Errorf("error construction the httpRequest to host [%s] with body [%s] : %v", eachHost, string(body), err)
		}
		httpRequest.SetBasicAuth(apimodel.Neo4jUser, apimodel.Neo4jPassword)
		client := &http.Client{}
		httpResponse, err := client.Do(httpRequest)
		if err != nil {
			tmpNeo4jHosts = append(tmpNeo4jHosts[:index], tmpNeo4jHosts[index+1:]...)
			apimodel.Anlogger.Errorf(lc, "ready_for_push.go : error making httpRequest to host [%s], result neo4j hosts %v : %v", eachHost, tmpNeo4jHosts, err)
			continue
		}
		defer httpResponse.Body.Close()

		respBody, err := ioutil.ReadAll(httpResponse.Body)
		if err != nil {
			apimodel.Anlogger.Errorf(lc, "ready_for_push : error reading response body : %v", err)
			return nil, fmt.Errorf("error reading response body : %v", err)
		}

		if httpResponse.StatusCode != 200 {
			tmpNeo4jHosts = append(tmpNeo4jHosts[:index], tmpNeo4jHosts[index+1:]...)
			apimodel.Anlogger.Errorf(lc, "ready_for_push.go : response from Neo4j NOT OK, requested host [%s], status code [%d] and status [%s], result neo4j hosts %v",
				eachHost, httpResponse.StatusCode, httpResponse.Status, tmpNeo4jHosts)
		} else {
			var response commons.PushResponse
			err := json.Unmarshal(respBody, &response)
			if err != nil {
				apimodel.Anlogger.Errorf(lc, "ready_for_push : error unmarshaling response body [%s] in PushResponse : %v", string(respBody), err)
				return nil, fmt.Errorf("error unmarshaling response body [%s] in PushResponse : %v", string(respBody), err)
			}
			apimodel.Anlogger.Debugf(lc, "ready_for_push : successfully handle ready_for_push httpRequest in %v millis with result size [%v]", commons.UnixTimeInMillis()-start, len(response.Users))

			//round robin load balancer
			tmpNeo4jHosts = append(tmpNeo4jHosts[:index], tmpNeo4jHosts[index+1:]...)
			apimodel.Anlogger.Debugf(lc, "ready_for_push : result neo4j hosts %v", tmpNeo4jHosts)
			return &response, nil
		}
	}
	apimodel.Anlogger.Errorf(lc, "ready_for_push.go : there is no alive hosts in %v", apimodel.Neo4jHosts)
	return nil, fmt.Errorf("ready_for_push : there is no alive hosts in %v", apimodel.Neo4jHosts)
}

func main() {
	basicLambda.Start(handler)
}
