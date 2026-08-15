package com.fulfilment.application.monolith.fulfilment.domain.usecases;

import com.fulfilment.application.monolith.fulfilment.domain.ports.ActiveWarehouseLookup;
import java.util.Set;

/** Treats as active only the business unit codes a test declares; everything else is archived. */
final class StubActiveWarehouseLookup implements ActiveWarehouseLookup {

  private final Set<String> active;

  StubActiveWarehouseLookup(String... active) {
    this.active = Set.of(active);
  }

  @Override
  public boolean isActive(String businessUnitCode) {
    return active.contains(businessUnitCode);
  }
}
