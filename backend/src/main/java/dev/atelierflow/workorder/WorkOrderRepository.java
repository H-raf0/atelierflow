package dev.atelierflow.workorder;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long>, JpaSpecificationExecutor<WorkOrder> {

    /**
     * The equipment and assignee are fetched in the same SQL query (LEFT JOIN) through an entity graph:
     * listing 20 work orders costs 1 query instead of 1 + 20 + 20 (the "N+1 select" problem).
     */
    @Override
    @EntityGraph(attributePaths = {"equipment", "assignee"})
    Page<WorkOrder> findAll(Specification<WorkOrder> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"equipment", "assignee"})
    Optional<WorkOrder> findWithDetailsById(Long id);

    /** Kanban content: all active work orders plus those closed recently. */
    @EntityGraph(attributePaths = {"equipment", "assignee"})
    @Query("""
            select w from WorkOrder w
            where w.status in :active
               or (w.status = dev.atelierflow.workorder.WorkOrderStatus.DONE and w.completedAt >= :doneSince)
            order by w.reportedAt
            """)
    List<WorkOrder> findBoard(@Param("active") Collection<WorkOrderStatus> active,
                              @Param("doneSince") Instant doneSince);

    boolean existsByEquipmentIdAndTypeAndStatusIn(Long equipmentId, WorkOrderType type,
                                                  Collection<WorkOrderStatus> statuses);
}
