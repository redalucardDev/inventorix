package com.fulfilment.application.monolith.fulfilment.domain.usecases;

import com.fulfilment.application.monolith.fulfilment.domain.ports.ProductCatalog;
import java.util.Set;

/** Knows only the products a test declares. */
final class StubProductCatalog implements ProductCatalog {

  private final Set<Long> known;

  StubProductCatalog(Long... known) {
    this.known = Set.of(known);
  }

  @Override
  public boolean contains(Long productId) {
    return known.contains(productId);
  }
}
