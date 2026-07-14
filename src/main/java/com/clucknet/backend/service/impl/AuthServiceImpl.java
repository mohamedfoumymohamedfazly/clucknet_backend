package com.clucknet.backend.service.impl;

import com.clucknet.backend.dto.request.LoginRequest;
import com.clucknet.backend.dto.request.RegisterRequest;
import com.clucknet.backend.dto.response.JwtAuthResponse;
import com.clucknet.backend.entity.User;
import com.clucknet.backend.exception.CustomException;
import com.clucknet.backend.repository.UserRepository;
import com.clucknet.backend.security.jwt.JwtTokenProvider;
import com.clucknet.backend.service.AuthService;
import com.clucknet.backend.security.role.Role;
import com.clucknet.backend.repository.NotificationRepository;
import com.clucknet.backend.repository.DeviceTokenRepository;
import com.clucknet.backend.repository.ZoneRepository;
import com.clucknet.backend.entity.Zone;
import com.clucknet.backend.dto.response.UserProfileResponse;
import com.clucknet.backend.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final ZoneRepository zoneRepository;

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider tokenProvider,
                           NotificationRepository notificationRepository,
                           DeviceTokenRepository deviceTokenRepository,
                           ZoneRepository zoneRepository) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.notificationRepository = notificationRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.zoneRepository = zoneRepository;
    }

    @Override
    public JwtAuthResponse login(LoginRequest loginRequest) {
        // Authenticate credentials via standard Spring Security mechanism
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Generate cryptographically signed JWT Token
        String token = tokenProvider.generateToken(authentication);

        // Fetch User's role metadata from principal
        String username = authentication.getName();
        String roleName = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_FARMER")
                .replace("ROLE_", ""); // Remove Prefix for client friendliness

        return JwtAuthResponse.builder()
                .accessToken(token)
                .username(username)
                .role(roleName)
                .build();
    }

    @Override
    @Transactional
    public void register(RegisterRequest registerRequest) {
        // Prevent username duplication
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new CustomException("Username is already taken.", HttpStatus.BAD_REQUEST);
        }

        // Prevent email duplication
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new CustomException("Email address is already in use.", HttpStatus.BAD_REQUEST);
        }

        // Create new User entity, securing password using BCrypt
        User user = User.builder()
                .username(registerRequest.getUsername())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .email(registerRequest.getEmail())
                .role(registerRequest.getRole())
                .build();

        userRepository.save(user);
    }

    @Override
    @Transactional
    public void deleteFarmer(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("Farmer not found.", HttpStatus.NOT_FOUND));

        if (user.getRole() != Role.FARMER) {
            throw new CustomException("Only farmers can be removed from the roster.", HttpStatus.BAD_REQUEST);
        }

        deviceTokenRepository.deleteByUserId(user.getId());
        notificationRepository.deleteByUserId(user.getId());
        userRepository.delete(user);
     }

    @Override
    @Transactional
    public void assignZones(String username, java.util.List<Long> zoneIds) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        if (user.getRole() != Role.FARMER) {
            throw new CustomException("Zones can only be assigned to Farmers.", HttpStatus.BAD_REQUEST);
        }

        java.util.List<Zone> zones = zoneRepository.findAllById(zoneIds);
        user.getAssignedZones().clear();
        user.getAssignedZones().addAll(zones);
        userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        java.util.List<Long> zoneIds = user.getAssignedZones().stream()
                .map(Zone::getId)
                .toList();

        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .assignedZones(zoneIds)
                .build();
    }
}
