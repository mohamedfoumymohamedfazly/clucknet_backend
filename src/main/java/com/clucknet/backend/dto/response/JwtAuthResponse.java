package com.clucknet.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JwtAuthResponse {

    private String accessToken;
    
    @Builder.Default
    private String tokenType = "Bearer";
    
    private String username;
    private String role;
}
