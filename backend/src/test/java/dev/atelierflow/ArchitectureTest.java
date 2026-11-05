package dev.atelierflow;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/**
 * Architecture rules checked on every build, so the structure documented in ARCHITECTURE.md cannot
 * silently erode: packages by domain, layered inside each domain, entities never exposed by the API.
 */
@AnalyzeClasses(packages = "dev.atelierflow", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule layersAreRespected = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Web").definedBy(annotatedWith(RestController.class))
            .layer("Service").definedBy(annotatedWith(Service.class))
            .layer("Persistence").definedBy(assignableTo(Repository.class))
            .whereLayer("Web").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Web", "Service")
            .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Service");

    @ArchTest
    static final ArchRule controllersNeverDependOnEntities = noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
            .because("the API only exposes DTOs, never JPA entities");

    @ArchTest
    static final ArchRule dtosAreRecordsInDtoPackages = classes()
            .that().haveSimpleNameEndingWith("Request").or().haveSimpleNameEndingWith("Response")
            .and().resideOutsideOfPackage("..common..")
            .should().resideInAPackage("..dto..")
            .andShould().beRecords();

    @ArchTest
    static final ArchRule domainsAreFreeOfCycles = slices()
            .matching("dev.atelierflow.(*)..")
            .should().beFreeOfCycles();
}
