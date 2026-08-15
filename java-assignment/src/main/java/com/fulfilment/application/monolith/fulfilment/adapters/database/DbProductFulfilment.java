package com.fulfilment.application.monolith.fulfilment.adapters.database;

import com.fulfilment.application.monolith.fulfilment.domain.models.Fulfilment;
import com.fulfilment.application.monolith.products.Product;
import com.fulfilment.application.monolith.stores.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Product and store are real foreign keys, cascading on delete so removing a product does not fail
 * on a constraint. The warehouse is kept as its business unit code: an association has to survive a
 * replacement, which archives one warehouse row and inserts another under the same code.
 */
@Entity
@Table(
    name = "product_fulfilment",
    uniqueConstraints =
        @UniqueConstraint(
            columnNames = {"product_id", "store_id", "warehouseBusinessUnitCode"}))
public class DbProductFulfilment {

  @Id @GeneratedValue public Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "product_id")
  @OnDelete(action = OnDeleteAction.CASCADE)
  public Product product;

  @ManyToOne(optional = false)
  @JoinColumn(name = "store_id")
  @OnDelete(action = OnDeleteAction.CASCADE)
  public Store store;

  @Column(nullable = false)
  public String warehouseBusinessUnitCode;

  public DbProductFulfilment() {}

  Fulfilment toFulfilment() {
    return new Fulfilment(this.id, this.product.id, this.store.id, this.warehouseBusinessUnitCode);
  }
}
