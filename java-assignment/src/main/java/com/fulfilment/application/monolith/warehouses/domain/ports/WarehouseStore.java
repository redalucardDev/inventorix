package com.fulfilment.application.monolith.warehouses.domain.ports;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import java.util.List;
import java.util.Optional;

/** Persistence port. Lookups and listings only expose active (non archived) warehouses. */
public interface WarehouseStore {

  List<Warehouse> getAll();

  void create(Warehouse warehouse);

  void update(Warehouse warehouse);

  void remove(Warehouse warehouse);

  Optional<Warehouse> findByBusinessUnitCode(String buCode);

  Optional<Warehouse> findById(String id);
}
