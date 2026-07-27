package com.sunasterisk.bookingtours.service.impl;

import com.sunasterisk.bookingtours.dto.CategoryRequest;
import com.sunasterisk.bookingtours.entity.Category;
import com.sunasterisk.bookingtours.exception.ResourceNotFoundException;
import com.sunasterisk.bookingtours.repository.CategoryRepository;
import com.sunasterisk.bookingtours.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Category> getAll() {
        return categoryRepository.findAll(
                org.springframework.data.domain.Sort.by("name").ascending());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Category> search(String keyword, Pageable pageable) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return categoryRepository.searchByKeyword(kw, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Category getById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    @Override
    @Transactional
    public Category create(CategoryRequest request) {
        String name = request.getName().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException(
                    "Category name '" + name + "' already exists");
        }

        Category category = Category.builder()
                .name(name)
                .description(request.getDescription() != null
                        ? request.getDescription().trim() : null)
                .build();

        return categoryRepository.save(category);
    }

    @Override
    @Transactional
    public Category update(Long id, CategoryRequest request) {
        Category category = getById(id);
        String name = request.getName().trim();

        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new IllegalArgumentException(
                    "Category name '" + name + "' already exists");
        }

        category.setName(name);
        category.setDescription(request.getDescription() != null
                ? request.getDescription().trim() : null);

        return categoryRepository.save(category);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Category category = getById(id);
        categoryRepository.delete(category);
    }
}
