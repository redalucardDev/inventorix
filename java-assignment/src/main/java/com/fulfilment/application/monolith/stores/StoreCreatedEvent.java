package com.fulfilment.application.monolith.stores;

/** Snapshot of a store that has just been created, fired once the transaction commits. */
record StoreCreatedEvent(Long id, String name, int quantityProductsInStock) {

  static StoreCreatedEvent of(Store store) {
    return new StoreCreatedEvent(store.id, store.name, store.quantityProductsInStock);
  }
}
