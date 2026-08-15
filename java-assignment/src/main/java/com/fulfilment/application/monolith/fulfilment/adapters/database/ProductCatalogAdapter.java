package com.fulfilment.application.monolith.fulfilment.adapters.database;

import com.fulfilment.application.monolith.fulfilment.domain.ports.ProductCatalog;
import com.fulfilment.application.monolith.products.ProductRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProductCatalogAdapter implements ProductCatalog {

  private final ProductRepository products;

  ProductCatalogAdapter(ProductRepository products) {
    this.products = products;
  }

  @Override
  public boolean contains(Long productId) {
    return products.findByIdOptional(productId).isPresent();
  }
}
