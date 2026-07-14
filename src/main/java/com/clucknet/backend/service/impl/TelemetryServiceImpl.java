//package com.clucknet.backend.service.impl;
//
//import com.clucknet.backend.config.InfluxDbConfig;
//import com.clucknet.backend.dto.response.TelemetryDataResponse;
//import com.clucknet.backend.service.TelemetryService;
//import com.influxdb.client.InfluxDBClient;
//import com.influxdb.client.QueryApi;
//import com.influxdb.client.WriteApiBlocking;
//import com.influxdb.client.domain.WritePrecision;
//import com.influxdb.client.write.Point;
//import com.influxdb.query.FluxRecord;
//import com.influxdb.query.FluxTable;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import java.time.Instant;
//import java.util.ArrayList;
//import java.util.List;
//
//@Service
//@Slf4j
//public class TelemetryServiceImpl implements TelemetryService {
//
//    private final InfluxDBClient influxDBClient;
//    private final InfluxDbConfig influxDbConfig;
//
//    public TelemetryServiceImpl(InfluxDBClient influxDBClient, InfluxDbConfig influxDbConfig) {
//        this.influxDBClient = influxDBClient;
//        this.influxDbConfig = influxDbConfig;
//    }
//
//    @Override
//    public void saveTelemetry(Long zoneId, String deviceId, Double temperature, Double humidity, Double nh3, Double lpg) {
//        try {
//            WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
//
//            // Construct time-series optimized point layout
//            Point point = Point.measurement("telemetry")
//                    .addTag("zone_id", String.valueOf(zoneId))
//                    .addTag("device_id", deviceId)
//                    .addField("temperature", temperature)
//                    .addField("humidity", humidity)
//                    .addField("nh3", nh3)
//                    .addField("lpg", lpg)
//                    .time(Instant.now(), WritePrecision.NS); // High accuracy nano-precision
//
//            writeApi.writePoint(influxDbConfig.getBucket(), influxDbConfig.getOrg(), point);
//            log.debug("Telemetry written successfully to InfluxDB for zone ID: {}, device ID: {}", zoneId, deviceId);
//        } catch (Exception ex) {
//            log.error("Failed to persist time-series telemetry to InfluxDB: {}", ex.getMessage(), ex);
//        }
//    }
//
//    @Override
//    public List<TelemetryDataResponse> getHistoricalTelemetry(Long zoneId, String range) {
//        List<TelemetryDataResponse> responseList = new ArrayList<>();
//
//        // Validate time range syntax to prevent injection issues in Flux queries (e.g. -24h, -7d, -30d)
//        String validatedRange = validateRange(range);
//
//        // Build a pivot optimized Flux query that consolidates fields into rows
//        String fluxQuery = String.format(
//                "from(bucket: \"%s\") " +
//                        "|> range(start: %s) " +
//                        "|> filter(fn: (r) => r[\"_measurement\"] == \"telemetry\") " +
//                        "|> filter(fn: (r) => r[\"zone_id\"] == \"%d\") " +
//                        "|> pivot(rowKey:[\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\") " +
//                        "|> sort(columns: [\"_time\"], desc: false)",
//                influxDbConfig.getBucket(), validatedRange, zoneId
//        );
//
//        try {
//            QueryApi queryApi = influxDBClient.getQueryApi();
//            List<FluxTable> tables = queryApi.query(fluxQuery, influxDbConfig.getOrg());
//
//            for (FluxTable table : tables) {
//                for (FluxRecord record : table.getRecords()) {
//                    TelemetryDataResponse response = TelemetryDataResponse.builder()
//                            .timestamp(record.getTime())
//                            .zoneId(zoneId)
//                            .deviceId(String.valueOf(record.getValueByKey("device_id")))
//                            .temperature(getDoubleValue(record.getValueByKey("temperature")))
//                            .humidity(getDoubleValue(record.getValueByKey("humidity")))
//                            .nh3(getDoubleValue(record.getValueByKey("nh3")))
//                            .lpg(getDoubleValue(record.getValueByKey("lpg")))
//                            .build();
//
//                    responseList.add(response);
//                }
//            }
//        } catch (Exception ex) {
//            log.error("Failed to query historical telemetry from InfluxDB: {}", ex.getMessage(), ex);
//        }
//
//        return responseList;
//    }
//
//    @Override
//    public TelemetryDataResponse getLatestTelemetry(Long zoneId) {
//        String fluxQuery = String.format(
//                "from(bucket: \"%s\") " +
//                        "|> range(start: -30d) " +
//                        "|> filter(fn: (r) => r[\"_measurement\"] == \"telemetry\") " +
//                        "|> filter(fn: (r) => r[\"zone_id\"] == \"%d\") " +
//                        "|> pivot(rowKey:[\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\") " +
//                        "|> sort(columns: [\"_time\"], desc: true) " +
//                        "|> limit(n: 1)",
//                influxDbConfig.getBucket(), zoneId
//        );
//
//        try {
//            QueryApi queryApi = influxDBClient.getQueryApi();
//            List<FluxTable> tables = queryApi.query(fluxQuery, influxDbConfig.getOrg());
//
//            for (FluxTable table : tables) {
//                for (FluxRecord record : table.getRecords()) {
//                    return TelemetryDataResponse.builder()
//                            .timestamp(record.getTime())
//                            .zoneId(zoneId)
//                            .deviceId(String.valueOf(record.getValueByKey("device_id")))
//                            .temperature(getDoubleValue(record.getValueByKey("temperature")))
//                            .humidity(getDoubleValue(record.getValueByKey("humidity")))
//                            .nh3(getDoubleValue(record.getValueByKey("nh3")))
//                            .lpg(getDoubleValue(record.getValueByKey("lpg")))
//                            .build();
//                }
//            }
//        } catch (Exception ex) {
//            log.error("Failed to query latest telemetry from InfluxDB: {}", ex.getMessage(), ex);
//        }
//
//        return null;
//    }
//
//    // Prevents injection issues by whitelisting acceptable Flux time increments
//    private String validateRange(String range) {
//        if (range == null || range.trim().isEmpty()) {
//            return "-24h"; // Default back to 24 hours
//        }
//
//        String cleanRange = range.trim().toLowerCase();
//        if (cleanRange.matches("^-[0-9]+[smhd]$")) {
//            return cleanRange;
//        }
//
//        log.warn("Invalid Flux range parameter received: '{}'. Falling back to -24h.", range);
//        return "-24h";
//    }
//
//    // Safely converts fields objects to Double values
//    private Double getDoubleValue(Object val) {
//        if (val == null) {
//            return null;
//        }
//        if (val instanceof Number) {
//            return ((Number) val).doubleValue();
//        }
//        try {
//            return Double.parseDouble(val.toString());
//        } catch (NumberFormatException e) {
//            return null;
//        }
//    }
//}

package com.clucknet.backend.service.impl;

import com.clucknet.backend.config.InfluxDbConfig;
import com.clucknet.backend.dto.response.TelemetryDataResponse;
import com.clucknet.backend.service.TelemetryService;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class TelemetryServiceImpl implements TelemetryService {

    private final InfluxDBClient influxDBClient;
    private final InfluxDbConfig influxDbConfig;

    public TelemetryServiceImpl(InfluxDBClient influxDBClient, InfluxDbConfig influxDbConfig) {
        this.influxDBClient = influxDBClient;
        this.influxDbConfig = influxDbConfig;
    }

    @Override
    public void saveTelemetry(Long zoneId, String deviceId, Double temperature, Double humidity, Double nh3, Double lpg) {
        try {
            WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();

            // Construct time-series optimized point layout
            Point point = Point.measurement("telemetry")
                    .addTag("zone_id", String.valueOf(zoneId))
                    .addTag("device_id", deviceId)
                    .addField("temperature", temperature)
                    .addField("humidity", humidity)
                    .addField("nh3", nh3)
                    .addField("lpg", lpg)
                    .time(Instant.now(), WritePrecision.NS); // High accuracy nano-precision

            writeApi.writePoint(influxDbConfig.getBucket(), influxDbConfig.getOrg(), point);
            log.debug("Telemetry written successfully to InfluxDB for zone ID: {}, device ID: {}", zoneId, deviceId);
        } catch (Exception ex) {
            log.error("Failed to persist time-series telemetry to InfluxDB: {}", ex.getMessage(), ex);
        }
    }

    @Override
    public List<TelemetryDataResponse> getHistoricalTelemetry(Long zoneId, String range) {
        List<TelemetryDataResponse> responseList = new ArrayList<>();

        // Validate time range syntax to prevent injection issues in Flux queries (e.g. -24h, -7d, -30d)
        String validatedRange = validateRange(range);

        // Build a pivot optimized Flux query that consolidates fields into rows
        String fluxQuery = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: %s) " +
                        "|> filter(fn: (r) => r[\"_measurement\"] == \"telemetry\") " +
                        "|> filter(fn: (r) => r[\"zone_id\"] == \"%d\") " +
                        "|> pivot(rowKey:[\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\") " +
                        "|> sort(columns: [\"_time\"], desc: false)",
                influxDbConfig.getBucket(), validatedRange, zoneId
        );

        try {
            QueryApi queryApi = influxDBClient.getQueryApi();
            List<FluxTable> tables = queryApi.query(fluxQuery, influxDbConfig.getOrg());

            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    TelemetryDataResponse response = TelemetryDataResponse.builder()
                            .timestamp(record.getTime())
                            .zoneId(zoneId)
                            .deviceId(String.valueOf(record.getValueByKey("device_id")))
                            .temperature(getDoubleValue(record.getValueByKey("temperature")))
                            .humidity(getDoubleValue(record.getValueByKey("humidity")))
                            .nh3(getDoubleValue(record.getValueByKey("nh3")))
                            .lpg(getDoubleValue(record.getValueByKey("lpg")))
                            .build();

                    responseList.add(response);
                }
            }
        } catch (Exception ex) {
            log.error("Failed to query historical telemetry from InfluxDB: {}", ex.getMessage(), ex);
        }

        return responseList;
    }

    @Override
    public TelemetryDataResponse getLatestTelemetry(Long zoneId) {
        // Updated to use group() and sort() / limit() to guarantee the absolute latest global record across all devices
        String fluxQuery = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: -30d) " +
                        "|> filter(fn: (r) => r[\"_measurement\"] == \"telemetry\") " +
                        "|> filter(fn: (r) => r[\"zone_id\"] == \"%d\") " +
                        "|> group(columns: [\"zone_id\"]) " +
                        "|> pivot(rowKey:[\"_time\", \"device_id\"], columnKey: [\"_field\"], valueColumn: \"_value\") " +
                        "|> sort(columns: [\"_time\"], desc: true) " +
                        "|> limit(n: 1)",
                influxDbConfig.getBucket(), zoneId
        );

        try {
            QueryApi queryApi = influxDBClient.getQueryApi();
            List<FluxTable> tables = queryApi.query(fluxQuery, influxDbConfig.getOrg());

            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    return TelemetryDataResponse.builder()
                            .timestamp(record.getTime())
                            .zoneId(zoneId)
                            .deviceId(String.valueOf(record.getValueByKey("device_id")))
                            .temperature(getDoubleValue(record.getValueByKey("temperature")))
                            .humidity(getDoubleValue(record.getValueByKey("humidity")))
                            .nh3(getDoubleValue(record.getValueByKey("nh3")))
                            .lpg(getDoubleValue(record.getValueByKey("lpg")))
                            .build();
                }
            }
        } catch (Exception ex) {
            log.error("Failed to query latest telemetry from InfluxDB: {}", ex.getMessage(), ex);
        }

        return null;
    }

    // Prevents injection issues by whitelisting acceptable Flux time increments
    private String validateRange(String range) {
        if (range == null || range.trim().isEmpty()) {
            return "-24h"; // Default back to 24 hours
        }

        String cleanRange = range.trim().toLowerCase();
        if (cleanRange.matches("^-[0-9]+[smhd]$")) {
            return cleanRange;
        }

        log.warn("Invalid Flux range parameter received: '{}'. Falling back to -24h.", range);
        return "-24h";
    }

    // Safely converts fields objects to Double values
    private Double getDoubleValue(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}