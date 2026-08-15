package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static com.fulfilment.application.monolith.warehouses.domain.usecases.WarehouseFixtures.warehouse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CreateWarehouseUseCaseTest {

  private static final String LOCATION = "EINDHOVEN-001";
  private static final int MAX_WAREHOUSES = 2;
  private static final int MAX_CAPACITY = 70;
  private static final String NEW_CODE = "MWH.101";
  private static final String EXISTING_CODE = "MWH.102";

  private final StubLocationResolver locationResolver =
      new StubLocationResolver().knowing(new Location(LOCATION, MAX_WAREHOUSES, MAX_CAPACITY));

  @Test
  void createsTheWarehouseWhenEveryRuleIsSatisfied() {
    // Given a location with room left
    InMemoryWarehouseStore warehouseStore = new InMemoryWarehouseStore();
    Warehouse warehouse = warehouse(NEW_CODE, LOCATION, 30, 10);

    // When
    createUseCaseOn(warehouseStore).create(warehouse);

    // Then
    assertThat(warehouseStore.createdWarehouses()).containsExactly(warehouse);
    assertThat(warehouse.createdAt).isNotNull().isBefore(LocalDateTime.now().plusMinutes(1));
    assertThat(warehouse.archivedAt).isNull();
  }

  @Test
  void rejectsCreationWhenTheBusinessUnitCodeAlreadyExists() {
    // Given an active warehouse already using the code
    InMemoryWarehouseStore warehouseStore =
        new InMemoryWarehouseStore(warehouse(EXISTING_CODE, LOCATION, 20, 5));
    Warehouse duplicate = warehouse(EXISTING_CODE, LOCATION, 10, 1);

    // When / Then
    assertThatExceptionOfType(WarehouseValidationException.class)
        .isThrownBy(() -> createUseCaseOn(warehouseStore).create(duplicate))
        .withMessageContaining(EXISTING_CODE);
    assertThat(warehouseStore.createdWarehouses()).isEmpty();
  }

  @Test
  void rejectsCreationWhenTheLocationDoesNotExist() {
    // Given a location the resolver does not know
    InMemoryWarehouseStore warehouseStore = new InMemoryWarehouseStore();
    Warehouse warehouse = warehouse(NEW_CODE, "ATLANTIS-001", 10, 1);

    // When / Then
    assertThatExceptionOfType(WarehouseValidationException.class)
        .isThrownBy(() -> createUseCaseOn(warehouseStore).create(warehouse))
        .withMessageContaining("ATLANTIS-001");
    assertThat(warehouseStore.createdWarehouses()).isEmpty();
  }

  @Test
  void rejectsCreationWhenTheLocationAlreadyHostsItsMaximumNumberOfWarehouses() {
    // Given a location saturated in number of warehouses but not in capacity
    InMemoryWarehouseStore warehouseStore =
        new InMemoryWarehouseStore(
            warehouse("MWH.103", LOCATION, 10, 1), warehouse("MWH.104", LOCATION, 10, 1));
    Warehouse warehouse = warehouse(NEW_CODE, LOCATION, 10, 1);

    // When / Then
    assertThatExceptionOfType(WarehouseValidationException.class)
        .isThrownBy(() -> createUseCaseOn(warehouseStore).create(warehouse))
        .withMessageContaining(LOCATION);
    assertThat(warehouseStore.createdWarehouses()).isEmpty();
  }

  @Test
  void rejectsCreationWhenTheCapacityExceedsWhatIsLeftAtTheLocation() {
    // Given 50 of the 70 units of capacity already taken
    InMemoryWarehouseStore warehouseStore =
        new InMemoryWarehouseStore(warehouse("MWH.105", LOCATION, 50, 5));
    Warehouse tooLarge = warehouse(NEW_CODE, LOCATION, 21, 1);

    // When / Then
    assertThatExceptionOfType(WarehouseValidationException.class)
        .isThrownBy(() -> createUseCaseOn(warehouseStore).create(tooLarge))
        .withMessageContaining(LOCATION);
    assertThat(warehouseStore.createdWarehouses()).isEmpty();
  }

  @Test
  void rejectsCreationWhenTheStockExceedsTheWarehouseCapacity() {
    // Given a warehouse holding more than it can store
    InMemoryWarehouseStore warehouseStore = new InMemoryWarehouseStore();
    Warehouse overloaded = warehouse(NEW_CODE, LOCATION, 10, 11);

    // When / Then
    assertThatExceptionOfType(WarehouseValidationException.class)
        .isThrownBy(() -> createUseCaseOn(warehouseStore).create(overloaded));
    assertThat(warehouseStore.createdWarehouses()).isEmpty();
  }

  @Test
  void rejectsCreationWhenMandatoryValuesAreMissing() {
    // Given a payload without capacity nor stock
    InMemoryWarehouseStore warehouseStore = new InMemoryWarehouseStore();
    var incomplete = new Warehouse();
    incomplete.businessUnitCode = NEW_CODE;
    incomplete.location = LOCATION;

    // When / Then
    assertThatExceptionOfType(WarehouseValidationException.class)
        .isThrownBy(() -> createUseCaseOn(warehouseStore).create(incomplete));
    assertThat(warehouseStore.createdWarehouses()).isEmpty();
  }

  @Test
  void countsOnlyActiveWarehousesAgainstTheLocationLimits() {
    // Given a location whose two slots are taken by warehouses that have been archived
    Warehouse archived = warehouse("MWH.106", LOCATION, 60, 5);
    archived.archivedAt = LocalDateTime.now();
    InMemoryWarehouseStore warehouseStore = new InMemoryWarehouseStore(archived);
    Warehouse warehouse = warehouse(NEW_CODE, LOCATION, 70, 10);

    // When
    createUseCaseOn(warehouseStore).create(warehouse);

    // Then
    assertThat(warehouseStore.createdWarehouses()).containsExactly(warehouse);
  }

  private CreateWarehouseUseCase createUseCaseOn(InMemoryWarehouseStore warehouseStore) {
    return new CreateWarehouseUseCase(
        warehouseStore, new WarehouseValidations(warehouseStore, locationResolver));
  }
}
