package hr.kricco.contractor.controller;

import hr.kricco.contractor.dto.ClientDto;
import hr.kricco.contractor.dto.ClientRequest;
import hr.kricco.contractor.dto.LocationDto;
import hr.kricco.contractor.dto.LocationRequest;
import hr.kricco.contractor.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public List<ClientDto> getAll() {
        return clientService.getAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public ClientDto getById(@PathVariable Long id) {
        return clientService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public ClientDto create(@Valid @RequestBody ClientRequest request) {
        return clientService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public ClientDto update(@PathVariable Long id, @Valid @RequestBody ClientRequest request) {
        return clientService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public void delete(@PathVariable Long id) {
        clientService.delete(id);
    }

    @PostMapping("/{id}/locations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public LocationDto addLocation(@PathVariable Long id, @Valid @RequestBody LocationRequest request) {
        return clientService.addLocation(id, request);
    }

    @PutMapping("/{id}/locations/{locationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public LocationDto updateLocation(@PathVariable Long id, @PathVariable Long locationId,
                                      @Valid @RequestBody LocationRequest request) {
        return clientService.updateLocation(id, locationId, request);
    }

    @DeleteMapping("/{id}/locations/{locationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public void deleteLocation(@PathVariable Long id, @PathVariable Long locationId) {
        clientService.deleteLocation(id, locationId);
    }
}
