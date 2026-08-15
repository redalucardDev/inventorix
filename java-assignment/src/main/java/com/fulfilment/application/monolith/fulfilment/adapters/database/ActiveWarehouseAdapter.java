package com.fulfilment.application.monolith.fulfilment.adapters.database;

import com.fulfilment.application.monolith.fulfilment.domain.ports.ActiveWarehouseLookup;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Bridges the fulfilment domain to the warehouse module through its port, which only ever returns
 * active warehouses — so an archived one can never become a fulfilment unit.
 */
@ApplicationScoped
public class ActiveWarehouseAdapter implements ActiveWarehouseLookup {

  private final WarehouseStore warehouses;

  ActiveWarehouseAdapter(WarehouseStore warehouses) {
    this.warehouses = warehouses;
  }

  @Override
  public boolean isActive(String businessUnitCode) {
    return warehouses.findByBusinessUnitCode(businessUnitCode).isPresent();
  }
}
