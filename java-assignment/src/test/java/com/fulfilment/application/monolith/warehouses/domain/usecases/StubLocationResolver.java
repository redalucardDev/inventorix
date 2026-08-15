package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Resolves only the locations a test explicitly declares; everything else is unknown. */
final class StubLocationResolver implements LocationResolver {

  private final Map<String, Location> locations = new HashMap<>();

  StubLocationResolver knowing(Location location) {
    locations.put(location.identification, location);
    return this;
  }

  @Override
  public Optional<Location> resolveByIdentifier(String identifier) {
    return Optional.ofNullable(locations.get(identifier));
  }
}
