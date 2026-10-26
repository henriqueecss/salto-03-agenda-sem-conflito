package br.com.jacaranda.agenda.catalog;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogController {

    private final CatalogRepository catalog;

    public CatalogController(CatalogRepository catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/services")
    List<ClinicService> services() {
        return catalog.findServices();
    }

    @GetMapping("/professionals")
    List<Professional> professionals() {
        return catalog.findProfessionals();
    }
}
