package com.riwi.audit.repository;

import com.riwi.audit.model.BusinessEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditEventRepositoryTest {

    private AuditEventRepository repository;

    @BeforeEach
    void setUp() {
        repository = new AuditEventRepository();
    }

    @Test
    void shouldSaveAndLimitEvents() {
        for (int i = 0; i < 510; i++) {
            repository.save(new BusinessEvent(UUID.randomUUID(), "Test", "id", "type", null, "corr", 1, null));
        }

        assertEquals(500, repository.findAll().size());
    }
}
