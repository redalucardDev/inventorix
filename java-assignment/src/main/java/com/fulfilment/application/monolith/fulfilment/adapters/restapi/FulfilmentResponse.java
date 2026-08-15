package com.fulfilment.application.monolith.fulfilment.adapters.restapi;

import com.fulfilment.application.monolith.fulfilment.domain.models.Fulfilment;

/** What an association looks like on the wire. */
public record FulfilmentResponse(
    Long id, Long productId, Long storeId, String warehouseBusinessUnitCode) {

  static FulfilmentResponse of(Fulfilment fulfilment) {
    return new FulfilmentResponse(
        fulfilment.id(),
        fulfilment.productId(),
        fulfilment.storeId(),
        fulfilment.warehouseBusinessUnitCode());
  }
}
