package hr.kricco.contractor.controller;

import hr.kricco.contractor.dto.EmployeeRequest;
import hr.kricco.contractor.dto.UserDto;
import hr.kricco.contractor.entity.Role;
import hr.kricco.contractor.security.UserPrincipal;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Employee accounts only. Client users are under /api/clients/{id}/users.
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // OFFICE needs the list to pick a servicer (?role=SERVICER)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public List<UserDto> getEmployees(@RequestParam(required = false) Role role) {
        return userService.getEmployees(role);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto create(@Valid @RequestBody EmployeeRequest request) {
        return userService.createEmployee(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody EmployeeRequest request,
                          @AuthenticationPrincipal UserPrincipal principal) {
        return userService.updateEmployee(id, request, principal.getUser());
    }

    // Deactivates the account, see UserService.deleteEmployee
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        userService.deleteEmployee(id, principal.getUser());
    }
}
