package com.sunasterisk.bookingtours.excel;

import com.sunasterisk.bookingtours.excel.dto.BookingExcelRow;
import com.sunasterisk.bookingtours.excel.dto.TourExcelRow;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelMapperTest {

    private ExcelMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ExcelMapper();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Sheet sheetWith(String[]... rows) {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Test");
        for (int i = 0; i < rows.length; i++) {
            Row row = sheet.createRow(i);
            for (int j = 0; j < rows[i].length; j++) {
                row.createCell(j).setCellValue(rows[i][j]);
            }
        }
        return sheet;
    }

    /** Row 0 = TourExcelRow headers in annotation order. */
    private static final String[] TOUR_HEADERS = {
            "Title", "Description", "Price", "Duration Days", "Max Participants",
            "Departure Location", "Destination", "Departure Date (yyyy-MM-dd)", "Category Name"
    };

    // ── 1. export writes headers in order ─────────────────────────────────────

    @Test
    @DisplayName("export() writes sheet name and headers in annotation order")
    void exportWritesHeadersInOrder() {
        BookingExcelRow row = new BookingExcelRow(
                "BK001", "user@example.com", "Tour A",
                2, new BigDecimal("500000"), "CONFIRMED",
                LocalDate.of(2026, 3, 15), LocalDateTime.of(2026, 3, 15, 10, 30)
        );

        XSSFWorkbook wb = mapper.export(List.of(row), BookingExcelRow.class, "Bookings");

        assertThat(wb.getSheetName(0)).isEqualTo("Bookings");
        Sheet sheet = wb.getSheetAt(0);
        Row header = sheet.getRow(0);

        assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Booking Code");
        assertThat(header.getCell(1).getStringCellValue()).isEqualTo("User Email");
        assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Tour Name");
        assertThat(header.getCell(3).getStringCellValue()).isEqualTo("Participants");
        assertThat(header.getCell(4).getStringCellValue()).isEqualTo("Total Price (VND)");
        assertThat(header.getCell(5).getStringCellValue()).isEqualTo("Status");
        assertThat(header.getCell(6).getStringCellValue()).isEqualTo("Departure Date");
        assertThat(header.getCell(7).getStringCellValue()).isEqualTo("Created Date");
    }

    // ── 2. export formats values correctly ────────────────────────────────────

    @Test
    @DisplayName("export() formats LocalDate, LocalDateTime, and BigDecimal using annotation dateFormat")
    void exportFormatsValuesCorrectly() {
        BookingExcelRow row = new BookingExcelRow(
                "BK002", "test@example.com", "Tour B",
                3, new BigDecimal("1500000.50"), "PENDING",
                LocalDate.of(2026, 3, 15), LocalDateTime.of(2026, 3, 15, 10, 30)
        );

        XSSFWorkbook wb = mapper.export(List.of(row), BookingExcelRow.class, "Bookings");
        Row dataRow = wb.getSheetAt(0).getRow(1);

        assertThat(dataRow.getCell(4).getStringCellValue()).isEqualTo("1500000.50");
        assertThat(dataRow.getCell(6).getStringCellValue()).isEqualTo("15/03/2026");
        assertThat(dataRow.getCell(7).getStringCellValue()).isEqualTo("15/03/2026 10:30");
    }

    // ── 3. export → import round-trip ─────────────────────────────────────────

    @Test
    @DisplayName("export→import round-trip preserves all field values")
    void exportImportRoundTrip() {
        BookingExcelRow r1 = new BookingExcelRow(
                "BK010", "alice@example.com", "Tour X",
                2, new BigDecimal("900000.00"), "CONFIRMED",
                LocalDate.of(2026, 6, 1), LocalDateTime.of(2026, 5, 20, 9, 0)
        );
        BookingExcelRow r2 = new BookingExcelRow(
                "BK011", "bob@example.com", "Tour Y",
                4, new BigDecimal("1200000.75"), "PENDING",
                LocalDate.of(2026, 7, 15), LocalDateTime.of(2026, 6, 10, 14, 30)
        );

        XSSFWorkbook wb = mapper.export(List.of(r1, r2), BookingExcelRow.class, "Bookings");
        Sheet sheet = wb.getSheetAt(0);
        List<ExcelMapper.ImportResult<BookingExcelRow>> results = mapper.importSheet(sheet, BookingExcelRow.class);

        assertThat(results).hasSize(2);

        ExcelMapper.ImportResult<BookingExcelRow> res1 = results.get(0);
        assertThat(res1.success()).isTrue();
        assertThat(res1.value().getBookingCode()).isEqualTo("BK010");
        assertThat(res1.value().getUserEmail()).isEqualTo("alice@example.com");
        assertThat(res1.value().getDepartureDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(res1.value().getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 5, 20, 9, 0));
        assertThat(res1.value().getTotalPrice()).isEqualByComparingTo(new BigDecimal("900000.00"));

        ExcelMapper.ImportResult<BookingExcelRow> res2 = results.get(1);
        assertThat(res2.success()).isTrue();
        assertThat(res2.value().getBookingCode()).isEqualTo("BK011");
        assertThat(res2.value().getUserEmail()).isEqualTo("bob@example.com");
        assertThat(res2.value().getDepartureDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(res2.value().getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 6, 10, 14, 30));
        assertThat(res2.value().getTotalPrice()).isEqualByComparingTo(new BigDecimal("1200000.75"));
    }

    // ── 4. import valid TourExcelRow rows ─────────────────────────────────────

    @Test
    @DisplayName("importSheet() parses two valid TourExcelRow rows with correct types")
    void importValidTourExcelRows() {
        Sheet sheet = sheetWith(
                TOUR_HEADERS,
                new String[]{"City Break", "Nice tour", "500000", "3", "20", "HCM", "Hanoi", "2027-01-01", "Beach"},
                new String[]{"Mountain Trek", "", "750000", "5", "15", "Hanoi", "SaPa", "2027-03-15", ""}
        );

        List<ExcelMapper.ImportResult<TourExcelRow>> results = mapper.importSheet(sheet, TourExcelRow.class);

        assertThat(results).hasSize(2);

        ExcelMapper.ImportResult<TourExcelRow> res1 = results.get(0);
        assertThat(res1.success()).isTrue();
        assertThat(res1.value().getTitle()).isEqualTo("City Break");
        assertThat(res1.value().getPrice()).isEqualByComparingTo(new BigDecimal("500000"));
        assertThat(res1.value().getDurationDays()).isEqualTo(3);
        assertThat(res1.value().getDepartureDate()).isEqualTo(LocalDate.of(2027, 1, 1));

        ExcelMapper.ImportResult<TourExcelRow> res2 = results.get(1);
        assertThat(res2.success()).isTrue();
        assertThat(res2.value().getTitle()).isEqualTo("Mountain Trek");
        assertThat(res2.value().getPrice()).isEqualByComparingTo(new BigDecimal("750000"));
        assertThat(res2.value().getDurationDays()).isEqualTo(5);
        assertThat(res2.value().getDepartureDate()).isEqualTo(LocalDate.of(2027, 3, 15));
    }

    // ── 5. import missing required field ──────────────────────────────────────

    @Test
    @DisplayName("importSheet() returns error when required 'Title' field is blank")
    void importMissingRequiredField() {
        Sheet sheet = sheetWith(
                TOUR_HEADERS,
                new String[]{"", "desc", "500000", "3", "20", "HCM", "Hanoi", "2027-01-01", ""}
        );

        List<ExcelMapper.ImportResult<TourExcelRow>> results = mapper.importSheet(sheet, TourExcelRow.class);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(0).error()).containsIgnoringCase("Title");
    }

    // ── 6. import bad date format ─────────────────────────────────────────────

    @Test
    @DisplayName("importSheet() returns error when date cell has wrong format")
    void importBadDateFormat() {
        Sheet sheet = sheetWith(
                TOUR_HEADERS,
                new String[]{"Test Tour", "", "500000", "3", "20", "HCM", "Hanoi", "31-02-2026", ""}
        );

        List<ExcelMapper.ImportResult<TourExcelRow>> results = mapper.importSheet(sheet, TourExcelRow.class);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(0).error()).containsIgnoringCase("Departure Date");
    }

    // ── 7. import bad number format ───────────────────────────────────────────

    @Test
    @DisplayName("importSheet() returns error when price cell is not a valid number")
    void importBadNumberFormat() {
        Sheet sheet = sheetWith(
                TOUR_HEADERS,
                new String[]{"Test Tour", "", "abc", "3", "20", "HCM", "Hanoi", "2027-01-01", ""}
        );

        List<ExcelMapper.ImportResult<TourExcelRow>> results = mapper.importSheet(sheet, TourExcelRow.class);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(0).error()).containsIgnoringCase("Price");
    }
}
