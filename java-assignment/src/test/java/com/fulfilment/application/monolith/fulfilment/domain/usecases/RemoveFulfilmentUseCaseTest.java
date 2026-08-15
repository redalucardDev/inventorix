package com.fulfilment.application.monolith.fulfilment.domain.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fulfilment.application.monolith.fulfilment.domain.exceptions.FulfilmentNotFoundException;
import com.fulfilment.application.monolith.fulfilment.domain.models.Fulfilment;
import org.junit.jupiter.api.Test;

class RemoveFulfilmentUseCaseTest {

  private static final Long PRODUCT = 1L;
  private static final Long STORE = 10L;
  private static final String WAREHOUSE = "MWH.001";
  private static final Long UNKNOWN_ID = 404L;

  @Test
  void removesTheAssociation() {
    // Given one registered association
    var fulfilments = new InMemoryFulfilmentStore(Fulfilment.of(PRODUCT, STORE, WAREHOUSE));
    Long id = fulfilments.stored().get(0).id();

    // When
    new RemoveFulfilmentUseCase(fulfilments).remove(id);

    // Then
    assertThat(fulfilments.stored()).isEmpty();
  }

  @Test
  void rejectsAnUnknownId() {
    // Given an empty register
    var fulfilments = new InMemoryFulfilmentStore();

    // When / Then
    assertThatExceptionOfType(FulfilmentNotFoundException.class)
        .isThrownBy(() -> new RemoveFulfilmentUseCase(fulfilments).remove(UNKNOWN_ID))
        .withMessageContaining(String.valueOf(UNKNOWN_ID));
  }
}
