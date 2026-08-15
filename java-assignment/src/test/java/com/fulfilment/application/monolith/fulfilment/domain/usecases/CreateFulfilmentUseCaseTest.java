package com.fulfilment.application.monolith.fulfilment.domain.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fulfilment.application.monolith.fulfilment.domain.exceptions.FulfilmentValidationException;
import com.fulfilment.application.monolith.fulfilment.domain.models.Fulfilment;
import org.junit.jupiter.api.Test;

class CreateFulfilmentUseCaseTest {

  private static final Long PRODUCT = 1L;
  private static final Long OTHER_PRODUCT = 2L;
  private static final Long STORE = 10L;
  private static final Long OTHER_STORE = 11L;
  private static final String WAREHOUSE_A = "MWH.001";
  private static final String WAREHOUSE_B = "MWH.002";
  private static final String WAREHOUSE_C = "MWH.003";
  private static final String WAREHOUSE_D = "MWH.004";
  private static final Long[] KNOWN_PRODUCTS = {1L, 2L, 3L, 4L, 5L, 6L};

  @Test
  void associatesTheWarehouseWhenEveryRuleIsSatisfied() {
    // Given an empty register
    var fulfilments = new InMemoryFulfilmentStore();

    // When
    Fulfilment stored = useCaseOn(fulfilments).create(Fulfilment.of(PRODUCT, STORE, WAREHOUSE_A));

    // Then
    assertThat(stored.id()).isNotNull();
    assertThat(stored.productId()).isEqualTo(PRODUCT);
    assertThat(fulfilments.stored()).containsExactly(stored);
  }

  @Test
  void rejectsAnAssociationThatAlreadyExists() {
    // Given the same triple already registered
    var fulfilments = new InMemoryFulfilmentStore(Fulfilment.of(PRODUCT, STORE, WAREHOUSE_A));

    // When / Then
    assertThatExceptionOfType(FulfilmentValidationException.class)
        .isThrownBy(() -> useCaseOn(fulfilments).create(Fulfilment.of(PRODUCT, STORE, WAREHOUSE_A)))
        .withMessageContaining(WAREHOUSE_A);
    assertThat(fulfilments.stored()).hasSize(1);
  }

  @Test
  void rejectsAThirdWarehouseForTheSameProductAndStore() {
    // Given a product already fulfilled by two warehouses in that store
    var fulfilments =
        new InMemoryFulfilmentStore(
            Fulfilment.of(PRODUCT, STORE, WAREHOUSE_A), Fulfilment.of(PRODUCT, STORE, WAREHOUSE_B));

    // When / Then
    assertThatExceptionOfType(FulfilmentValidationException.class)
        .isThrownBy(
            () -> useCaseOn(fulfilments).create(Fulfilment.of(PRODUCT, STORE, WAREHOUSE_C)));
    assertThat(fulfilments.stored()).hasSize(2);
  }

  @Test
  void rejectsAFourthWarehouseForTheSameStore() {
    // Given a store already fulfilled by three distinct warehouses
    var fulfilments =
        new InMemoryFulfilmentStore(
            Fulfilment.of(1L, STORE, WAREHOUSE_A),
            Fulfilment.of(2L, STORE, WAREHOUSE_B),
            Fulfilment.of(3L, STORE, WAREHOUSE_C));

    // When / Then even a product holding a single warehouse cannot bring a fourth one
    assertThatExceptionOfType(FulfilmentValidationException.class)
        .isThrownBy(() -> useCaseOn(fulfilments).create(Fulfilment.of(1L, STORE, WAREHOUSE_D)))
        .withMessageContaining(String.valueOf(STORE));
    assertThat(fulfilments.stored()).hasSize(3);
  }

  @Test
  void acceptsAWarehouseTheStoreAlreadyReliesOn() {
    // Given the same saturated store
    var fulfilments =
        new InMemoryFulfilmentStore(
            Fulfilment.of(1L, STORE, WAREHOUSE_A),
            Fulfilment.of(2L, STORE, WAREHOUSE_B),
            Fulfilment.of(3L, STORE, WAREHOUSE_C));

    // When a product takes a second warehouse that the store already uses
    useCaseOn(fulfilments).create(Fulfilment.of(1L, STORE, WAREHOUSE_B));

    // Then the store still counts three distinct warehouses
    assertThat(fulfilments.stored()).hasSize(4);
  }

  @Test
  void rejectsASixthProductTypeInTheSameWarehouse() {
    // Given a warehouse already storing five product types
    var fulfilments =
        new InMemoryFulfilmentStore(
            Fulfilment.of(1L, STORE, WAREHOUSE_A),
            Fulfilment.of(2L, STORE, WAREHOUSE_A),
            Fulfilment.of(3L, STORE, WAREHOUSE_A),
            Fulfilment.of(4L, STORE, WAREHOUSE_A),
            Fulfilment.of(5L, STORE, WAREHOUSE_A));

    // When / Then
    assertThatExceptionOfType(FulfilmentValidationException.class)
        .isThrownBy(
            () -> useCaseOn(fulfilments).create(Fulfilment.of(6L, OTHER_STORE, WAREHOUSE_A)))
        .withMessageContaining(WAREHOUSE_A);
    assertThat(fulfilments.stored()).hasSize(5);
  }

  @Test
  void acceptsAProductTheWarehouseAlreadyStores() {
    // Given the same saturated warehouse
    var fulfilments =
        new InMemoryFulfilmentStore(
            Fulfilment.of(1L, STORE, WAREHOUSE_A),
            Fulfilment.of(2L, STORE, WAREHOUSE_A),
            Fulfilment.of(3L, STORE, WAREHOUSE_A),
            Fulfilment.of(4L, STORE, WAREHOUSE_A),
            Fulfilment.of(5L, STORE, WAREHOUSE_A));

    // When one of those products is fulfilled for another store
    useCaseOn(fulfilments).create(Fulfilment.of(1L, OTHER_STORE, WAREHOUSE_A));

    // Then the warehouse still stores five product types
    assertThat(fulfilments.stored()).hasSize(6);
  }

  @Test
  void rejectsAnUnknownProduct() {
    // Given a product the catalog does not know
    var fulfilments = new InMemoryFulfilmentStore();

    // When / Then
    assertThatExceptionOfType(FulfilmentValidationException.class)
        .isThrownBy(() -> useCaseOn(fulfilments).create(Fulfilment.of(999L, STORE, WAREHOUSE_A)))
        .withMessageContaining("999");
    assertThat(fulfilments.stored()).isEmpty();
  }

  @Test
  void rejectsAnUnknownStore() {
    // Given a store the directory does not know
    var fulfilments = new InMemoryFulfilmentStore();

    // When / Then
    assertThatExceptionOfType(FulfilmentValidationException.class)
        .isThrownBy(() -> useCaseOn(fulfilments).create(Fulfilment.of(PRODUCT, 999L, WAREHOUSE_A)))
        .withMessageContaining("999");
    assertThat(fulfilments.stored()).isEmpty();
  }

  @Test
  void rejectsAWarehouseThatIsNotActive() {
    // Given a business unit code that no longer designates an active warehouse
    var fulfilments = new InMemoryFulfilmentStore();

    // When / Then
    assertThatExceptionOfType(FulfilmentValidationException.class)
        .isThrownBy(() -> useCaseOn(fulfilments).create(Fulfilment.of(PRODUCT, STORE, "MWH.999")))
        .withMessageContaining("MWH.999");
    assertThat(fulfilments.stored()).isEmpty();
  }

  @Test
  void rejectsAnIncompleteAssociation() {
    // Given a request without a warehouse
    var fulfilments = new InMemoryFulfilmentStore();

    // When / Then
    assertThatExceptionOfType(FulfilmentValidationException.class)
        .isThrownBy(() -> useCaseOn(fulfilments).create(Fulfilment.of(PRODUCT, STORE, null)));
    assertThatExceptionOfType(FulfilmentValidationException.class)
        .isThrownBy(() -> useCaseOn(fulfilments).create(Fulfilment.of(null, STORE, WAREHOUSE_A)));
    assertThat(fulfilments.stored()).isEmpty();
  }

  private static CreateFulfilmentUseCase useCaseOn(InMemoryFulfilmentStore fulfilments) {
    return new CreateFulfilmentUseCase(
        fulfilments,
        new StubProductCatalog(KNOWN_PRODUCTS),
        new StubStoreDirectory(STORE, OTHER_STORE),
        new StubActiveWarehouseLookup(WAREHOUSE_A, WAREHOUSE_B, WAREHOUSE_C, WAREHOUSE_D));
  }
}
