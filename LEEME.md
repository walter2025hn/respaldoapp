# Señal IPTV — Proyecto Android

Este es un proyecto de **Android Studio** que envuelve tu reproductor IPTV (HTML/JS) en una app nativa usando un `WebView`. Así puedes seguir modificando la app editando **un solo archivo HTML**, sin tocar código Android, y generar el `.apk` cuando quieras.

## 1. Requisitos
- Instalar **Android Studio** (gratis): https://developer.android.com/studio
- Abrir Android Studio → *Open* → seleccionar la carpeta `IPTVPlayerApp` (esta carpeta).
- Deja que Android Studio descargue el SDK y sincronice Gradle la primera vez (puede tardar varios minutos).

## 2. Generar el APK
1. En Android Studio: menú **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
2. Cuando termine, aparece un aviso "APK(s) generated successfully" → clic en **locate** para encontrar el archivo `.apk` (normalmente en `app/build/outputs/apk/debug/app-debug.apk`).
3. Copia ese `.apk` a tu celular Android e instálalo (activa "Instalar apps de origen desconocido" si te lo pide).

Para una versión "release" firmada y optimizada (para subir a Play Store o repartir de forma más seria):
**Build → Generate Signed Bundle / APK** → sigue el asistente para crear tu propio keystore (guarda ese archivo, lo necesitarás para futuras actualizaciones).

## 3. Cómo seguir modificando la app
Toda la interfaz vive en un único archivo:

```
app/src/main/assets/www/index.html
```

Es exactamente el mismo reproductor que ya tienes (HTML + CSS + JavaScript). Para cambiar cualquier cosa — colores, textos, lógica del parser M3U, llamadas a Xtream Codes, etc. — edita ese archivo directamente (en Android Studio, VS Code, o cualquier editor de texto) y vuelve a compilar el APK (paso 2). No necesitas tocar nada de Kotlin salvo que quieras cambiar comportamiento nativo (permisos, ícono, nombre de la app, etc.).

- **Nombre de la app**: `app/src/main/res/values/strings.xml`
- **Ícono**: `app/src/main/res/drawable/ic_launcher_foreground.xml` y `ic_launcher_background.xml` (o reemplázalos por imágenes PNG reales usando *Image Asset Studio* en Android Studio: clic derecho en `res` → New → Image Asset).
- **Permisos / configuración de red**: `app/src/main/AndroidManifest.xml`

## 4. Notas importantes
- **Tráfico HTTP (no HTTPS)**: muchos servidores IPTV usan `http://` sin cifrar. Ya está habilitado (`usesCleartextTraffic="true"` + `network_security_config.xml`), si no lo estuviera, Android bloquea esas conexiones por defecto.
- **CORS**: dentro de una app nativa (WebView cargando `file:///android_asset/...`) las peticiones a servidores externos generalmente NO sufren el mismo bloqueo CORS que en un navegador de escritorio para recursos como video/streams, pero pueden seguir aplicando restricciones para `fetch()` de JSON según el servidor. Si algo no carga, revisa la consola remota de WebView (`chrome://inspect` conectando el celular por USB con depuración activada).
- **Pantalla completa de video**: ya está implementado (`onShowCustomView`/`onHideCustomView` en `MainActivity.kt`) para que el botón de pantalla completa del reproductor funcione.
- **Selector de archivos**: ya está implementado para que el botón "subir archivo .m3u" abra el explorador de archivos de Android.

## 5. Estructura del proyecto
```
IPTVPlayerApp/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/senal/iptvplayer/MainActivity.kt   ← lógica nativa (WebView)
│       ├── res/                                         ← ícono, tema, layout
│       └── assets/www/index.html                        ← TU APP (edita aquí)
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```
