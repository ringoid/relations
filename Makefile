stage-all: clean stage-deploy
test-all: clean test-deploy
prod-all: clean prod-deploy

build-go:
	@echo '--- Build Go modules ---'
	GOOS=linux go build kinesis-consumer/handle_stream.go
	GOOS=linux go build new-faces/new_faces.go
	GOOS=linux go build likes-you/likes_you.go
	GOOS=linux go build match-you/match.go
	GOOS=linux go build message-you/messages.go
	GOOS=linux go build ready-for-push/ready_for_push.go
	GOOS=linux go build lmhis-you/lmhis.go

zip-go: build-go
	@echo '--- Zip Go modules ---'
	zip handle_stream.zip ./handle_stream
	zip new_faces.zip ./new_faces
	zip likes_you.zip ./likes_you
	zip match.zip ./match
	zip messages.zip ./messages
	zip ready_for_push.zip ./ready_for_push
	zip lmhis.zip ./lmhis

buildgradle:
	@echo '--- Building kinesis-consumer-relationships function ---'
	gradle build

test-deploy: zip-go buildgradle
	@echo '--- Build lambda test ---'
	@echo '--- Upload jars --'
	aws s3 cp common/build/libs/common-1.0-SNAPSHOT.jar s3://test-ringoid-neo4j-jars
	aws s3 cp neo4j-extension/build/libs/neo4j-extension-1.0-SNAPSHOT.jar s3://test-ringoid-neo4j-jars
	@echo 'Package template'
	sam package --template-file relationships-template.yaml --s3-bucket ringoid-cloudformation-template --output-template-file relationships-template-packaged.yaml
	@echo 'Deploy relationships-image-stack'
	sam deploy --template-file relationships-template-packaged.yaml --s3-bucket ringoid-cloudformation-template --stack-name test-relationships-stack --capabilities CAPABILITY_IAM --parameter-overrides Env=test --no-fail-on-empty-changeset

stage-deploy: zip-go buildgradle
	@echo '--- Build lambda stage ---'
	@echo '--- Upload jars --'
	aws s3 cp common/build/libs/common-1.0-SNAPSHOT.jar s3://stage-ringoid-neo4j-jars
	aws s3 cp neo4j-extension/build/libs/neo4j-extension-1.0-SNAPSHOT.jar s3://stage-ringoid-neo4j-jars
	@echo 'Package template'
	sam package --template-file relationships-template.yaml --s3-bucket ringoid-cloudformation-template --output-template-file relationships-template-packaged.yaml
	@echo 'Deploy relationships-image-stack'
	sam deploy --template-file relationships-template-packaged.yaml --s3-bucket ringoid-cloudformation-template --stack-name stage-relationships-stack --capabilities CAPABILITY_IAM --parameter-overrides Env=stage --no-fail-on-empty-changeset

prod-deploy: zip-go buildgradle
	@echo '--- Build lambda prod ---'
	@echo '--- Upload jars --'
	aws s3 cp common/build/libs/common-1.0-SNAPSHOT.jar s3://prod-ringoid-neo4j-jars
	aws s3 cp neo4j-extension/build/libs/neo4j-extension-1.0-SNAPSHOT.jar s3://prod-ringoid-neo4j-jars
	@echo 'Package template'
	sam package --template-file relationships-template.yaml --s3-bucket ringoid-cloudformation-template --output-template-file relationships-template-packaged.yaml
	@echo 'Deploy relationships-image-stack'
	sam deploy --template-file relationships-template-packaged.yaml --s3-bucket ringoid-cloudformation-template --stack-name prod-relationships-stack --capabilities CAPABILITY_IAM --parameter-overrides Env=prod --no-fail-on-empty-changeset

clean:
	@echo '--- Delete old artifacts ---'
	gradle clean
	rm -rf handle_stream.zip
	rm -rf handle_stream
	rm -rf new_faces.zip
	rm -rf new_faces
	rm -rf likes_you.zip
	rm -rf likes_you
	rm -rf match.zip
	rm -rf match
	rm -rf messages.zip
	rm -rf messages
	rm -rf ready_for_push
	rm -rf ready_for_push.zip
	rm -rf lmhis
	rm -rf lmhis.zip

