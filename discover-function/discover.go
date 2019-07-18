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
	apimodel.InitLambdaVars("relationships-discover")
}

func handler(ctx context.Context, request commons.DiscoverRequest) (commons.InternalGetNewFacesResp, error) {
	lc, _ := lambdacontext.FromContext(ctx)

	apimodel.Anlogger.Debugf(lc, "discover.go : start handle discover request %v for userId [%s]", request, request.UserId)

	start := commons.UnixTimeInMillis()

	if len(tmpNeo4jHosts) == 0 {
		tmpNeo4jHosts = make([]string, len(apimodel.Neo4jHosts))
		copy(tmpNeo4jHosts, apimodel.Neo4jHosts)
	}

	body, err := json.Marshal(request)
	if err != nil {
		apimodel.Anlogger.Errorf(lc, "discover.go : error marshaling request %v into json for userId [%s] : %v", request, request.UserId, err)
		return commons.InternalGetNewFacesResp{}, fmt.Errorf("error marshaling request %v into json : %v", request, err)
	}

	apimodel.Anlogger.Debugf(lc, "discover.go : start iterate on hosts %v for userId [%s]", tmpNeo4jHosts, request.UserId)
	for index, eachHost := range tmpNeo4jHosts {
		url := fmt.Sprintf("%s%s", eachHost, apimodel.DiscoverExtensionSuffix)
		apimodel.Anlogger.Debugf(lc, "discover.go : try this url [%s]", url)
		httpRequest, err := http.NewRequest("GET", url, bytes.NewReader(body))
		if err != nil {
			apimodel.Anlogger.Errorf(lc, "discover.go : error construction the httpRequest to host [%s] with body [%s] for userId [%s] : %v", eachHost, string(body), request.UserId, err)
			return commons.InternalGetNewFacesResp{}, fmt.Errorf("error construction the httpRequest to host [%s] with body [%s] : %v", eachHost, string(body), err)
		}
		httpRequest.SetBasicAuth(apimodel.Neo4jUser, apimodel.Neo4jPassword)
		client := &http.Client{}
		httpResponse, err := client.Do(httpRequest)
		if err != nil {
			tmpNeo4jHosts = append(tmpNeo4jHosts[:index], tmpNeo4jHosts[index+1:]...)
			apimodel.Anlogger.Errorf(lc, "discover.go : error making httpRequest to host [%s], result neo4j hosts %v for userId [%s] : %v", eachHost, tmpNeo4jHosts, request.UserId, err)
			continue
		}
		defer httpResponse.Body.Close()

		respBody, err := ioutil.ReadAll(httpResponse.Body)
		if err != nil {
			apimodel.Anlogger.Errorf(lc, "discover.go : error reading response body for userId [%s] : %v", request.UserId, err)
			return commons.InternalGetNewFacesResp{}, fmt.Errorf("error reading response body : %v", err)
		}

		apimodel.Anlogger.Debugf(lc, "discover.go : response body %s for userId [%s]", string(respBody), request.UserId)

		if httpResponse.StatusCode != 200 {
			tmpNeo4jHosts = append(tmpNeo4jHosts[:index], tmpNeo4jHosts[index+1:]...)
			apimodel.Anlogger.Errorf(lc, "discover.go : response from Neo4j NOT OK, requested host [%s], status code [%d] and status [%s], result neo4j hosts %v for userId [%s]",
				eachHost, httpResponse.StatusCode, httpResponse.Status, tmpNeo4jHosts, request.UserId)
		} else {
			var response commons.InternalGetNewFacesResp
			err := json.Unmarshal(respBody, &response)
			if err != nil {
				apimodel.Anlogger.Errorf(lc, "discover.go : error unmarshaling response body [%s] in commons.InternalGetNewFacesResp for userId [%s] : %v", string(respBody), request.UserId, err)
				return commons.InternalGetNewFacesResp{}, fmt.Errorf("error unmarshaling response body [%s] in commons.InternalGetNewFacesResp : %v", string(respBody), err)
			}
			apimodel.Anlogger.Debugf(lc, "discover.go : successfully handle new_faces httpRequest in %v millis for userId [%s]", commons.UnixTimeInMillis()-start, request.UserId)

			//round robin load balancer
			tmpNeo4jHosts = append(tmpNeo4jHosts[:index], tmpNeo4jHosts[index+1:]...)
			apimodel.Anlogger.Debugf(lc, "discover.go : result neo4j hosts %v for userId [%s]", tmpNeo4jHosts, request.UserId)
			return response, nil
		}
	}

	apimodel.Anlogger.Errorf(lc, "discover.go : there is no alive hosts in %v for userId [%s]", apimodel.Neo4jHosts, request.UserId)
	return commons.InternalGetNewFacesResp{}, fmt.Errorf("discover.go : there is no alive hosts in %v", apimodel.Neo4jHosts)
}

func main() {
	basicLambda.Start(handler)
}
