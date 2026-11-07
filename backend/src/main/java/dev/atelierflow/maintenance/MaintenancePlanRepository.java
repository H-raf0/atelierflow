package dev.atelierflow.maintenance;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenancePlanRepository extends JpaRepository<MaintenancePlan, Long> {

    @EntityGraph(attributePaths = "equipment")
    List<MaintenancePlan> findByActiveTrue();

    @EntityGraph(attributePaths = "equipment")
    List<MaintenancePlan> findAllByOrderByIdAsc();

    @EntityGraph(attributePaths = "equipment")
    List<MaintenancePlan> findByEquipmentIdOrderByIdAsc(Long equipmentId);

    @EntityGraph(attributePaths = "equipment")
    Optional<MaintenancePlan> findWithEquipmentById(Long id);
}
