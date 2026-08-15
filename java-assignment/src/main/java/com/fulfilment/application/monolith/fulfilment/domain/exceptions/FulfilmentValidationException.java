package com.fulfilment.application.monolith.fulfilment.domain.exceptions;

/** Raised when an association breaks a fulfilment rule. The REST layer maps it to a 400. */
public class FulfilmentValidationException extends RuntimeException {

  public FulfilmentValidationException(String message) {
    super(message);
  }
}
