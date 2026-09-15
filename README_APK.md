# TikBoost — proyecto Android listo para generar APK

Este proyecto convierte la interfaz actual de TikBoost en una aplicación Android instalable mediante un `WebView` nativo. La interfaz se carga desde `app/src/main/assets/index.html`, por lo que no depende de publicar una web para abrir la app.

## Qué incluye

- Aplicación Android con nombre **TikBoost**.
- ID de aplicación: `com.tikboost.app`.
- Icono TikBoost incluido en todas las densidades Android.
- Pantalla de arranque con identidad visual TikBoost.
- JavaScript y almacenamiento local habilitados para conservar ideas guardadas y contadores.
- Botón nativo para copiar texto.
- Botón para compartir una idea mediante WhatsApp, mensajes, correo u otras apps instaladas.
- Permiso de Internet preparado para una futura API/IA real.
- Workflow de GitHub Actions que genera automáticamente un APK de prueba.

## Opción más fácil: generar el APK desde GitHub

1. Sube **todo el contenido de esta carpeta** a la raíz de tu repositorio de GitHub.
2. Confirma los cambios con un commit en la rama `principal`, `main` o `master`.
3. En GitHub entra a **Actions**.
4. Abre **Build TikBoost APK**.
5. Espera a que el proceso aparezca en verde.
6. Abre la ejecución terminada y, en **Artifacts**, descarga `TikBoost-debug-apk`.
7. Descomprime el archivo descargado. Dentro estará `app-debug.apk`.
8. Envía el APK a tu Android e instálalo. Android puede pedir permiso para instalar apps desde esa fuente.

También puedes abrir **Actions → Build TikBoost APK → Run workflow** para generar un APK manualmente sin hacer otro cambio.

## Generar el APK con Android Studio

1. Instala Android Studio.
2. Abre la carpeta `TikBoost_Android_APK` como proyecto.
3. Espera a que termine **Gradle Sync** y que Android Studio instale el SDK requerido si lo solicita.
4. Ve a **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. El APK aparecerá normalmente en:
   `app/build/outputs/apk/debug/app-debug.apk`

## Para publicar en Google Play

El APK de GitHub Actions es una compilación **debug**, pensada para probar e instalar la app. Para Google Play necesitarás crear una clave de firma y generar un **AAB de release** desde Android Studio mediante **Build → Generate Signed Bundle / APK**.

## Sobre la IA, Premium y estadísticas

La app conserva la lógica actual del prototipo: las ideas se generan desde contenido incorporado localmente y las estadísticas son demostrativas. Para tener IA real, cuentas de usuario, datos sincronizados y pagos Premium reales, se debe conectar un backend seguro. No guardes claves privadas de APIs dentro de `index.html`, JavaScript ni el APK.

## Archivos importantes

- `app/src/main/assets/index.html`: interfaz y lógica de TikBoost.
- `app/src/main/java/com/tikboost/app/MainActivity.java`: contenedor Android y funciones nativas.
- `app/src/main/AndroidManifest.xml`: permisos y configuración de la app.
- `app/build.gradle`: versión, SDK e identificador Android.
- `.github/workflows/build-apk.yml`: generación automática del APK en GitHub.
- `marketing/playstore-icon-512.png`: icono de 512 px para futuras fichas de tienda.
