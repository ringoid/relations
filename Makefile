all: clean stage-deploy

build:
	@echo '--- Building kinesis-consumer-relationships function ---'
	gradle build

stage-deploy: clean build
	@echo '--- Build lambda stage ---'
	@echo 'Package template'
	sam package --template-file relationships-template.yaml --s3-bucket ringoid-cloudformation-template --output-template-file relationships-template-packaged.yaml
	@echo 'Deploy relationships-image-stack'
	sam deploy --template-file relationships-template-packaged.yaml --s3-bucket ringoid-cloudformation-template --stack-name stage-relationships-stack --capabilities CAPABILITY_IAM --parameter-overrides Env=stage --no-fail-on-empty-changeset

clean:
	@echo '--- Delete old artifacts ---'
	gradle clean

