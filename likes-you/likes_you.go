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
	apimodel.InitLambdaVars("relationships-likes-you")
}

func handler(ctx context.Context, request commons.InternalLMMReq) (commons.InternalLMMResp, error) {
	lc, _ := lambdacontext.FromContext(ctx)

	apimodel.Anlogger.Debugf(lc, "likes_you.go : start handle likes_you request %v", request)

	start := commons.UnixTimeInMillis()

	if len(tmpNeo4jHosts) == 0 {
		tmpNeo4jHosts = make([]string, len(apimodel.Neo4jHosts))
		copy(tmpNeo4jHosts, apimodel.Neo4jHosts)
	}

	body, err := json.Marshal(request)
	if err != nil {
		apimodel.Anlogger.Errorf(lc, "likes_you.go : error marshaling request %v into json : %v", request, err)
		return commons.InternalLMMResp{}, fmt.Errorf("error marshaling request %v into json : %v", request, err)
	}

	apimodel.Anlogger.Debugf(lc, "likes_you.go : start iterate on hosts %v", tmpNeo4jHosts)
	for index, eachHost := range tmpNeo4jHosts {
		url := fmt.Sprintf("%s%s", eachHost, apimodel.LikesYouExtensionSuffix)
		apimodel.Anlogger.Debugf(lc, "likes_you.go : try this url [%s]", url)
		request, err := http.NewRequest("GET", url, bytes.NewReader(body))
		if err != nil {
			apimodel.Anlogger.Errorf(lc, "likes_you.go : error construction the request to host [%s] with body [%s] : %v", eachHost, string(body), err)
			return commons.InternalLMMResp{}, fmt.Errorf("error construction the request to host [%s] with body [%s] : %v", eachHost, string(body), err)
		}
		request.SetBasicAuth(apimodel.Neo4jUser, apimodel.Neo4jPassword)
		client := &http.Client{}
		httpResponse, err := client.Do(request)
		if err != nil {
			tmpNeo4jHosts = append(tmpNeo4jHosts[:index], tmpNeo4jHosts[index+1:]...)
			apimodel.Anlogger.Errorf(lc, "likes_you.go : error making request to host [%s], result neo4j hosts %v : %v", eachHost, tmpNeo4jHosts, err)
			continue
		}
		defer httpResponse.Body.Close()

		respBody, err := ioutil.ReadAll(httpResponse.Body)
		if err != nil {
			apimodel.Anlogger.Errorf(lc, "likes_you.go : error reading response body : %v", err)
			return commons.InternalLMMResp{}, fmt.Errorf("error reading response body : %v", err)
		}

		apimodel.Anlogger.Debugf(lc, "likes_you.go : response body %s", string(respBody))

		if httpResponse.StatusCode != 200 {
			tmpNeo4jHosts = append(tmpNeo4jHosts[:index], tmpNeo4jHosts[index+1:]...)
			apimodel.Anlogger.Errorf(lc, "likes_you.go : response from Neo4j NOT OK, requested host [%s], status code [%d] and status [%s], result neo4j hosts %v",
				eachHost, httpResponse.StatusCode, httpResponse.Status, tmpNeo4jHosts)
		} else {
			var response commons.InternalLMMResp
			err := json.Unmarshal(respBody, &response)
			if err != nil {
				apimodel.Anlogger.Errorf(lc, "likes_you.go : error unmarshaling response body [%s] in commons.InternalLMMResp : %v", string(respBody), err)
				return commons.InternalLMMResp{}, fmt.Errorf("error unmarshaling response body [%s] in commons.InternalLMMResp : %v", string(respBody), err)
			}
			apimodel.Anlogger.Debugf(lc, "likes_you.go : successfully handle likes_you request in %v millis", commons.UnixTimeInMillis()-start)

			//round robin load balancer
			tmpNeo4jHosts = append(tmpNeo4jHosts[:index], tmpNeo4jHosts[index+1:]...)
			apimodel.Anlogger.Debugf(lc, "likes_you.go : result neo4j hosts %v", tmpNeo4jHosts)
			return response, nil
		}
	}

	apimodel.Anlogger.Errorf(lc, "likes_you.go : there is no alive hosts in %v", apimodel.Neo4jHosts)
	return commons.InternalLMMResp{}, fmt.Errorf("likes_you.go : there is no alive hosts in %v", apimodel.Neo4jHosts)
}

func main() {
	basicLambda.Start(handler)
}
