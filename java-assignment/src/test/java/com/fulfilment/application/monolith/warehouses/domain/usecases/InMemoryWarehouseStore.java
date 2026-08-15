package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lightweight replacement for the JPA backed store: keeps warehouses in memory, hides the archived
 * ones from the lookups exactly like the repository does, and records what was written so a test
 * can assert on the persisted outcome.
 */
final class InMemoryWarehouseStore implements WarehouseStore {

  private final List<Warehouse> warehouses = new ArrayList<>();
  private final List<Warehouse> created = new ArrayList<>();
  private final List<Warehouse> updated = new ArrayList<>();

  InMemoryWarehouseStore(Warehouse... existing) {
    warehouses.addAll(List.of(existing));
  }

  @Override
  public List<Warehouse> getAll() {
    return warehouses.stream().filter(warehouse -> warehouse.archivedAt == null).toList();
  }

  @Override
  public void create(Warehouse warehouse) {
    warehouses.add(warehouse);
    created.add(warehouse);
  }

  @Override
  public void update(Warehouse warehouse) {
    updated.add(warehouse);
  }

  @Override
  public void remove(Warehouse warehouse) {
    warehouses.remove(warehouse);
  }

  @Override
  public Optional<Warehouse> findByBusinessUnitCode(String buCode) {
    return getAll().stream()
        .filter(warehouse -> buCode.equals(warehouse.businessUnitCode))
        .findFirst();
  }

  @Override
  public Optional<Warehouse> findById(String id) {
    return getAll().stream().filter(warehouse -> id.equals(warehouse.id)).findFirst();
  }

  List<Warehouse> createdWarehouses() {
    return List.copyOf(created);
  }

  List<Warehouse> updatedWarehouses() {
    return List.copyOf(updated);
  }
}
