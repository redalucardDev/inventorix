package com.fulfilment.application.monolith.fulfilment.domain.usecases;

import com.fulfilment.application.monolith.fulfilment.domain.exceptions.FulfilmentValidationException;
import com.fulfilment.application.monolith.fulfilment.domain.models.Fulfilment;
import com.fulfilment.application.monolith.fulfilment.domain.ports.ActiveWarehouseLookup;
import com.fulfilment.application.monolith.fulfilment.domain.ports.CreateFulfilmentOperation;
import com.fulfilment.application.monolith.fulfilment.domain.ports.FulfilmentStore;
import com.fulfilment.application.monolith.fulfilment.domain.ports.ProductCatalog;
import com.fulfilment.application.monolith.fulfilment.domain.ports.StoreDirectory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class CreateFulfilmentUseCase implements CreateFulfilmentOperation {

  private static final int MAX_WAREHOUSES_PER_PRODUCT_IN_STORE = 2;
  private static final int MAX_WAREHOUSES_PER_STORE = 3;
  private static final int MAX_PRODUCT_TYPES_PER_WAREHOUSE = 5;

  private static final String MISSING_VALUES =
      "Product, store and warehouse business unit code are mandatory.";
  private static final String UNKNOWN_PRODUCT = "Product with id of %d does not exist.";
  private static final String UNKNOWN_STORE = "Store with id of %d does not exist.";
  private static final String UNKNOWN_WAREHOUSE =
      "No active warehouse found with business unit code '%s'.";
  private static final String ALREADY_ASSOCIATED =
      "Warehouse '%s' already fulfils product %d for store %d.";
  private static final String TOO_MANY_WAREHOUSES_FOR_PRODUCT =
      "Product %d is already fulfilled by %d warehouses in store %d.";
  private static final String TOO_MANY_WAREHOUSES_FOR_STORE =
      "Store %d is already fulfilled by %d warehouses.";
  private static final String TOO_MANY_PRODUCTS_IN_WAREHOUSE =
      "Warehouse '%s' already stores %d product types.";

  private final FulfilmentStore fulfilments;
  private final ProductCatalog products;
  private final StoreDirectory stores;
  private final ActiveWarehouseLookup warehouses;

  CreateFulfilmentUseCase(
          final FulfilmentStore fulfilments,
          final ProductCatalog products,
          final StoreDirectory stores,
          final ActiveWarehouseLookup warehouses) {
    this.fulfilments = fulfilments;
    this.products = products;
    this.stores = stores;
    this.warehouses = warehouses;
  }

  @Override
  @Transactional
  public Fulfilment create(final Fulfilment fulfilment) {
    requireCompleteAssociation(fulfilment);
    requireKnownReferences(fulfilment);
    requireNewAssociation(fulfilment);
    requireRoomForTheProductInTheStore(fulfilment);
    requireRoomInTheStore(fulfilment);
    requireRoomInTheWarehouse(fulfilment);

    return fulfilments.create(fulfilment);
  }

  private static void requireCompleteAssociation(final Fulfilment fulfilment) {
    if (fulfilment.productId() == null || fulfilment.storeId() == null) {
      throw new FulfilmentValidationException(MISSING_VALUES);
    }
    if (isBlank(fulfilment.warehouseBusinessUnitCode())) {
      throw new FulfilmentValidationException(MISSING_VALUES);
    }
  }

  private void requireKnownReferences(final Fulfilment fulfilment) {
    if (!products.contains(fulfilment.productId())) {
      throw new FulfilmentValidationException(UNKNOWN_PRODUCT.formatted(fulfilment.productId()));
    }
    if (!stores.contains(fulfilment.storeId())) {
      throw new FulfilmentValidationException(UNKNOWN_STORE.formatted(fulfilment.storeId()));
    }
    if (!warehouses.isActive(fulfilment.warehouseBusinessUnitCode())) {
      throw new FulfilmentValidationException(
          UNKNOWN_WAREHOUSE.formatted(fulfilment.warehouseBusinessUnitCode()));
    }
  }

  private void requireNewAssociation(final Fulfilment fulfilment) {
    boolean known =
        warehousesOf(inTheSameStore(fulfilment)).contains(fulfilment.warehouseBusinessUnitCode());

    if (known) {
      throw new FulfilmentValidationException(
          ALREADY_ASSOCIATED.formatted(
              fulfilment.warehouseBusinessUnitCode(),
              fulfilment.productId(),
              fulfilment.storeId()));
    }
  }

  /**
   * The association is known to be new at this point, so every warehouse already serving that
   * product in that store is a distinct one and no membership test is needed.
   */
  private void requireRoomForTheProductInTheStore(final Fulfilment fulfilment) {
    final List<String> used = warehousesOf(inTheSameStore(fulfilment));

    if (used.size() >= MAX_WAREHOUSES_PER_PRODUCT_IN_STORE) {
      throw new FulfilmentValidationException(
          TOO_MANY_WAREHOUSES_FOR_PRODUCT.formatted(
              fulfilment.productId(), used.size(), fulfilment.storeId()));
    }
  }

  private void requireRoomInTheStore(final Fulfilment fulfilment) {
    final List<String> used = warehousesOf(fulfilments.ofStore(fulfilment.storeId()));

    if (isNewTo(used, fulfilment.warehouseBusinessUnitCode(), MAX_WAREHOUSES_PER_STORE)) {
      throw new FulfilmentValidationException(
          TOO_MANY_WAREHOUSES_FOR_STORE.formatted(fulfilment.storeId(), used.size()));
    }
  }

  private void requireRoomInTheWarehouse(final Fulfilment fulfilment) {
    final List<Long> stored =
        fulfilments.ofWarehouse(fulfilment.warehouseBusinessUnitCode()).stream()
            .map(Fulfilment::productId)
            .distinct()
            .toList();

    if (isNewTo(stored, fulfilment.productId(), MAX_PRODUCT_TYPES_PER_WAREHOUSE)) {
      throw new FulfilmentValidationException(
          TOO_MANY_PRODUCTS_IN_WAREHOUSE.formatted(
              fulfilment.warehouseBusinessUnitCode(), stored.size()));
    }
  }

  private List<Fulfilment> inTheSameStore(final Fulfilment fulfilment) {
    return fulfilments.ofProductInStore(fulfilment.productId(), fulfilment.storeId());
  }

  private static <T> boolean isNewTo(final List<T> known, final T candidate, final int limit) {
    return !known.contains(candidate) && known.size() >= limit;
  }

  private static List<String> warehousesOf(final List<Fulfilment> fulfilments) {
    return fulfilments.stream()
        .map(Fulfilment::warehouseBusinessUnitCode)
        .distinct()
        .toList();
  }

  private static boolean isBlank(final String value) {
    return value == null || value.isBlank();
  }
}
