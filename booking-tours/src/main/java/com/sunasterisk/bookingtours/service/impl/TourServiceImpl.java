package com.sunasterisk.bookingtours.service.impl;

import com.sunasterisk.bookingtours.dto.TourRequest;
import com.sunasterisk.bookingtours.entity.Category;
import com.sunasterisk.bookingtours.entity.Tour;
import com.sunasterisk.bookingtours.entity.TourStatus;
import com.sunasterisk.bookingtours.exception.ResourceNotFoundException;
import com.sunasterisk.bookingtours.repository.CategoryRepository;
import com.sunasterisk.bookingtours.repository.TourRepository;
import com.sunasterisk.bookingtours.service.TourService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai {@link TourService}.
 * Mỗi method public có annotation transaction riêng để kiểm soát rõ ràng.
 */
@Service
@RequiredArgsConstructor
public class TourServiceImpl implements TourService {

    private final TourRepository tourRepository;
    private final CategoryRepository categoryRepository;

    /**
     * {@inheritDoc}
     * Sử dụng {@code readOnly = true} để Hibernate tối ưu flush snapshot tracking.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Tour> search(String keyword, Pageable pageable) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return tourRepository.searchByKeyword(kw, pageable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Tour> searchPublic(String keyword, Long categoryId, Pageable pageable) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return tourRepository.searchPublic(kw, categoryId, TourStatus.ACTIVE, pageable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Tour getById(Long id) {
        return tourRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tour", id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Tour getPublicById(Long id) {
        return tourRepository.findByIdAndStatus(id, TourStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Tour", id));
    }

    /**
     * {@inheritDoc}
     * Kiểm tra title trùng (case-insensitive) trước khi lưu.
     */
    @Override
    @Transactional
    public Tour create(TourRequest tourRequest) {
        String title = tourRequest.getTitle().trim();

        // Ngăn tạo tour trùng tiêu đề (không phân biệt hoa/thường)
        if (tourRepository.existsByTitleIgnoreCase(title)) {
            throw new IllegalArgumentException("Tour title '" + title + "' already exists");
        }

        // Xác nhận category tồn tại
        Long categoryId = tourRequest.getCategoryId();
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));

        Tour tour = Tour.builder()
                .title(title)
                .description(tourRequest.getDescription().trim())
                .price(tourRequest.getPrice())
                .durationDays(tourRequest.getDurationDays())
                .maxParticipants(tourRequest.getMaxParticipants())
                .departureLocation(tourRequest.getDepartureLocation().trim())
                .destination(tourRequest.getDestination().trim())
                .departureDate(tourRequest.getDepartureDate())
                .thumbnailUrl(tourRequest.getThumbnailUrl() != null
                        ? tourRequest.getThumbnailUrl().trim() : null)
                .category(category)
                // Dùng enum trực tiếp — null-safe với fallback ACTIVE
                .status(tourRequest.getStatus() != null
                        ? tourRequest.getStatus() : TourStatus.ACTIVE)
                .build();

        return tourRepository.save(tour);
    }

    /**
     * {@inheritDoc}
     * Kiểm tra title trùng với các tour khác (bỏ qua chính tour đang sửa).
     */
    @Override
    @Transactional
    public Tour update(Long id, TourRequest tourRequest) {
        Tour tour = getById(id);
        String title = tourRequest.getTitle().trim();

        // Ngăn đổi tiêu đề thành tên đã tồn tại ở tour khác
        if (tourRepository.existsByTitleIgnoreCaseAndIdNot(title, id)) {
            throw new IllegalArgumentException("Tour title '" + title + "' already exists");
        }

        tour.setTitle(title);
        tour.setDescription(tourRequest.getDescription().trim());
        tour.setPrice(tourRequest.getPrice());
        tour.setDurationDays(tourRequest.getDurationDays());
        tour.setMaxParticipants(tourRequest.getMaxParticipants());
        tour.setDepartureLocation(tourRequest.getDepartureLocation().trim());
        tour.setDestination(tourRequest.getDestination().trim());
        tour.setDepartureDate(tourRequest.getDepartureDate());
        tour.setThumbnailUrl(tourRequest.getThumbnailUrl() != null
                ? tourRequest.getThumbnailUrl().trim() : null);

        // Cập nhật category — xác nhận tồn tại
        Long categoryId = tourRequest.getCategoryId();
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
        tour.setCategory(category);

        // Cập nhật status — giữ nguyên status cũ nếu request null
        if (tourRequest.getStatus() != null) {
            tour.setStatus(tourRequest.getStatus());
        }

        return tourRepository.save(tour);
    }

    /**
     * {@inheritDoc}
     * Xóa vật lý tour khỏi DB. Các booking liên quan cần được xử lý trước ở business layer.
     */
    @Override
    @Transactional
    public void delete(Long id) {
        Tour tour = getById(id);
        tourRepository.delete(tour);
    }
}
