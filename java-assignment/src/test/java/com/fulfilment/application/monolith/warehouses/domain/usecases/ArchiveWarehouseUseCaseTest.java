package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static com.fulfilment.application.monolith.warehouses.domain.usecases.WarehouseFixtures.warehouse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ArchiveWarehouseUseCaseTest {

  private static final String LOCATION = "VETSBY-001";
  private static final String CODE = "MWH.301";

  @Test
  void archivesTheWarehouseRegisteredUnderTheBusinessUnitCode() {
    // Given an active warehouse
    Warehouse stored = warehouse(CODE, LOCATION, 20, 5);
    InMemoryWarehouseStore warehouseStore = new InMemoryWarehouseStore(stored);

    // When
    new ArchiveWarehouseUseCase(warehouseStore).archive(warehouse(CODE, LOCATION, 20, 5));

    // Then it is stamped, persisted, and no longer active
    assertThat(stored.archivedAt).isNotNull();
    assertThat(warehouseStore.updatedWarehouses()).containsExactly(stored);
    assertThat(warehouseStore.getAll()).isEmpty();
  }

  @Test
  void rejectsArchivingAnUnknownBusinessUnitCode() {
    // Given an empty register
    InMemoryWarehouseStore warehouseStore = new InMemoryWarehouseStore();
    Warehouse request = warehouse(CODE, LOCATION, 20, 5);

    // When / Then
    assertThatExceptionOfType(WarehouseNotFoundException.class)
        .isThrownBy(() -> new ArchiveWarehouseUseCase(warehouseStore).archive(request))
        .withMessageContaining(CODE);
    assertThat(warehouseStore.updatedWarehouses()).isEmpty();
  }

  @Test
  void rejectsArchivingAWarehouseThatIsAlreadyArchived() {
    // Given a warehouse archived by a previous call
    Warehouse alreadyArchived = warehouse(CODE, LOCATION, 20, 5);
    alreadyArchived.archivedAt = LocalDateTime.now();
    InMemoryWarehouseStore warehouseStore = new InMemoryWarehouseStore(alreadyArchived);
    Warehouse request = warehouse(CODE, LOCATION, 20, 5);

    // When / Then
    assertThatExceptionOfType(WarehouseNotFoundException.class)
        .isThrownBy(() -> new ArchiveWarehouseUseCase(warehouseStore).archive(request));
    assertThat(warehouseStore.updatedWarehouses()).isEmpty();
  }
}
