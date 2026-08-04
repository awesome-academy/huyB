package com.sunasterisk.bookingtours.service.impl;

import com.sunasterisk.bookingtours.entity.Booking;
import com.sunasterisk.bookingtours.entity.BookingStatus;
import com.sunasterisk.bookingtours.excel.BookingExcelExporter;
import com.sunasterisk.bookingtours.service.BookingService;
import com.sunasterisk.bookingtours.service.ExcelExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelExportServiceImpl implements ExcelExportService {

    private final BookingService bookingService;
    private final BookingExcelExporter bookingExcelExporter;

    @Override
    @Transactional(readOnly = true)
    public XSSFWorkbook exportBookings(String keyword, BookingStatus status,
                                       LocalDate fromDate, LocalDate toDate) {
        List<Booking> bookings = bookingService
                .search(keyword, status, fromDate, toDate, Pageable.unpaged())
                .getContent();

        log.info("Excel export: {} bookings matched filters (keyword={}, status={}, from={}, to={})",
                bookings.size(), keyword, status, fromDate, toDate);

        return bookingExcelExporter.export(bookings);
    }
}
