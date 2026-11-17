package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.CreateCategoryUseCase;
import com.riwi.skillbridge.application.port.in.DeleteCategoryUseCase;
import com.riwi.skillbridge.application.port.in.ListCategoriesUseCase;
import com.riwi.skillbridge.application.port.in.UpdateCategoryUseCase;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.CategoryResponse;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.CreateCategoryRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Categories", description = "Consulta y gestión de categorías de servicios")
public class CategoryController {
    private final ListCategoriesUseCase listCategories;
    private final CreateCategoryUseCase createCategory;
    private final UpdateCategoryUseCase updateCategory;
    private final DeleteCategoryUseCase deleteCategory;

    public CategoryController(ListCategoriesUseCase listCategories,
                              CreateCategoryUseCase createCategory,
                              UpdateCategoryUseCase updateCategory,
                              DeleteCategoryUseCase deleteCategory) {
        this.listCategories = listCategories;
        this.createCategory = createCategory;
        this.updateCategory = updateCategory;
        this.deleteCategory = deleteCategory;
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

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    @Operation(summary = "Actualiza una categoría existente")
    public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CreateCategoryRequest request) {
        return CategoryResponse.from(updateCategory.update(id, request.name()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    @Operation(summary = "Elimina una categoría")
    public void delete(@PathVariable UUID id) {
        deleteCategory.delete(id);
    }
}
