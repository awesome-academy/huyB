package com.sunasterisk.bookingtours.service.impl;

import com.sunasterisk.bookingtours.entity.*;
import com.sunasterisk.bookingtours.excel.TourExcelImporter;
import com.sunasterisk.bookingtours.excel.TourExcelImporter.ImportRowResult;
import com.sunasterisk.bookingtours.repository.CategoryRepository;
import com.sunasterisk.bookingtours.repository.TourImportJobRepository;
import com.sunasterisk.bookingtours.repository.TourRepository;
import com.sunasterisk.bookingtours.service.ExcelImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelImportServiceImpl implements ExcelImportService {

    private static final long   MAX_FILE_SIZE = 5L * 1024 * 1024; // 5 MB
    private static final int    MAX_ROWS      = 500;
    private static final String[] TEMPLATE_HEADERS = {
            "Title", "Description", "Price", "Duration Days",
            "Max Participants", "Departure Location", "Destination",
            "Departure Date (yyyy-MM-dd)", "Category Name"
    };

    private final TourImportJobRepository tourImportJobRepository;
    private final TourExcelImporter       tourExcelImporter;
    private final CategoryRepository      categoryRepository;
    private final TourRepository          tourRepository;

    @Override
    @Transactional
    public TourImportJob importTours(MultipartFile file, Long createdBy) {
        // === Phase 1: validate BEFORE any DB write (H2 fix) ===
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File không được để trống");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException("Chỉ chấp nhận file .xlsx");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File vượt quá giới hạn 5MB");
        }

        // Read file bytes so workbook can be opened without holding the stream
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Không thể đọc file: " + e.getMessage());
        }

        // Pre-check row count BEFORE creating job record
        try (Workbook probe = WorkbookFactory.create(new ByteArrayInputStream(fileBytes))) {
            int dataRows = probe.getSheetAt(0).getLastRowNum();
            if (dataRows > MAX_ROWS) {
                throw new IllegalArgumentException("File chứa quá " + MAX_ROWS + " dòng dữ liệu");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("File Excel không hợp lệ: " + e.getMessage());
        }

        // === Phase 2: create job record (all validation passed) ===
        TourImportJob job = TourImportJob.builder()
                .fileName(originalName)
                .status(TourImportJob.ImportJobStatus.PROCESSING)
                .createdBy(createdBy)
                .build();
        job = tourImportJobRepository.save(job);

        // === Phase 3: process ===
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            int dataRows = sheet.getLastRowNum(); // row 0 = header
            if (dataRows <= 0) {
                return finishJob(job, 0, 0, 0, "[]");
            }

            // Preload both name→id and id→entity maps (M1 fix: eliminates N+1 in persist loop)
            List<Category> allCategories = categoryRepository.findAll();
            Map<String, Long> categoryByName = allCategories.stream()
                    .collect(Collectors.toMap(
                            c -> c.getName().toLowerCase(),
                            Category::getId,
                            (a, b) -> a));
            Map<Long, Category> categoryById = allCategories.stream()
                    .collect(Collectors.toMap(Category::getId, c -> c, (a, b) -> a));

            // Fan-out parse sang importExecutor (workers chỉ parse, không JPA)
            List<ImportRowResult> results = tourExcelImporter.parseRows(sheet, categoryByName);

            // Persist thành công và thu thập lỗi — đơn luồng, transaction hiện tại
            int successRows = 0;
            int failedRows  = 0;
            List<String> errors = new ArrayList<>();

            for (ImportRowResult r : results) {
                if (!r.success()) {
                    failedRows++;
                    errors.add("{\"row\":" + r.rowNum() + ",\"reason\":\"" + escape(r.error()) + "\"}");
                    continue;
                }
                // Bỏ qua dòng có title trùng
                if (tourRepository.existsByTitleIgnoreCase(r.title())) {
                    failedRows++;
                    errors.add("{\"row\":" + r.rowNum() + ",\"reason\":\"Title '" + escape(r.title()) + "' already exists\"}");
                    continue;
                }
                try {
                    // M1 fix: use preloaded map, no extra DB query
                    Category category = (r.categoryId() != null)
                            ? categoryById.get(r.categoryId())
                            : null;

                    Tour tour = Tour.builder()
                            .title(r.title())
                            .description(r.description() != null ? r.description() : "")
                            .price(r.price())
                            .durationDays(r.durationDays())
                            .maxParticipants(r.maxParticipants())
                            .departureLocation(r.departureLocation())
                            .destination(r.destination())
                            .departureDate(r.departureDate())
                            .category(category)
                            .status(TourStatus.INACTIVE)
                            .build();
                    tourRepository.save(tour);
                    successRows++;
                } catch (Exception e) {
                    log.error("Row {} save failed: {}", r.rowNum(), e.getMessage());
                    failedRows++;
                    errors.add("{\"row\":" + r.rowNum() + ",\"reason\":\"Save error: " + escape(e.getMessage()) + "\"}");
                }
            }

            String errorJson = "[" + String.join(",", errors) + "]";
            log.info("Import job {}: total={} success={} failed={}",
                    job.getId(), results.size(), successRows, failedRows);
            return finishJob(job, results.size(), successRows, failedRows, errorJson);

        } catch (IOException e) {
            log.error("Failed to read Excel file: {}", e.getMessage());
            job.setStatus(TourImportJob.ImportJobStatus.FAILED);
            job.setErrorDetails(e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            return tourImportJobRepository.save(job);
        }
    }

    @Override
    public XSSFWorkbook generateTemplate() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet("Tours");
        var header = sheet.createRow(0);
        for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
            header.createCell(i).setCellValue(TEMPLATE_HEADERS[i]);
            sheet.setColumnWidth(i, 5000);
        }
        return workbook;
    }

    private TourImportJob finishJob(TourImportJob job, int total, int success, int failed, String errorDetails) {
        job.setStatus(TourImportJob.ImportJobStatus.COMPLETED);
        job.setTotalRows(total);
        job.setSuccessRows(success);
        job.setFailedRows(failed);
        job.setErrorDetails(errorDetails);
        job.setCompletedAt(LocalDateTime.now());
        return tourImportJobRepository.save(job);
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
