package com.sunasterisk.bookingtours.excel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Stateless codec for converting between Java field values and Excel cell strings.
 * Extracted from ExcelMapper to keep that class under 200 lines.
 */
final class ExcelValueCodec {

    private ExcelValueCodec() {}

    static String format(Object value, String dateFormat) {
        if (value == null) return "";
        if (value instanceof String s)          return s;
        if (value instanceof BigDecimal bd)      return bd.toPlainString();
        if (value instanceof Integer i)          return String.valueOf(i);
        if (value instanceof Long l)             return String.valueOf(l);
        if (value instanceof LocalDate ld)       return ld.format(DateTimeFormatter.ofPattern(dateFormat));
        if (value instanceof LocalDateTime ldt)  return ldt.format(DateTimeFormatter.ofPattern(dateFormat));
        if (value instanceof Enum<?> e)          return e.name();
        return String.valueOf(value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object parse(String raw, Class<?> fieldType, String dateFormat) {
        try {
            if (fieldType == String.class)      return raw.trim();
            if (fieldType == BigDecimal.class)  return new BigDecimal(raw.replace(",", ""));
            if (fieldType == Integer.class
                    || fieldType == int.class)  return Integer.parseInt(raw.trim());
            if (fieldType == Long.class
                    || fieldType == long.class) return Long.parseLong(raw.trim());
            if (fieldType == LocalDate.class)
                return LocalDate.parse(raw.trim(), DateTimeFormatter.ofPattern(dateFormat));
            if (fieldType == LocalDateTime.class)
                return LocalDateTime.parse(raw.trim(), DateTimeFormatter.ofPattern(dateFormat));
            if (fieldType.isEnum())
                return Enum.valueOf((Class<Enum>) fieldType, raw.trim());
            return raw.trim();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid value: '" + raw + "'", e);
        }
    }
}
