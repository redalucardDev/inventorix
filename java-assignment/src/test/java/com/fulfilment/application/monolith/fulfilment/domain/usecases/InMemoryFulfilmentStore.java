package com.fulfilment.application.monolith.fulfilment.domain.usecases;

import com.fulfilment.application.monolith.fulfilment.domain.models.Fulfilment;
import com.fulfilment.application.monolith.fulfilment.domain.ports.FulfilmentStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Keeps associations in a list and hands out ids the way the sequence would. */
final class InMemoryFulfilmentStore implements FulfilmentStore {

  private final List<Fulfilment> fulfilments = new ArrayList<>();
  private long nextId = 1;

  InMemoryFulfilmentStore(Fulfilment... existing) {
    for (Fulfilment fulfilment : existing) {
      create(fulfilment);
    }
  }

  @Override
  public List<Fulfilment> findBy(Long productId, Long storeId) {
    return fulfilments.stream()
        .filter(fulfilment -> productId == null || productId.equals(fulfilment.productId()))
        .filter(fulfilment -> storeId == null || storeId.equals(fulfilment.storeId()))
        .toList();
  }

  @Override
  public List<Fulfilment> ofProductInStore(Long productId, Long storeId) {
    return findBy(productId, storeId);
  }

  @Override
  public List<Fulfilment> ofStore(Long storeId) {
    return findBy(null, storeId);
  }

  @Override
  public List<Fulfilment> ofWarehouse(String businessUnitCode) {
    return fulfilments.stream()
        .filter(fulfilment -> businessUnitCode.equals(fulfilment.warehouseBusinessUnitCode()))
        .toList();
  }

  @Override
  public Optional<Fulfilment> findAssociation(Long id) {
    return fulfilments.stream()
        .filter(fulfilment -> Objects.equals(id, fulfilment.id()))
        .findFirst();
  }

  @Override
  public Fulfilment create(Fulfilment fulfilment) {
    Fulfilment stored = fulfilment.storedAs(nextId++);
    fulfilments.add(stored);

    return stored;
  }

  @Override
  public void remove(Fulfilment fulfilment) {
    fulfilments.removeIf(stored -> Objects.equals(stored.id(), fulfilment.id()));
  }

  List<Fulfilment> stored() {
    return List.copyOf(fulfilments);
  }
}
