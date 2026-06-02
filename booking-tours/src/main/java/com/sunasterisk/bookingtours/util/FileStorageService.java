package com.sunasterisk.bookingtours.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
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
     * Các định dạng ảnh được chấp nhận khi upload thumbnail.
     */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    /**
     * Thư mục lưu file upload, đọc từ application.properties: {@code app.upload.dir}.
     */
    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /**
     * Lưu file upload vào thư mục {@code uploadDir} với tên unique (UUID prefix).
     *
     * @param file file cần lưu, không được null và không được rỗng
     * @return đường dẫn URL tương đối để lưu vào DB, ví dụ {@code /uploads/abc123_photo.jpg}
     * @throws IOException              nếu gặp lỗi I/O khi ghi file
     * @throws IllegalArgumentException nếu content-type không phải ảnh hợp lệ, hoặc tên file không hợp lệ
     */
    public String store(MultipartFile file) throws IOException {
        // Kiểm tra content-type hợp lệ
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Invalid file type. Allowed: JPEG, PNG, GIF, WebP");
        }

        // Lấy phần tên file thuần (không có directory component) để chống path traversal.
        // StringUtils.cleanPath một mình không đủ: "uuid_../../../etc/passwd" vẫn escape
        // khỏi uploadDir khi được resolve(). Path.getFileName() trả về component cuối cùng,
        // loại bỏ hoàn toàn mọi dấu phân cách thư mục do browser/client gửi lên.
        String raw = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        String safeFilename = Paths.get(StringUtils.cleanPath(raw)).getFileName().toString();

        // Từ chối tên file rỗng hoặc còn chứa ".." sau khi đã làm sạch
        if (safeFilename.isBlank() || safeFilename.contains("..")) {
            throw new IllegalArgumentException("Invalid filename: " + raw);
        }

        // Tạo tên file unique bằng UUID để tránh trùng
        String storedFilename = UUID.randomUUID() + "_" + safeFilename;

        // Tạo thư mục nếu chưa tồn tại
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);

        // Resolve rồi normalize, sau đó kiểm tra containment để đảm bảo file
        // không bị ghi ra ngoài uploadDir dù có bất kỳ ký tự nào còn sót lại.
        Path targetPath = uploadPath.resolve(storedFilename).normalize();
        if (!targetPath.startsWith(uploadPath)) {
            throw new IllegalArgumentException("Invalid file path detected: " + storedFilename);
        }

        // Ghi file (ghi đè nếu tình cờ trùng tên — thực tế UUID đảm bảo unique)
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        // Trả về URL tương đối để lưu vào DB
        return "/uploads/" + storedFilename;
    }

    /**
     * Xóa file khỏi filesystem theo URL tương đối.
     * Bỏ qua nếu URL null hoặc không bắt đầu bằng {@code /uploads/}.
     * Kiểm tra containment để đảm bảo chỉ xóa file trong uploadDir.
     *
     * @param fileUrl URL tương đối, ví dụ {@code /uploads/abc123_photo.jpg}
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
}
