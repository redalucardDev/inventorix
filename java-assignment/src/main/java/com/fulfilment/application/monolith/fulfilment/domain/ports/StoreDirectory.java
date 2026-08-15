package com.fulfilment.application.monolith.fulfilment.domain.ports;

/** Tells whether a store exists, without exposing the store itself to this domain. */
public interface StoreDirectory {

  boolean contains(Long storeId);
}
