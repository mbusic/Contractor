package hr.kricco.contractor.service;

import hr.kricco.contractor.dto.LoginResponse;
import hr.kricco.contractor.entity.User;
import hr.kricco.contractor.exception.InvalidCredentialsException;
import hr.kricco.contractor.repository.UserRepository;
import hr.kricco.contractor.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional(readOnly = true)
    public LoginResponse login(String username, String password) {
        User user = userRepository.findByUsernameAndActiveTrue(username)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        String token = jwtUtil.generate(user);
        return toLoginResponse(user, token);
    }

    private LoginResponse toLoginResponse(User user, String token) {
        Long branchId = user.getBranch() == null ? null : user.getBranch().getId();
        return new LoginResponse(
                token, user.getId(), user.getUsername(), user.getRole(), user.getDisplayName(), branchId);
    }
}
