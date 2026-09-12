package hr.kricco.contractor.controller;

import hr.kricco.contractor.dto.ClientDto;
import hr.kricco.contractor.dto.ClientRequest;
import hr.kricco.contractor.dto.ClientUserRequest;
import hr.kricco.contractor.dto.LocationDto;
import hr.kricco.contractor.dto.LocationRequest;
import hr.kricco.contractor.dto.UserDto;
import hr.kricco.contractor.security.UserPrincipal;
import hr.kricco.contractor.service.ClientService;
import hr.kricco.contractor.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

// Clients with their locations and client users. Employees use /api/clients,
// client users reach their own client's locations through /api/portal/locations.
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;
    private final UserService userService;

    @GetMapping("/clients")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public List<ClientDto> getAll() {
        return clientService.getAll();
    }

    @GetMapping("/clients/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public ClientDto getById(@PathVariable Long id) {
        return clientService.getById(id);
    }

    @PostMapping("/clients")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public ClientDto create(@Valid @RequestBody ClientRequest request) {
        return clientService.create(request);
    }

    @PutMapping("/clients/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public ClientDto update(@PathVariable Long id, @Valid @RequestBody ClientRequest request) {
        return clientService.update(id, request);
    }

    @DeleteMapping("/clients/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public void delete(@PathVariable Long id) {
        clientService.delete(id);
    }

    @PostMapping("/clients/{id}/locations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public LocationDto addLocation(@PathVariable Long id, @Valid @RequestBody LocationRequest request) {
        return clientService.addLocation(id, request);
    }

    @PutMapping("/clients/{id}/locations/{locationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public LocationDto updateLocation(@PathVariable Long id, @PathVariable Long locationId,
                                      @Valid @RequestBody LocationRequest request) {
        return clientService.updateLocation(id, locationId, request);
    }

    @DeleteMapping("/clients/{id}/locations/{locationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public void deleteLocation(@PathVariable Long id, @PathVariable Long locationId) {
        clientService.deleteLocation(id, locationId);
    }

    // Client users: login accounts for the client portal

    @GetMapping("/clients/{id}/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public List<UserDto> getUsers(@PathVariable Long id) {
        return userService.getClientUsers(id);
    }

    @PostMapping("/clients/{id}/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public UserDto createUser(@PathVariable Long id, @Valid @RequestBody ClientUserRequest request) {
        return userService.createClientUser(id, request);
    }

    @PutMapping("/clients/{id}/users/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public UserDto updateUser(@PathVariable Long id, @PathVariable Long userId,
                              @Valid @RequestBody ClientUserRequest request) {
        return userService.updateClientUser(id, userId, request);
    }

    @DeleteMapping("/clients/{id}/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public void deleteUser(@PathVariable Long id, @PathVariable Long userId) {
        userService.deleteClientUser(id, userId);
    }

    // Client portal: the client user's own locations. Only CLIENT users (rest-api.md [A2]).

    @GetMapping("/portal/locations")
    @PreAuthorize("hasRole('CLIENT')")
    public List<LocationDto> getPortalLocations(@AuthenticationPrincipal UserPrincipal principal) {
        return clientService.getPortalLocations(principal.getUser());
    }

    @PostMapping("/portal/locations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLIENT')")
    public LocationDto addPortalLocation(@Valid @RequestBody LocationRequest request,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        return clientService.addPortalLocation(request, principal.getUser());
    }
}
