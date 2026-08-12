package com.fulfilment.application.monolith.stores;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class StoreEndpointTest {

  private static final String PATH = "store";
  private static final String ALREADY_SEEDED_NAME = "TONSTAD";
  private static final String CREATED_NAME = "SYNC-CREATE";
  private static final String UPDATE_SOURCE_NAME = "SYNC-UPDATE";
  private static final String UPDATED_NAME = "SYNC-UPDATE-RENAMED";
  private static final int UPDATED_QUANTITY = 9;

  @Inject RecordingLegacyStoreManagerGateway legacyGateway;

  @BeforeEach
  void forgetPreviousNotifications() {
    legacyGateway.reset();
  }

  @Test
  void notifiesTheLegacySystemOnceTheNewStoreIsCommitted() {
    // Given a store that does not exist yet
    // When
    given()
        .contentType(ContentType.JSON)
        .body(storeBody(CREATED_NAME, 7))
        .when()
        .post(PATH)
        .then()
        .statusCode(201);

    // Then the legacy system received the persisted entity, id included
    assertThat(legacyGateway.createdStores())
        .singleElement()
        .satisfies(
            snapshot -> {
              assertThat(snapshot.name()).isEqualTo(CREATED_NAME);
              assertThat(snapshot.quantityProductsInStock()).isEqualTo(7);
              assertThat(snapshot.id()).isNotNull();
            });
  }

  @Test
  void notifiesTheLegacySystemWithTheStoredStateWhenAStoreIsUpdated() {
    // Given a store already committed
    int id =
        given()
            .contentType(ContentType.JSON)
            .body(storeBody(UPDATE_SOURCE_NAME, 1))
            .when()
            .post(PATH)
            .then()
            .statusCode(201)
            .extract()
            .path("id");
    legacyGateway.reset();

    // When the store is renamed
    given()
        .contentType(ContentType.JSON)
        .body(storeBody(UPDATED_NAME, UPDATED_QUANTITY))
        .when()
        .put(PATH + "/" + id)
        .then()
        .statusCode(200);

    // Then the legacy system received the managed entity, identified, not the raw payload
    assertThat(legacyGateway.updatedStores())
        .singleElement()
        .satisfies(
            snapshot -> {
              assertThat(snapshot.id()).isEqualTo(id);
              assertThat(snapshot.name()).isEqualTo(UPDATED_NAME);
              assertThat(snapshot.quantityProductsInStock()).isEqualTo(UPDATED_QUANTITY);
            });
  }

  @Test
  void doesNotNotifyTheLegacySystemWhenTheTransactionRollsBack() {
    // Given a name already taken, which breaks the unique constraint at commit time
    // When
    given()
        .contentType(ContentType.JSON)
        .body(storeBody(ALREADY_SEEDED_NAME, 1))
        .when()
        .post(PATH)
        .then()
        .statusCode(greaterThan(399));

    // Then nothing was propagated, since nothing was committed
    assertThat(legacyGateway.createdStores()).isEmpty();
  }

  private static String storeBody(String name, int quantityProductsInStock) {
    return "{\"name\":\"" + name + "\",\"quantityProductsInStock\":" + quantityProductsInStock + "}";
  }
}
