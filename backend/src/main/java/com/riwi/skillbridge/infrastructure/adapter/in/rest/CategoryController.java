package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.CreateCategoryUseCase;
import com.riwi.skillbridge.application.port.in.ListCategoriesUseCase;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.CategoryResponse;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.CreateCategoryRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categorias")
@Tag(name = "Categories", description = "Consulta y creación de categorías de servicios")
public class CategoryController {
    private final ListCategoriesUseCase listCategories;
    private final CreateCategoryUseCase createCategory;

    public CategoryController(ListCategoriesUseCase listCategories, CreateCategoryUseCase createCategory) {
        this.listCategories = listCategories;
        this.createCategory = createCategory;
    }

    @GetMapping
    @Operation(summary = "Lista las categorías activas")
    public List<CategoryResponse> list() {
        return listCategories.listActive().stream().map(CategoryResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    @Operation(summary = "Crea una nueva categoría")
    public CategoryResponse create(@Valid @RequestBody CreateCategoryRequest request) {
        return CategoryResponse.from(createCategory.create(request.name()));
    }
}
