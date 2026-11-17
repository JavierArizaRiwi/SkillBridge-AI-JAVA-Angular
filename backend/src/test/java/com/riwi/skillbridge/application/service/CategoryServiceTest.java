package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.CategoryRepositoryPort;
import com.riwi.skillbridge.application.service.knowledge.KnowledgeIndexService;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("CategoryService Unit Tests (T024, FR-015)")
class CategoryServiceTest {

    private CategoryRepositoryPort repository;
    private KnowledgeIndexService knowledgeIndexService;
    private CategoryService service;

    @BeforeEach
    void setUp() {
        repository = mock(CategoryRepositoryPort.class);
        knowledgeIndexService = mock(KnowledgeIndexService.class);
        service = new CategoryService(repository, knowledgeIndexService);
    }

    @Test
    @DisplayName("listActive debe retornar todas las categorías activas del repositorio")
    void listActive_retorna_categorias_activas() {
        Category c1 = new Category(UUID.randomUUID(), "BACKEND", true, Instant.now());
        Category c2 = new Category(UUID.randomUUID(), "FRONTEND", true, Instant.now());
        when(repository.findAllActive()).thenReturn(List.of(c1, c2));

        List<Category> result = service.listActive();

        assertEquals(2, result.size());
        assertEquals("BACKEND", result.get(0).name());
        assertEquals("FRONTEND", result.get(1).name());
        verify(repository).findAllActive();
    }

    @Test
    @DisplayName("create guarda la categoría en mayúsculas y notifica a KnowledgeIndexService")
    void create_guarda_categoria_y_notifica_reindexacion() {
        when(repository.existsByNameIgnoreCase("DEVOPS")).thenReturn(false);
        when(repository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        Category created = service.create("devops");

        assertNotNull(created);
        assertEquals("DEVOPS", created.name());
        assertTrue(created.active());
        verify(repository).save(argThat(c -> c.name().equals("DEVOPS") && c.active()));
        verify(knowledgeIndexService, times(1)).reindexCategory(created);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    @DisplayName("create lanza IllegalArgumentException si el nombre es nulo o blanco")
    void create_falla_si_nombre_es_vacio(String blankName) {
        assertThrows(IllegalArgumentException.class, () -> service.create(blankName));
        verifyNoInteractions(repository);
        verifyNoInteractions(knowledgeIndexService);
    }

    @Test
    @DisplayName("create lanza BusinessRuleException si la categoría ya existe")
    void create_falla_si_categoria_ya_existe() {
        when(repository.existsByNameIgnoreCase("BACKEND")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.create("backend"));

        verify(repository, never()).save(any());
        verifyNoInteractions(knowledgeIndexService);
    }

    @Test
    @DisplayName("update actualiza nombre en mayúsculas y notifica a KnowledgeIndexService")
    void update_actualiza_categoria_y_notifica_reindexacion() {
        UUID id = UUID.randomUUID();
        Category existing = new Category(id, "OLD_NAME", true, Instant.now().minusSeconds(3600));
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.existsByNameIgnoreCase("NEW_NAME")).thenReturn(false);
        when(repository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        Category updated = service.update(id, "new_name");

        assertNotNull(updated);
        assertEquals("NEW_NAME", updated.name());
        assertEquals(id, updated.id());
        verify(repository).save(argThat(c -> c.name().equals("NEW_NAME") && c.id().equals(id)));
        verify(knowledgeIndexService, times(1)).reindexCategory(updated);
    }

    @Test
    @DisplayName("update falla si la categoría no existe")
    void update_falla_si_categoria_no_existe() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class, () -> service.update(id, "ANY"));
        verify(repository, never()).save(any());
        verifyNoInteractions(knowledgeIndexService);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("update falla si el nuevo nombre es nulo o blanco")
    void update_falla_si_nuevo_nombre_es_blanco(String blankName) {
        UUID id = UUID.randomUUID();
        Category existing = new Category(id, "EXISTING", true, Instant.now());
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> service.update(id, blankName));
        verify(repository, never()).save(any());
        verifyNoInteractions(knowledgeIndexService);
    }

    @Test
    @DisplayName("update falla si el nuevo nombre colisiona con otra categoría existente")
    void update_falla_si_nuevo_nombre_colisiona() {
        UUID id = UUID.randomUUID();
        Category existing = new Category(id, "ORIGINAL", true, Instant.now());
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.existsByNameIgnoreCase("OTHER")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.update(id, "OTHER"));
        verify(repository, never()).save(any());
        verifyNoInteractions(knowledgeIndexService);
    }

    @Test
    @DisplayName("delete elimina categoría y notifica a KnowledgeIndexService")
    void delete_elimina_categoria_y_notifica_a_knowledge_index() {
        UUID id = UUID.randomUUID();
        Category existing = new Category(id, "TO_DELETE", true, Instant.now());
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        service.delete(id);

        verify(repository).delete(id);
        verify(knowledgeIndexService, times(1)).deleteCategory(id);
    }

    @Test
    @DisplayName("delete falla si la categoría a eliminar no existe")
    void delete_falla_si_categoria_no_existe() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class, () -> service.delete(id));
        verify(repository, never()).delete(any());
        verifyNoInteractions(knowledgeIndexService);
    }

    @Test
    @DisplayName("Criterio T024: un cambio de categoría reindexa ese fragmento en KnowledgeIndexService")
    void cambio_de_categoria_reindexa_ese_fragmento() {
        UUID id = UUID.randomUUID();
        Category existing = new Category(id, "MACHINE_LEARNING", true, Instant.now());
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.existsByNameIgnoreCase("AI_ML")).thenReturn(false);
        when(repository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        Category updated = service.update(id, "ai_ml");

        assertEquals("AI_ML", updated.name());
        // Verifica que la llamada a KnowledgeIndexService recibió exactamente la categoría actualizada
        verify(knowledgeIndexService).reindexCategory(argThat(c ->
                c.id().equals(id) && c.name().equals("AI_ML")
        ));
    }
}
