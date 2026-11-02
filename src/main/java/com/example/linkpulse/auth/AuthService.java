package com.example.linkpulse.auth;

import com.example.linkpulse.user.User;
import com.example.linkpulse.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private static final String EMAIL_CONSTRAINT = "uk_users_email";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    public User register(String email, String password) {
        String normalized = normalize(email);
        try {
            return users.saveAndFlush(new User(normalized, passwordEncoder.encode(password)));
        } catch (DataIntegrityViolationException e) {
            if (isEmailTaken(e)) {
                throw new EmailTakenException(normalized);
            }
            throw e;
        }
    }

    public String login(String email, String password) {
        Optional<User> user = users.findByEmail(normalize(email));
        // Always run one BCrypt check, so unknown emails take as long as wrong passwords.
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(password, hash);
        if (user.isEmpty() || !matches) {
            throw new InvalidCredentialsException();
        }
        return tokens.issue(user.get());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isEmailTaken(DataIntegrityViolationException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve
                    && EMAIL_CONSTRAINT.equalsIgnoreCase(cve.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}