package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/** Business rules shared by the creation and the replacement of a warehouse. */
@ApplicationScoped
class WarehouseValidations {

  private static final String MISSING_VALUES =
      "Business unit code, location, capacity and stock are mandatory.";
  private static final String NEGATIVE_VALUES = "Capacity and stock must not be negative.";
  private static final String CODE_ALREADY_USED =
      "A warehouse with business unit code '%s' already exists.";
  private static final String UNKNOWN_LOCATION = "Location '%s' does not exist.";
  private static final String STOCK_OVER_CAPACITY =
      "Stock %d exceeds the warehouse capacity of %d.";
  private static final String LOCATION_IS_FULL =
      "Location '%s' already hosts its maximum of %d warehouses.";
  private static final String LOCATION_CAPACITY_EXCEEDED =
      "Location '%s' only has %d capacity left, but %d was requested.";
  private static final String STOCK_MISMATCH =
      "Stock %d does not match the stock %d of the warehouse being replaced.";
  private static final String CAPACITY_BELOW_PREVIOUS_STOCK =
      "Capacity %d cannot accommodate the stock %d of the warehouse being replaced.";

  private final WarehouseStore warehouseStore;
  private final LocationResolver locationResolver;

  WarehouseValidations(WarehouseStore warehouseStore, LocationResolver locationResolver) {
    this.warehouseStore = warehouseStore;
    this.locationResolver = locationResolver;
  }

  void validateNewWarehouse(Warehouse warehouse) {
    requireCompleteWarehouse(warehouse);
    requireUnusedBusinessUnitCode(warehouse.businessUnitCode);
    Location location = resolveLocation(warehouse.location);
    requireStockWithinCapacity(warehouse);
    requireRoomAtLocation(warehouse, location, activeWarehousesAt(warehouse.location));
  }

  /**
   * The warehouse being replaced keeps its slot and its share of the location capacity, so it is
   * left out of the per-location limits.
   */
  void validateReplacement(Warehouse newWarehouse, Warehouse replaced) {
    requireCompleteWarehouse(newWarehouse);
    Location location = resolveLocation(newWarehouse.location);
    requireStockWithinCapacity(newWarehouse);
    requireCapacityForPreviousStock(newWarehouse, replaced);
    requireMatchingStock(newWarehouse, replaced);

    List<Warehouse> others =
        activeWarehousesAt(newWarehouse.location).stream()
            .filter(warehouse -> !replaced.businessUnitCode.equals(warehouse.businessUnitCode))
            .toList();
    requireRoomAtLocation(newWarehouse, location, others);
  }

  private void requireCompleteWarehouse(Warehouse warehouse) {
    if (hasMissingIdentity(warehouse) || hasMissingQuantities(warehouse)) {
      throw new WarehouseValidationException(MISSING_VALUES);
    }
    if (warehouse.capacity < 0 || warehouse.stock < 0) {
      throw new WarehouseValidationException(NEGATIVE_VALUES);
    }
  }

  private void requireUnusedBusinessUnitCode(String businessUnitCode) {
    if (warehouseStore.findByBusinessUnitCode(businessUnitCode).isPresent()) {
      throw new WarehouseValidationException(CODE_ALREADY_USED.formatted(businessUnitCode));
    }
  }

  private Location resolveLocation(String identifier) {
    return locationResolver
        .resolveByIdentifier(identifier)
        .orElseThrow(
            () -> new WarehouseValidationException(UNKNOWN_LOCATION.formatted(identifier)));
  }

  private void requireStockWithinCapacity(Warehouse warehouse) {
    if (warehouse.stock > warehouse.capacity) {
      throw new WarehouseValidationException(
          STOCK_OVER_CAPACITY.formatted(warehouse.stock, warehouse.capacity));
    }
  }

  private void requireCapacityForPreviousStock(Warehouse newWarehouse, Warehouse replaced) {
    if (newWarehouse.capacity < replaced.stock) {
      throw new WarehouseValidationException(
          CAPACITY_BELOW_PREVIOUS_STOCK.formatted(newWarehouse.capacity, replaced.stock));
    }
  }

  private void requireMatchingStock(Warehouse newWarehouse, Warehouse replaced) {
    if (!newWarehouse.stock.equals(replaced.stock)) {
      throw new WarehouseValidationException(
          STOCK_MISMATCH.formatted(newWarehouse.stock, replaced.stock));
    }
  }

  private void requireRoomAtLocation(
      Warehouse warehouse, Location location, List<Warehouse> occupants) {
    if (occupants.size() >= location.maxNumberOfWarehouses) {
      throw new WarehouseValidationException(
          LOCATION_IS_FULL.formatted(location.identification, location.maxNumberOfWarehouses));
    }
    int capacityLeft = location.maxCapacity - totalCapacityOf(occupants);
    if (warehouse.capacity > capacityLeft) {
      throw new WarehouseValidationException(
          LOCATION_CAPACITY_EXCEEDED.formatted(
              location.identification, capacityLeft, warehouse.capacity));
    }
  }

  private List<Warehouse> activeWarehousesAt(String location) {
    return warehouseStore.getAll().stream()
        .filter(warehouse -> location.equals(warehouse.location))
        .toList();
  }

  private static int totalCapacityOf(List<Warehouse> warehouses) {
    return warehouses.stream().mapToInt(warehouse -> warehouse.capacity).sum();
  }

  private static boolean hasMissingIdentity(Warehouse warehouse) {
    return isBlank(warehouse.businessUnitCode) || isBlank(warehouse.location);
  }

  private static boolean hasMissingQuantities(Warehouse warehouse) {
    return warehouse.capacity == null || warehouse.stock == null;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
