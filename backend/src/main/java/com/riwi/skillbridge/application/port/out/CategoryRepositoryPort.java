package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Category;
import java.util.List;

public interface CategoryRepositoryPort {
    List<Category> findAllActive();
    boolean existsByNameIgnoreCase(String name);
    Category save(Category category);
}
