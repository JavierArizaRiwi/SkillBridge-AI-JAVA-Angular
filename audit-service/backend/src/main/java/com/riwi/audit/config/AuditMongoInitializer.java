package com.riwi.audit.config;

import com.riwi.audit.model.BusinessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * Inicializador versionado de la colección {@code audit_events} en MongoDB.
 *
 * <p>MongoDB no es relacional, así que Flyway no aplica aquí. En su lugar, cada
 * cambio de esquema/índices se agrega como un nuevo método versionado en esta
 * clase (V1, V2, ...), documentado con un comentario, simulando el historial de
 * migraciones de Flyway sin añadir una librería extra (decisión explícita del
 * equipo: ver docs/course/BACKLOG.md y la bitácora de Obsidian del proyecto).</p>
 *
 * <p>Se ejecuta con {@link ApplicationRunner} (no {@code @PostConstruct}) para
 * garantizar que el {@link MongoTemplate}/driver ya esté completamente
 * inicializado por el contexto de Spring. Las operaciones son idempotentes:
 * {@code ensureIndex} no falla ni duplica si el índice ya existe.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditMongoInitializer implements ApplicationRunner {

    private static final String COLLECTION = "audit_events";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!mongoTemplate.collectionExists(BusinessEvent.class)) {
            mongoTemplate.createCollection(BusinessEvent.class);
            log.info("[AUDIT-MONGO] Colección '{}' creada.", COLLECTION);
        }
        applyV1Indexes();
    }

    /**
     * V1 — índices iniciales: orden por fecha de ocurrencia (consulta más común,
     * listar eventos recientes primero) y por tipo de evento (usado en /stats).
     */
    private void applyV1Indexes() {
        IndexOperations indexOps = mongoTemplate.indexOps(BusinessEvent.class);

        indexOps.ensureIndex(new Index().on("occurredAt", org.springframework.data.domain.Sort.Direction.DESC)
            .named("occurredAt_idx"));
        indexOps.ensureIndex(new Index().on("eventType", org.springframework.data.domain.Sort.Direction.ASC)
            .named("eventType_idx"));

        log.info("[AUDIT-MONGO] Índices V1 verificados en '{}'.", COLLECTION);
    }
}
