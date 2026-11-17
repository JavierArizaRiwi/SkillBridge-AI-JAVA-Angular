package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.CategoryRepositoryPort;
import com.riwi.skillbridge.domain.model.Category;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.CategoryEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaCategoryRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class CategoryPersistenceAdapter implements CategoryRepositoryPort {
    private final JpaCategoryRepository repository;

    public CategoryPersistenceAdapter(JpaCategoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Category> findAllActive() {
        return repository.findByActiveTrueOrderByNameAsc().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Category> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsByNameIgnoreCase(String name) {
        return repository.existsByNameIgnoreCase(name);
    }

    @Override
    public Category save(Category category) {
        CategoryEntity saved = repository.save(new CategoryEntity(
            category.id(), category.name(), category.active(), category.createdAt()
        ));
        return toDomain(saved);
    }

    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
    }

    private Category toDomain(CategoryEntity entity) {
        return new Category(entity.getId(), entity.getName(), entity.isActive(), entity.getCreatedAt());
    }
}
