package com.riwi.skillbridge.application.service.knowledge;

import com.riwi.skillbridge.domain.knowledge.KnowledgeChunk;
import com.riwi.skillbridge.domain.knowledge.KnowledgeSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validador de citas y conciliador de fuentes para respuestas generativas (HU-26, FR-007, SC-003).
 * <p>
 * Responsabilidades:
 * <ul>
 *     <li>Identificar las fuentes citadas o referenciadas en el texto generado por el LLM.</li>
 *     <li>Descartar identificadores alucinados o inventados por el LLM que no formaban parte
 *         de los fragmentos inyectados en el contexto.</li>
 *     <li>Descartar fuentes inyectadas que no hayan sido efectivamente citadas en la respuesta.</li>
 *     <li>Garantizar que cada identificador válido citado se devuelva una sola vez (deduplicación).</li>
 * </ul>
 * <p>
 * Clase de aplicación pura sin dependencias de Spring, Jakarta ni Hibernate.
 */
public final class SourceCitationValidator {

    /**
     * Valida y filtra los identificadores citados en la respuesta generada por el LLM.
     * Retorna únicamente aquellos {@code sourceId} que están presentes en el texto de la respuesta
     * y que pertenecen al conjunto de fuentes inyectadas, preservando el orden de aparición y sin duplicados.
     *
     * @param answer             texto redactado generado por el LLM
     * @param injectedSourceIds colección o conjunto de identificadores inyectados en el contexto
     * @return lista de {@code sourceId} válidos citados, sin duplicados
     */
    public List<String> validateCitations(String answer, Collection<String> injectedSourceIds) {
        if (answer == null || answer.isBlank() || injectedSourceIds == null || injectedSourceIds.isEmpty()) {
            return List.of();
        }

        // Normalizar los candidatos eliminando nulos y vacíos
        Set<String> validInjectedIds = injectedSourceIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (validInjectedIds.isEmpty()) {
            return List.of();
        }

        // Registrar la primera posición de aparición de cada sourceId inyectado en la respuesta
        Map<String, Integer> firstOccurrencePositions = new LinkedHashMap<>();

        for (String sourceId : validInjectedIds) {
            // El patrón asegura límite de identificador: no precedido ni seguido por caracteres de identificador
            // (letras, dígitos, guion bajo, dos puntos o guion)
            Pattern pattern = Pattern.compile(
                    "(?<![a-zA-Z0-9_:-])" + Pattern.quote(sourceId) + "(?![a-zA-Z0-9_:-])",
                    Pattern.CASE_INSENSITIVE
            );
            Matcher matcher = pattern.matcher(answer);
            if (matcher.find()) {
                firstOccurrencePositions.put(sourceId, matcher.start());
            }
        }

        // Ordenar por la posición de primera aparición en el texto de la respuesta
        return firstOccurrencePositions.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Sobrecarga conveniente que acepta un varargs de identificadores inyectados.
     *
     * @param answer            texto generado por el LLM
     * @param injectedSourceIds identificadores inyectados
     * @return lista de {@code sourceId} válidos citados sin duplicados
     */
    public List<String> validateCitations(String answer, String... injectedSourceIds) {
        if (injectedSourceIds == null) {
            return List.of();
        }
        return validateCitations(answer, Arrays.asList(injectedSourceIds));
    }

    /**
     * Alias de conveniencia para {@link #validateCitations(String, Collection)}.
     *
     * @param answer            texto redactado por el LLM
     * @param injectedSourceIds conjunto o colección de fuentes inyectadas
     * @return lista de identificadores citados y verificados
     */
    public List<String> validate(String answer, Collection<String> injectedSourceIds) {
        return validateCitations(answer, injectedSourceIds);
    }

    /**
     * Variante que retorna el resultado como {@link Set}.
     *
     * @param answer            texto generado por el LLM
     * @param injectedSourceIds identificadores inyectados
     * @return conjunto ordenado de identificadores citados válidos
     */
    public Set<String> validateCitationsAsSet(String answer, Collection<String> injectedSourceIds) {
        return new LinkedHashSet<>(validateCitations(answer, injectedSourceIds));
    }

    /**
     * Valida citas directamente a partir de una colección de {@link KnowledgeChunk} inyectados.
     *
     * @param answer texto generado por el LLM
     * @param chunks fragmentos inyectados en el contexto
     * @return lista de {@code sourceId} válidos citados
     */
    public List<String> validateChunkCitations(String answer, Collection<KnowledgeChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<String> sourceIds = chunks.stream()
                .filter(Objects::nonNull)
                .map(KnowledgeChunk::sourceId)
                .filter(Objects::nonNull)
                .toList();
        return validateCitations(answer, sourceIds);
    }

    /**
     * Filtra una lista de candidatos {@link KnowledgeSource} para conservar únicamente aquellos
     * cuyo {@code sourceId} haya sido citado en el texto de la respuesta (FR-007, SC-003).
     *
     * @param answer  texto redactado por el LLM
     * @param sources fuentes candidatas recuperadas
     * @return lista de fuentes efectivamente citadas, ordenadas por aparición y sin duplicados
     */
    public List<KnowledgeSource> filterValidSources(String answer, Collection<KnowledgeSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }

        Map<String, KnowledgeSource> bySourceId = new LinkedHashMap<>();
        for (KnowledgeSource source : sources) {
            if (source != null && !bySourceId.containsKey(source.sourceId())) {
                bySourceId.put(source.sourceId(), source);
            }
        }

        List<String> validIds = validateCitations(answer, bySourceId.keySet());
        return validIds.stream()
                .map(bySourceId::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
