package hr.kricco.contractor.service;

import hr.kricco.contractor.dto.EmployeeRequest;
import hr.kricco.contractor.dto.UserDto;
import hr.kricco.contractor.entity.Branch;
import hr.kricco.contractor.entity.Role;
import hr.kricco.contractor.entity.User;
import hr.kricco.contractor.exception.BadRequestException;
import hr.kricco.contractor.exception.ConflictException;
import hr.kricco.contractor.exception.NotFoundException;
import hr.kricco.contractor.repository.BranchRepository;
import hr.kricco.contractor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

// Employee accounts (ADMIN, OFFICE, SERVICER). Client users come with the Client slice.
@Service
@RequiredArgsConstructor
public class UserService {

    private static final List<Role> EMPLOYEE_ROLES = List.of(Role.ADMIN, Role.OFFICE, Role.SERVICER);

    // BCrypt uses only the first 72 bytes, and BCryptPasswordEncoder.encode throws above that
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;

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
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username is taken");
        }
        if (isEmpty(request.password())) {
            throw new BadRequestException("Password is required");
        }
        User user = new User();
        copyRequestFields(request, user);
        setPassword(user, request.password());
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
        boolean usernameChanged = !user.getUsername().equals(request.username());
        if (usernameChanged && userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username is taken");
        }
        copyRequestFields(request, user);
        if (!isEmpty(request.password())) {
            setPassword(user, request.password());
        }
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

    private User findEmployee(Long id) {
        return userRepository.findById(id)
                .filter(user -> EMPLOYEE_ROLES.contains(user.getRole()))
                .orElseThrow(() -> new NotFoundException("Employee not found"));
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

    // Full replace of everything except the password
    private void copyRequestFields(EmployeeRequest request, User user) {
        user.setUsername(request.username());
        user.setRole(request.role());
        user.setDisplayName(request.displayName());
        user.setBranch(findBranchForRole(request.role(), request.branchId()));
        user.setActive(request.active());
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
        return new UserDto(
                user.getId(), user.getUsername(), user.getRole(), user.getDisplayName(),
                branchId, branchName, user.isActive());
    }
}
