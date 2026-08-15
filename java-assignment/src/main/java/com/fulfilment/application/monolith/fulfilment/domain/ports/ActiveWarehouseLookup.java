package com.fulfilment.application.monolith.fulfilment.domain.ports;

/**
 * Tells whether a business unit code designates a warehouse that is still active. Owning this port
 * keeps the fulfilment domain independent from the warehouse module, which an adapter bridges.
 */
public interface ActiveWarehouseLookup {

  boolean isActive(String businessUnitCode);
}
