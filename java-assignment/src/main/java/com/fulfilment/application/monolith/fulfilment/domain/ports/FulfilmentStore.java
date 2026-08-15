package com.fulfilment.application.monolith.fulfilment.domain.ports;

import com.fulfilment.application.monolith.fulfilment.domain.models.Fulfilment;
import java.util.List;
import java.util.Optional;

/** Persistence port for the product / store / warehouse associations. */
public interface FulfilmentStore {

  List<Fulfilment> findBy(Long productId, Long storeId);

  List<Fulfilment> ofProductInStore(Long productId, Long storeId);

  List<Fulfilment> ofStore(Long storeId);

  List<Fulfilment> ofWarehouse(String businessUnitCode);

  Optional<Fulfilment> findAssociation(Long id);

  Fulfilment create(Fulfilment fulfilment);

  void remove(Fulfilment fulfilment);
}
