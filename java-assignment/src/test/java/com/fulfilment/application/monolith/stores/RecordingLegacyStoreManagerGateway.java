package com.fulfilment.application.monolith.stores;

import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the real gateway in tests and records what the legacy system would have received, so a
 * test can assert both the payload and the fact that nothing is sent for a rolled back transaction.
 */
@Mock
@ApplicationScoped
public class RecordingLegacyStoreManagerGateway extends LegacyStoreManagerGateway {

  record StoreSnapshot(Long id, String name, int quantityProductsInStock) {}

  private final List<StoreSnapshot> created = new ArrayList<>();
  private final List<StoreSnapshot> updated = new ArrayList<>();

  @Override
  public void createStoreOnLegacySystem(Store store) {
    created.add(snapshotOf(store));
  }

  @Override
  public void updateStoreOnLegacySystem(Store store) {
    updated.add(snapshotOf(store));
  }

  List<StoreSnapshot> createdStores() {
    return List.copyOf(created);
  }

  List<StoreSnapshot> updatedStores() {
    return List.copyOf(updated);
  }

  void reset() {
    created.clear();
    updated.clear();
  }

  private static StoreSnapshot snapshotOf(Store store) {
    return new StoreSnapshot(store.id, store.name, store.quantityProductsInStock);
  }
}
