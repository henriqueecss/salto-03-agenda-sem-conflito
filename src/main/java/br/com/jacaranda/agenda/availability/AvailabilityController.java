package br.com.jacaranda.agenda.availability;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AvailabilityController {

    private final AvailabilityService availability;

    public AvailabilityController(AvailabilityService availability) {
        this.availability = availability;
    }

    @GetMapping("/availability")
    Availability availability(@RequestParam String serviceId, @RequestParam LocalDate date,
            @RequestParam(required = false) String professionalId) {
        return availability.find(serviceId, date, professionalId);
    }
}
