package com.fulfilment.application.monolith.fulfilment.domain.exceptions;

/** Raised when no association matches the requested id. Mapped to a 404. */
public class FulfilmentNotFoundException extends RuntimeException {

  private static final String UNKNOWN_ID = "Fulfilment with id of %d does not exist.";

  private FulfilmentNotFoundException(String message) {
    super(message);
  }

  public static FulfilmentNotFoundException forId(Long id) {
    return new FulfilmentNotFoundException(UNKNOWN_ID.formatted(id));
  }
}
