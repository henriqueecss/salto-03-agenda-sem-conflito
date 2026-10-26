package br.com.jacaranda.agenda.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import br.com.jacaranda.agenda.TestcontainersConfiguration;
import br.com.jacaranda.agenda.config.ClinicSeeder;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CatalogApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    ClinicSeeder seeder;

    @Autowired
    CatalogRepository catalog;

    @Test
    void listsServicesWithDurations() {
        assertThat(mvc.get().uri("/services")).hasStatusOk().bodyJson()
                .extractingPath("$.length()").isEqualTo(5);
        assertThat(mvc.get().uri("/services")).bodyJson()
                .extractingPath("$[?(@.id == 'S4')].durationMinutes").asList().containsExactly(90);
    }

    @Test
    void listsProfessionalsWithScheduleAndServices() {
        assertThat(mvc.get().uri("/professionals")).hasStatusOk().bodyJson()
                .extractingPath("$.length()").isEqualTo(3);

        assertThat(catalog.findProfessional("PRO2")).hasValueSatisfying(p -> {
            assertThat(p.serviceIds()).contains("S4");
            assertThat(p.weekdays()).containsExactly(2, 3, 4, 5, 6);
        });
        assertThat(catalog.findProfessional("PRO1")).hasValueSatisfying(p ->
                assertThat(p.serviceIds()).doesNotContain("S4"));
        assertThat(catalog.findProfessional("PRO3")).hasValueSatisfying(p ->
                assertThat(p.weekdays()).containsExactly(1, 2, 3, 4, 5));
    }

    @Test
    void seedingTwiceKeepsTheCatalogUnchanged() throws Exception {
        seeder.run(null);

        assertThat(mvc.get().uri("/services")).bodyJson().extractingPath("$.length()").isEqualTo(5);
        assertThat(mvc.get().uri("/professionals")).bodyJson().extractingPath("$.length()").isEqualTo(3);
    }
}
