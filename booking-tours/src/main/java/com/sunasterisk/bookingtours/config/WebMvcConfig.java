package com.sunasterisk.bookingtours.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Cấu hình MVC để serve file upload từ filesystem ra ngoài qua HTTP.
 *
 * <p>Spring Boot mặc định chỉ serve static resources từ classpath (resources/static/).
 * File được upload (thumbnail tour, v.v.) lưu vào thư mục {@code app.upload.dir} trên
 * filesystem, cần đăng ký resource handler để browser có thể truy cập qua URL
 * {@code /uploads/**}.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * Thư mục lưu file upload — đọc từ {@code app.upload.dir} trong application.properties.
     * Mặc định: {@code uploads} (thư mục tương đối so với working directory).
     */
    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /**
     * Map URL {@code /uploads/**} tới thư mục filesystem {@code uploadDir}.
     *
     * <p>Ví dụ: file tại {@code uploads/abc123_photo.jpg} sẽ có thể truy cập qua
     * {@code http://localhost:8080/uploads/abc123_photo.jpg}.
     */
    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // Bước 1: Lấy absolute path của thư mục uploads/
        // uploadDir = "uploads" (từ app.upload.dir trong application.properties)
        // Kết quả: "file:///Users/nguyen.duc.huyb/.../booking-tours/uploads/"
        String absoluteUploadPath = Paths.get(uploadDir) // Path object đại diện cho path tương đối "uploads"
                .toAbsolutePath() // Chuyển path tương đối → tuyệt đối bằng cách ghép với working directory
                .normalize() // Loại bỏ các thành phần dư thừa hoặc mơ hồ trong path, tránh path traversal attack
                .toUri() // Chuyển Path → URI object theo chuẩn RFC 3986. URI này sẽ có schema "file://", đảm bảo Spring hiểu đây là path trên filesystem chứ không phải classpath.
                .toString();

        // Bước 2: Đảm bảo path kết thúc bằng "/" (Spring yêu cầu)
        if (!absoluteUploadPath.endsWith("/")) {
            absoluteUploadPath += "/";
        }

        // Bước 3: Đăng ký mapping URL pattern /uploads/** tới thư mục filesystem
        // VD: GET /uploads/abc123_photo.jpg
        // → tìm file tại: <working_dir>/uploads/abc123_photo.jpg
        registry.addResourceHandler("/uploads/**") // URL pattern
                .addResourceLocations(absoluteUploadPath); // Nơi tìm file
    }
}
