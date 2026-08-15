package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Turns the warehouse domain exceptions into HTTP responses, keeping the JSON shape produced by the
 * global {@code StoreResource.ErrorMapper} so clients see a single error format.
 */
final class WarehouseExceptionMappers {

  private static final int BAD_REQUEST = 400;
  private static final int NOT_FOUND = 404;

  private WarehouseExceptionMappers() {}

  record ErrorPayload(String exceptionType, int code, String error) {}

  @Provider
  public static class ValidationExceptionMapper
      implements ExceptionMapper<WarehouseValidationException> {

    @Override
    public Response toResponse(WarehouseValidationException exception) {
      return errorResponse(exception, BAD_REQUEST);
    }
  }

  @Provider
  public static class NotFoundExceptionMapper
      implements ExceptionMapper<WarehouseNotFoundException> {

    @Override
    public Response toResponse(WarehouseNotFoundException exception) {
      return errorResponse(exception, NOT_FOUND);
    }
  }

  private static Response errorResponse(RuntimeException exception, int code) {
    var payload = new ErrorPayload(exception.getClass().getName(), code, exception.getMessage());

    return Response.status(code).entity(payload).build();
  }
}
