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
)

var tmpNeo4jHosts []string

func init() {
	apimodel.InitLambdaVars("relationships-new-faces")
}

func handler(ctx context.Context, request commons.InternalGetNewFacesReq) (commons.InternalGetNewFacesResp, error) {
	lc, _ := lambdacontext.FromContext(ctx)

	apimodel.Anlogger.Debugf(lc, "new_faces.go : start handle new_faces request %v", request)

	start := commons.UnixTimeInMillis()

	if len(tmpNeo4jHosts) == 0 {
		tmpNeo4jHosts = make([]string, len(apimodel.Neo4jHosts))
		copy(tmpNeo4jHosts, apimodel.Neo4jHosts)
	}

	body, err := json.Marshal(request)
	if err != nil {
		apimodel.Anlogger.Errorf(lc, "new_faces : error marshaling request %v into json : %v", request, err)
		return commons.InternalGetNewFacesResp{}, fmt.Errorf("error marshaling request %v into json : %v", request, err)
	}

	apimodel.Anlogger.Debugf(lc, "new_faces : start iterate on hosts %v", tmpNeo4jHosts)
	for index, eachHost := range tmpNeo4jHosts {
		url := fmt.Sprintf("%s%s", eachHost, apimodel.NewFacesExtensionSuffix)
		apimodel.Anlogger.Debugf(lc, "new_faces : try this url [%s]", url)
		request, err := http.NewRequest("GET", url, bytes.NewReader(body))
		if err != nil {
			apimodel.Anlogger.Errorf(lc, "new_faces : error construction the request to host [%s] with body [%s] : %v", eachHost, string(body), err)
			return commons.InternalGetNewFacesResp{}, fmt.Errorf("error construction the request to host [%s] with body [%s] : %v", eachHost, string(body), err)
		}
		request.SetBasicAuth(apimodel.Neo4jUser, apimodel.Neo4jPassword)
		client := &http.Client{}
		httpResponse, err := client.Do(request)
		if err != nil {
			tmpNeo4jHosts = append(tmpNeo4jHosts[:index], tmpNeo4jHosts[index+1:]...)
			apimodel.Anlogger.Errorf(lc, "new_faces.go : error making request to host [%s], result neo4j hosts %v : %v", eachHost, tmpNeo4jHosts, err)
			continue
		}
		defer httpResponse.Body.Close()

		respBody, err := ioutil.ReadAll(httpResponse.Body)
		if err != nil {
			apimodel.Anlogger.Errorf(lc, "new_faces : error reading response body : %v", err)
			return commons.InternalGetNewFacesResp{}, fmt.Errorf("error reading response body : %v", err)
		}

		apimodel.Anlogger.Debugf(lc, "new_faces : response body %s", string(respBody))

		if httpResponse.StatusCode != 200 {
			tmpNeo4jHosts = append(tmpNeo4jHosts[:index], tmpNeo4jHosts[index+1:]...)
			apimodel.Anlogger.Errorf(lc, "new_faces.go : response from Neo4j NOT OK, requested host [%s], status code [%d] and status [%s], result neo4j hosts %v",
				eachHost, httpResponse.StatusCode, httpResponse.Status, tmpNeo4jHosts)
		} else {
			var response commons.InternalGetNewFacesResp
			err := json.Unmarshal(respBody, &response)
			if err != nil {
				apimodel.Anlogger.Errorf(lc, "new_faces : error unmarshaling response body [%s] in commons.InternalGetNewFacesResp : %v", string(respBody), err)
				return commons.InternalGetNewFacesResp{}, fmt.Errorf("error unmarshaling response body [%s] in commons.InternalGetNewFacesResp : %v", string(respBody), err)
			}
			apimodel.Anlogger.Debugf(lc, "new_faces : successfully handle new_faces request in %v millis", commons.UnixTimeInMillis()-start)

			//round robin load balancer
			tmpNeo4jHosts = append(tmpNeo4jHosts[:index], tmpNeo4jHosts[index+1:]...)
			apimodel.Anlogger.Debugf(lc, "new_faces : result neo4j hosts %v", tmpNeo4jHosts)
			return response, nil
		}
	}

	apimodel.Anlogger.Errorf(lc, "new_faces.go : there is no alive hosts in %v", apimodel.Neo4jHosts)
	return commons.InternalGetNewFacesResp{}, fmt.Errorf("new_faces : there is no alive hosts in %v", apimodel.Neo4jHosts)
}

func main() {
	basicLambda.Start(handler)
}
