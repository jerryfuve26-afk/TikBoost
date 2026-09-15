# TikBoost 3.0 · Commercial Ready

Proyecto Android de TikBoost preparado para pasar de prototipo a producto comercial.

## Incluye

- Registro e inicio de sesión por correo con **Supabase Auth**.
- Créditos diarios de IA asociados a la cuenta.
- Plan Gratis: **5 generaciones/día** por defecto.
- Plan Premium: **100 generaciones/día** por defecto.
- Consumo de créditos controlado en el backend, no en el teléfono.
- Generación de contenido con OpenAI desde backend seguro.
- **Google Play Billing Library 9.1.0** para la suscripción `tikboost_premium_monthly`.
- Verificación de compra en backend con Google Play Developer API antes de activar Premium.
- Restauración de suscripción.
- Workflow para APK de prueba y workflow para **AAB release firmado**.

## Archivos importantes

- `app/src/main/assets/index.html`: interfaz y autenticación.
- `app/src/main/java/com/tikboost/app/MainActivity.java`: WebView, red nativa y Google Play Billing.
- `server/api/generate.js`: IA + control de límite diario.
- `server/api/billing/verify.js`: verificación de suscripción.
- `server/supabase.sql`: tabla de perfiles, créditos y funciones atómicas.
- `CONFIGURAR_COMERCIAL.md`: guía completa de configuración.
- `PLAY_STORE_CHECKLIST.md`: checklist antes de publicar.

## Importante

El proyecto está **implementado**, pero no puede quedar conectado a tus cuentas externas sin que tú crees/configures Supabase, Vercel, OpenAI y Google Play Console. No subas `SUPABASE_SERVICE_ROLE_KEY`, `OPENAI_API_KEY` ni el JSON de la cuenta de servicio de Google a GitHub.
