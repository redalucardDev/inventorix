package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.ports.ArchiveWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.CreateWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import com.warehouse.api.WarehouseResource;
import com.warehouse.api.beans.Warehouse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@RequestScoped
public class WarehouseResourceImpl implements WarehouseResource {

  private final WarehouseStore warehouseStore;
  private final CreateWarehouseOperation createWarehouse;
  private final ReplaceWarehouseOperation replaceWarehouse;
  private final ArchiveWarehouseOperation archiveWarehouse;

  WarehouseResourceImpl(
      WarehouseStore warehouseStore,
      CreateWarehouseOperation createWarehouse,
      ReplaceWarehouseOperation replaceWarehouse,
      ArchiveWarehouseOperation archiveWarehouse) {
    this.warehouseStore = warehouseStore;
    this.createWarehouse = createWarehouse;
    this.replaceWarehouse = replaceWarehouse;
    this.archiveWarehouse = archiveWarehouse;
  }

  @Override
  public List<Warehouse> listAllWarehousesUnits() {
    return warehouseStore.getAll().stream().map(this::toWarehouseResponse).toList();
  }

  @Override
  public Warehouse createANewWarehouseUnit(@NotNull Warehouse data) {
    com.fulfilment.application.monolith.warehouses.domain.models.Warehouse warehouse =
        toDomainWarehouse(data);

    createWarehouse.create(warehouse);

    return toWarehouseResponse(warehouse);
  }

  @Override
  public Warehouse getAWarehouseUnitByID(String id) {
    return toWarehouseResponse(activeWarehouseById(id));
  }

  @Override
  public void archiveAWarehouseUnitByID(String id) {
    archiveWarehouse.archive(activeWarehouseById(id));
  }

  @Override
  public Warehouse replaceTheCurrentActiveWarehouse(
      String businessUnitCode, @NotNull Warehouse data) {
    com.fulfilment.application.monolith.warehouses.domain.models.Warehouse replacement =
        toDomainWarehouse(data);
    replacement.businessUnitCode = businessUnitCode;

    replaceWarehouse.replace(replacement);

    return toWarehouseResponse(replacement);
  }

  private com.fulfilment.application.monolith.warehouses.domain.models.Warehouse
      activeWarehouseById(String id) {
    return warehouseStore.findById(id).orElseThrow(() -> WarehouseNotFoundException.forId(id));
  }

  private static com.fulfilment.application.monolith.warehouses.domain.models.Warehouse
      toDomainWarehouse(Warehouse data) {
    var warehouse = new com.fulfilment.application.monolith.warehouses.domain.models.Warehouse();
    warehouse.businessUnitCode = data.getBusinessUnitCode();
    warehouse.location = data.getLocation();
    warehouse.capacity = data.getCapacity();
    warehouse.stock = data.getStock();

    return warehouse;
  }

  private Warehouse toWarehouseResponse(
      com.fulfilment.application.monolith.warehouses.domain.models.Warehouse warehouse) {
    var response = new Warehouse();
    response.setId(warehouse.id);
    response.setBusinessUnitCode(warehouse.businessUnitCode);
    response.setLocation(warehouse.location);
    response.setCapacity(warehouse.capacity);
    response.setStock(warehouse.stock);

    return response;
  }
}
