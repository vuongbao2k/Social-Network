package com.jb.identity_service.config;

import com.jb.identity_service.entity.User;
import com.jb.identity_service.enums.Role;
import com.jb.identity_service.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Set;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ApplicationInitConfig {
    UserRepository userRepository;
    PasswordEncoder passwordEncoder;

    @Bean
    ApplicationRunner applicationRunner() {
        return args -> {
            if (!userRepository.existsByUsername("admin")) {
                var roles = Set.of(Role.ADMIN.name());
                User user = User.builder()
                        .username("admin")
                        .password(passwordEncoder.encode("admin"))
                        .firstName("Admin")
                        .dateOfBirth(LocalDate.of(1990, 1, 1))
                        .roles(roles)
                        .build();

                userRepository.save(user);
                log.info("Admin user created with username: {}", user.getUsername());
            }
        };
    }
}
