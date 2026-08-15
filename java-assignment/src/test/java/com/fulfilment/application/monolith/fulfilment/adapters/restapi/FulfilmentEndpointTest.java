package com.fulfilment.application.monolith.fulfilment.adapters.restapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/**
 * Every test owns the products and stores it associates, so the three limits are asserted on data
 * no other test can move. The seeded warehouses are shared, which is safe as long as each of them
 * stays well below the five product types of the third rule.
 */
@QuarkusTest
class FulfilmentEndpointTest {

  private static final String PATH = "fulfilment";
  private static final String PRODUCT_PATH = "product";
  private static final String STORE_PATH = "store";
  private static final String WAREHOUSE_PATH = "warehouse";
  private static final String SEEDED_WAREHOUSE_A = "MWH.001";
  private static final String SEEDED_WAREHOUSE_B = "MWH.012";
  private static final String SEEDED_WAREHOUSE_C = "MWH.023";
  private static final String SPARE_LOCATION = "AMSTERDAM-001";
  private static final String SPARE_LOCATION_2 = "AMSTERDAM-002";

  @Test
  void associatesAWarehouseToAProductForAStore() {
    // Given a product and a store of its own
    final long productId = createProduct("FUL-T1-P1");
    final long storeId = createStore("FUL-T1-S1");

    // When
    given()
        .contentType(ContentType.JSON)
        .body(fulfilmentBody(productId, storeId, SEEDED_WAREHOUSE_B))
        .when()
        .post(PATH)
        .then()
        .statusCode(201)
        .body("id", notNullValue())
        .body("productId", equalTo((int) productId))
        .body("storeId", equalTo((int) storeId))
        .body("warehouseBusinessUnitCode", equalTo(SEEDED_WAREHOUSE_B));

    // Then it is listed under both filters
    given()
        .queryParam("productId", productId)
        .when()
        .get(PATH)
        .then()
        .statusCode(200)
        .body("size()", equalTo(1))
        .body("[0].warehouseBusinessUnitCode", equalTo(SEEDED_WAREHOUSE_B));

    given()
        .queryParam("storeId", storeId)
        .when()
        .get(PATH)
        .then()
        .statusCode(200)
        .body("size()", equalTo(1));
  }

  @Test
  void rejectsAnAssociationThatAlreadyExists() {
    // Given an existing association
    final long productId = createProduct("FUL-T2-P1");
    final long storeId = createStore("FUL-T2-S1");
    associate(productId, storeId, SEEDED_WAREHOUSE_B, 201);

    // When / Then
    associate(productId, storeId, SEEDED_WAREHOUSE_B, 400);
  }

  @Test
  void rejectsAThirdWarehouseForTheSameProductAndStore() {
    // Given a product already fulfilled by two warehouses in that store
    final long productId = createProduct("FUL-T3-P1");
    final long storeId = createStore("FUL-T3-S1");
    associate(productId, storeId, SEEDED_WAREHOUSE_A, 201);
    associate(productId, storeId, SEEDED_WAREHOUSE_B, 201);

    // When / Then
    associate(productId, storeId, SEEDED_WAREHOUSE_C, 400);
  }

  @Test
  void rejectsAFourthWarehouseForTheSameStore() {
    // Given a store already fulfilled by three different warehouses
    final long storeId = createStore("FUL-T4-S1");
    final long firstProductId = createProduct("FUL-T4-P1");
    associate(firstProductId, storeId, SEEDED_WAREHOUSE_A, 201);
    associate(createProduct("FUL-T4-P2"), storeId, SEEDED_WAREHOUSE_B, 201);
    associate(createProduct("FUL-T4-P3"), storeId, SEEDED_WAREHOUSE_C, 201);
    createWarehouse("MWH.501", SPARE_LOCATION);

    // When / Then a fourth warehouse is refused, even for a product with a single one
    associate(firstProductId, storeId, "MWH.501", 400);
  }

  @Test
  void rejectsASixthProductTypeInTheSameWarehouse() {
    // Given a warehouse of its own already storing five product types
    createWarehouse("MWH.502", SPARE_LOCATION);
    final long storeId = createStore("FUL-T5-S1");
    for (int product = 1; product <= 5; product++) {
      associate(createProduct("FUL-T5-P" + product), storeId, "MWH.502", 201);
    }

    // When / Then
    associate(createProduct("FUL-T5-P6"), storeId, "MWH.502", 400);
  }

  @Test
  void reusingAWarehouseDoesNotCountTwiceAgainstTheStoreLimit() {
    // Given a store fulfilled by its three warehouses, one product each
    final long storeId = createStore("FUL-T6-S1");
    createWarehouse("MWH.503", SPARE_LOCATION_2);
    createWarehouse("MWH.504", SPARE_LOCATION_2);
    createWarehouse("MWH.505", SPARE_LOCATION_2);
    final long firstProductId = createProduct("FUL-T6-P1");
    associate(firstProductId, storeId, "MWH.503", 201);
    associate(createProduct("FUL-T6-P2"), storeId, "MWH.504", 201);
    associate(createProduct("FUL-T6-P3"), storeId, "MWH.505", 201);

    // When a product takes a second warehouse the store already uses
    // Then the store still counts three distinct warehouses
    associate(firstProductId, storeId, "MWH.504", 201);
  }

  @Test
  void rejectsAnAssociationPointingAtSomethingThatDoesNotExist() {
    // Given valid references on one side only
    final long productId = createProduct("FUL-T7-P1");
    final long storeId = createStore("FUL-T7-S1");

    // When / Then
    associate(999999L, storeId, SEEDED_WAREHOUSE_B, 400);
    associate(productId, 999999L, SEEDED_WAREHOUSE_B, 400);
    associate(productId, storeId, "MWH.999", 400);
  }

  @Test
  void removesAnAssociation() {
    // Given an existing association
    final long productId = createProduct("FUL-T8-P1");
    final long storeId = createStore("FUL-T8-S1");
    final int id =
        given()
            .contentType(ContentType.JSON)
            .body(fulfilmentBody(productId, storeId, SEEDED_WAREHOUSE_C))
            .when()
            .post(PATH)
            .then()
            .statusCode(201)
            .extract()
            .path("id");

    // When
    given().when().delete(PATH + "/" + id).then().statusCode(204);

    // Then it is gone and cannot be removed twice
    given()
        .queryParam("productId", productId)
        .when()
        .get(PATH)
        .then()
        .statusCode(200)
        .body("size()", equalTo(0));
    given().when().delete(PATH + "/" + id).then().statusCode(404);
  }

  @Test
  void refusesAWarehouseThatHasBeenArchived() {
    // Given a warehouse archived right after its creation
    createWarehouse("MWH.506", SPARE_LOCATION);
    final String warehouseId =
        given()
            .when()
            .get(WAREHOUSE_PATH)
            .then()
            .statusCode(200)
            .extract()
            .path("find { it.businessUnitCode == 'MWH.506' }.id");
    given().when().delete(WAREHOUSE_PATH + "/" + warehouseId).then().statusCode(204);

    // When / Then it is no longer a fulfilment unit
    associate(createProduct("FUL-T9-P1"), createStore("FUL-T9-S1"), "MWH.506", 400);
  }

  @Test
  void deletingAProductTakesItsAssociationsWithIt() {
    // Given a product fulfilled by a warehouse
    final long productId = createProduct("FUL-T10-P1");
    final long storeId = createStore("FUL-T10-S1");
    associate(productId, storeId, SEEDED_WAREHOUSE_A, 201);

    // When the product is deleted
    given().when().delete(PRODUCT_PATH + "/" + productId).then().statusCode(204);

    // Then the association is gone with it, and the store is free again
    given()
        .queryParam("storeId", storeId)
        .when()
        .get(PATH)
        .then()
        .statusCode(200)
        .body("size()", equalTo(0));
  }

  @Test
  void listsEveryAssociationWhenNoFilterIsGiven() {
    // Given an association of its own
    final long productId = createProduct("FUL-T11-P1");
    final long storeId = createStore("FUL-T11-S1");
    final long id = createdId(PATH, fulfilmentBody(productId, storeId, SEEDED_WAREHOUSE_C));

    // When the whole register is asked for
    // Then it is part of the answer
    given().when().get(PATH).then().statusCode(200).body("id", hasItem((int) id));
  }

  @Test
  void combinesTheProductAndTheStoreFilters() {
    // Given one product fulfilled by the same warehouse in two stores
    final long productId = createProduct("FUL-T12-P1");
    final long storeId = createStore("FUL-T12-S1");
    final long otherStoreId = createStore("FUL-T12-S2");
    associate(productId, storeId, SEEDED_WAREHOUSE_C, 201);
    associate(productId, otherStoreId, SEEDED_WAREHOUSE_C, 201);

    // When both filters are combined
    // Then only the association of that store is returned
    given()
        .queryParam("productId", productId)
        .queryParam("storeId", storeId)
        .when()
        .get(PATH)
        .then()
        .statusCode(200)
        .body("size()", equalTo(1))
        .body("[0].storeId", equalTo((int) storeId));
  }

  @Test
  void rejectsAPayloadThatCarriesNoAssociationAtAll() {
    // Given a body that deserialises to nothing
    // When / Then
    given()
        .contentType(ContentType.JSON)
        .body("null")
        .when()
        .post(PATH)
        .then()
        .statusCode(400);
  }

  private static void associate(
          final long productId, final long storeId, final String warehouse, final int expectedStatus) {
    given()
        .contentType(ContentType.JSON)
        .body(fulfilmentBody(productId, storeId, warehouse))
        .when()
        .post(PATH)
        .then()
        .statusCode(expectedStatus);
  }

  private static long createProduct(final String name) {
    return createdId(PRODUCT_PATH, "{\"name\":\"" + name + "\"}");
  }

  private static long createStore(final String name) {
    return createdId(STORE_PATH, "{\"name\":\"" + name + "\",\"quantityProductsInStock\":1}");
  }

  private static void createWarehouse(final String businessUnitCode, final String location) {
    given()
        .contentType(ContentType.JSON)
        .body(
            "{\"businessUnitCode\":\""
                + businessUnitCode
                + "\",\"location\":\""
                + location
                + "\",\"capacity\":10,\"stock\":1}")
        .when()
        .post(WAREHOUSE_PATH)
        .then()
        .statusCode(201);
  }

  private static long createdId(final String path, final String body) {
    final Integer id =
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post(path)
            .then()
            .statusCode(201)
            .extract()
            .path("id");

    return id.longValue();
  }

  private static String fulfilmentBody(final long productId, final long storeId, final String warehouse) {
    return "{\"productId\":"
        + productId
        + ",\"storeId\":"
        + storeId
        + ",\"warehouseBusinessUnitCode\":\""
        + warehouse
        + "\"}";
  }
}
