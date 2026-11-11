package com.riwi.audit.repository;

import com.riwi.audit.model.BusinessEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persistencia real en MongoDB (colección {@code audit_events}), en reemplazo
 * de la lista en memoria original (se perdía al reiniciar el servicio).
 * Spring Data implementa {@code save} y {@code findById} automáticamente.
 */
public interface AuditEventRepository extends MongoRepository<BusinessEvent, UUID> {

    List<BusinessEvent> findAllByOrderByOccurredAtDesc();
}
