package com.clucknet.backend.dto.response;

import com.clucknet.backend.entity.DeviceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceResponse {

    private String id;
    private String name;
    private DeviceStatus status;
    private Long zoneId;
    private String zoneName;
    private LocalDateTime createdAt;
}
