package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Category;

import java.util.UUID;

public interface UpdateCategoryUseCase {
    Category update(UUID id, String name);
}
