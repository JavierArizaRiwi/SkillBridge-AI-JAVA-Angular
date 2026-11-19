package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CreateCategoryUseCase;
import com.riwi.skillbridge.application.port.in.ListCategoriesUseCase;
import com.riwi.skillbridge.application.port.out.CategoryRepositoryPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.Category;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CategoryService implements ListCategoriesUseCase, CreateCategoryUseCase {
    private final CategoryRepositoryPort repository;

    public CategoryService(CategoryRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public List<Category> listActive() {
        return repository.findAllActive();
    }

    @Override
    public Category create(String name) {
        String normalized = name == null ? "" : name.trim().toUpperCase();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("El nombre de la categoría es obligatorio");
        }
        if (repository.existsByNameIgnoreCase(normalized)) {
            throw new BusinessRuleException("La categoría ya existe");
        }
        return repository.save(new Category(UUID.randomUUID(), normalized, true, Instant.now()));
    }
}
