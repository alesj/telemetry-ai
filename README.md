# Telemetry AI

## How to run / test the AI part

From project root, run this 3 commands

(1) ./mvn.proxy.sh quarkus:dev

Wait for (1) to **fully** start, so (2) and (3) share the **same** LGTM resources.

(2) ./mvn.app.sh quarkus:dev

(3) ./mvn.ai.sh quarkus:dev

In (3) you have 3 profiles:
* default - OpenAI
* -Pwatsonx - WatsonX
* -Pgemini - Gemini

This will run Quarkus with same `quarkus.profile` property as Maven profile name,
and picking up the right Quarkus LangChain4J dependencies.

```xml
<!-- Default: OpenAI -->
<profile>
    <id>openai</id>
    <activation>
        <activeByDefault>true</activeByDefault>
    </activation>
    <properties>
        <quarkus.profile>openai</quarkus.profile>
    </properties>
    ...
```

---

(1) Starts a simple `proxy` app, that just forwards the request to an "actual" `app`. 

(2) Starts an `app` that based on the `poke` value returns http code.

```java
    @GET
    @Path("/poke")
    @Produces(MediaType.TEXT_PLAIN)
    public Response poke(@QueryParam("value") Integer value) {
        log.info("Poking ... " + value);
        arr[0] = value;
        if (inRange(400, 600, value)) {
            Response.Status status = Response.Status.fromStatusCode(value);
            throw new WebApplicationException(
                    "App error: " + status.getStatusCode(),
                    Response.status(status)
                            .entity(status.getReasonPhrase())
                            .build()
            );
        }
        Integer copy = value;
        if (!inRange(200, 600, value)) {
            copy = 200; // plain ok
        }
        return Response.status(copy).entity("Poked with " + value).build();
    }
```

(3) Starts the actual AI analysis application.

---

Poke the `proxy` app via browser or curl: `http://localhost:8081/poke?value=<your choice of http code>`

Poke it a few times with different `http codes`, so we get different traces, logs, metrics, ...

Then hit the `ai` app with the number of last request you want to analyze: `http://localhost:8080/analyze/<number of last requests>`

You should see the AI analysis output in `ai` app's logs or browser / curl.

---

You can check any app's DevUI on where the Grafana / LGTM is available - for any other queries, etc
