# HU-23 Recomendaciones multimodales

Backend acepta texto, voz e imagen mediante Gemini. Los endpoints son `POST /api/ai/recommendations`, `/voice` y `/image`; los dos últimos reciben `multipart/form-data` con `file`.

Audio permitido: WAV, MP3, MP4 y WEBM hasta 10 MB. Imagen permitida: JPEG, PNG y WEBP hasta 5 MB. 
Los archivos se procesan en memoria y no se almacenan. 
El resultado contiene `recommendationId`, `inputType`, explicación y recomendaciones. 
Se publican eventos solicitada y generada con correlation ID.

## QA

| Caso | Cobertura |
|---|---|
| Texto | `POST /api/ai/recommendations` produce resultado con `recommendationId`. |
| Voz | `POST /api/ai/recommendations/voice` valida audio y convierte a contexto Gemini. |
| Imagen | `POST /api/ai/recommendations/image` valida imagen y convierte a contexto Gemini. |
| Archivo inválido | `MediaFileValidatorTest` rechaza vacío y MIME no permitido. |
| Analytics | Se publican `RecommendationRequested` y `RecommendationGenerated` con `inputType`. |

## Verificación

- `mvn -q -DskipTests compile`: correcto.
- `mvn -q -DskipTests test-compile`: correcto.
- `npm run build`: correcto.
- La suite Maven completa depende de Docker para Testcontainers y de adjuntar el agente Mockito; el entorno local 
- actual no permite ambos.

## Variables de entorno

- `GEMINI_API_KEY`: credencial exclusiva del backend.
- `GEMINI_MODEL`: modelo Gemini multimodal.
- `AI_MEDIA_MAX_AUDIO_BYTES`: límite de audio; por defecto 10485760.
- `AI_MEDIA_MAX_IMAGE_BYTES`: límite de imagen; por defecto 5242880.

## Límite multipart y respuestas

Spring aplica `AI_MULTIPART_MAX_FILE_SIZE` y `AI_MULTIPART_MAX_REQUEST_SIZE` antes de cargar un archivo en memoria. Si se supera el máximo, la API responde `413 Payload Too Large`. Angular conserva la entrada seleccionada y permite reintentar ante un error.

## UI

La pantalla permite escribir una necesidad, elegir voz o imagen, grabar audio desde el micrófono, revisar la transcripción de una entrada de voz, consultar `recommendationId`, `inputType` y los servicios devueltos.

## Commits relevantes

- `4133292`: validación de extensiones.
- `2dc2e0d`: resultado estructurado en Angular.
- `8007c1a`: grabación de voz.
- `99d9f15`: transcripción en respuesta.
- `107b0bb`: reintento.
- `36b9f7e`: usuario autenticado real.
- `c56fef8`: límite multipart y respuesta 413.
