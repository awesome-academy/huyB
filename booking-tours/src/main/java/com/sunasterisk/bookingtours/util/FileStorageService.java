package com.sunasterisk.bookingtours.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Service xử lý upload / xóa file trên local filesystem.
 *
 * <p>File được lưu vào thư mục {@code app.upload.dir} (mặc định: {@code uploads/})
 * ở working directory của ứng dụng, và có thể truy cập qua URL {@code /uploads/<filename>}
 * (đã được cấu hình {@code permitAll} trong SecurityConfig).
 */
@Service
public class FileStorageService {

    /**
     * Thư mục lưu file upload, đọc từ application.properties: {@code app.upload.dir}.
     */
    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /**
     * Lưu file upload vào thư mục {@code uploadDir} với tên {@code <UUID>.<ext>}.
     *
     * <p>Loại file được xác định bằng magic bytes của nội dung thật — KHÔNG tin
     * Content-Type header hay đuôi file client gửi lên (cả hai đều giả mạo được;
     * một file .html khai là image/png sẽ được serve dưới dạng text/html từ
     * /uploads/ → stored XSS). Tên file gốc bị bỏ hoàn toàn nên không còn
     * bề mặt path traversal.
     *
     * @param file file cần lưu, không được null và không được rỗng
     * @return đường dẫn URL tương đối để lưu vào DB, ví dụ {@code /uploads/abc123.jpg}
     * @throws IOException              nếu gặp lỗi I/O khi ghi file
     * @throws IllegalArgumentException nếu nội dung file không phải ảnh JPEG/PNG/GIF/WebP
     */
    public String store(MultipartFile file) throws IOException {
        byte[] data = file.getBytes();

        String extension = detectImageExtension(data);
        if (extension == null) {
            throw new IllegalArgumentException(
                    "Invalid file content. Allowed: JPEG, PNG, GIF, WebP");
        }

        String storedFilename = UUID.randomUUID() + "." + extension;

        // Tạo thư mục nếu chưa tồn tại
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);

        Files.write(uploadPath.resolve(storedFilename), data);

        // Trả về URL tương đối để lưu vào DB
        return "/uploads/" + storedFilename;
    }

    /**
     * Xóa file khỏi filesystem theo URL tương đối.
     * Bỏ qua nếu URL null hoặc không bắt đầu bằng {@code /uploads/}.
     * Kiểm tra containment để đảm bảo chỉ xóa file trong uploadDir.
     *
     * @param fileUrl URL tương đối, ví dụ {@code /uploads/abc123.jpg}
     */
    public void delete(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith("/uploads/")) {
            return;
        }
        String filename = fileUrl.substring("/uploads/".length());
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path filePath = uploadPath.resolve(filename).normalize();

        // Đảm bảo đường dẫn nằm trong uploadDir trước khi xóa
        if (!filePath.startsWith(uploadPath)) {
            return;
        }
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            // Log warning nhưng không ném exception — việc xóa file cũ là best-effort
            Logger.getLogger(FileStorageService.class.getName())
                    .warning("Failed to delete uploaded file '" + filePath + "': " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------

    /**
     * Nhận diện định dạng ảnh qua magic bytes.
     *
     * @return đuôi file tương ứng ({@code jpg/png/gif/webp}) hoặc {@code null}
     *         nếu nội dung không phải một trong các định dạng được hỗ trợ
     */
    private String detectImageExtension(byte[] data) {
        if (data.length >= 3
                && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (data.length >= 8
                && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G'
                && data[4] == 0x0D && data[5] == 0x0A && data[6] == 0x1A && data[7] == 0x0A) {
            return "png";
        }
        if (data.length >= 6
                && data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8'
                && (data[4] == '7' || data[4] == '9') && data[5] == 'a') {
            return "gif";
        }
        if (data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
