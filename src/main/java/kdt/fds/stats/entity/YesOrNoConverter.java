package kdt.fds.stats.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 이 파일은 Y/N 문자열과 boolean을 변환하는 컨버터 파일이다.
 * STATS_CODEBOOK의 IS_ACTIVE 컬럼 매핑에 사용한다.
 */
@Converter
public class YesOrNoConverter implements AttributeConverter<Boolean, String> {
    @Override
    public String convertToDatabaseColumn(Boolean attribute) {
        return Boolean.TRUE.equals(attribute) ? "Y" : "N";
    }

    @Override
    public Boolean convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return false;
        }
        return "Y".equalsIgnoreCase(dbData.trim());
    }
}
