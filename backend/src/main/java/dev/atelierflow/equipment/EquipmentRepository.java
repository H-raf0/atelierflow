package dev.atelierflow.equipment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface EquipmentRepository extends JpaRepository<Equipment, Long>, JpaSpecificationExecutor<Equipment> {

    boolean existsByCode(String code);

    @Query("select distinct e.workshop from Equipment e order by e.workshop")
    List<String> findDistinctWorkshops();
}
