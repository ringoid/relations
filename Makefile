stage-all: clean stage-deploy
test-all: clean test-deploy
prod-all: clean prod-deploy

build:
	@echo '--- Building kinesis-consumer-relationships function ---'
	gradle build

test-deploy: clean build
	@echo '--- Build lambda test ---'
	@echo 'Package template'
	sam package --template-file relationships-template.yaml --s3-bucket ringoid-cloudformation-template --output-template-file relationships-template-packaged.yaml
	@echo 'Deploy relationships-image-stack'
	sam deploy --template-file relationships-template-packaged.yaml --s3-bucket ringoid-cloudformation-template --stack-name test-relationships-stack --capabilities CAPABILITY_IAM --parameter-overrides Env=test --no-fail-on-empty-changeset

stage-deploy: clean build
	@echo '--- Build lambda stage ---'
	@echo 'Package template'
	sam package --template-file relationships-template.yaml --s3-bucket ringoid-cloudformation-template --output-template-file relationships-template-packaged.yaml
	@echo 'Deploy relationships-image-stack'
	sam deploy --template-file relationships-template-packaged.yaml --s3-bucket ringoid-cloudformation-template --stack-name stage-relationships-stack --capabilities CAPABILITY_IAM --parameter-overrides Env=stage --no-fail-on-empty-changeset

prod-deploy: clean build
	@echo '--- Build lambda prod ---'
	@echo 'Package template'
	sam package --template-file relationships-template.yaml --s3-bucket ringoid-cloudformation-template --output-template-file relationships-template-packaged.yaml
	@echo 'Deploy relationships-image-stack'
	sam deploy --template-file relationships-template-packaged.yaml --s3-bucket ringoid-cloudformation-template --stack-name prod-relationships-stack --capabilities CAPABILITY_IAM --parameter-overrides Env=prod --no-fail-on-empty-changeset

clean:
	@echo '--- Delete old artifacts ---'
	gradle clean

