package main

import (
	"context"
	basicLambda "github.com/aws/aws-lambda-go/lambda"
	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-lambda-go/lambdacontext"
	"../apicommon"
	"bytes"
	"net/http"
	"io/ioutil"
	"fmt"
	"github.com/ringoid/commons"
)

var tmpNeo4jHosts []string

func init() {
	apimodel.InitLambdaVars("relationships-kinesis-consumer")
}

func handler(ctx context.Context, event events.KinesisEvent) (error) {
	lc, _ := lambdacontext.FromContext(ctx)

	start := commons.UnixTimeInMillis()

	if len(tmpNeo4jHosts) == 0 {
		tmpNeo4jHosts = make([]string, len(apimodel.Neo4jHosts))
		copy(tmpNeo4jHosts, apimodel.Neo4jHosts)
	}

	apimodel.Anlogger.Debugf(lc, "handle_stream.go : start handle request with [%d] records", len(event.Records))
	buffer := bytes.NewBufferString("{\"events\":[")

	for recordIndex, record := range event.Records {
		body := record.Kinesis.Data
		_, err := buffer.Write(body)
		if err != nil {
			apimodel.Anlogger.Fatalf(lc, "handle_stream : error writing record to buffer : %v", err)
		}

		if recordIndex < len(event.Records)-1 {
			_, err = buffer.WriteString(",")
			if err != nil {
				apimodel.Anlogger.Fatalf(lc, "handle_stream : error writing comma to buffer : %v", err)
			}
		}
	}

	_, err := buffer.WriteString("]}")
	if err != nil {
		apimodel.Anlogger.Fatalf(lc, "handle_stream : error writing end of json to buffer : %v", err)
	}

	apimodel.Anlogger.Debugf(lc, "handle_stream : start iterate on hosts %v", tmpNeo4jHosts)

	for index, eachHost := range tmpNeo4jHosts {
		url := fmt.Sprintf("%s%s", eachHost, apimodel.ActionExtensionSuffix)
		apimodel.Anlogger.Debugf(lc, "handle_stream : try this url [%s]", url)
		request, err := http.NewRequest("POST", url, buffer)
		if err != nil {
			apimodel.Anlogger.Fatalf(lc, "handle_stream : error construction the request to host [%s] : %v", eachHost, err)
		}
		request.SetBasicAuth(apimodel.Neo4jUser, apimodel.Neo4jPassword)
		client := &http.Client{}
		httpResponse, err := client.Do(request)
		if err != nil {
			tmpNeo4jHosts = append(tmpNeo4jHosts[:index], tmpNeo4jHosts[index+1:]...)
			apimodel.Anlogger.Errorf(lc, "handle_stream : error making request to host [%s], result neo4j hosts %v, try next one : %v", eachHost, tmpNeo4jHosts, err)
			continue
		}
		defer httpResponse.Body.Close()

		respBody, err := ioutil.ReadAll(httpResponse.Body)
		if err != nil {
			apimodel.Anlogger.Fatalf(lc, "handle_stream : error reading response body : %v", err)
		}

		apimodel.Anlogger.Debugf(lc, "handle_stream : response body %s", string(respBody))

		if httpResponse.StatusCode != 200 {
			tmpNeo4jHosts = append(tmpNeo4jHosts[:index], tmpNeo4jHosts[index+1:]...)
			apimodel.Anlogger.Errorf(lc, "handle_stream : response from Neo4j NOT OK, requested host [%s], status code [%d] and status [%s], result neo4j hosts %v",
				eachHost, httpResponse.StatusCode, httpResponse.Status, tmpNeo4jHosts)
		} else {
			apimodel.Anlogger.Infof(lc, "handle_stream : successfully handle %d records from the stream in %v millis",
				len(event.Records), commons.UnixTimeInMillis()-start)
			return nil
		}
	}

	apimodel.Anlogger.Errorf(lc, "handle_stream : there is no alive hosts in %v, restart the function", apimodel.Neo4jHosts)
	return fmt.Errorf("handle_stream : there is no alive hosts in %v, restart the function", apimodel.Neo4jHosts)
}

func main() {
	basicLambda.Start(handler)
}
