package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.IsNot.not;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
class WarehouseEndpointTest {

  private static final String PATH = "warehouse";
  private static final String REPLACEMENT = "/replacement";
  private static final String SEEDED_CODE = "MWH.001";
  private static final String ERROR_CODE_FIELD = "code";

  @Test
  void listsTheWarehousesSeededInTheDatabase() {
    // Given the three warehouses of import.sql
    // When / Then
    given()
        .when()
        .get(PATH)
        .then()
        .statusCode(200)
        .body(
            containsString(SEEDED_CODE),
            containsString("MWH.012"),
            containsString("MWH.023"),
            containsString("\"id\""));
  }

  @Test
  void createsAWarehouseAndExposesItById() {
    // Given a free location and an unused business unit code
    // When
    String id =
        given()
            .contentType(ContentType.JSON)
            .body(warehouseBody("MWH.401", "VETSBY-001", 50, 10))
            .when()
            .post(PATH)
            .then()
            .statusCode(201)
            .body("businessUnitCode", equalTo("MWH.401"))
            .body("id", notNullValue())
            .extract()
            .path("id");

    // Then
    given()
        .when()
        .get(PATH + "/" + id)
        .then()
        .statusCode(200)
        .body("businessUnitCode", equalTo("MWH.401"))
        .body("capacity", equalTo(50))
        .body("stock", equalTo(10));
  }

  @Test
  void rejectsACreationReusingAnExistingBusinessUnitCode() {
    // Given the business unit code of a seeded warehouse
    // When / Then
    given()
        .contentType(ContentType.JSON)
        .body(warehouseBody(SEEDED_CODE, "AMSTERDAM-001", 10, 1))
        .when()
        .post(PATH)
        .then()
        .statusCode(400)
        .body(ERROR_CODE_FIELD, equalTo(400))
        .body("error", containsString(SEEDED_CODE));
  }

  @Test
  void rejectsACreationOnAnUnknownLocation() {
    // Given a location that does not exist
    // When / Then
    given()
        .contentType(ContentType.JSON)
        .body(warehouseBody("MWH.402", "ATLANTIS-001", 10, 1))
        .when()
        .post(PATH)
        .then()
        .statusCode(400)
        .body("error", containsString("ATLANTIS-001"));
  }

  @Test
  void rejectsACreationWhoseStockExceedsItsCapacity() {
    // Given more stock than the warehouse can hold
    // When / Then
    given()
        .contentType(ContentType.JSON)
        .body(warehouseBody("MWH.403", "ZWOLLE-002", 10, 11))
        .when()
        .post(PATH)
        .then()
        .statusCode(400);
  }

  @Test
  void returnsNotFoundForAnUnknownWarehouseId() {
    // Given an id that was never assigned
    // When / Then
    given()
        .when()
        .get(PATH + "/999999")
        .then()
        .statusCode(404)
        .body(ERROR_CODE_FIELD, equalTo(404));
  }

  @Test
  void archivedWarehousesDisappearFromTheListingAndFromTheLookups() {
    // Given a warehouse of its own
    String id =
        given()
            .contentType(ContentType.JSON)
            .body(warehouseBody("MWH.404", "ZWOLLE-002", 20, 5))
            .when()
            .post(PATH)
            .then()
            .statusCode(201)
            .extract()
            .path("id");

    // When it is archived
    given().when().delete(PATH + "/" + id).then().statusCode(204);

    // Then it is gone from the listing, from the lookup, and cannot be archived twice
    given().when().get(PATH).then().statusCode(200).body(not(containsString("MWH.404")));
    given().when().get(PATH + "/" + id).then().statusCode(404);
    given().when().delete(PATH + "/" + id).then().statusCode(404);
  }

  @Test
  void replacesTheActiveWarehouseKeepingItsBusinessUnitCode() {
    // Given an active warehouse
    String previousId =
        given()
            .contentType(ContentType.JSON)
            .body(warehouseBody("MWH.405", "EINDHOVEN-001", 30, 5))
            .when()
            .post(PATH)
            .then()
            .statusCode(201)
            .extract()
            .path("id");

    // When a larger warehouse replaces it
    given()
        .contentType(ContentType.JSON)
        .body(warehouseBody("MWH.405", "EINDHOVEN-001", 40, 5))
        .when()
        .post(PATH + "/MWH.405" + REPLACEMENT)
        .then()
        .statusCode(200)
        .body("capacity", equalTo(40))
        .body("id", not(equalTo(previousId)));

    // Then only the new one is active
    given().when().get(PATH + "/" + previousId).then().statusCode(404);
    given().when().get(PATH).then().statusCode(200).body(containsString("MWH.405"));
  }

  @Test
  void rejectsAReplacementWhoseStockDoesNotMatchTheReplacedWarehouse() {
    // Given an active warehouse holding 5 units
    given()
        .contentType(ContentType.JSON)
        .body(warehouseBody("MWH.406", "HELMOND-001", 20, 5))
        .when()
        .post(PATH)
        .then()
        .statusCode(201);

    // When / Then
    given()
        .contentType(ContentType.JSON)
        .body(warehouseBody("MWH.406", "HELMOND-001", 20, 6))
        .when()
        .post(PATH + "/MWH.406" + REPLACEMENT)
        .then()
        .statusCode(400)
        .body(ERROR_CODE_FIELD, equalTo(400));
  }

  @Test
  void returnsNotFoundWhenReplacingAnUnknownBusinessUnitCode() {
    // Given a business unit code nobody uses
    // When / Then
    given()
        .contentType(ContentType.JSON)
        .body(warehouseBody("MWH.407", "ZWOLLE-002", 10, 1))
        .when()
        .post(PATH + "/MWH.407" + REPLACEMENT)
        .then()
        .statusCode(404)
        .body(ERROR_CODE_FIELD, equalTo(404));
  }

  private static String warehouseBody(
      String businessUnitCode, String location, int capacity, int stock) {
    return "{\"businessUnitCode\":\""
        + businessUnitCode
        + "\",\"location\":\""
        + location
        + "\",\"capacity\":"
        + capacity
        + ",\"stock\":"
        + stock
        + "}";
  }
}
