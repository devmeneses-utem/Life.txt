# Arquitectura de Life.txt

## Estructura de almacenamiento interno

```
/life/
├── calendar/
│   ├── calendar.txt        # 365 días del año en formato ISO
│   ├── past.txt            # Días archivados automáticamente
│   └── recurring.txt       # Definición de tareas recurrentes
├── todo/
│   ├── todo.txt            # Lista activa con prioridades (A/B) y etiquetas
│   └── done.txt            # Archivo histórico de tareas completadas
├── inbox/
│   ├── inbox.txt           # Entrada de diario ordenada descendente por fecha ISO
│   └── characters.txt      # Información libre sobre personas relevantes
├── notes/
│   ├── notes.txt           # Índice de apuntes (+ título prefijado con fecha ISO)
│   ├── media/              # Carpeta con subcarpetas por nota
│   │   ├── audio/          # Grabaciones .m4a (una por nota)
│   │   └── images/         # Recursos importados vía SAF
│   └── timestamps/         # Almacena saltos [+HH:MM:SS] por nota (JSON interno)
└── projects/
    └── projects.txt        # Metas y proyectos de texto plano
```

Los archivos se crean al primer arranque si no existen. Todo se guarda en el almacenamiento interno privado de la app (`context.filesDir/life/…`). Para exportar/importar se comprime/descomprime toda la carpeta `/life` mediante SAF.

## Módulos

| Capa | Responsabilidad |
|------|-----------------|
| `data` | `FileRepository` administra I/O textual, adjuntos y backups. |
| `domain` | Parsers/formatters (Calendar, Todo, Inbox, Notes), scripts (`generateYearIfMissing`, `archivePastDays`, `applyRecurring`). |
| `ui` | Pantallas Compose con ViewModels (MVVM) que consumen casos de uso. |

## Scripts automáticos

1. `generateYearIfMissing(year)`  
   * Revisa `calendar.txt`; si falta el bloque YYYY crea 365 encabezados `YYYY-MM-DD`.
2. `archivePastDays(today)`  
   * Mueve días < `today` desde `calendar.txt` hacia `past.txt` preservando orden temporal.
3. `applyRecurring(fromDate, horizonWeeks=6, monthSpan=1)`  
   * Analiza `recurring.txt` con secciones `@anual`, `@mensual`, `@semanal`.  
   * Inserta tareas futuras dentro del horizonte indicado evitando duplicados por hash (fecha+texto).

## Parsers principales

| Archivo | Elementos detectados |
|---------|---------------------|
| Calendar | Encabezados `YYYY-MM-DD`, tareas `+ texto`, horas `[HH:MM]`, etiquetas `#p/#h/#w`. |
| Todo | Prioridad `(A)/(B)`, estado (vacío vs `x`), etiquetas. |
| Inbox | Bloques encabezados por fecha ISO descendente. |
| Notes | Títulos `+(YYYY-MM-DD) Nombre`, marcas `+[HH:MM:SS]` asociadas al audio. |

Cada parser expone DTOs inmutables y un `Formatter` para volver a texto. Se añaden pruebas unitarias para validar redondeo de tiempos, regex y ordenamiento.

## Medios

* Audio `.m4a` grabado con `MediaRecorder` (permiso `RECORD_AUDIO` on-demand).  
* Reproducción con `MediaPlayer` y control para saltar usando las marcas `+[HH:MM:SS]`.  
* Imágenes importadas con SAF (`ACTION_OPEN_DOCUMENT`) y copiadas a `/life/notes/media/images/<noteId>/`.  

## UI y navegación

* Jetpack Compose + Navigation + bottom bar: Calendar, Todo, Inbox, Notes, Projects.  
* Cada pestaña muestra editor de texto plano, barra de búsqueda y acciones contextualizadas.  
* Notes tiene lista, detalle, botones para grabar/reproducir audio y galería.  
* Inbox incluye subvista para `characters.txt`.  

## Internacionalización y accesibilidad

* Locale `es-CL` forzado en formateos (nombres de día).  
* Formato 24h, validación ISO estricta.  
* Tipografías escalables (`sp` + `rememberSaveable` para preferencias).  
* IO en corrutinas/Dispatchers.IO para no bloquear UI.
