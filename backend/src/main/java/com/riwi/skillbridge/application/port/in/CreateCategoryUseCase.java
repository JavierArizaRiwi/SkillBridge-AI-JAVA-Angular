package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Category;

public interface CreateCategoryUseCase {
    Category create(String name);
}
