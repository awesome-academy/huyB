package com.sunasterisk.bookingtours.excel;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.CompletableFuture;

/**
 * Đọc từng dòng trong sheet Excel và xử lý song song qua {@code importExecutor}.
 *
 * <p><b>Concurrency safety:</b> Apache POI Sheet/Row không thread-safe.
 * Tất cả đọc POI xảy ra trên calling thread TRƯỚC khi fan-out;
 * worker chỉ nhận mảng String và thực hiện parse + validate — không có JPA.</p>
 */
@Slf4j
@Component
public class TourExcelImporter {

    // Template column indices
    private static final int COL_TITLE            = 0;
    private static final int COL_DESCRIPTION      = 1;
    private static final int COL_PRICE            = 2;
    private static final int COL_DURATION_DAYS    = 3;
    private static final int COL_MAX_PARTICIPANTS = 4;
    private static final int COL_DEPARTURE_LOC    = 5;
    private static final int COL_DESTINATION      = 6;
    private static final int COL_DEPARTURE_DATE   = 7;
    private static final int COL_CATEGORY_NAME    = 8;
    private static final int COLUMN_COUNT         = 9;

    @Autowired
    @Qualifier("importExecutor")
    private Executor importExecutor;

    /**
     * Kết quả parse của một dòng Excel.
     * Khi {@code success = true}, tất cả field tour được điền; khi false chỉ có {@code error}.
     */
    public record ImportRowResult(
            int rowNum,
            boolean success,
            String error,
            // tour fields (null when success=false)
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
     * Đọc tất cả dòng từ sheet (bỏ qua header row 0), fan-out sang importExecutor để parse song song.
     *
     * @param sheet           sheet dữ liệu từ workbook đã mở
     * @param categoryByName  map categoryName (lowercase) → categoryId, preloaded trên calling thread
     * @return danh sách kết quả parse theo thứ tự dòng
     */
    public List<ImportRowResult> parseRows(Sheet sheet, Map<String, Long> categoryByName) {
        // Bước 1: đọc tất cả cells thành String[][] trên calling thread (POI không thread-safe)
        List<String[]> rawRows = new ArrayList<>();
        List<Integer> rowNums = new ArrayList<>();

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String[] cells = new String[COLUMN_COUNT];
            for (int c = 0; c < COLUMN_COUNT; c++) {
                cells[c] = getCellString(row.getCell(c));
            }
            rawRows.add(cells);
            rowNums.add(r + 1); // 1-based row number for user-facing error messages
        }

        // Bước 2: fan-out mỗi dòng sang importExecutor qua CompletableFuture (chỉ parse/validate, không JPA)
        List<CompletableFuture<ImportRowResult>> futures = new ArrayList<>(rawRows.size());
        for (int i = 0; i < rawRows.size(); i++) {
            final String[] cells = rawRows.get(i);
            final int rowNum = rowNums.get(i);
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

    /** Parse + validate một dòng đã được đọc thành mảng String. Không có JPA. */
    private ImportRowResult parseRow(int rowNum, String[] cells, Map<String, Long> categoryByName) {
        String title = cells[COL_TITLE];
        if (title == null || title.isBlank()) {
            return ImportRowResult.error(rowNum, "Title is required");
        }
        if (title.length() > 255) {
            return ImportRowResult.error(rowNum, "Title exceeds 255 characters");
        }

        String description = cells[COL_DESCRIPTION];
        if (description == null || description.isBlank()) description = "";

        BigDecimal price;
        try {
            price = new BigDecimal(cells[COL_PRICE].replace(",", ""));
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                return ImportRowResult.error(rowNum, "Price must be greater than 0");
            }
        } catch (NumberFormatException | NullPointerException e) {
            return ImportRowResult.error(rowNum, "Invalid price: '" + cells[COL_PRICE] + "'");
        }

        Integer durationDays;
        try {
            durationDays = Integer.parseInt(cells[COL_DURATION_DAYS].trim());
            if (durationDays <= 0) return ImportRowResult.error(rowNum, "Duration Days must be > 0");
        } catch (NumberFormatException | NullPointerException e) {
            return ImportRowResult.error(rowNum, "Invalid duration days: '" + cells[COL_DURATION_DAYS] + "'");
        }

        Integer maxParticipants;
        try {
            maxParticipants = Integer.parseInt(cells[COL_MAX_PARTICIPANTS].trim());
            if (maxParticipants <= 0) return ImportRowResult.error(rowNum, "Max Participants must be > 0");
        } catch (NumberFormatException | NullPointerException e) {
            return ImportRowResult.error(rowNum, "Invalid max participants: '" + cells[COL_MAX_PARTICIPANTS] + "'");
        }

        String departureLocation = cells[COL_DEPARTURE_LOC];
        if (departureLocation == null || departureLocation.isBlank()) {
            return ImportRowResult.error(rowNum, "Departure Location is required");
        }

        String destination = cells[COL_DESTINATION];
        if (destination == null || destination.isBlank()) {
            return ImportRowResult.error(rowNum, "Destination is required");
        }

        LocalDate departureDate;
        try {
            departureDate = LocalDate.parse(cells[COL_DEPARTURE_DATE].trim());
            if (!departureDate.isAfter(LocalDate.now())) {
                return ImportRowResult.error(rowNum, "Departure date must be in the future");
            }
        } catch (DateTimeParseException | NullPointerException e) {
            return ImportRowResult.error(rowNum, "Invalid departure date (expected yyyy-MM-dd): '"
                    + cells[COL_DEPARTURE_DATE] + "'");
        }

        // Category lookup từ preloaded map — không có JPA
        Long categoryId = null;
        String categoryName = cells[COL_CATEGORY_NAME];
        if (categoryName != null && !categoryName.isBlank()) {
            categoryId = categoryByName.get(categoryName.trim().toLowerCase());
            if (categoryId == null) {
                return ImportRowResult.error(rowNum, "Category '" + categoryName + "' not found");
            }
        }

        return ImportRowResult.ok(rowNum, title.trim(), description.trim(), price,
                durationDays, maxParticipants, departureLocation.trim(), destination.trim(),
                departureDate, categoryId);
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : new java.math.BigDecimal(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCachedFormulaResultType() == CellType.NUMERIC
                    ? new java.math.BigDecimal(cell.getNumericCellValue()).stripTrailingZeros().toPlainString()
                    : cell.getRichStringCellValue().getString().trim();
            default -> "";
        };
    }
}
