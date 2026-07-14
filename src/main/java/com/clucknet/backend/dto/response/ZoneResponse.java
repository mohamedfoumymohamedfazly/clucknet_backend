package com.clucknet.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneResponse {

    private Long id;
    private String name;
    private String deviceId;
    private String deviceName;
    private String deviceStatus;
    private ThresholdResponse threshold;
    private LocalDateTime createdAt;
}
