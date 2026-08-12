package com.fulfilment.application.monolith.stores;

/** Snapshot of a store that has just been updated, fired once the transaction commits. */
record StoreUpdatedEvent(Long id, String name, int quantityProductsInStock) {

  static StoreUpdatedEvent of(Store store) {
    return new StoreUpdatedEvent(store.id, store.name, store.quantityProductsInStock);
  }
}
