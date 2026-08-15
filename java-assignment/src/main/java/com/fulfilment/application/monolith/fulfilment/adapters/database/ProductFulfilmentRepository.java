package com.fulfilment.application.monolith.fulfilment.adapters.database;

import com.fulfilment.application.monolith.fulfilment.domain.exceptions.FulfilmentNotFoundException;
import com.fulfilment.application.monolith.fulfilment.domain.models.Fulfilment;
import com.fulfilment.application.monolith.fulfilment.domain.ports.FulfilmentStore;
import com.fulfilment.application.monolith.products.Product;
import com.fulfilment.application.monolith.stores.Store;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class ProductFulfilmentRepository
    implements FulfilmentStore, PanacheRepository<DbProductFulfilment> {

  private static final String BY_PRODUCT = "product.id = :productId";
  private static final String BY_STORE = "store.id = :storeId";
  private static final String BY_PRODUCT_AND_STORE = BY_PRODUCT + " and " + BY_STORE;
  private static final String PRODUCT_ID = "productId";
  private static final String STORE_ID = "storeId";

  @Override
  public List<Fulfilment> findBy(Long productId, Long storeId) {
    if (productId == null && storeId == null) {
      return asFulfilments(listAll());
    }
    return asFulfilments(
        list(filterFor(productId, storeId), parametersFor(productId, storeId)));
  }

  @Override
  public List<Fulfilment> ofProductInStore(Long productId, Long storeId) {
    return asFulfilments(
        list(
            BY_PRODUCT_AND_STORE,
            Parameters.with(PRODUCT_ID, productId).and(STORE_ID, storeId)));
  }

  @Override
  public List<Fulfilment> ofStore(Long storeId) {
    return asFulfilments(list(BY_STORE, Parameters.with(STORE_ID, storeId)));
  }

  @Override
  public List<Fulfilment> ofWarehouse(String businessUnitCode) {
    return asFulfilments(list("warehouseBusinessUnitCode", businessUnitCode));
  }

  @Override
  public Optional<Fulfilment> findAssociation(Long id) {
    return findByIdOptional(id).map(DbProductFulfilment::toFulfilment);
  }

  @Override
  public Fulfilment create(Fulfilment fulfilment) {
    var entity = new DbProductFulfilment();
    entity.product = getEntityManager().getReference(Product.class, fulfilment.productId());
    entity.store = getEntityManager().getReference(Store.class, fulfilment.storeId());
    entity.warehouseBusinessUnitCode = fulfilment.warehouseBusinessUnitCode();

    persist(entity);

    return fulfilment.storedAs(entity.id);
  }

  @Override
  public void remove(Fulfilment fulfilment) {
    DbProductFulfilment entity =
        findByIdOptional(fulfilment.id())
            .orElseThrow(() -> FulfilmentNotFoundException.forId(fulfilment.id()));

    delete(entity);
  }

  private static List<Fulfilment> asFulfilments(List<DbProductFulfilment> entities) {
    return entities.stream().map(DbProductFulfilment::toFulfilment).toList();
  }

  private static String filterFor(Long productId, Long storeId) {
    if (productId == null) {
      return BY_STORE;
    }
    return storeId == null ? BY_PRODUCT : BY_PRODUCT_AND_STORE;
  }

  private static Map<String, Object> parametersFor(Long productId, Long storeId) {
    var parameters = new HashMap<String, Object>();
    if (productId != null) {
      parameters.put(PRODUCT_ID, productId);
    }
    if (storeId != null) {
      parameters.put(STORE_ID, storeId);
    }
    return parameters;
  }
}
