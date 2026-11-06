package dev.atelierflow.workorder;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderCommentRepository extends JpaRepository<WorkOrderComment, Long> {

    @EntityGraph(attributePaths = "author")
    List<WorkOrderComment> findByWorkOrderIdOrderByCreatedAtAscIdAsc(Long workOrderId);
}
