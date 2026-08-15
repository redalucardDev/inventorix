package com.fulfilment.application.monolith.fulfilment.adapters.restapi;

import com.fulfilment.application.monolith.fulfilment.domain.exceptions.FulfilmentNotFoundException;
import com.fulfilment.application.monolith.fulfilment.domain.exceptions.FulfilmentValidationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Same JSON shape as the global {@code StoreResource.ErrorMapper} and as the warehouse mappers, so
 * the API reports one error format. Discovered by {@code @Provider}, never referenced from code.
 */
final class FulfilmentExceptionMappers {

  private static final int BAD_REQUEST = 400;
  private static final int NOT_FOUND = 404;

  private FulfilmentExceptionMappers() {}

  record ErrorPayload(String exceptionType, int code, String error) {}

  @Provider
  public static class ValidationExceptionMapper
      implements ExceptionMapper<FulfilmentValidationException> {

    @Override
    public Response toResponse(FulfilmentValidationException exception) {
      return errorResponse(exception, BAD_REQUEST);
    }
  }

  @Provider
  public static class NotFoundExceptionMapper
      implements ExceptionMapper<FulfilmentNotFoundException> {

    @Override
    public Response toResponse(FulfilmentNotFoundException exception) {
      return errorResponse(exception, NOT_FOUND);
    }
  }

  private static Response errorResponse(RuntimeException exception, int code) {
    var payload = new ErrorPayload(exception.getClass().getName(), code, exception.getMessage());

    return Response.status(code).entity(payload).build();
  }
}
