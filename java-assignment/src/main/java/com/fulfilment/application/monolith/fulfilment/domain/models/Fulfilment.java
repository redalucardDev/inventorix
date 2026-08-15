package com.fulfilment.application.monolith.fulfilment.domain.models;

/**
 * A warehouse acting as a fulfilment unit of one product for one store. The warehouse is held by its
 * business unit code, the identifier the business uses, so an association follows its warehouse
 * through a replacement.
 */
public record Fulfilment(Long id, Long productId, Long storeId, String warehouseBusinessUnitCode) {

  public static Fulfilment of(Long productId, Long storeId, String warehouseBusinessUnitCode) {
    return new Fulfilment(null, productId, storeId, warehouseBusinessUnitCode);
  }

  public Fulfilment storedAs(Long id) {
    return new Fulfilment(id, productId, storeId, warehouseBusinessUnitCode);
  }
}
