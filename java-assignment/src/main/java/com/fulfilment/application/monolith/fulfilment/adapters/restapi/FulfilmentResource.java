package com.fulfilment.application.monolith.fulfilment.adapters.restapi;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * Transport contract of the fulfilment API, mirroring
 * {@code src/main/resources/openapi/fulfilment-openapi.yaml}. Hand-written because the OpenAPI
 * generator only processes one specification per build — see {@code docs/bonus-fulfilment.md} §1.
 * Implemented by {@link FulfilmentResourceImpl}.
 */
@Path("fulfilment")
@Produces("application/json")
@Consumes("application/json")
public interface FulfilmentResource {

  @GET
  List<FulfilmentResponse> listFulfilments(
      @QueryParam("productId") Long productId, @QueryParam("storeId") Long storeId);

  @POST
  Response createFulfilment(FulfilmentRequest request);

  @DELETE
  @Path("{id}")
  Response removeFulfilment(@PathParam("id") Long id);
}
