package com.fulfilment.application.monolith.fulfilment.domain.ports;

import com.fulfilment.application.monolith.fulfilment.domain.models.Fulfilment;
import java.util.List;

public interface ListFulfilmentsOperation {
  List<Fulfilment> list(Long productId, Long storeId);
}
