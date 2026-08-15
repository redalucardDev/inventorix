package com.fulfilment.application.monolith.fulfilment.domain.usecases;

import com.fulfilment.application.monolith.fulfilment.domain.exceptions.FulfilmentNotFoundException;
import com.fulfilment.application.monolith.fulfilment.domain.models.Fulfilment;
import com.fulfilment.application.monolith.fulfilment.domain.ports.FulfilmentStore;
import com.fulfilment.application.monolith.fulfilment.domain.ports.RemoveFulfilmentOperation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class RemoveFulfilmentUseCase implements RemoveFulfilmentOperation {

  private final FulfilmentStore fulfilments;

  RemoveFulfilmentUseCase(FulfilmentStore fulfilments) {
    this.fulfilments = fulfilments;
  }

  @Override
  @Transactional
  public void remove(Long id) {
    Fulfilment fulfilment =
        fulfilments.findAssociation(id).orElseThrow(() -> FulfilmentNotFoundException.forId(id));

    fulfilments.remove(fulfilment);
  }
}
