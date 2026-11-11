package dev.atelierflow.demo;

import dev.atelierflow.equipment.Criticality;
import dev.atelierflow.workorder.Priority;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads a realistic production workshop into an empty database ({@code demo} profile only):
 * 3 sign-in accounts (one per role) plus 3 other technicians, 20 machines in 5 workshops,
 * maintenance plans, about 6 months of work order history and the matching audit trail.
 *
 * <p>The history is written with plain SQL so that every timestamp lies in the past and is
 * consistent (report → planning → start → end), including the Envers audit rows: the work order
 * pages then show a believable history, and the KPIs are computed on meaningful data.
 * The generator is seeded, so every start produces the same data set.
 */
@Component
@Profile("demo")
class DemoDataLoader implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DemoDataLoader.class);

    /** Password of the three documented demo accounts (README). */
    static final String DEMO_PASSWORD = "Demo-Atelier-2026";
    private static final Duration HISTORY = Duration.ofDays(180);
    private static final String SYSTEM = "system";

    private record MachineSpec(String code, String name, String workshop, Criticality criticality, double hours,
                               LocalDate commissionedOn) {
    }

    private static final List<MachineSpec> MACHINES = List.of(
            new MachineSpec("PRS-001", "Presse hydraulique 400 t", "Emboutissage", Criticality.CRITICAL, 28450, LocalDate.of(2014, 3, 1)),
            new MachineSpec("PRS-002", "Presse mécanique 250 t", "Emboutissage", Criticality.HIGH, 21300, LocalDate.of(2016, 9, 12)),
            new MachineSpec("PRS-003", "Presse plieuse 6 m", "Emboutissage", Criticality.MEDIUM, 9800, LocalDate.of(2020, 2, 3)),
            new MachineSpec("ROB-001", "Robot de transfert presses", "Emboutissage", Criticality.HIGH, 17600, LocalDate.of(2018, 6, 18)),
            new MachineSpec("TCN-001", "Tour CN bi-broche", "Usinage", Criticality.HIGH, 15200, LocalDate.of(2017, 4, 10)),
            new MachineSpec("TCN-002", "Tour CN 2 axes", "Usinage", Criticality.MEDIUM, 23900, LocalDate.of(2012, 11, 5)),
            new MachineSpec("CUB-001", "Centre d'usinage 5 axes", "Usinage", Criticality.CRITICAL, 12750, LocalDate.of(2019, 1, 21)),
            new MachineSpec("CUB-002", "Centre d'usinage 3 axes", "Usinage", Criticality.HIGH, 19400, LocalDate.of(2015, 7, 7)),
            new MachineSpec("RCT-001", "Rectifieuse plane", "Usinage", Criticality.LOW, 8300, LocalDate.of(2011, 5, 16)),
            new MachineSpec("SOU-001", "Cellule de soudage robotisée", "Assemblage", Criticality.CRITICAL, 14100, LocalDate.of(2019, 10, 1)),
            new MachineSpec("SOU-002", "Poste de soudage MIG", "Assemblage", Criticality.MEDIUM, 6200, LocalDate.of(2021, 3, 8)),
            new MachineSpec("VIS-001", "Visseuse automatique", "Assemblage", Criticality.MEDIUM, 7900, LocalDate.of(2020, 8, 24)),
            new MachineSpec("CNV-001", "Convoyeur d'assemblage", "Assemblage", Criticality.HIGH, 25600, LocalDate.of(2013, 2, 11)),
            new MachineSpec("CTL-001", "Banc de contrôle dimensionnel", "Assemblage", Criticality.LOW, 5400, LocalDate.of(2022, 1, 17)),
            new MachineSpec("CAB-001", "Cabine de peinture poudre", "Peinture", Criticality.CRITICAL, 18800, LocalDate.of(2016, 4, 4)),
            new MachineSpec("FOU-001", "Four de polymérisation", "Peinture", Criticality.HIGH, 22100, LocalDate.of(2015, 12, 1)),
            new MachineSpec("TUN-001", "Tunnel de dégraissage", "Peinture", Criticality.MEDIUM, 16300, LocalDate.of(2016, 4, 4)),
            new MachineSpec("CMP-001", "Compresseur à vis 75 kW", "Utilités", Criticality.CRITICAL, 31200, LocalDate.of(2012, 6, 25)),
            new MachineSpec("CHA-001", "Chaudière vapeur", "Utilités", Criticality.HIGH, 27400, LocalDate.of(2010, 10, 18)),
            new MachineSpec("PNT-001", "Pont roulant 10 t", "Utilités", Criticality.MEDIUM, 11900, LocalDate.of(2014, 9, 29)));

    /** Failure modes with the matching repair, used for titles and resolutions. */
    private static final List<String[]> FAILURES = List.of(
            new String[] {"Fuite hydraulique vérin principal", "Remplacement des joints de tige, appoint d'huile, essais OK"},
            new String[] {"Surchauffe moteur broche", "Nettoyage du circuit de refroidissement, remplacement du ventilateur"},
            new String[] {"Vibrations anormales", "Roulements usés remplacés, réalignement de l'arbre"},
            new String[] {"Défaut capteur de position", "Capteur inductif remplacé et recalibré"},
            new String[] {"Arrêt d'urgence intempestif", "Bouton d'arrêt d'urgence défectueux remplacé, câblage contrôlé"},
            new String[] {"Rupture de courroie", "Courroie remplacée, tension réglée"},
            new String[] {"Défaut variateur de vitesse", "Variateur réinitialisé, ventilateur de carte remplacé"},
            new String[] {"Pression d'air insuffisante", "Fuite sur raccord pneumatique réparée, filtre remplacé"},
            new String[] {"Bourrage en sortie de convoyeur", "Guide réaligné, rouleau grippé remplacé"},
            new String[] {"Défaut de communication automate", "Câble réseau endommagé remplacé, programme rechargé"});

    private static final List<String[]> PREVENTIVE_PLANS = List.of(
            new String[] {"Graissage et contrôle des niveaux", "Graisser les points 1 à 12, contrôler niveaux d'huile et de liquide de refroidissement."},
            new String[] {"Inspection sécurité", "Vérifier arrêts d'urgence, carters, barrières immatérielles et signalisation."},
            new String[] {"Vidange et changement des filtres", "Vidange complète, remplacement des filtres huile et air."});

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final Random random = new Random(42);

    private final Map<String, Long> users = new HashMap<>();
    private final List<String> technicians = new ArrayList<>();

    DemoDataLoader(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, TransactionTemplate transaction, Clock clock) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long existingUsers = jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Long.class);
        if (existingUsers != null && existingUsers > 0) {
            LOG.info("Demo data already present, nothing loaded");
            return;
        }
        transaction.executeWithoutResult(status -> load());
    }

    private void load() {
        Instant now = clock.instant();
        createUsers();
        int workOrders = 0;
        for (MachineSpec spec : MACHINES) {
            long equipmentId = insertEquipment(spec, now);
            List<Timeline> timelines = new ArrayList<>(correctiveHistory(spec, equipmentId, now));
            timelines.addAll(preventiveHistory(spec, equipmentId, now));
            timelines.sort(Comparator.comparing(t -> t.reportedAt));
            for (Timeline timeline : timelines) {
                timeline.persist();
            }
            workOrders += timelines.size();
            boolean down = timelines.stream().anyMatch(t -> t.type.equals("CORRECTIVE") && t.isActive());
            if (down) {
                jdbc.update("UPDATE equipment SET status = 'DOWN' WHERE id = ?", equipmentId);
            }
        }
        LOG.info("Demo data loaded: {} users, {} machines, {} work orders", users.size(), MACHINES.size(), workOrders);
    }

    private void createUsers() {
        String demoPassword = passwordEncoder.encode(DEMO_PASSWORD);
        // the extra technicians cannot sign in: their password is random and discarded
        String disabledPassword = passwordEncoder.encode(UUID.randomUUID().toString());
        insertUser("admin", "Claire Fontaine", "ADMIN", demoPassword);
        insertUser("responsable", "Sophie Bernard", "MANAGER", demoPassword);
        insertUser("technicien", "Karim Haddad", "TECHNICIAN", demoPassword);
        insertUser("jmoreau", "Julien Moreau", "TECHNICIAN", disabledPassword);
        insertUser("lpetit", "Lucas Petit", "TECHNICIAN", disabledPassword);
        insertUser("egirard", "Emma Girard", "TECHNICIAN", disabledPassword);
    }

    private void insertUser(String username, String fullName, String role, String passwordHash) {
        Long id = jdbc.queryForObject("""
                INSERT INTO app_user (username, full_name, role, password_hash, created_at, updated_at)
                VALUES (?, ?, ?, ?, now(), now()) RETURNING id
                """, Long.class, username, fullName, role, passwordHash);
        users.put(username, id);
        if (role.equals("TECHNICIAN")) {
            technicians.add(username);
        }
    }

    private long insertEquipment(MachineSpec spec, Instant now) {
        return jdbc.queryForObject("""
                INSERT INTO equipment (code, name, workshop, criticality, status, operating_hours, commissioned_on,
                                       created_at, updated_at)
                VALUES (?, ?, ?, ?, 'OPERATIONAL', ?, ?, ?, ?) RETURNING id
                """, Long.class, spec.code(), spec.name(), spec.workshop(), spec.criticality().name(), spec.hours(),
                spec.commissionedOn(), ts(now.minus(HISTORY)), ts(now));
    }

    /** Breakdowns spread over the last 6 months; the most critical machines fail more often. */
    private List<Timeline> correctiveHistory(MachineSpec spec, long equipmentId, Instant now) {
        int failures = switch (spec.criticality()) {
            case CRITICAL -> 4;
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        } + random.nextInt(2);
        Priority priority = Priority.forBreakdownOf(spec.criticality());
        List<Timeline> timelines = new ArrayList<>();
        for (int i = 0; i < failures; i++) {
            String[] failure = FAILURES.get(random.nextInt(FAILURES.size()));
            // closed breakdowns: reported at least 2 days ago, so that their repair (≤ ~33 h) is over
            Instant reported = now.minus(HISTORY).plus(Duration.ofMinutes(random.nextLong(HISTORY.toMinutes() - 2 * 24 * 60)));
            String reporter = random.nextInt(3) == 0 ? SYSTEM : pickTechnician();
            Timeline wo = new Timeline(equipmentId, "CORRECTIVE", priority.name(), failure[0],
                    "Constaté par l'opérateur en production.", reported, reporter);
            if (random.nextInt(25) == 0) {
                wo.cancel(reported.plus(minutes(15, 60)), "responsable", "Fausse alerte : capteur débranché lors d'un nettoyage");
            } else {
                completeTimeline(wo, failure[1], spec.criticality());
            }
            timelines.add(wo);
        }
        // a few breakdowns still being handled today make the board and the KPIs alive
        if (spec.criticality() != Criticality.LOW && random.nextInt(2) == 0) {
            String[] failure = FAILURES.get(random.nextInt(FAILURES.size()));
            Timeline wo = new Timeline(equipmentId, "CORRECTIVE", priority.name(), failure[0],
                    "Machine arrêtée, production reportée sur une autre ligne.", now.minus(minutes(4 * 60, 30 * 60)), pickTechnician());
            int stage = random.nextInt(3);
            if (stage >= 1) {
                wo.plan(wo.reportedAt.plus(minutes(10, 60)), pickTechnician());
            }
            if (stage == 2) {
                wo.start(wo.lastStepAt().plus(minutes(10, 90)));
            }
            timelines.add(wo);
        }
        return timelines;
    }

    private void completeTimeline(Timeline wo, String resolution, Criticality criticality) {
        Instant planned = wo.reportedAt.plus(minutes(10, criticality == Criticality.CRITICAL ? 45 : 240));
        wo.plan(planned, pickTechnician());
        Instant started = planned.plus(minutes(5, criticality == Criticality.LOW ? 24 * 60 : 180));
        wo.start(started);
        Instant completed = started.plus(minutes(30, criticality == Criticality.CRITICAL ? 8 * 60 : 5 * 60));
        int worked = (int) Math.max(15, Duration.between(started, completed).toMinutes() * (60 + random.nextInt(40)) / 100);
        wo.complete(completed, worked, resolution);
    }

    /** Machines with a criticality above LOW get a calendar plan, executed every 60 days. */
    private List<Timeline> preventiveHistory(MachineSpec spec, long equipmentId, Instant now) {
        if (spec.criticality() == Criticality.LOW) {
            return List.of();
        }
        String[] planSpec = PREVENTIVE_PLANS.get(random.nextInt(PREVENTIVE_PLANS.size()));
        int interval = 60;
        // start the series at a random offset so that machines are not all serviced the same day
        Instant first = now.minus(HISTORY).plus(Duration.ofDays(random.nextInt(interval)));
        List<Instant> occurrences = new ArrayList<>();
        for (Instant at = first; at.isBefore(now.minus(Duration.ofDays(1))); at = at.plus(Duration.ofDays(interval))) {
            occurrences.add(at);
        }
        Instant last = occurrences.isEmpty() ? now.minus(Duration.ofDays(interval)) : occurrences.getLast();
        long planId = jdbc.queryForObject("""
                INSERT INTO maintenance_plan (equipment_id, title, instructions, trigger_type, interval_days,
                                              last_service_date, last_service_hours, created_at, updated_at)
                VALUES (?, ?, ?, 'CALENDAR', ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, equipmentId, planSpec[0], planSpec[1], interval,
                LocalDate.ofInstant(last, ZoneOffset.UTC), spec.hours(), ts(now.minus(HISTORY)), ts(now));

        Priority priority = Priority.forPreventiveOn(spec.criticality());
        List<Timeline> timelines = new ArrayList<>();
        for (Instant reported : occurrences) {
            Timeline wo = new Timeline(equipmentId, "PREVENTIVE", priority.name(), "Préventif — " + planSpec[0],
                    planSpec[1], reported, SYSTEM);
            wo.maintenancePlanId = planId;
            boolean recent = Duration.between(reported, now).toDays() < 10;
            Instant planned = reported.plus(minutes(60, 24 * 60));
            if (recent && random.nextBoolean()) {
                // the latest occurrence is still waiting to be done
                if (random.nextBoolean()) {
                    wo.plan(planned.isAfter(now) ? reported.plus(Duration.ofMinutes(30)) : planned, pickTechnician());
                }
            } else {
                Instant started = planned.plus(minutes(60, 48 * 60));
                Instant completed = started.plus(minutes(30, 3 * 60));
                if (completed.isBefore(now)) {
                    wo.plan(planned, pickTechnician());
                    wo.start(started);
                    wo.complete(completed, (int) Duration.between(started, completed).toMinutes(),
                            "Opérations du plan réalisées, aucune anomalie relevée.");
                } else if (planned.isBefore(now)) {
                    // generated a day or two ago: planned, not done yet
                    wo.plan(planned, pickTechnician());
                }
            }
            timelines.add(wo);
        }
        return timelines;
    }

    private String pickTechnician() {
        return technicians.get(random.nextInt(technicians.size()));
    }

    private Duration minutes(int min, int max) {
        return Duration.ofMinutes(min + random.nextInt(max - min));
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    /**
     * The successive states of one work order. Each step becomes an Envers revision (by its author,
     * at its time); the last state is the row of the work_order table.
     */
    private final class Timeline {

        private final long equipmentId;
        private final String type;
        private final String priority;
        private final String title;
        private final String description;
        private final Instant reportedAt;
        private Long maintenancePlanId;
        private final List<Step> steps = new ArrayList<>();

        private record Step(Instant at, String username, State state) {
        }

        /** Snapshot of the audited columns. */
        private record State(String status, String assignee, LocalDate scheduledFor, Instant startedAt,
                             Instant completedAt, Integer timeSpent, String resolution, Instant cancelledAt,
                             String cancellationReason) {
        }

        Timeline(long equipmentId, String type, String priority, String title, String description,
                 Instant reportedAt, String reporter) {
            this.equipmentId = equipmentId;
            this.type = type;
            this.priority = priority;
            this.title = title;
            this.description = description;
            this.reportedAt = reportedAt;
            steps.add(new Step(reportedAt, reporter, new State("OPEN", null, null, null, null, null, null, null, null)));
        }

        private State current() {
            return steps.getLast().state();
        }

        Instant lastStepAt() {
            return steps.getLast().at();
        }

        boolean isActive() {
            return List.of("OPEN", "PLANNED", "IN_PROGRESS").contains(current().status());
        }

        void plan(Instant at, String technician) {
            State s = current();
            steps.add(new Step(at, "responsable", new State("PLANNED", technician, LocalDate.ofInstant(at, ZoneOffset.UTC),
                    s.startedAt(), s.completedAt(), s.timeSpent(), s.resolution(), null, null)));
        }

        void start(Instant at) {
            State s = current();
            steps.add(new Step(at, s.assignee(), new State("IN_PROGRESS", s.assignee(), s.scheduledFor(), at,
                    null, null, null, null, null)));
        }

        void complete(Instant at, int timeSpent, String resolution) {
            State s = current();
            steps.add(new Step(at, s.assignee(), new State("DONE", s.assignee(), s.scheduledFor(), s.startedAt(), at,
                    timeSpent, resolution, null, null)));
        }

        void cancel(Instant at, String username, String reason) {
            State s = current();
            steps.add(new Step(at, username, new State("CANCELLED", s.assignee(), s.scheduledFor(), null, null,
                    null, null, at, reason)));
        }

        void persist() {
            State last = current();
            long id = jdbc.queryForObject("""
                    INSERT INTO work_order (equipment_id, type, status, priority, title, description, assignee_id,
                        reported_at, scheduled_for, started_at, completed_at, time_spent_minutes, resolution,
                        cancelled_at, cancellation_reason, maintenance_plan_id, version, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                    """, Long.class, equipmentId, type, last.status(), priority, title, description,
                    userId(last.assignee()), ts(reportedAt), last.scheduledFor(), ts(last.startedAt()),
                    ts(last.completedAt()), last.timeSpent(), last.resolution(), ts(last.cancelledAt()),
                    last.cancellationReason(), maintenancePlanId, steps.size() - 1, ts(reportedAt), ts(lastStepAt()));

            for (int i = 0; i < steps.size(); i++) {
                Step step = steps.get(i);
                long revision = jdbc.queryForObject(
                        "INSERT INTO audit_revision (revision_timestamp, username) VALUES (?, ?) RETURNING id",
                        Long.class, step.at().toEpochMilli(), step.username());
                State s = step.state();
                jdbc.update("""
                        INSERT INTO work_order_aud (id, rev, revtype, equipment_id, type, status, priority, title,
                            description, assignee_id, reported_at, scheduled_for, started_at, completed_at,
                            time_spent_minutes, resolution, cancelled_at, cancellation_reason, maintenance_plan_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, id, revision, i == 0 ? 0 : 1, equipmentId, type, s.status(), priority, title, description,
                        userId(s.assignee()), ts(reportedAt), s.scheduledFor(), ts(s.startedAt()), ts(s.completedAt()),
                        s.timeSpent(), s.resolution(), ts(s.cancelledAt()), s.cancellationReason(), maintenancePlanId);
            }
        }

        private Long userId(String username) {
            return username == null ? null : users.get(username);
        }
    }
}
