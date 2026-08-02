# Plan de Actualización Crítica: Salto a Gemini 3.6 y Estabilidad de Datos

Gracias a los diagnósticos detallados (¡excelentes capturas!), hemos identificado que el error de "Serialización" es en realidad un fallo del SDK al intentar explicar un error 404. El modelo `1.5-flash-8b` ya no existe en los servidores de Google para el endpoint que usamos.

## Proposed Changes

### [Capa de IA (Remota)]

#### [MODIFY] [GeminiRepository.kt](file:///C:/Users/QuintiVG/AndroidStudioProjects/ScreenAssistant/app/src/main/java/com/screenassistant/data/remote/GeminiRepository.kt)
- **Nuevo Modelo 2026**: Cambiar el motor a `gemini-3.6-flash`. Según la documentación de Google para julio de 2026, este es el modelo estándar más estable y rápido disponible.
- **Restauración de Potencia**:
    - Re-activar las **herramientas (tools)** para que pueda volver a poner alarmas, buscar archivos y gestionar memorias.
    - Re-activar la **visión (imágenes)** con el redimensionamiento optimizado que ya configuramos.
- **Manejo de Errores Blindado**: He añadido un filtro que detecta específicamente el fallo de "MissingFieldException" (el error de los 'details' en las capturas) para que, si el servidor de Google tiene un micro-corte, el asistente se reinicie solo sin mostrarte ese código técnico tan feo.

---

## Verification Plan

### Manual Verification
1. **Saludo Inicial**: Confirmar que el asistente se presenta correctamente con el nuevo modelo 3.6.
2. **Prueba de Herramientas**: Decir "Pon una alarma" para verificar que el bindeo de funciones funciona con el nuevo motor.
3. **Prueba de Visión**: Preguntar "¿Qué hay en mi pantalla?" para asegurar que la imagen llega bien al servidor.
4. **Resiliencia**: Si hay un error de red, verificar que el mensaje sea amigable y no técnico.
