# Alerta Rutas

App Android que avisa con una alarma fuerte cuando **Envíos Extra** manda una
notificación de oferta de ruta, y guarda el registro de todas las ofertas que
llegaron.

**Lo que hace**

- Escucha las notificaciones del paquete `com.mercadoenvios.crowdsourcing` (Envíos Extra).
- Cuando llega una, sube el volumen de alarma al máximo, suena la alarma del
  teléfono en bucle y vibra. Suena aunque el teléfono esté en silencio, porque
  usa el canal de *alarma*.
- Muestra un aviso **a pantalla completa, estilo llamada entrante**: la pantalla
  se enciende sola, aparece la oferta en grande y un botón **Abrir Envíos
  Extra** que lleva directo a la app, más **Silenciar**. Funciona sobre la
  pantalla bloqueada; si el teléfono tiene clave, pide desbloquear antes de
  saltar a la otra app.
- **Horario de trabajo**: días de la semana y rango de horas en que la alarma
  puede sonar. Soporta turnos que cruzan la medianoche (18:00 a 02:00, por
  ejemplo). Fuera del horario la oferta queda anotada igual, pero sin alarma.
- Anota fecha, hora y texto de cada oferta, para ver después en qué horarios y
  zonas salen más rutas. El registro se puede compartir o exportar como texto.
- **Respaldo en Descargas**: guarda el historial como archivo `.csv` abrible en
  Excel o Google Sheets, con un botón y también solo, una vez al día.
- Switch para activar/desactivar, control de duración de la alarma (5 a 60 s) o
  **hasta que la apagues**, y botón de prueba.

**Lo que NO hace, a propósito**

No acepta rutas de forma automática. El botón del aviso solo *abre* Envíos
Extra; el toque para aceptar lo da la persona. Automatizar la app va contra los
términos de Mercado Libre y es la vía rápida a que te bloqueen la cuenta de
repartidor.

---

## Cómo obtener el APK sin instalar nada en el computador

El proyecto trae un workflow de GitHub Actions que compila el APK en los
servidores de GitHub (gratis en repositorios públicos).

1. Crear una cuenta en [github.com](https://github.com) si no tienes.
2. Crear un repositorio nuevo, por ejemplo `alerta-rutas`.
3. En la página del repo vacío: **uploading an existing file** → arrastrar
   *todo* el contenido de esta carpeta (incluida la carpeta oculta
   `.github`) → **Commit changes**.
   - Si GitHub no deja arrastrar carpetas ocultas, se puede crear el archivo a
     mano: **Add file → Create new file**, nombre
     `.github/workflows/build.yml`, y pegar el contenido de ese archivo.
4. Pestaña **Actions** → workflow **Compilar APK** → **Run workflow**.
5. Cuando termine (4 a 6 minutos), entrar al run y bajar el artefacto
   **AlertaRutas-apk**. Dentro viene `app-debug.apk`.
6. Pasar ese APK al teléfono (WhatsApp a ti misma, Google Drive, cable, etc.).

Si prefieres compilar en el computador: instalar Android Studio, abrir esta
carpeta y usar **Build → Build Bundle(s)/APK(s) → Build APK(s)**.

## Instalación y configuración en el teléfono

1. Abrir el archivo `app-debug.apk` y aceptar instalar desde esta fuente
   (Android lo pide porque no viene de Play Store).
2. Abrir **Alerta Rutas**.
3. Tocar **Dar acceso a notificaciones** y activar *Alerta Rutas* en la lista.
   Es el permiso que le deja leer las notificaciones; sin eso no funciona.
4. En Ajustes del teléfono, buscar **Alerta Rutas** → **Batería** → elegir
   *Sin restricciones*, para que Android no la duerma.
5. Probar con el botón **Probar alarma**. Debería sonar fuerte y vibrar.
6. Si quieres que no te despierte de noche: activar **Sonar solo en mi horario**,
   marcar los días y elegir desde/hasta.
7. Dejar el switch **Alerta activada** encendido. La app ya no necesita estar
   abierta.

La primera vez Android va a pedir permiso para mostrar notificaciones: hay que
aceptarlo, porque es el aviso que trae el botón para abrir Envíos Extra.

Si el teléfono está en **No molestar**, conviene permitir las alarmas en los
ajustes de No molestar; por defecto Android ya las deja pasar.

## Estructura

```
app/src/main/java/cl/jeisell/alertarutas/
  RutaListenerService.kt   escucha las notificaciones y dispara la alerta
  Alarma.kt                sonido en canal de alarma + vibración
  Aviso.kt                 aviso en pantalla con el botón "Abrir Envíos Extra"
  AlertaActivity.kt        pantalla completa estilo llamada entrante
  SilenciarReceiver.kt     apaga la alarma desde el botón del aviso
  Respaldo.kt              exporta el registro a CSV en la carpeta Descargas
  Prefs.kt                 configuración, horario de trabajo y registro
  MainActivity.kt          pantalla única: estado, ajustes y registro
.github/workflows/build.yml  compila el APK en GitHub Actions
```

Sin librerías externas: solo el SDK de Android y Kotlin. Eso hace la app
liviana y el build más difícil de romper.

## Ajustes que puede querer cambiar

- **Otra app además de Envíos Extra**: en `Prefs.kt`, la constante
  `PAQUETE_ENVIOS_EXTRA`.
- **Filtrar por monto o palabra**: en `RutaListenerService.kt`, antes de llamar
  a `Alarma.sonar`, agregar una condición sobre `titulo` y `texto`.
- **Otro sonido**: en `Alarma.kt`, cambiar el `RingtoneManager.TYPE_ALARM` por
  un archivo propio en `res/raw`.
