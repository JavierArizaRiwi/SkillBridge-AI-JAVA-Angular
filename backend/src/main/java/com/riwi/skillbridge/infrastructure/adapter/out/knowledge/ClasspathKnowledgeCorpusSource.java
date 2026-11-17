package com.riwi.skillbridge.infrastructure.adapter.out.knowledge;

import com.riwi.skillbridge.application.port.out.KnowledgeCorpusSourcePort;
import com.riwi.skillbridge.application.port.out.KnowledgeSourceDocument;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Adaptador de infraestructura para cargar documentos del corpus de conocimiento
 * desde el classpath de la aplicación (HU-26, FR-004, FR-014).
 * <p>
 * Lee exclusivamente archivos Markdown bajo {@code classpath*:knowledge/*.md},
 * aplicando el filtro de exclusión para segmentos de ruta prohibidos ('docs') y
 * restricción a 'knowledge'.
 */
@Component
public class ClasspathKnowledgeCorpusSource implements KnowledgeCorpusSourcePort {

    public static final String DEFAULT_RESOURCE_PATTERN = "classpath*:knowledge/*.md";
    private static final String REQUIRED_PATH_KEYWORD = "knowledge";
    private static final String FORBIDDEN_PATH_KEYWORD = "docs";

    private final ResourcePatternResolver resourcePatternResolver;
    private final String resourcePattern;

    public ClasspathKnowledgeCorpusSource() {
        this(new PathMatchingResourcePatternResolver(), DEFAULT_RESOURCE_PATTERN);
    }

    public ClasspathKnowledgeCorpusSource(ResourcePatternResolver resourcePatternResolver) {
        this(resourcePatternResolver, DEFAULT_RESOURCE_PATTERN);
    }

    public ClasspathKnowledgeCorpusSource(ResourcePatternResolver resourcePatternResolver, String resourcePattern) {
        validateResourcePattern(resourcePattern);
        this.resourcePatternResolver = Objects.requireNonNull(resourcePatternResolver, "resourcePatternResolver no puede ser nulo");
        this.resourcePattern = resourcePattern;
    }

    @Override
    public List<KnowledgeSourceDocument> loadDocuments() {
        return loadFromPattern(this.resourcePattern);
    }

    /**
     * Carga documentos a partir de un patrón de recursos específico.
     *
     * @param pattern patrón de recursos (debe contener 'knowledge' y no contener 'docs')
     * @return lista inmutable de documentos de conocimiento
     */
    public List<KnowledgeSourceDocument> loadFromPattern(String pattern) {
        validateResourcePattern(pattern);
        try {
            Resource[] resources = resourcePatternResolver.getResources(pattern);
            return loadFromResources(resources);
        } catch (IOException e) {
            throw new IllegalStateException("Error al resolver los recursos de conocimiento con el patrón: " + pattern, e);
        }
    }

    /**
     * Carga documentos a partir de un arreglo de recursos {@link Resource}.
     *
     * @param resources recursos a procesar
     * @return lista inmutable de documentos de conocimiento
     */
    public List<KnowledgeSourceDocument> loadFromResources(Resource... resources) {
        if (resources == null || resources.length == 0) {
            return List.of();
        }

        List<KnowledgeSourceDocument> documents = new ArrayList<>();
        for (Resource resource : resources) {
            if (resource == null || !resource.exists()) {
                continue;
            }

            String filename = resource.getFilename();
            if (filename == null || !filename.endsWith(".md")) {
                continue;
            }

            String uriStr = null;
            try {
                uriStr = resource.getURI().toString();
                String uriLower = uriStr.toLowerCase(Locale.ROOT);
                if (hasPathSegment(uriLower, FORBIDDEN_PATH_KEYWORD) || !hasPathSegment(uriLower, REQUIRED_PATH_KEYWORD)) {
                    continue;
                }
            } catch (IOException ignored) {
                // Si el URI no puede obtenerse (ej. en ciertos entornos virtuales), se continúa con la lectura
            }

            if (uriStr == null) {
                uriStr = filename;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
                documents.add(new KnowledgeSourceDocument(filename, uriStr, builder.toString()));
            } catch (IOException e) {
                throw new IllegalStateException("Error al leer el recurso de conocimiento: " + filename, e);
            }
        }

        return Collections.unmodifiableList(documents);
    }

    /**
     * Comprueba que {@code segment} aparece como segmento completo de ruta, no como parte de otro nombre.
     * Así {@code /mi-proyecto-docs/} no cuenta como {@code docs} (evita descartar todo el corpus en silencio).
     */
    public static boolean hasPathSegment(String text, String segment) {
        return Pattern.compile("(?<![\\w.-])" + Pattern.quote(segment) + "(?![\\w.-])")
                .matcher(text)
                .find();
    }

    private void validateResourcePattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("El patrón de recursos no puede ser nulo ni estar vacío");
        }
        String lower = pattern.toLowerCase(Locale.ROOT);
        if (hasPathSegment(lower, FORBIDDEN_PATH_KEYWORD)) {
            throw new IllegalArgumentException("Acceso prohibido: no se permite la lectura de la ruta 'docs/' (FR-004)");
        }
        if (!hasPathSegment(lower, REQUIRED_PATH_KEYWORD)) {
            throw new IllegalArgumentException("Acceso restringido: solo se permite la lectura dentro del directorio 'knowledge/' (FR-004)");
        }
    }
}
