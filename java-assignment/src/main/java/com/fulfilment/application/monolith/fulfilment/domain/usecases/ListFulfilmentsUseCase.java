package com.fulfilment.application.monolith.fulfilment.domain.usecases;

import com.fulfilment.application.monolith.fulfilment.domain.models.Fulfilment;
import com.fulfilment.application.monolith.fulfilment.domain.ports.FulfilmentStore;
import com.fulfilment.application.monolith.fulfilment.domain.ports.ListFulfilmentsOperation;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class ListFulfilmentsUseCase implements ListFulfilmentsOperation {

  private final FulfilmentStore fulfilments;

  ListFulfilmentsUseCase(FulfilmentStore fulfilments) {
    this.fulfilments = fulfilments;
  }

  @Override
  public List<Fulfilment> list(Long productId, Long storeId) {
    return fulfilments.findBy(productId, storeId);
  }
}
