# Life.txt

Aplicación Android (Kotlin + Jetpack Compose + WorkManager) inspirada en el flujo “Life.txt”, sistema de productividad planteado en el video https://youtu.be/EUCneUnGjv8. Todo se guarda como archivos `.txt` dentro de `context.filesDir/life/` para mantener un modelo de texto plano simple y exportable.

## Características principales

**Calendar.txt**: 365 días en formato ISO con tareas `+`, etiquetas `#p/#h/#w` y `#t` para tareas urgentes. Igualmente permite integrar tareas recurrentes (`recurring.txt`) y archivo automático de días pasados (`past.txt`). Se incluye un worker diario con WorkManager.

<img width="650" height="1280" alt="image" src="https://github.com/user-attachments/assets/81eeb0be-f339-4648-84b2-a692231c83b2" />

---

**Todo.txt**: lista con prioridades `(A)/(B)`, etiquetas al igual que en *Calendar.txt*, filtros en UI y archivo de completados `done.txt`.

<img width="650" height="1280" alt="image" src="https://github.com/user-attachments/assets/fbeb2568-72c9-4f34-909f-15df8283807d" />

---

**Notes.txt**: notas con prefijo de fecha, grabación/reproducción de audio `.m4a`, marcas `+[HH:MM:SS]` y editor en tiempo real.

<img width="650" height="1280" alt="image" src="https://github.com/user-attachments/assets/06e4d5a7-cc39-4474-bcfc-c1d367e72336" />

---

**Pomodoro**: Pomodoro personalizable con modo flow y resumen de tiempo concentrado en base al tiempo.

<img width="650" height="1280" alt="image" src="https://github.com/user-attachments/assets/96dcb050-99d8-4c4e-b3e6-d8fd1998d840" />

---

**Inbox.txt**: Diario cronológico más pestaña `characters.txt` para agendar personas.

---

**Projects.txt**: Atajos para exportar/importar un `.zip` completo de la carpeta `/life`, haciendo fácil exportar toda la información a otro dispositivo.

---

## Arquitectura

**Capa de datos**: `FileRepositoryImpl` gestiona I/O atómico, creación de estructura inicial, y empaquetado/restore `.zip`.
  
**Capa de dominio**: Parsers para cada archivo más `CalendarScripts` con `generateYearIfMissing`, `archivePastDays`, `applyRecurring`.
  
**Capa de presentación**: Compose + Navigation + MVVM. Cada pestaña tiene su ViewModel y operaciones específicas. Notas integran `NotesMediaManager` para audio y permisos on-demand.
  
**Pruebas unitarias** (JUnit) cubren parsers críticos: Calendar, Todo, Notes y Recurring.
