package kdt.fds.project.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record FraudDetailDTO(
        Long id,
        Long txId,
        String userName,
        String userId,
        String sourceValue,
        String targetValue,
        Double txAmount,

        @JsonProperty("probability") // JSON 키 이름을 "probability"로 고정
        Double probability,

        @JsonProperty("isFraud")
        Integer isFraud,

        @JsonProperty("engine")      // JSON 키 이름을 "engine"으로 고정
        String engine,

        String vFeatures, // [추가] AI 판단 근거(JSON 문자열)
        LocalDateTime txTimestamp
) {}