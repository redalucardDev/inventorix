package com.fulfilment.application.monolith.location;

import static org.assertj.core.api.Assertions.assertThat;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LocationGatewayTest {

  private static final String KNOWN_IDENTIFIER = "ZWOLLE-001";

  private final LocationGateway locationGateway = new LocationGateway();

  @Test
  void resolvesAKnownLocationWithItsLimits() {
    // Given a location seeded in the gateway
    // When
    Optional<Location> resolved = locationGateway.resolveByIdentifier(KNOWN_IDENTIFIER);

    // Then
    assertThat(resolved)
        .hasValueSatisfying(
            location -> {
              assertThat(location.identification).isEqualTo(KNOWN_IDENTIFIER);
              assertThat(location.maxNumberOfWarehouses).isEqualTo(1);
              assertThat(location.maxCapacity).isEqualTo(40);
            });
  }

  @Test
  void returnsEmptyWhenTheIdentifierIsUnknown() {
    // Given an identifier that is not seeded
    // When
    Optional<Location> resolved = locationGateway.resolveByIdentifier("ATLANTIS-001");

    // Then
    assertThat(resolved).isEmpty();
  }
}
