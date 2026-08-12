package com.fulfilment.application.monolith.stores;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;

/**
 * Propagates store changes to the legacy system only after the transaction has committed, so the
 * downstream system never receives data that ends up rolled back.
 */
@ApplicationScoped
class LegacyStoreEventsObserver {

  private final LegacyStoreManagerGateway legacyStoreManagerGateway;

  LegacyStoreEventsObserver(LegacyStoreManagerGateway legacyStoreManagerGateway) {
    this.legacyStoreManagerGateway = legacyStoreManagerGateway;
  }

  void onStoreCreated(@Observes(during = TransactionPhase.AFTER_SUCCESS) StoreCreatedEvent event) {
    legacyStoreManagerGateway.createStoreOnLegacySystem(
        asStore(event.id(), event.name(), event.quantityProductsInStock()));
  }

  void onStoreUpdated(@Observes(during = TransactionPhase.AFTER_SUCCESS) StoreUpdatedEvent event) {
    legacyStoreManagerGateway.updateStoreOnLegacySystem(
        asStore(event.id(), event.name(), event.quantityProductsInStock()));
  }

  private static Store asStore(Long id, String name, int quantityProductsInStock) {
    var store = new Store(name);
    store.id = id;
    store.quantityProductsInStock = quantityProductsInStock;
    return store;
  }
}
