package apimodel

import (
	"github.com/ringoid/commons"
	"os"
	"fmt"
	"strings"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/aws"
)

var Anlogger *commons.Logger
var Neo4jUser string
var Neo4jPassword string
var Neo4jHosts []string

const (
	ActionExtensionSuffix   = ":7474/graphaware/actions"
	NewFacesExtensionSuffix = ":7474/graphaware/new_faces"
	LikesYouExtensionSuffix = ":7474/graphaware/likes_you"
	MatchYouExtensionSuffix = ":7474/graphaware/matches"
	MessageYouExtensionSuffix = ":7474/graphaware/messages"
)

func InitLambdaVars(lambdaName string) {
	var env string
	var ok bool
	var papertrailAddress string
	var err error
	var neo4jUris string
	var awsSession *session.Session

	env, ok = os.LookupEnv("ENV")
	if !ok {
		fmt.Printf("lambda-initialization : service_common.go : env can not be empty ENV\n")
		os.Exit(1)
	}
	fmt.Printf("lambda-initialization : service_common.go : start with ENV = [%s]\n", env)

	papertrailAddress, ok = os.LookupEnv("PAPERTRAIL_LOG_ADDRESS")
	if !ok {
		fmt.Printf("lambda-initialization : service_common.go : env can not be empty PAPERTRAIL_LOG_ADDRESS\n")
		os.Exit(1)
	}
	fmt.Printf("lambda-initialization : service_common.go : start with PAPERTRAIL_LOG_ADDRESS = [%s]\n", papertrailAddress)

	Anlogger, err = commons.New(papertrailAddress, fmt.Sprintf("%s-%s", env, lambdaName))
	if err != nil {
		fmt.Errorf("lambda-initialization : service_common.go : error during startup : %v\n", err)
		os.Exit(1)
	}
	Anlogger.Debugf(nil, "lambda-initialization : service_common.go : logger was successfully initialized")

	awsSession, err = session.NewSession(aws.NewConfig().
		WithRegion(commons.Region).WithMaxRetries(commons.MaxRetries).
		WithLogger(aws.LoggerFunc(func(args ...interface{}) { Anlogger.AwsLog(args) })).WithLogLevel(aws.LogOff))
	if err != nil {
		Anlogger.Fatalf(nil, "lambda-initialization : service_common.go : error during initialization : %v", err)
	}
	Anlogger.Debugf(nil, "lambda-initialization : service_common.go : aws session was successfully initialized")

	Neo4jUser, ok = os.LookupEnv("NEO4J_USER")
	if !ok {
		Anlogger.Fatalf(nil, "lambda-initialization : service_common.go : env can not be empty NEO4J_USER")
	}
	Anlogger.Debugf(nil, "lambda-initialization : service_common.go : start with NEO4J_USER = [%s]", Neo4jUser)

	neo4jUris, ok = os.LookupEnv("NEO4J_URIS")
	if !ok {
		Anlogger.Fatalf(nil, "lambda-initialization : service_common.go : env can not be empty NEO4J_URIS")
	}
	Anlogger.Debugf(nil, "lambda-initialization : service_common.go : start with NEO4J_URIS = [%s]", neo4jUris)

	for _, each := range strings.Split(neo4jUris, "&") {
		Neo4jHosts = append(Neo4jHosts, fmt.Sprintf("http://%s", each))
	}

	Neo4jPassword = commons.GetSecret(fmt.Sprintf("%s/Neo4j/Password", env), "password", awsSession, Anlogger, nil)
}
