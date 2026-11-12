package com.riwi.skillbridge.infrastructure.config;

import com.riwi.skillbridge.application.port.out.PasswordHasherPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AdminSeeder implements CommandLineRunner {

    private final UserRepositoryPort userRepository;
    private final PasswordHasherPort passwordHasher;

    public AdminSeeder(UserRepositoryPort userRepository, PasswordHasherPort passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.existsByEmail("admin")) {
            UserAccount admin = new UserAccount(
                    UUID.randomUUID(),
                    "Admin User",
                    "admin",
                    passwordHasher.encode("12345678"),
                    Role.ADMIN
            );
            userRepository.save(admin);
            System.out.println("Admin user created (username/email: admin, password: 12345678, role: ADMIN)");
        }
    }
}
