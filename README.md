# Life.txt

Aplicación Android (Kotlin + Jetpack Compose + WorkManager) inspirada en el flujo “Life.txt”. Todo se guarda como archivos `.txt` dentro de `context.filesDir/life/` para mantener un modelo de texto plano simple y exportable.

## Características principales

- **Calendar.txt**: 365 días en formato ISO con tareas `+`, etiquetas `#p/#h/#w` y apoyo para tareas recurrentes (`recurring.txt`) y archivo automático de días pasados (`past.txt`). Se incluye un worker diario con WorkManager.
- **Todo.txt**: lista con prioridades `(A)/(B)`, filtros en UI y archivo de completados `done.txt`.
- **Inbox.txt**: diario cronológico más pestaña `characters.txt`.
- **Notes.txt**: notas con prefijo de fecha, grabación/reproducción de audio `.m4a`, marcas `+[HH:MM:SS]` y editor en tiempo real.
- **Projects.txt**: visión general de proyectos + atajos para exportar/importar un `.zip` completo de la carpeta `/life`.

## Arquitectura

- **Capa de datos**: `FileRepositoryImpl` gestiona I/O atómico, creación de estructura inicial, y empaquetado/restore `.zip`.
- **Capa de dominio**: Parsers para cada archivo más `CalendarScripts` con `generateYearIfMissing`, `archivePastDays`, `applyRecurring`.
- **Capa de presentación**: Compose + Navigation + MVVM. Cada pestaña tiene su ViewModel y operaciones específicas. Notas integran `NotesMediaManager` para audio y permisos on-demand.
- **Pruebas unitarias** (JUnit) cubren parsers críticos: Calendar, Todo, Notes y Recurring.

## Próximos pasos sugeridos

1. Añadir soporte para adjuntar imágenes a las notas (usando SAF y `notes/media/images/<noteId>`).
2. Implementar vista para `done.txt` y permitir restaurar tareas completadas.
3. Añadir estado visual del worker (última ejecución) y permitir reintentos manuales.
4. Integrar DataStore/Settings para preferencias como etiquetas personalizadas o horizonte de tareas recurrentes.
