package dev.atelierflow.equipment;

import static org.assertj.core.api.Assertions.assertThat;

import dev.atelierflow.support.IntegrationTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class EquipmentControllerIntegrationTest extends IntegrationTest {

    @Autowired
    private EquipmentRepository repository;

    private Equipment givenEquipment(String code, String name, String workshop, Criticality criticality) {
        return repository.save(new Equipment(code, name, workshop, criticality, 100, null));
    }

    @Nested
    class Create {

        @Test
        void createsEquipmentAndReturnsLocation() {
            var result = mvc.post().uri("/api/equipment")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"code": "PRS-001", "name": "Presse hydraulique 200 t", "workshop": "Emboutissage",
                             "criticality": "CRITICAL", "operatingHours": 1250.5, "commissionedOn": "2019-03-01"}
                            """)
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.CREATED)
                    .headers().hasHeaderSatisfying("Location", values ->
                            assertThat(values.getFirst()).matches(".*/api/equipment/\\d+"));
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("PRS-001");
            assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("OPERATIONAL");
            assertThat(result).bodyJson().extractingPath("$.operatingHours").isEqualTo(1250.5);
            assertThat(repository.existsByCode("PRS-001")).isTrue();
        }

        @Test
        void operatingHoursDefaultToZeroWhenOmitted() {
            assertThat(mvc.post().uri("/api/equipment")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"code": "CMP-01", "name": "Compresseur", "workshop": "Utilités", "criticality": "MEDIUM"}
                            """))
                    .hasStatus(HttpStatus.CREATED)
                    .bodyJson().extractingPath("$.operatingHours").isEqualTo(0.0);
        }

        @Test
        void rejectsDuplicateCodeWithConflict() {
            givenEquipment("PRS-001", "Presse", "Emboutissage", Criticality.HIGH);

            assertThat(mvc.post().uri("/api/equipment")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"code": "PRS-001", "name": "Autre presse", "workshop": "Emboutissage", "criticality": "LOW"}
                            """))
                    .hasStatus(HttpStatus.CONFLICT)
                    .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .bodyJson().extractingPath("$.code").isEqualTo("EQUIPMENT_CODE_TAKEN");
        }

        @Test
        void rejectsInvalidPayloadWithFieldViolations() {
            var result = mvc.post().uri("/api/equipment")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"code": "bad code", "name": "", "workshop": "Usinage", "operatingHours": -3}
                            """)
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
                    .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
            assertThat(result).bodyJson().extractingPath("$.violations[*].field").asArray()
                    .containsExactlyInAnyOrder("code", "criticality", "name", "operatingHours");
        }

        @Test
        void rejectsMalformedJsonWithProblemDetail() {
            assertThat(mvc.post().uri("/api/equipment")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{not json"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        }
    }

    @Nested
    class Read {

        @Test
        void returnsOneEquipment() {
            Equipment saved = givenEquipment("TOUR-01", "Tour CN", "Usinage", Criticality.MEDIUM);

            assertThat(mvc.get().uri("/api/equipment/{id}", saved.getId()))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.name").isEqualTo("Tour CN");
        }

        @Test
        void returnsNotFoundProblemForUnknownId() {
            assertThat(mvc.get().uri("/api/equipment/{id}", 999))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("RESOURCE_NOT_FOUND");
        }

        @Test
        void listsDistinctWorkshopsSorted() {
            givenEquipment("TOUR-01", "Tour", "Usinage", Criticality.LOW);
            givenEquipment("TOUR-02", "Tour", "Usinage", Criticality.LOW);
            givenEquipment("PRS-01", "Presse", "Emboutissage", Criticality.LOW);

            assertThat(mvc.get().uri("/api/equipment/workshops"))
                    .hasStatusOk()
                    .bodyJson().isEqualTo("""
                            ["Emboutissage", "Usinage"]
                            """);
        }
    }

    @Nested
    class Search {

        @Test
        void filtersByFreeTextCaseInsensitivelyOnCodeOrName() {
            givenEquipment("PRS-001", "Presse hydraulique", "Emboutissage", Criticality.HIGH);
            givenEquipment("TOUR-01", "Tour CN", "Usinage", Criticality.LOW);
            givenEquipment("ROB-01", "Robot de presse", "Emboutissage", Criticality.MEDIUM);

            assertThat(mvc.get().uri("/api/equipment?q=PRESSE"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.content[*].code").asArray()
                    .containsExactly("PRS-001", "ROB-01");
        }

        @Test
        void treatsLikeWildcardsAsLiteralText() {
            givenEquipment("TOUR-01", "Tour CN", "Usinage", Criticality.LOW);

            assertThat(mvc.get().uri("/api/equipment?q=%25"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.totalElements").isEqualTo(0);
        }

        @Test
        void combinesWorkshopCriticalityAndStatusFilters() {
            givenEquipment("PRS-001", "Presse", "Emboutissage", Criticality.HIGH);
            givenEquipment("PRS-002", "Presse", "Emboutissage", Criticality.LOW);
            givenEquipment("TOUR-01", "Tour", "Usinage", Criticality.HIGH);
            Equipment down = givenEquipment("PRS-003", "Presse", "Emboutissage", Criticality.HIGH);
            down.changeStatus(EquipmentStatus.DOWN);
            repository.save(down);

            assertThat(mvc.get().uri("/api/equipment?workshop=Emboutissage&criticality=HIGH&status=OPERATIONAL"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.content[*].code").asArray().containsExactly("PRS-001");
        }

        @Test
        void paginatesAndSortsOnTheServer() {
            for (int i = 1; i <= 5; i++) {
                givenEquipment("EQ-00" + i, "Machine " + i, "Usinage", Criticality.LOW);
            }

            var result = mvc.get().uri("/api/equipment?page=1&size=2&sort=code,desc").exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.content[*].code").asArray()
                    .containsExactly("EQ-003", "EQ-002");
            assertThat(result).bodyJson().extractingPath("$.page").isEqualTo(1);
            assertThat(result).bodyJson().extractingPath("$.size").isEqualTo(2);
            assertThat(result).bodyJson().extractingPath("$.totalElements").isEqualTo(5);
            assertThat(result).bodyJson().extractingPath("$.totalPages").isEqualTo(3);
        }

        @Test
        void rejectsUnknownSortPropertyWithBadRequest() {
            assertThat(mvc.get().uri("/api/equipment?sort=doesNotExist"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_SORT");
        }
    }

    @Nested
    class UpdateAndDelete {

        @Test
        void updatesEditableAttributes() {
            Equipment saved = givenEquipment("TOUR-01", "Tour", "Usinage", Criticality.LOW);

            var result = mvc.put().uri("/api/equipment/{id}", saved.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name": "Tour CN 5 axes", "workshop": "Usinage", "criticality": "HIGH"}
                            """)
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Tour CN 5 axes");
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("TOUR-01");
            assertThat(repository.findById(saved.getId())).get()
                    .extracting(Equipment::getCriticality).isEqualTo(Criticality.HIGH);
        }

        @Test
        void updateOfUnknownEquipmentIsNotFound() {
            assertThat(mvc.put().uri("/api/equipment/{id}", 404)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name": "X", "workshop": "Usinage", "criticality": "HIGH"}
                            """))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void deletesEquipment() {
            Equipment saved = givenEquipment("TOUR-01", "Tour", "Usinage", Criticality.LOW);

            assertThat(mvc.delete().uri("/api/equipment/{id}", saved.getId()))
                    .hasStatus(HttpStatus.NO_CONTENT);
            assertThat(repository.existsById(saved.getId())).isFalse();
        }
    }
}
