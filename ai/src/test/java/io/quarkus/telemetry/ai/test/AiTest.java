package io.quarkus.telemetry.ai.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.telemetry.common.Context;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.io.InputStream;

import static io.restassured.RestAssured.given;

@QuarkusTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class AiTest {

    private static final String CONTEXT = "context2.json";

    @Inject
    ObjectMapper mapper;

    @Test
    public void testContext() {
        try (InputStream stream = AiTest.class.getClassLoader().getResourceAsStream(CONTEXT)) {
            Context context = mapper.readValue(stream, Context.class); // so we validate input
            given()
                    .contentType("application/json")
                    .body(context)
                    .when()
                    .post("/analyze")
                    .then()
                    .statusCode(204);
        } catch (IOException ignored) {
            ignored.printStackTrace();
        }
    }

}
