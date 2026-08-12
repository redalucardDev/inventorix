package com.fulfilment.application.monolith.warehouses.domain.ports;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import java.util.Optional;

public interface LocationResolver {
  Optional<Location> resolveByIdentifier(String identifier);
}
