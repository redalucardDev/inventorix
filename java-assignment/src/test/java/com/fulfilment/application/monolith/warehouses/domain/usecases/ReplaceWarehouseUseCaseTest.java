package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static com.fulfilment.application.monolith.warehouses.domain.usecases.WarehouseFixtures.warehouse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import org.junit.jupiter.api.Test;

class ReplaceWarehouseUseCaseTest {

  private static final String SINGLE_SLOT_LOCATION = "HELMOND-001";
  private static final int SINGLE_SLOT_MAX_CAPACITY = 45;
  private static final String CODE = "MWH.201";
  private static final int PREVIOUS_CAPACITY = 30;
  private static final int PREVIOUS_STOCK = 10;
  private static final String SHARED_LOCATION = "ZWOLLE-002";
  private static final int SHARED_MAX_WAREHOUSES = 2;
  private static final int SHARED_MAX_CAPACITY = 70;
  private static final String NEIGHBOUR_CODE = "MWH.202";
  private static final int NEIGHBOUR_CAPACITY = 30;

  private final StubLocationResolver locationResolver =
      new StubLocationResolver()
          .knowing(new Location(SINGLE_SLOT_LOCATION, 1, SINGLE_SLOT_MAX_CAPACITY));

  @Test
  void archivesThePreviousWarehouseAndCreatesTheReplacementAtTheSameLocation() {
    // Given the only warehouse allowed at that location
    Warehouse previous = previousWarehouse();
    InMemoryWarehouseStore warehouseStore = new InMemoryWarehouseStore(previous);
    Warehouse replacement = warehouse(CODE, SINGLE_SLOT_LOCATION, 40, PREVIOUS_STOCK);

    // When
    replaceUseCaseOn(warehouseStore).replace(replacement);

    // Then the previous unit is archived and the new one takes its place
    assertThat(previous.archivedAt).isNotNull();
    assertThat(warehouseStore.updatedWarehouses()).containsExactly(previous);
    assertThat(warehouseStore.createdWarehouses()).containsExactly(replacement);
    assertThat(replacement.createdAt).isNotNull();
    assertThat(warehouseStore.findByBusinessUnitCode(CODE)).hasValue(replacement);
  }

  @Test
  void rejectsReplacementWhenTheBusinessUnitCodeIsUnknown() {
    // Given no warehouse registered under that code
    InMemoryWarehouseStore warehouseStore = new InMemoryWarehouseStore();
    Warehouse replacement = warehouse(CODE, SINGLE_SLOT_LOCATION, 40, PREVIOUS_STOCK);

    // When / Then
    assertThatExceptionOfType(WarehouseNotFoundException.class)
        .isThrownBy(() -> replaceUseCaseOn(warehouseStore).replace(replacement))
        .withMessageContaining(CODE);
    assertThat(warehouseStore.createdWarehouses()).isEmpty();
    assertThat(warehouseStore.updatedWarehouses()).isEmpty();
  }

  @Test
  void rejectsReplacementWhenTheNewCapacityCannotAccommodateThePreviousStock() {
    // Given a replacement smaller than the stock currently held
    Warehouse previous = previousWarehouse();
    InMemoryWarehouseStore warehouseStore = new InMemoryWarehouseStore(previous);
    Warehouse tooSmall = warehouse(CODE, SINGLE_SLOT_LOCATION, 5, 5);

    // When / Then
    assertThatExceptionOfType(WarehouseValidationException.class)
        .isThrownBy(() -> replaceUseCaseOn(warehouseStore).replace(tooSmall));
    assertThat(previous.archivedAt).isNull();
    assertThat(warehouseStore.createdWarehouses()).isEmpty();
  }

  @Test
  void rejectsReplacementWhenTheStockDoesNotMatchThePreviousWarehouse() {
    // Given a replacement declaring a different stock
    Warehouse previous = previousWarehouse();
    InMemoryWarehouseStore warehouseStore = new InMemoryWarehouseStore(previous);
    Warehouse mismatched = warehouse(CODE, SINGLE_SLOT_LOCATION, 40, PREVIOUS_STOCK - 1);

    // When / Then
    assertThatExceptionOfType(WarehouseValidationException.class)
        .isThrownBy(() -> replaceUseCaseOn(warehouseStore).replace(mismatched));
    assertThat(previous.archivedAt).isNull();
    assertThat(warehouseStore.createdWarehouses()).isEmpty();
  }

  @Test
  void rejectsReplacementWhenTheNewLocationDoesNotExist() {
    // Given a replacement moving to an unknown location
    Warehouse previous = previousWarehouse();
    InMemoryWarehouseStore warehouseStore = new InMemoryWarehouseStore(previous);
    Warehouse elsewhere = warehouse(CODE, "ATLANTIS-001", 40, PREVIOUS_STOCK);

    // When / Then
    assertThatExceptionOfType(WarehouseValidationException.class)
        .isThrownBy(() -> replaceUseCaseOn(warehouseStore).replace(elsewhere))
        .withMessageContaining("ATLANTIS-001");
    assertThat(previous.archivedAt).isNull();
    assertThat(warehouseStore.createdWarehouses()).isEmpty();
  }

  @Test
  void rejectsReplacementWhenTheNewCapacityExceedsTheLocationLimit() {
    // Given a replacement larger than the whole location, the replaced unit set aside
    Warehouse previous = previousWarehouse();
    InMemoryWarehouseStore warehouseStore = new InMemoryWarehouseStore(previous);
    Warehouse oversized =
        warehouse(CODE, SINGLE_SLOT_LOCATION, SINGLE_SLOT_MAX_CAPACITY + 1, PREVIOUS_STOCK);

    // When / Then
    assertThatExceptionOfType(WarehouseValidationException.class)
        .isThrownBy(() -> replaceUseCaseOn(warehouseStore).replace(oversized))
        .withMessageContaining(SINGLE_SLOT_LOCATION);
    assertThat(previous.archivedAt).isNull();
    assertThat(warehouseStore.createdWarehouses()).isEmpty();
  }

  @Test
  void freesTheCapacityOfTheReplacedWarehouseButNotThatOfItsNeighbours() {
    // Given a neighbour holding 30 of the 70 units of the location, next to the unit to replace
    Warehouse previous = warehouse(CODE, SHARED_LOCATION, PREVIOUS_CAPACITY, PREVIOUS_STOCK);
    Warehouse neighbour = warehouse(NEIGHBOUR_CODE, SHARED_LOCATION, NEIGHBOUR_CAPACITY, 5);
    InMemoryWarehouseStore warehouseStore = new InMemoryWarehouseStore(previous, neighbour);
    Warehouse replacement =
        warehouse(
            CODE, SHARED_LOCATION, SHARED_MAX_CAPACITY - NEIGHBOUR_CAPACITY, PREVIOUS_STOCK);

    // When it claims everything the neighbour leaves
    replaceUseCaseAtTheSharedLocation(warehouseStore).replace(replacement);

    // Then the capacity of the replaced unit did not count against it
    assertThat(previous.archivedAt).isNotNull();
    assertThat(warehouseStore.createdWarehouses()).containsExactly(replacement);
  }

  @Test
  void rejectsTheReplacementWhenTheNeighboursLeaveTooLittleCapacity() {
    // Given the same neighbour, and a replacement one unit above what it leaves
    Warehouse previous = warehouse(CODE, SHARED_LOCATION, PREVIOUS_CAPACITY, PREVIOUS_STOCK);
    Warehouse neighbour = warehouse(NEIGHBOUR_CODE, SHARED_LOCATION, NEIGHBOUR_CAPACITY, 5);
    InMemoryWarehouseStore warehouseStore = new InMemoryWarehouseStore(previous, neighbour);
    Warehouse tooLarge =
        warehouse(
            CODE, SHARED_LOCATION, SHARED_MAX_CAPACITY - NEIGHBOUR_CAPACITY + 1, PREVIOUS_STOCK);

    // When / Then
    assertThatExceptionOfType(WarehouseValidationException.class)
        .isThrownBy(() -> replaceUseCaseAtTheSharedLocation(warehouseStore).replace(tooLarge))
        .withMessageContaining(SHARED_LOCATION);
    assertThat(previous.archivedAt).isNull();
    assertThat(warehouseStore.createdWarehouses()).isEmpty();
  }

  private static Warehouse previousWarehouse() {
    return warehouse(CODE, SINGLE_SLOT_LOCATION, PREVIOUS_CAPACITY, PREVIOUS_STOCK);
  }

  private ReplaceWarehouseUseCase replaceUseCaseOn(InMemoryWarehouseStore warehouseStore) {
    return new ReplaceWarehouseUseCase(
        warehouseStore, new WarehouseValidations(warehouseStore, locationResolver));
  }

  /** A location with room for a second warehouse, so a replacement has neighbours to count. */
  private static ReplaceWarehouseUseCase replaceUseCaseAtTheSharedLocation(
      InMemoryWarehouseStore warehouseStore) {
    var resolver =
        new StubLocationResolver()
            .knowing(new Location(SHARED_LOCATION, SHARED_MAX_WAREHOUSES, SHARED_MAX_CAPACITY));

    return new ReplaceWarehouseUseCase(
        warehouseStore, new WarehouseValidations(warehouseStore, resolver));
  }
}
