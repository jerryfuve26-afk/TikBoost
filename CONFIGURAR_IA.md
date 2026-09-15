# TikBoost 2.0 — Activar IA real

La app ya está preparada para pedir ideas a un backend seguro. La clave de OpenAI **no se guarda en el APK**.

## 1) Desplegar el backend

La carpeta `server/` está preparada para Vercel.

1. Sube este proyecto a GitHub.
2. En Vercel crea un proyecto nuevo desde ese repositorio.
3. Configura `server` como **Root Directory**.
4. En **Environment Variables** agrega:
   - `OPENAI_API_KEY` = tu clave secreta de OpenAI.
   - `OPENAI_MODEL` = `gpt-5-mini` (opcional; puedes cambiarlo por un modelo disponible en tu cuenta).
5. Despliega.
6. Copia la URL HTTPS, por ejemplo `https://tikboost-ai.vercel.app`.

## 2) Conectar la app

1. Abre TikBoost.
2. Ve a **Perfil**.
3. En **URL del servidor TikBoost**, pega la URL de Vercel sin `/api/generate`.
4. Pulsa **Guardar conexión**.
5. Pulsa **Probar conexión**.
6. Vuelve a Inicio → **Generar con IA**.

## 3) Generar APK

El workflow `.github/workflows/build-apk.yml` compila el APK en GitHub Actions.

1. Sube el proyecto a GitHub.
2. Ve a **Actions** → **Build TikBoost APK**.
3. Ejecuta el workflow.
4. Descarga el artefacto `TikBoost-debug-apk`.
5. Dentro encontrarás `app-debug.apk`.

## Seguridad

- No pegues tu clave de OpenAI en `index.html`.
- No guardes la clave en Java/Kotlin.
- No publiques `.env`.
- La app solo conoce la URL del backend; el backend conserva la clave en una variable de entorno.

## Qué genera la IA

- Título de la idea.
- Categoría y emoji.
- Descripción.
- Gancho de 2 segundos.
- Guion corto.
- Qué grabar / planos.
- Caption.
- Hashtags.
- Estrategia breve.
