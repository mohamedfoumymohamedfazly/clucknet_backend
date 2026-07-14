package com.clucknet.backend.service;

import com.clucknet.backend.dto.request.LoginRequest;
import com.clucknet.backend.dto.request.RegisterRequest;
import com.clucknet.backend.dto.response.JwtAuthResponse;
import com.clucknet.backend.dto.response.UserProfileResponse;

public interface AuthService {
    
    JwtAuthResponse login(LoginRequest loginRequest);
    
    void register(RegisterRequest registerRequest);

    void deleteFarmer(String username);

    void assignZones(String username, java.util.List<Long> zoneIds);

    UserProfileResponse getUserProfile(String username);
}
