package com.sunasterisk.bookingtours.excel;

import com.sunasterisk.bookingtours.excel.annotation.ExcelColumn;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;

/**
 * Excel mapper generic dựa trên reflection.
 * Export: List&lt;T&gt; → XSSFWorkbook. Import: Sheet/String[] → List&lt;T&gt;/T.
 * An toàn với worker thread: importRow(String[], Class) không đụng đến POI object.
 * Encode/decode giá trị được ủy cho {@link ExcelValueCodec}.
 */
@Component
public class ExcelMapper {

    // ── Kết quả import ───────────────────────────────────────────────────────

    public record ImportResult<T>(int rowNum, boolean success, T value, String error) {
        public static <T> ImportResult<T> ok(int rowNum, T value) {
            return new ImportResult<>(rowNum, true, value, null);
        }
        public static <T> ImportResult<T> error(int rowNum, String msg) {
            return new ImportResult<>(rowNum, false, null, msg);
        }
    }

    // ── Hỗ trợ field ─────────────────────────────────────────────────────────

    private List<Field> orderedFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Field f : type.getDeclaredFields()) {
            if (f.isAnnotationPresent(ExcelColumn.class)) {
                f.setAccessible(true);
                fields.add(f);
            }
        }
        fields.sort(Comparator.comparingInt(f -> f.getAnnotation(ExcelColumn.class).order()));
        return fields;
    }

    // ── Export ───────────────────────────────────────────────────────────────

    public <T> XSSFWorkbook export(List<T> rows, Class<T> type) {
        return export(rows, type, type.getSimpleName());
    }

    public <T> XSSFWorkbook export(List<T> rows, Class<T> type, String sheetName) {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet(sheetName);
        List<Field> fields = orderedFields(type);

        CellStyle headerStyle = buildHeaderStyle(wb);
        CellStyle evenStyle   = buildRowStyle(wb, new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
        CellStyle oddStyle    = buildRowStyle(wb, new byte[]{(byte) 0xF5, (byte) 0xF5, (byte) 0xF5});

        // Dòng header
        Row header = sheet.createRow(0);
        for (int i = 0; i < fields.size(); i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(fields.get(i).getAnnotation(ExcelColumn.class).header());
            cell.setCellStyle(headerStyle);
        }

        // Các dòng dữ liệu
        int rowIdx = 1;
        for (T obj : rows) {
            Row row = sheet.createRow(rowIdx);
            CellStyle style = (rowIdx % 2 == 0) ? evenStyle : oddStyle;
            for (int i = 0; i < fields.size(); i++) {
                Field f = fields.get(i);
                Cell cell = row.createCell(i);
                try {
                    String fmt = f.getAnnotation(ExcelColumn.class).dateFormat();
                    cell.setCellValue(ExcelValueCodec.format(f.get(obj), fmt));
                } catch (IllegalAccessException e) {
                    cell.setCellValue("");
                }
                cell.setCellStyle(style);
            }
            rowIdx++;
        }

        for (int i = 0; i < fields.size(); i++) sheet.autoSizeColumn(i);
        return wb;
    }

    // ── Import ───────────────────────────────────────────────────────────────

    /** An toàn với worker thread: nhận String[], không đụng đến POI. */
    public <T> T importRow(String[] cells, Class<T> type) throws Exception {
        T instance = type.getDeclaredConstructor().newInstance();
        List<Field> fields = orderedFields(type);
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            ExcelColumn ann = f.getAnnotation(ExcelColumn.class);
            String raw = (i < cells.length) ? cells[i] : "";
            if (ann.required() && (raw == null || raw.isBlank())) {
                throw new IllegalArgumentException(ann.header() + " is required");
            }
            if (raw != null && !raw.isBlank()) {
                try {
                    f.set(instance, ExcelValueCodec.parse(raw, f.getType(), ann.dateFormat()));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(ann.header() + ": " + e.getMessage(), e);
                }
            }
        }
        return instance;
    }

    /** Tiện ích single-thread — đọc POI cell rồi ủy cho importRow. */
    public <T> List<ImportResult<T>> importSheet(Sheet sheet, Class<T> type) {
        List<ImportResult<T>> results = new ArrayList<>();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            List<Field> fields = orderedFields(type);
            String[] cells = new String[fields.size()];
            for (int c = 0; c < fields.size(); c++) cells[c] = getCellString(row.getCell(c));
            try {
                results.add(ImportResult.ok(r + 1, importRow(cells, type)));
            } catch (Exception e) {
                results.add(ImportResult.error(r + 1, e.getMessage()));
            }
        }
        return results;
    }

    // ── Đọc cell POI ─────────────────────────────────────────────────────────

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : new BigDecimal(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCachedFormulaResultType() == CellType.NUMERIC
                    ? new BigDecimal(cell.getNumericCellValue()).stripTrailingZeros().toPlainString()
                    : cell.getRichStringCellValue().getString().trim();
            default -> "";
        };
    }

    // ── Xây dựng style ───────────────────────────────────────────────────────

    private CellStyle buildHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xBD, (byte) 0xD7, (byte) 0xEE}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(style, BorderStyle.THIN);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle buildRowStyle(XSSFWorkbook wb, byte[] rgb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(rgb, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(style, BorderStyle.THIN);
        return style;
    }

    private void setBorders(XSSFCellStyle style, BorderStyle border) {
        style.setBorderTop(border);
        style.setBorderBottom(border);
        style.setBorderLeft(border);
        style.setBorderRight(border);
    }
}
