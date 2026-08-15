package com.fulfilment.application.monolith.warehouses.domain.exceptions;

/** Raised when no active warehouse matches the requested identifier. Mapped to a 404. */
public class WarehouseNotFoundException extends RuntimeException {

  private static final String UNKNOWN_BUSINESS_UNIT_CODE =
      "No active warehouse found with business unit code '%s'.";
  private static final String UNKNOWN_ID = "No active warehouse found with id '%s'.";

  private WarehouseNotFoundException(String message) {
    super(message);
  }

  public static WarehouseNotFoundException forBusinessUnitCode(String businessUnitCode) {
    return new WarehouseNotFoundException(UNKNOWN_BUSINESS_UNIT_CODE.formatted(businessUnitCode));
  }

  public static WarehouseNotFoundException forId(String id) {
    return new WarehouseNotFoundException(UNKNOWN_ID.formatted(id));
  }
}
