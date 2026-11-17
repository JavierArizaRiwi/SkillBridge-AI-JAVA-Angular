package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepositoryPort {
    List<Category> findAllActive();
    Optional<Category> findById(UUID id);
    boolean existsByNameIgnoreCase(String name);
    Category save(Category category);
    void delete(UUID id);
}
