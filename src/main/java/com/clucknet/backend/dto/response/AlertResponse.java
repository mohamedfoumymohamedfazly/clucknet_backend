package com.clucknet.backend.dto.response;

import com.clucknet.backend.entity.AlertType;
import com.clucknet.backend.entity.AlertStatus;
import com.clucknet.backend.entity.Severity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertResponse {
    private Long id;
    private Long zoneId;
    private String zoneName;
    private Severity severity;
    private String message;
    private AlertStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private AlertType type;
    private Double triggeredValue;
    private Double thresholdValue;
}
