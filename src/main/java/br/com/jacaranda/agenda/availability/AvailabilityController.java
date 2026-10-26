package br.com.jacaranda.agenda.availability;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AvailabilityController {

    private final AvailabilityService availability;

    public AvailabilityController(AvailabilityService availability) {
        this.availability = availability;
    }

    @Operation(summary = "Lista os horários livres de um serviço em um dia",
            description = "Sem professionalId, traz todos os profissionais que fazem o serviço. É informativo: "
                    + "quem garante a ausência de conflito é a reserva.")
    @GetMapping("/availability")
    Availability availability(@RequestParam String serviceId, @RequestParam LocalDate date,
            @RequestParam(required = false) String professionalId) {
        return availability.find(serviceId, date, professionalId);
    }
}
