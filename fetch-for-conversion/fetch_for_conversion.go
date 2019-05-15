package main

import (
	"../apicommon"
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

type ConvertRequest struct {
	Skip  int `json:"skip"`
	Limit int `json:"limit"`
}

type ConvertResponse struct {
	Objects []ConvertObject `json:"objects"`
}

type ConvertObject struct {
	UserId    string `json:"userId"`
	ObjectKey string `json:"objectKey"`
}

func init() {
	apimodel.InitLambdaVars("relationships-fetch-for-conversion")
}

func handler(ctx context.Context, request ConvertRequest) (ConvertResponse, error) {
	lc, _ := lambdacontext.FromContext(ctx)

	apimodel.Anlogger.Debugf(lc, "fetch_for_conversion.go : start handle ready_for_push request [%v]", request)

	resp, err := sendRequest(&request, lc)
	if err != nil {
		return ConvertResponse{}, err
	}

	return *resp, nil
}

func sendRequest(request *ConvertRequest, lc *lambdacontext.LambdaContext) (*ConvertResponse, error) {
	if len(tmpNeo4jHosts) == 0 {
		tmpNeo4jHosts = make([]string, len(apimodel.Neo4jHosts))
		copy(tmpNeo4jHosts, apimodel.Neo4jHosts)
	}

	body, err := json.Marshal(request)
	if err != nil {
		apimodel.Anlogger.Errorf(lc, "fetch_for_conversion.go : error marshaling request %v into json : %v", request, err)
		return nil, fmt.Errorf("error marshaling request %v into json : %v", request, err)
	}
	apimodel.Anlogger.Debugf(lc, "fetch_for_conversion.go : start iterate on hosts %v ", tmpNeo4jHosts)

	for index, eachHost := range tmpNeo4jHosts {
		url := fmt.Sprintf("%s%s", eachHost, apimodel.FetchForConversionExtensionSuffix)
		apimodel.Anlogger.Debugf(lc, "fetch_for_conversion.go : try this url [%s]", url)
		httpRequest, err := http.NewRequest("GET", url, bytes.NewReader(body))
		if err != nil {
			apimodel.Anlogger.Errorf(lc, "fetch_for_conversion.go : error construction the httpRequest to host [%s] with body [%s] : %v", eachHost, string(body), err)
			return nil, fmt.Errorf("error construction the httpRequest to host [%s] with body [%s] : %v", eachHost, string(body), err)
		}
		httpRequest.SetBasicAuth(apimodel.Neo4jUser, apimodel.Neo4jPassword)
		client := &http.Client{}
		httpResponse, err := client.Do(httpRequest)
		if err != nil {
			tmpNeo4jHosts = append(tmpNeo4jHosts[:index], tmpNeo4jHosts[index+1:]...)
			apimodel.Anlogger.Errorf(lc, "fetch_for_conversion.go : error making httpRequest to host [%s], result neo4j hosts %v : %v", eachHost, tmpNeo4jHosts, err)
			continue
		}
		defer httpResponse.Body.Close()

		respBody, err := ioutil.ReadAll(httpResponse.Body)
		if err != nil {
			apimodel.Anlogger.Errorf(lc, "fetch_for_conversion.go : error reading response body : %v", err)
			return nil, fmt.Errorf("error reading response body : %v", err)
		}

		if httpResponse.StatusCode != 200 {
			tmpNeo4jHosts = append(tmpNeo4jHosts[:index], tmpNeo4jHosts[index+1:]...)
			apimodel.Anlogger.Errorf(lc, "fetch_for_conversion.go : response from Neo4j NOT OK, requested host [%s], status code [%d] and status [%s], result neo4j hosts %v",
				eachHost, httpResponse.StatusCode, httpResponse.Status, tmpNeo4jHosts)
		} else {
			var response ConvertResponse
			err := json.Unmarshal(respBody, &response)
			if err != nil {
				apimodel.Anlogger.Errorf(lc, "fetch_for_conversion.go : error unmarshaling response body [%s] in ConvertResponse : %v", string(respBody), err)
				return nil, fmt.Errorf("error unmarshaling response body [%s] in PushResponse : %v", string(respBody), err)
			}

			//round robin load balancer
			tmpNeo4jHosts = append(tmpNeo4jHosts[:index], tmpNeo4jHosts[index+1:]...)
			apimodel.Anlogger.Debugf(lc, "fetch_for_conversion.go : result neo4j hosts %v", tmpNeo4jHosts)
			return &response, nil
		}
	}
	apimodel.Anlogger.Errorf(lc, "fetch_for_conversion.go : there is no alive hosts in %v", apimodel.Neo4jHosts)
	return nil, fmt.Errorf("fetch_for_conversion : there is no alive hosts in %v", apimodel.Neo4jHosts)
}

func main() {
	basicLambda.Start(handler)
}
