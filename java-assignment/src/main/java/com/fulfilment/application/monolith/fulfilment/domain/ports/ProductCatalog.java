package com.fulfilment.application.monolith.fulfilment.domain.ports;

/** Tells whether a product exists, without exposing the product itself to this domain. */
public interface ProductCatalog {

  boolean contains(Long productId);
}
