package hr.kricco.contractor.service;

import hr.kricco.contractor.dto.ClientUserRequest;
import hr.kricco.contractor.dto.EmployeeRequest;
import hr.kricco.contractor.dto.UserDto;
import hr.kricco.contractor.entity.Branch;
import hr.kricco.contractor.entity.Client;
import hr.kricco.contractor.entity.Role;
import hr.kricco.contractor.entity.User;
import hr.kricco.contractor.exception.BadRequestException;
import hr.kricco.contractor.exception.ConflictException;
import hr.kricco.contractor.exception.NotFoundException;
import hr.kricco.contractor.repository.BranchRepository;
import hr.kricco.contractor.repository.ClientRepository;
import hr.kricco.contractor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

// Employee accounts (ADMIN, OFFICE, SERVICER) and client users (CLIENT)
@Service
@RequiredArgsConstructor
public class UserService {

    private static final List<Role> EMPLOYEE_ROLES = List.of(Role.ADMIN, Role.OFFICE, Role.SERVICER);

    // BCrypt uses only the first 72 bytes, and BCryptPasswordEncoder.encode throws above that
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    // Employees

    // Active and deactivated employees, or only one role if role isn't null
    @Transactional(readOnly = true)
    public List<UserDto> getEmployees(Role role) {
        List<Role> roles = EMPLOYEE_ROLES;
        if (role != null) {
            checkEmployeeRole(role);
            roles = List.of(role);
        }
        return userRepository.findByRoleIn(roles, Sort.by("displayName")).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public UserDto createEmployee(EmployeeRequest request) {
        checkEmployeeRole(request.role());
        checkUsernameFree(request.username());
        User user = new User();
        copyRequestFields(request, user);
        setRequiredPassword(user, request.password());
        return toDto(userRepository.save(user));
    }

    // Full replace, except an empty password keeps the current one
    @Transactional
    public UserDto updateEmployee(Long id, EmployeeRequest request, User currentUser) {
        User user = findEmployee(id);
        checkEmployeeRole(request.role());
        if (isSameUser(user, currentUser)) {
            checkAdminKeepsAccess(request);
        }
        checkUsernameFreeIfChanged(user, request.username());
        copyRequestFields(request, user);
        setPasswordIfGiven(user, request.password());
        return toDto(userRepository.save(user));
    }

    // Deactivates instead of deleting, so orders and notes keep pointing to the user (domain-model Q3)
    @Transactional
    public void deleteEmployee(Long id, User currentUser) {
        User user = findEmployee(id);
        if (isSameUser(user, currentUser)) {
            throw new ConflictException("You can't deactivate your own account");
        }
        user.setActive(false);
        userRepository.save(user);
    }

    // Client users

    @Transactional(readOnly = true)
    public List<UserDto> getClientUsers(Long clientId) {
        Client client = findClient(clientId);
        return userRepository.findByClientId(client.getId(), Sort.by("displayName")).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public UserDto createClientUser(Long clientId, ClientUserRequest request) {
        Client client = findClient(clientId);
        checkUsernameFree(request.username());
        User user = new User();
        user.setRole(Role.CLIENT);
        user.setClient(client);
        copyRequestFields(request, user);
        setRequiredPassword(user, request.password());
        return toDto(userRepository.save(user));
    }

    // Full replace, except an empty password keeps the current one. The client stays the same.
    @Transactional
    public UserDto updateClientUser(Long clientId, Long userId, ClientUserRequest request) {
        User user = findClientUser(clientId, userId);
        checkUsernameFreeIfChanged(user, request.username());
        copyRequestFields(request, user);
        setPasswordIfGiven(user, request.password());
        return toDto(userRepository.save(user));
    }

    // Really deleted: nothing points to a client user. Their token stops working with the row.
    @Transactional
    public void deleteClientUser(Long clientId, Long userId) {
        User user = findClientUser(clientId, userId);
        userRepository.delete(user);
    }

    private User findEmployee(Long id) {
        return userRepository.findById(id)
                .filter(user -> EMPLOYEE_ROLES.contains(user.getRole()))
                .orElseThrow(() -> new NotFoundException("Employee not found"));
    }

    // A user of another client, or an employee, counts as not found
    private User findClientUser(Long clientId, Long userId) {
        return userRepository.findByIdAndClientId(userId, clientId)
                .orElseThrow(() -> new NotFoundException("Client user not found"));
    }

    private Client findClient(Long clientId) {
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new NotFoundException("Client not found"));
    }

    private void checkEmployeeRole(Role role) {
        if (!EMPLOYEE_ROLES.contains(role)) {
            throw new BadRequestException("Role must be ADMIN, OFFICE or SERVICER");
        }
    }

    // Stops the last admin from locking everyone out
    private void checkAdminKeepsAccess(EmployeeRequest request) {
        if (request.role() != Role.ADMIN) {
            throw new ConflictException("You can't remove your own admin role");
        }
        if (!request.active()) {
            throw new ConflictException("You can't deactivate your own account");
        }
    }

    private boolean isSameUser(User user, User currentUser) {
        return user.getId().equals(currentUser.getId());
    }

    // Usernames are unique over employees and client users (one table)
    private void checkUsernameFree(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username is taken");
        }
    }

    private void checkUsernameFreeIfChanged(User user, String newUsername) {
        if (!user.getUsername().equals(newUsername)) {
            checkUsernameFree(newUsername);
        }
    }

    // Full replace of everything except the password
    private void copyRequestFields(EmployeeRequest request, User user) {
        user.setUsername(request.username());
        user.setRole(request.role());
        user.setDisplayName(request.displayName());
        user.setBranch(findBranchForRole(request.role(), request.branchId()));
        user.setActive(request.active());
    }

    private void copyRequestFields(ClientUserRequest request, User user) {
        user.setUsername(request.username());
        user.setDisplayName(request.displayName());
    }

    // OFFICE and SERVICER work in a branch, ADMIN doesn't
    private Branch findBranchForRole(Role role, Long branchId) {
        if (role == Role.ADMIN) {
            if (branchId != null) {
                throw new BadRequestException("An admin has no branch");
            }
            return null;
        }
        if (branchId == null) {
            throw new BadRequestException("Branch is required for " + role);
        }
        return branchRepository.findById(branchId)
                .orElseThrow(() -> new BadRequestException("Branch not found"));
    }

    // On create
    private void setRequiredPassword(User user, String password) {
        if (isEmpty(password)) {
            throw new BadRequestException("Password is required");
        }
        setPassword(user, password);
    }

    // On update: an empty password keeps the current one
    private void setPasswordIfGiven(User user, String password) {
        if (!isEmpty(password)) {
            setPassword(user, password);
        }
    }

    private void setPassword(User user, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BadRequestException("Password is too long");
        }
        user.setPassword(passwordEncoder.encode(password));
    }

    private boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private UserDto toDto(User user) {
        Branch branch = user.getBranch();
        Long branchId = branch == null ? null : branch.getId();
        String branchName = branch == null ? null : branch.getName();
        Client client = user.getClient();
        Long clientId = client == null ? null : client.getId();
        String clientName = client == null ? null : client.getName();
        return new UserDto(
                user.getId(), user.getUsername(), user.getRole(), user.getDisplayName(),
                branchId, branchName, clientId, clientName, user.isActive());
    }
}
