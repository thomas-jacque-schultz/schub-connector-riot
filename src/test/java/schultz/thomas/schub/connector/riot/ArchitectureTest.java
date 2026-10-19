package schultz.thomas.schub.connector.riot;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

// La règle de package-info.java : api → business → data.
@AnalyzeClasses(packages = "schultz.thomas.schub.connector.riot", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String API = "schultz.thomas.schub.connector.riot..api..";
    private static final String BUSINESS = "schultz.thomas.schub.connector.riot..business..";
    private static final String DATA = "schultz.thomas.schub.connector.riot..data..";

    @ArchTest
    static final ArchRule businessIgnoreApi = noClasses().that().resideInAPackage(BUSINESS)
            .should().dependOnClassesThat().resideInAPackage(API)
            .because("business ne connaît ni les DTO, ni les contrôleurs, ni les clients HTTP");

    @ArchTest
    static final ArchRule businessSansHttp = noClasses().that().resideInAPackage(BUSINESS)
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "org.springframework.http..")
            .because("un appel sortant passe par une interface de business, que api implémente");

    @ArchTest
    static final ArchRule dataIsolee = noClasses().that().resideInAPackage(DATA)
            .should().dependOnClassesThat().resideInAnyPackage(BUSINESS, API)
            .because("le modèle persisté ne connaît personne");

    @ArchTest
    static final ArchRule clientsDansApi = noClasses()
            .that().resideOutsideOfPackages("schultz.thomas.schub.connector.riot..api.client..", "schultz.thomas.schub.connector.riot.config..")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.web.client.RestClient")
            .because("les clients HTTP vivent dans api/client");
}
