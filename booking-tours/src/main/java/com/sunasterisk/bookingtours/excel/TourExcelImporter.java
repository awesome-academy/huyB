package com.sunasterisk.bookingtours.excel;

import com.sunasterisk.bookingtours.excel.annotation.ExcelColumn;
import com.sunasterisk.bookingtours.excel.dto.TourExcelRow;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Đọc từng dòng từ Excel sheet và xử lý song song qua {@code importExecutor}.
 *
 * <p><b>An toàn concurrency:</b> Apache POI Sheet/Row không thread-safe.
 * Toàn bộ đọc POI xảy ra trên calling thread TRƯỚC khi fan-out;
 * worker chỉ nhận String array và thực hiện parse + validate — không dùng JPA.</p>
 */
@Slf4j
@Component
public class TourExcelImporter {

    // Lấy từ các field @ExcelColumn của TourExcelRow — tự đồng bộ khi thêm/xóa field.
    private static final int COLUMN_COUNT = (int) java.util.Arrays.stream(TourExcelRow.class.getDeclaredFields())
            .filter(f -> f.isAnnotationPresent(ExcelColumn.class))
            .count();

    @Autowired
    @Qualifier("importExecutor")
    private Executor importExecutor;

    @Autowired
    private ExcelMapper excelMapper;

    // ── Giao diện public ──────────────────────────────────────────────────────

    /**
     * Kết quả parse một dòng Excel.
     * Khi {@code success = true} toàn bộ field tour được điền; khi false chỉ có {@code error} được set.
     */
    public record ImportRowResult(
            int rowNum,
            boolean success,
            String error,
            // các field tour (null khi success=false)
            String title,
            String description,
            BigDecimal price,
            Integer durationDays,
            Integer maxParticipants,
            String departureLocation,
            String destination,
            LocalDate departureDate,
            Long categoryId
    ) {
        static ImportRowResult ok(int rowNum, String title, String description,
                                  BigDecimal price, Integer durationDays, Integer maxParticipants,
                                  String departureLocation, String destination,
                                  LocalDate departureDate, Long categoryId) {
            return new ImportRowResult(rowNum, true, null, title, description,
                    price, durationDays, maxParticipants, departureLocation, destination,
                    departureDate, categoryId);
        }

        static ImportRowResult error(int rowNum, String reason) {
            return new ImportRowResult(rowNum, false, reason,
                    null, null, null, null, null, null, null, null, null);
        }
    }

    /**
     * Đọc toàn bộ dòng từ sheet (bỏ qua header row 0), fan-out sang importExecutor để parse song song.
     *
     * @param sheet           data sheet từ workbook đã mở
     * @param categoryByName  map categoryName (lowercase) → categoryId, nạp sẵn trên calling thread
     * @return kết quả parse theo thứ tự dòng
     */
    public List<ImportRowResult> parseRows(Sheet sheet, Map<String, Long> categoryByName) {
        // Bước 1: đọc toàn bộ cell thành String[][] trên calling thread (POI không thread-safe)
        List<String[]> rawRows = new ArrayList<>();
        List<Integer> rowNums  = new ArrayList<>();

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String[] cells = new String[COLUMN_COUNT];
            for (int c = 0; c < COLUMN_COUNT; c++) {
                cells[c] = getCellString(row.getCell(c));
            }
            rawRows.add(cells);
            rowNums.add(r + 1); // đánh số từ 1 cho thông báo lỗi hiển thị người dùng
        }

        // Bước 2: fan-out từng dòng sang importExecutor qua CompletableFuture (chỉ parse/validate, không JPA)
        List<CompletableFuture<ImportRowResult>> futures = new ArrayList<>(rawRows.size());
        for (int i = 0; i < rawRows.size(); i++) {
            final String[] cells  = rawRows.get(i);
            final int      rowNum = rowNums.get(i);
            futures.add(CompletableFuture.supplyAsync(
                    () -> parseRow(rowNum, cells, categoryByName), importExecutor));
        }

        // Bước 3: thu thập kết quả theo thứ tự
        List<ImportRowResult> results = new ArrayList<>(futures.size());
        for (int i = 0; i < futures.size(); i++) {
            int rowNum = rowNums.get(i);
            try {
                results.add(futures.get(i).get(30, TimeUnit.SECONDS));
            } catch (TimeoutException e) {
                log.warn("Row {} parse timed out", rowNum);
                futures.get(i).cancel(true);
                results.add(ImportRowResult.error(rowNum, "Parse timed out"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                results.add(ImportRowResult.error(rowNum, "Interrupted"));
            } catch (ExecutionException e) {
                log.error("Row {} parse threw unexpected exception", rowNum, e.getCause());
                results.add(ImportRowResult.error(rowNum,
                        "Unexpected error: " + e.getCause().getMessage()));
            }
        }
        return results;
    }

    // ── Parse + validate nội bộ ───────────────────────────────────────────────

    /**
     * Parse và validate một dòng (đã đọc thành String[]).
     * Parse cấu trúc/kiểu do mapper xử lý (kiểm tra required, ép kiểu).
     * Business rule (kiểm tra khoảng, ngày tương lai, tra cứu category) được áp dụng ở đây.
     * Không JPA — mọi tra cứu dùng map đã nạp sẵn.
     */
    private ImportRowResult parseRow(int rowNum, String[] cells, Map<String, Long> categoryByName) {
        TourExcelRow row;
        try {
            row = excelMapper.importRow(cells, TourExcelRow.class);
        } catch (IllegalArgumentException e) {
            return ImportRowResult.error(rowNum, e.getMessage());
        } catch (Exception e) {
            return ImportRowResult.error(rowNum, "Parse error: " + e.getMessage());
        }

        // Validate domain — các business rule không thể biểu đạt qua @ExcelColumn
        if (row.getTitle().length() > 255) {
            return ImportRowResult.error(rowNum, "Title exceeds 255 characters");
        }
        if (row.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return ImportRowResult.error(rowNum, "Price must be greater than 0");
        }
        if (row.getDurationDays() <= 0) {
            return ImportRowResult.error(rowNum, "Duration Days must be > 0");
        }
        if (row.getMaxParticipants() <= 0) {
            return ImportRowResult.error(rowNum, "Max Participants must be > 0");
        }
        if (!row.getDepartureDate().isAfter(LocalDate.now())) {
            return ImportRowResult.error(rowNum, "Departure date must be in the future");
        }

        // Tra cứu category từ map đã nạp sẵn — không JPA
        Long categoryId = null;
        if (row.getCategoryName() != null && !row.getCategoryName().isBlank()) {
            categoryId = categoryByName.get(row.getCategoryName().trim().toLowerCase());
            if (categoryId == null) {
                return ImportRowResult.error(rowNum,
                        "Category '" + row.getCategoryName() + "' not found");
            }
        }

        String description = row.getDescription() != null ? row.getDescription().trim() : "";

        return ImportRowResult.ok(
                rowNum,
                row.getTitle().trim(),
                description,
                row.getPrice(),
                row.getDurationDays(),
                row.getMaxParticipants(),
                row.getDepartureLocation().trim(),
                row.getDestination().trim(),
                row.getDepartureDate(),
                categoryId);
    }

    // ── Đọc cell POI (chỉ trên calling thread) ───────────────────────────────

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : new java.math.BigDecimal(cell.getNumericCellValue())
                            .stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCachedFormulaResultType() == CellType.NUMERIC
                    ? new java.math.BigDecimal(cell.getNumericCellValue())
                            .stripTrailingZeros().toPlainString()
                    : cell.getRichStringCellValue().getString().trim();
            default -> "";
        };
    }
}
