package com.fulfilment.application.monolith.warehouses.domain.exceptions;

/** Raised when a warehouse operation breaks a business rule. The REST layer maps it to a 400. */
public class WarehouseValidationException extends RuntimeException {

  public WarehouseValidationException(String message) {
    super(message);
  }
}
