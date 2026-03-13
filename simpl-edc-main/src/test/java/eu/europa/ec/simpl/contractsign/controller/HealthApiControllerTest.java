package eu.europa.ec.simpl.contractsign.controller;

import org.eclipse.edc.junit.annotations.ApiTest;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.jersey.testfixtures.RestControllerTestBase;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@ApiTest
class HealthApiControllerTest extends RestControllerTestBase {

    private final Monitor monitor = mock();

    @Override
    protected Object controller() {
        return new HealthApiController(monitor);
    }

    @Test
    void callbackSignedShouldBeSuccessful() {
        String responseBody = given()
                .contentType("application/json")
                .baseUri("http://localhost:" + port + "/health")
                .contentType(JSON)
                .get()
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .extract().body().asString();

        assertEquals("{\"response\":\"I'm alive!\"}",responseBody);
    }
}
