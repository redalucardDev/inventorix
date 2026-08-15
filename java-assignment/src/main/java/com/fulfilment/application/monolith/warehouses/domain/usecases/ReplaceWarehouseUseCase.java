package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;

@ApplicationScoped
public class ReplaceWarehouseUseCase implements ReplaceWarehouseOperation {

  private final WarehouseStore warehouseStore;
  private final WarehouseValidations validations;

  ReplaceWarehouseUseCase(WarehouseStore warehouseStore, WarehouseValidations validations) {
    this.warehouseStore = warehouseStore;
    this.validations = validations;
  }

  @Override
  @Transactional
  public void replace(Warehouse newWarehouse) {
    Warehouse replaced =
        warehouseStore
            .findByBusinessUnitCode(newWarehouse.businessUnitCode)
            .orElseThrow(
                () -> WarehouseNotFoundException.forBusinessUnitCode(
                    newWarehouse.businessUnitCode));

    validations.validateReplacement(newWarehouse, replaced);

    LocalDateTime replacedAt = LocalDateTime.now();
    replaced.archivedAt = replacedAt;
    warehouseStore.update(replaced);

    newWarehouse.createdAt = replacedAt;
    newWarehouse.archivedAt = null;
    warehouseStore.create(newWarehouse);
  }
}
