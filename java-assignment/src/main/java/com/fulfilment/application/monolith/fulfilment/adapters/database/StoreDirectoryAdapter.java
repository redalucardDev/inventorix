package com.fulfilment.application.monolith.fulfilment.adapters.database;

import com.fulfilment.application.monolith.fulfilment.domain.ports.StoreDirectory;
import com.fulfilment.application.monolith.stores.Store;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class StoreDirectoryAdapter implements StoreDirectory {

  @Override
  public boolean contains(Long storeId) {
    return Store.findByIdOptional(storeId).isPresent();
  }
}
