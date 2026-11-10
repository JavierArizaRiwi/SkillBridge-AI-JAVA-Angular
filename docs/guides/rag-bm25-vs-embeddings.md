# Guía: BM25 frente a embeddings y bases vectoriales (RAG)

Explica las dos formas de buscar que usa el asistente de HU-26 y por qué el proyecto adopta ambas ([ADR-0008](../adr/0008-rag-retrieval-embeddings-and-vector-store.md)). Los números de los ejemplos son **ilustrativos**: no salen de una medición real.

## 1. El problema: «recuperar» antes de «generar»

Un RAG tiene dos pasos. Primero **recupera** los fragmentos de conocimiento más relevantes para la pregunta; después se los pasa al modelo generativo para que redacte la respuesta **solo con esa información**. Si la recuperación falla, el modelo responde sin base o inventa. Por eso la parte de buscar es la que más decide la calidad.

En SkillBridge los fragmentos son las ofertas activas, las categorías y los documentos de política de `backend/src/main/resources/knowledge/`.

## 2. BM25: buscar por palabras

BM25 es un algoritmo clásico de búsqueda textual. Puntúa cada fragmento según **qué palabras de la pregunta aparecen en él**:

- Una palabra **rara** vale más que una común («cancelar» pesa más que «de»).
- Que una palabra aparezca muchas veces ayuda, pero con rendimientos decrecientes (el parámetro `k1`).
- Los textos muy largos se penalizan un poco (el parámetro `b`).

Forma aproximada de la puntuación, para cada palabra `t` de la pregunta `q` y un fragmento `d`:

```text
score(q, d) = Σ  IDF(t) · tf(t,d)·(k1+1) / ( tf(t,d) + k1·(1 − b + b·|d|/promedio) )
```

Lo importante: **solo cuenta coincidencias de palabras**. No entiende nada.

**Ejemplo.** Pregunta: «¿Con cuánta anticipación puedo cancelar mi reserva?». El documento de cancelación dice «cancelarse con al menos 24 horas de anticipación». Comparten «cancelar», «anticipación» y «reserva»: BM25 lo encuentra bien.

**Dónde falla.** Si la pregunta usa otras palabras, no hay coincidencia:

| Pregunta | Texto del documento | BM25 |
|---|---|---|
| «¿Hasta cuándo puedo anular mi sesión?» | «cancelarse… reserva» | Nada en común («anular»/«sesión» frente a «cancelar»/«reserva») |
| «How early can I cancel my booking?» | texto en español | Casi nada: el inglés no comparte palabras con el español |

## 3. Embeddings: buscar por significado

Un **modelo de embeddings** convierte un texto en una lista de números (un **vector**). Textos con significado parecido quedan con vectores cercanos, aunque usen palabras distintas, y un modelo **multilingüe** coloca frases equivalentes en español e inglés en la misma zona.

La cercanía se mide casi siempre con la **similitud coseno** (el ángulo entre dos vectores):

```text
coseno(a, b) = (a · b) / (|a| · |b|)      →  1 = misma dirección, 0 = sin relación
```

**Ejemplo (ilustrativo).** «How early can I cancel my booking?» y «La reserva debe cancelarse con al menos 24 horas de anticipación» quedarían cerca, aunque no compartan ni una palabra.

**Dónde falla.** Puede acercar textos que *suenan* parecidos pero no responden la pregunta, y distingue mal nombres exactos, siglas o identificadores («Spring Boot 3» frente a «Spring Boot 2»). Además cada consulta necesita calcular un embedding de la pregunta.

## 4. Almacén (base) vectorial

Los vectores hay que guardarlos y buscar los más cercanos a la pregunta. Eso hace un almacén vectorial:

- Guarda por cada fragmento: su texto, sus metadatos (tipo, id de oferta…) y su vector.
- Dada la pregunta convertida en vector, devuelve los **k más cercanos** (búsqueda del vecino más próximo).
- **Búsqueda exacta**: compara con todos. Es perfecta con decenas de fragmentos.
- **Búsqueda aproximada** (índices como HNSW o IVFFlat): sacrifica un poco de exactitud por velocidad cuando hay muchísimos vectores. Con el volumen actual no hace falta, pero es justo lo que conviene practicar.

pgvector es una extensión de PostgreSQL que añade el tipo vector y estos índices. Ventaja: los vectores viven junto a tus datos y se pueden combinar con SQL.

## 5. Comparación

| Aspecto | BM25 | Embeddings + almacén vectorial |
|---|---|---|
| Cómo busca | Palabras | Significado |
| Sinónimos y paráfrasis | No | Sí |
| Entre idiomas | **No** | Sí (modelo multilingüe) |
| Nombres exactos, siglas | **Muy bien** | Regular |
| Determinista | Sí | Depende del modelo |
| Explicable | Sí (se ven las palabras) | Poco |
| Infraestructura | Ninguna | Extensión/servicio vectorial |
| Coste por consulta | Nulo | Una llamada a un modelo |
| Pruebas | Sin red | Modelo falso y determinista |
| Fallo típico | Vocabulario distinto | Vecinos falsos, umbral mal calibrado |

Ninguno es «mejor». **Se complementan**.

## 6. Búsqueda híbrida

Se ejecutan las dos y se **fusionan los rankings**. La técnica más usada es *Reciprocal Rank Fusion* (RRF): cada fragmento suma `1 / (c + posición)` por cada ranking en el que aparece (con `c` una constante; 60 es un valor habitual). Un fragmento bien colocado en ambos sube; uno que solo gana por casualidad en uno, no.

Lo que consigue: BM25 aporta la precisión con términos exactos y los embeddings, la flexibilidad con paráfrasis e idiomas.

## 7. Cómo saber cuál funciona mejor: evaluar

No se decide «a ojo». Se arma un **conjunto de evaluación** versionado:

1. 30 o más preguntas reales en español e inglés.
2. Para cada una, qué fragmento(s) deberían salir.
3. Incluir preguntas **sin respuesta** (el asistente debe decir «sin información»).

Y se mide por estrategia:

- **hit@k:** ¿el fragmento correcto está entre los k primeros?
- **MRR:** cuanto más arriba esté el correcto, más puntúa.
- **Falsos positivos** en las preguntas sin respuesta: el umbral de similitud se calibra aquí.

Resultado esperado a comprobar: los embeddings ganan en inglés sobre ofertas en español y en paráfrasis; BM25 gana en nombres exactos. Si no es así, los números mandan.

## 8. Trampas habituales

- **Cambiar de modelo de embeddings obliga a recalcular todos los vectores.** Dimensión y espacio cambian; mezclar dos modelos en la misma columna da resultados sin sentido.
- La **dimensión** de la columna vectorial debe coincidir con la del modelo.
- El **umbral de similitud** hay que calibrarlo con datos; un valor «razonable» inventado produce falsos positivos o rechaza preguntas válidas.
- Enviar textos a un proveedor externo de embeddings: solo contenido público, nunca datos de usuarios.
- Recalcular embeddings en cada arranque cuesta cuota. Se recalculan solo los fragmentos que cambian.

## 9. Para practicar en el proyecto

1. Entregar primero el adaptador **BM25** y el conjunto de evaluación: es la línea base.
2. Añadir el adaptador **vectorial** y comparar con el mismo conjunto.
3. Probar **híbrido** y quedarse con la estrategia que mejor puntúe.
4. Experimentar: cambiar el umbral, el `topK`, el modelo; ver el efecto en hit@k y en los falsos positivos.
