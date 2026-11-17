# Informe de evaluación — HU-26 (generado por KnowledgeEvaluationTest)

> **Cómo leerlo:** ejecución con dobles deterministas (embedding de bolsa de palabras con hashing, sin red ni Gemini).
> Mide el cableado y la fusión, **no** la calidad semántica real. Para calibrar `minScoreThreshold` y `rrfK` (T030)
> hay que repetirlo con Gemini. El embedding determinista no cruza ES-EN: las preguntas en inglés
> solo aciertan por coincidencia léxica con la sección EN.

Parámetros: k = 3, umbral coseno = 0.50, rrfK = 60, preguntas = 42 (respondibles: 30, sin respuesta: 12).

| Estrategia | hit@3 | MRR | Falsos positivos |
|---|---|---|---|
| BM25_ONLY | 1.000 | 0.950 | 0 / 12 |
| EMBEDDINGS_ONLY | 1.000 | 0.944 | 0 / 12 |
| HYBRID | 1.000 | 0.950 | 0 / 12 |

Definiciones: hit@k = fracción de preguntas respondibles con algún fragmento esperado entre los k primeros;
MRR = media del inverso del rango del primer acierto; falso positivo = pregunta sin respuesta cuya mejor
similitud coseno supera el umbral (el sistema llamaría al LLM).
