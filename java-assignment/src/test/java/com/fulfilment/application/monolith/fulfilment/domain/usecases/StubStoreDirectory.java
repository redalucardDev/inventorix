package com.fulfilment.application.monolith.fulfilment.domain.usecases;

import com.fulfilment.application.monolith.fulfilment.domain.ports.StoreDirectory;
import java.util.Set;

/** Knows only the stores a test declares. */
final class StubStoreDirectory implements StoreDirectory {

  private final Set<Long> known;

  StubStoreDirectory(Long... known) {
    this.known = Set.of(known);
  }

  @Override
  public boolean contains(Long storeId) {
    return known.contains(storeId);
  }
}
