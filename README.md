# interlok-workflow-rest-services

[![GitHub tag](https://img.shields.io/github/tag/adaptris/interlok-workflow-rest-services.svg)](https://github.com/adaptris/interlok-workflow-rest-services/tags) [![Build Status](https://travis-ci.org/adaptris/interlok-workflow-rest-services.svg?branch=develop)](https://travis-ci.org/adaptris/interlok-workflow-rest-services) [![CircleCI](https://circleci.com/gh/adaptris/interlok-workflow-rest-services/tree/develop.svg?style=svg)](https://circleci.com/gh/adaptris/interlok-workflow-rest-services/tree/develop) [![codecov](https://codecov.io/gh/adaptris/interlok-workflow-rest-services/branch/develop/graph/badge.svg)](https://codecov.io/gh/adaptris/interlok-workflow-rest-services) [![Total alerts](https://img.shields.io/lgtm/alerts/g/adaptris/interlok-workflow-rest-services.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/adaptris/interlok-workflow-rest-services/alerts/) [![Language grade: Java](https://img.shields.io/lgtm/grade/java/g/adaptris/interlok-workflow-rest-services.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/adaptris/interlok-workflow-rest-services/context:java)

REST style API into the Interlok workflows.

## Health-Check

### Installation

Drop the __interlok-workflow-rest-services.jar__ built from this project in to your Interlok __lib__ directory, then modify your __bootstrap.properties__ to make sure the managementComponents property contains all of "__jetty__", "__jmx__" and "__health-check__".
```
managementComponents=jetty:jmx:health-check
```
Optionally, you can also set the property named __rest.health-check.path__, which directly affects the REST API URL path.  The default value is; "__/workflow-health-check/*__".

### Running

Using your favourite HTTP GET/POST tool, make a GET request to the running Interlok instance;
```
http GET http://<host>:<port>/workflow-health-check
```

This will return a JSON array, with all of your adapter, channel and workflow states.  A state, can be one of either; __StartedState__, __InitialisedState__, __StoppedState__ or __ClosedState__.

You can further narrow the results of this service, by optionally specifying the adapter instance, channel and further even the workflow on the URL.  Like this;
```
http GET http://<host>:<port>/workflow-health-check/<adapter-id>/<channel-id>/<workflow-id>
```

Below is an example of the resulting JSON.

```json
{
  "java.util.Collection": [
    {
      "adapter-state": {
        "id": "MyInterlokInstance",
        "state": "StartedState",
        "channel-states": [
          {
            "channel-state": {
              "id": "http_channel",
              "state": "StartedState",
              "workflow-states": [
                {
                  "workflow-state": [
                    {
                      "id": "secondStandardWorkflow",
                      "state": "StartedState"
                    },
                    {
                      "id": "standardWorkflow",
                      "state": "StartedState"
                    }
                  ]
                }
              ]
            }
          }
        ]
      }
    }
  ]
}
```

## Workflow Rest Services


### Installation

Drop the __interlok-workflow-rest-services.jar__ built from this project in to your Interlok __lib__ directory along with the following __interlok-jmx.jar__ and __interlok-jmx-client.jar__.

You can obtain these jars either by;

- building from source from our public github.
- downloading from our public nexus; [Public Nexus](https://nexus.adaptris.net/nexus/content/repositories/releases/com/adaptris/)
- contacting our support team

Then modify your __bootstrap.properties__ to make sure the managementComponents property contains all of "__jetty__", "__jmx__" and "__rest__".
```
managementComponents=jetty:jmx:rest
```

### The API definition ###

There is a single http GET API endpoint that will return an OpenApi 3.0.1 API definition.

You can reach this definition with a HTTP GET to the root url of ;
`http://<host>:<port>/workflow-services/`

The definition is dynamically built in OpenApi 3.0.1 yaml format to include all of your Interlok workflows; a brief example;

```yaml
openapi: 3.0.1
info:
  title: Interlok Workflow REST services
  description: A REST entry point into your Interlok workflows.
  version: "1.0"
servers:
  - url: 'http://yourhost:yourport'
  /workflow-services/myAdapterId/myChannelId/myWorkflowId:
    post:
      description: Post your message directly into your Interlok workflow.
      requestBody:
        content:
          text/plain:
            schema:
              type: string
            examples:
              '0':
                value: MyMessageContent
      responses:
        '200':
          description: The response content after your workflow has processed the incoming message.
          content:
            text/plain:
              schema:
                type: string
              examples:
                '0':
                  value: MyMessageResponseContent
      servers:
        - url: 'http://yourhost:yourport'
    servers:
      - url: 'http://yourhost:yourport'
```

By pointing an OpenApi 3.0.1 tool, such as swagger to the definition url you can visualize the full API available for this instance of Interlok.


### Injecting your messages into your Interlok workflows ###

In true restful style you will POST to the base url with all of the Interlok instance id, channel id and workflow id built into the full url;

`http://<host>:<port>/workflow-services/myInterlokId/myChannelId/myWorkflowId`

The Interlok message content will be drawn from the body of your POST request.

Any http headers will be converted into Interlok message metadata.

If your message was successfully submitted to the workflow then you will get a http status 200 code response with the updated content as the body of the response.

Should your request fail or cause an error, you will receive a http status 400 code along with details of the error in the body of the response.