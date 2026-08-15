package com.fulfilment.application.monolith.fulfilment.domain.ports;

import com.fulfilment.application.monolith.fulfilment.domain.models.Fulfilment;

public interface CreateFulfilmentOperation {
  Fulfilment create(Fulfilment fulfilment);
}
