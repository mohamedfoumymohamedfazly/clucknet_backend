package com.clucknet.backend.controller;

import com.clucknet.backend.dto.response.ApiResponse;
import com.clucknet.backend.dto.response.UserProfileResponse;
import com.clucknet.backend.entity.User;
import com.clucknet.backend.security.CustomUserDetails;
import com.clucknet.backend.security.role.AuthorityConstants;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.clucknet.backend.service.AuthService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    @PreAuthorize(AuthorityConstants.HAS_FARMER_OR_OWNER)
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        UserProfileResponse response = authService.getUserProfile(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved successfully.", response));
    }

    @DeleteMapping("/{username}")
    @PreAuthorize(AuthorityConstants.HAS_OWNER)
    public ResponseEntity<ApiResponse<Void>> deleteFarmer(@PathVariable String username) {
        authService.deleteFarmer(username);
        return ResponseEntity.ok(ApiResponse.success("Farmer deleted successfully."));
    }

    @PutMapping("/{username}/zones")
    @PreAuthorize(AuthorityConstants.HAS_OWNER)
    public ResponseEntity<ApiResponse<Void>> assignZones(
            @PathVariable String username,
            @RequestBody java.util.List<Long> zoneIds) {
        authService.assignZones(username, zoneIds);
        return ResponseEntity.ok(ApiResponse.success("Zones assigned successfully."));
    }
}
