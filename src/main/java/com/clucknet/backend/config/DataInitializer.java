
package com.clucknet.backend.config;

import com.clucknet.backend.entity.User;
import com.clucknet.backend.repository.UserRepository;
import com.clucknet.backend.security.role.Role;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            // Owner Account
            if (!userRepository.existsByUsername("owner")) {
                User owner = User.builder()
                        .username("owner")
                        .password(passwordEncoder.encode("Owner123!"))
                        .email("owner@clucknet.com")
                        .role(Role.OWNER)                    // Make sure this matches your Role enum/string
                        .build();
                userRepository.save(owner);
                System.out.println("✅ Demo Owner account created");
            }

            // Farmer Account
            if (!userRepository.existsByUsername("farmer")) {
                User farmer = User.builder()
                        .username("farmer")
                        .password(passwordEncoder.encode("Farmer123!"))
                        .email("farmer@clucknet.com")
                        .role(Role.FARMER)                   // Make sure this matches your Role enum/string
                        .build();
                userRepository.save(farmer);
                System.out.println("✅ Demo Farmer account created");
            }
        };
    }
}