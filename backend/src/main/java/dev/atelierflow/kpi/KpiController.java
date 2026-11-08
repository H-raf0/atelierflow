package dev.atelierflow.kpi;

import dev.atelierflow.kpi.dto.EquipmentKpi;
import dev.atelierflow.kpi.dto.KpiSummary;
import dev.atelierflow.kpi.dto.MonthlyKpi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard indicators; the period defaults to the last 90 days. Formulas: see {@link KpiService}. */
@RestController
@RequestMapping("/api/kpis")
@Tag(name = "KPIs", description = "MTBF, MTTR, availability, backlog")
class KpiController {

    private final KpiService service;

    KpiController(KpiService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    @Operation(summary = "Fleet-wide indicators over a period")
    KpiSummary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.summary(from, to);
    }

    @GetMapping("/equipment")
    @Operation(summary = "Indicators per machine, worst downtime first")
    List<EquipmentKpi> byEquipment(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.byEquipment(from, to);
    }

    @GetMapping("/monthly")
    @Operation(summary = "Corrective and preventive activity per month")
    List<MonthlyKpi> monthly(@RequestParam(defaultValue = "6") @Min(1) @Max(24) int months) {
        return service.monthly(months);
    }
}
