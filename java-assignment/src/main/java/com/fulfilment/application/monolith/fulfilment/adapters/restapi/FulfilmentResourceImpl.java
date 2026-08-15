package com.fulfilment.application.monolith.fulfilment.adapters.restapi;

import com.fulfilment.application.monolith.fulfilment.domain.models.Fulfilment;
import com.fulfilment.application.monolith.fulfilment.domain.ports.CreateFulfilmentOperation;
import com.fulfilment.application.monolith.fulfilment.domain.ports.ListFulfilmentsOperation;
import com.fulfilment.application.monolith.fulfilment.domain.ports.RemoveFulfilmentOperation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import java.util.List;

@ApplicationScoped
public class FulfilmentResourceImpl implements FulfilmentResource {

  private static final int CREATED = 201;
  private static final int NO_CONTENT = 204;

  private final CreateFulfilmentOperation createFulfilment;
  private final RemoveFulfilmentOperation removeFulfilment;
  private final ListFulfilmentsOperation listFulfilments;

  FulfilmentResourceImpl(
      final CreateFulfilmentOperation createFulfilment,
      final RemoveFulfilmentOperation removeFulfilment,
      final ListFulfilmentsOperation listFulfilments) {
    this.createFulfilment = createFulfilment;
    this.removeFulfilment = removeFulfilment;
    this.listFulfilments = listFulfilments;
  }

  @Override
  public List<FulfilmentResponse> listFulfilments(final Long productId, final Long storeId) {
    return listFulfilments.list(productId, storeId).stream().map(FulfilmentResponse::of).toList();
  }

  @Override
  public Response createFulfilment(final FulfilmentRequest request) {
    final Fulfilment created = createFulfilment.create(toFulfilment(request));

    return Response.status(CREATED).entity(FulfilmentResponse.of(created)).build();
  }

  @Override
  public Response removeFulfilment(final Long id) {
    removeFulfilment.remove(id);

    return Response.status(NO_CONTENT).build();
  }

  private static Fulfilment toFulfilment(final FulfilmentRequest request) {
    if (request == null) {
      return Fulfilment.of(null, null, null);
    }
    return Fulfilment.of(
        request.productId(), request.storeId(), request.warehouseBusinessUnitCode());
  }
}
