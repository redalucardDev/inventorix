package com.fulfilment.application.monolith.warehouses.adapters.database;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class WarehouseRepository implements WarehouseStore, PanacheRepository<DbWarehouse> {

  private static final String ACTIVE = "archivedAt is null";
  private static final String ACTIVE_WITH_CODE = "businessUnitCode = ?1 and archivedAt is null";

  @Override
  public List<Warehouse> getAll() {
    return this.list(ACTIVE).stream().map(DbWarehouse::toWarehouse).toList();
  }

  @Override
  public void create(Warehouse warehouse) {
    DbWarehouse entity = DbWarehouse.from(warehouse);
    this.persist(entity);
    warehouse.id = String.valueOf(entity.id);
  }

  @Override
  public void update(Warehouse warehouse) {
    DbWarehouse entity = managedEntityOf(warehouse);
    entity.businessUnitCode = warehouse.businessUnitCode;
    entity.location = warehouse.location;
    entity.capacity = warehouse.capacity;
    entity.stock = warehouse.stock;
    entity.createdAt = warehouse.createdAt;
    entity.archivedAt = warehouse.archivedAt;
    this.persist(entity);
  }

  @Override
  public void remove(Warehouse warehouse) {
    this.delete(managedEntityOf(warehouse));
  }

  @Override
  public Optional<Warehouse> findByBusinessUnitCode(String buCode) {
    return this.find(ACTIVE_WITH_CODE, buCode).firstResultOptional().map(DbWarehouse::toWarehouse);
  }

  @Override
  public Optional<Warehouse> findById(String id) {
    return numericId(id)
        .flatMap(this::findByIdOptional)
        .filter(entity -> entity.archivedAt == null)
        .map(DbWarehouse::toWarehouse);
  }

  /** Resolves the row backing a warehouse the domain already read, archived rows included. */
  private DbWarehouse managedEntityOf(Warehouse warehouse) {
    if (warehouse.id == null) {
      return this.find(ACTIVE_WITH_CODE, warehouse.businessUnitCode)
          .firstResultOptional()
          .orElseThrow(
              () -> WarehouseNotFoundException.forBusinessUnitCode(warehouse.businessUnitCode));
    }
    return numericId(warehouse.id)
        .flatMap(this::findByIdOptional)
        .orElseThrow(() -> WarehouseNotFoundException.forId(warehouse.id));
  }

  private static Optional<Long> numericId(String id) {
    try {
      return Optional.of(Long.valueOf(id));
    } catch (NumberFormatException notAnIdentifier) {
      return Optional.empty();
    }
  }
}
