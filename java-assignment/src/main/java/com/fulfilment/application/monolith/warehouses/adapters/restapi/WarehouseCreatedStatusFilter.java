package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.ext.Provider;
import java.util.List;

/**
 * The OpenAPI contract answers a warehouse creation with a 201, but the status can only be declared
 * on the generated {@code WarehouseResource} interface, which does not carry it. The filter
 * restores it on the single endpoint concerned.
 */
@Provider
public class WarehouseCreatedStatusFilter implements ContainerResponseFilter {

  private static final String POST = "POST";
  private static final String CREATION_PATH = "warehouse";
  private static final int SINGLE_SEGMENT = 1;
  private static final int OK = 200;
  private static final int CREATED = 201;

  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext response) {
    if (isWarehouseCreation(request) && response.getStatus() == OK) {
      response.setStatus(CREATED);
    }
  }

  private static boolean isWarehouseCreation(ContainerRequestContext request) {
    List<PathSegment> segments = request.getUriInfo().getPathSegments();

    return POST.equals(request.getMethod())
        && segments.size() == SINGLE_SEGMENT
        && CREATION_PATH.equals(segments.get(0).getPath());
  }
}
