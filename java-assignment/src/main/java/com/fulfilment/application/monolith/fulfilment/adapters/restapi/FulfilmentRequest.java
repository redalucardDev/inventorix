package com.fulfilment.application.monolith.fulfilment.adapters.restapi;

/** Body of a fulfilment creation: which warehouse fulfils which product for which store. */
public record FulfilmentRequest(Long productId, Long storeId, String warehouseBusinessUnitCode) {}
