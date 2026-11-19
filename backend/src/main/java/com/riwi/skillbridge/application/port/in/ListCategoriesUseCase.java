package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Category;
import java.util.List;

public interface ListCategoriesUseCase {
    List<Category> listActive();
}
