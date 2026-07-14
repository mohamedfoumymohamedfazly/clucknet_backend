package com.clucknet.backend.dto.request;

import com.clucknet.backend.security.role.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Username must not be empty.")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters.")
    private String username;

    @NotBlank(message = "Password must not be empty.")
    @Size(min = 6, max = 100, message = "Password must be at least 6 characters.")
    private String password;

    @NotBlank(message = "Email must not be empty.")
    @Email(message = "Must be a valid email address.")
    @Size(max = 100, message = "Email length cannot exceed 100 characters.")
    private String email;

    @NotNull(message = "Role is required.")
    private Role role;
}
