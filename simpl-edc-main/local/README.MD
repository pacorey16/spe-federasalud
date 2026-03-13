provided configuration to present policy feature in edc was done based on https://github.com/eclipse-edc/Samples/tree/main/policy .

---
MINIO S3 EXTENSION INTEGRATION

The project now includes the Gaia-X MinIO S3 extension for native MinIO support. Both provider-config.properties and consumer-config.properties have been updated with MinIO S3 extension configuration:

```properties
# MinIO S3 Extension Configuration (Gaia-X Implementation)
fr.gxfs.s3.access.key=minio-user
fr.gxfs.s3.secret.key=minio-password
fr.gxfs.s3.endpoint=http://localhost:9000
```

This extension uses data address type `MinioS3` instead of `AmazonS3`. To have it local setup working, you should have minio available on 9000 port. Minio is available as a part of docker-compose installation.


steps for launching connectors on local environment (to run it on docker only counterparty field in the body in following requests should be changed to `"counterPartyAddress": "http://provider:19194/protocol"`):
0. Run `docker-compose  up` in docker folder
1. Make sure that in config.properties client.okhttp.type=OkHttpClient is set and also ephemeral.proof.token is provided
2. provide values for  fr.gxfs.s3.access.key, fr.gxfs.s3.secret.key, fr.gxfs.s3.endpoint in provider-config.properties and consumer-config-properties
3. Run step. We neeed to edit our configuration in intellij in order to run project. 
Click on currently used run configuration -> edit configurations
provide org.eclipse.edc.boot.system.runtime.BaseRuntime as a main class entry point
provide environment variable edc.fs.config=/Users/A200245193/IdeaProjects/simpl-edc/local/provider-config.properties;/Users/A200245193/IdeaProjects/simpl-edc/local/provider-config.properties
keep in mind that path need to reflect your path to given property files

in terminal logs we should see that provider and consumer connector are up and running.

---
CONTRACT SIGN EXTENSION

if `contractmanager.extension.enabled=true` is set then it means we are enabling this extension, that allow us to communicate with extension
manager service during contract negotiation phase (to see edc flows please read README.MD in root folder). For the purpose of local development
we use mocked contract manager service (they are created automatically in 0.). If enabled in order to push negotiation state to `FINALIZED`
we need to copy from logs of consumer and provider contract negotiation ids and execute following endpoints:


on consumer side
```
curl --location --request POST 'http://localhost:29191/api/signed/4be4e61d-bf70-4896-bee8-8a3775faa0c3/true' \
--header 'Accept: application/json'
```

and on provider side
```
curl --location --request POST 'http://localhost:19191/api/signed/77a7b57c-a3e2-4002-b817-8fe0ef21fb2d/true' \
--header 'Accept: application/json'
```

keep in mind to provide correct ids and flag to true. true means that consumer/provider agree on negotiation and false 
that they don't, in case of false contract negotiation state won't be transitioned to state `FINALIZED`

if `contractmanager.extension.enabled=false` then during negotiation call state after a couple of seconds will automatically
be moved to state `FINALIZED` and no http calls are needed

The code of the mock is available in [simpl-contract-negotiation-mockup repository](https://code.europa.eu/simpl/simpl-open/development/gaia-x-edc/simpl-contract-negotiation-mockup) and its images are generated manually and stored in [container registry](https://code.europa.eu/simpl/simpl-open/development/gaia-x-edc/simpl-contract-negotiation-mockup/container_registry)

---
TRACING WITH OPENTELEMETRY

The agent is located at:

otel/opentelemetry-javaagent.jar

#### Running via Command Line

Add the agent to your Java command:
```
-javaagent:<path-to-project>/otel/opentelemetry-javaagent.jar -Dotel.javaagent.configuration-file=<path-to-project>/local/<provider|consumer>-otel.properties
```
Replace <path-to-project> with the actual path to your simpl-edc project directory.
pick the correct configuration for provider or consumer depending on which one you are running
- For example, if you are running the provider connector, use `local/provider-otel.properties`
- If you are running the consumer connector, use `local/consumer-otel.properties`

#### Running in IntelliJ IDEA

- Open Run > Edit Configurations
- Select your run configuration
- In the VM options field, add:
```
   -javaagent:<path-to-project>/otel/opentelemetry-javaagent.jar -javaagent:<path-to-project>/otel/opentelemetry-javaagent.jar -Dotel.javaagent.configuration-file=<path-to-project>/local/<provider|consumer>-otel.properties
```
- Replace <path-to-project> with the actual path to your simpl-edc project directory
- pick the correct configuration for provider or consumer depending on which one you are running. The example you can see in the section below.
- Click Apply, then OK

