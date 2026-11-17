package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CreateCategoryUseCase;
import com.riwi.skillbridge.application.port.in.DeleteCategoryUseCase;
import com.riwi.skillbridge.application.port.in.ListCategoriesUseCase;
import com.riwi.skillbridge.application.port.in.UpdateCategoryUseCase;
import com.riwi.skillbridge.application.port.out.CategoryRepositoryPort;
import com.riwi.skillbridge.application.service.knowledge.KnowledgeIndexService;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.Category;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CategoryService implements ListCategoriesUseCase, CreateCategoryUseCase, UpdateCategoryUseCase, DeleteCategoryUseCase {
    private final CategoryRepositoryPort repository;
    private final KnowledgeIndexService knowledgeIndexService;

    @Autowired
    public CategoryService(CategoryRepositoryPort repository,
                           @Autowired(required = false) KnowledgeIndexService knowledgeIndexService) {
        this.repository = repository;
        this.knowledgeIndexService = knowledgeIndexService;
    }

    public CategoryService(CategoryRepositoryPort repository) {
        this(repository, null);
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
        Category saved = repository.save(new Category(UUID.randomUUID(), normalized, true, Instant.now()));
        if (knowledgeIndexService != null) {
            knowledgeIndexService.reindexCategory(saved);
        }
        return saved;
    }

    @Override
    public Category update(UUID id, String name) {
        Category existing = repository.findById(id)
            .orElseThrow(() -> new BusinessRuleException("La categoría no existe"));

        String normalized = name == null ? "" : name.trim().toUpperCase();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("El nombre de la categoría es obligatorio");
        }
        if (!existing.name().equals(normalized) && repository.existsByNameIgnoreCase(normalized)) {
            throw new BusinessRuleException("La categoría ya existe");
        }

        Category updated = new Category(existing.id(), normalized, existing.active(), existing.createdAt());
        Category saved = repository.save(updated);
        if (knowledgeIndexService != null) {
            knowledgeIndexService.reindexCategory(saved);
        }
        return saved;
    }

    @Override
    public void delete(UUID id) {
        if (repository.findById(id).isEmpty()) {
            throw new BusinessRuleException("La categoría no existe");
        }
        repository.delete(id);
        if (knowledgeIndexService != null) {
            knowledgeIndexService.deleteCategory(id);
        }
    }
}
