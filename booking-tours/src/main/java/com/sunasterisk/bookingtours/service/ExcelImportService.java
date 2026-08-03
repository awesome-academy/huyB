package com.sunasterisk.bookingtours.service;

import com.sunasterisk.bookingtours.entity.TourImportJob;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

/**
 * Xử lý import tour hàng loạt từ file Excel.
 */
public interface ExcelImportService {

    /**
     * Nhận file .xlsx, parse song song, lưu các dòng hợp lệ thành tour INACTIVE.
     *
     * @param file      file .xlsx upload từ admin
     * @param createdBy id của admin đang thực hiện import
     * @return {@link TourImportJob} với trạng thái COMPLETED / FAILED và thống kê số dòng
     */
    TourImportJob importTours(MultipartFile file, Long createdBy);

    /**
     * Tạo file .xlsx template trống với đúng header để admin tải về.
     *
     * @return XSSFWorkbook template sẵn sàng ghi ra response stream
     */
    XSSFWorkbook generateTemplate();
}
