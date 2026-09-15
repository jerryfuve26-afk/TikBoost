# Configurar TikBoost 3.0 para cuentas, créditos y Premium

## 1. Crear Supabase

1. Crea un proyecto en Supabase.
2. En **Authentication > Providers**, activa Email/Password.
3. Abre **SQL Editor**.
4. Copia y ejecuta todo el archivo `server/supabase.sql`.
5. En **Project Settings > API** copia:
   - Project URL → `SUPABASE_URL`
   - anon/public key → `SUPABASE_ANON_KEY`
   - service_role key → `SUPABASE_SERVICE_ROLE_KEY`

La `service_role` es privada: solo va en Vercel/backend.

## 2. Backend en Vercel

Sube la carpeta `server` a Vercel y configura estas variables de entorno:

```text
OPENAI_API_KEY=...
OPENAI_MODEL=gpt-5.6-luna
SUPABASE_URL=https://....supabase.co
SUPABASE_ANON_KEY=...
SUPABASE_SERVICE_ROLE_KEY=...
FREE_DAILY_LIMIT=5
PREMIUM_DAILY_LIMIT=100
ANDROID_PACKAGE_NAME=com.tikboost.app
PLAY_PRODUCT_ID=tikboost_premium_monthly
GOOGLE_SERVICE_ACCOUNT_JSON={...}
```

Después del deploy, prueba:

```text
https://TU-PROYECTO.vercel.app/api/health
```

Debe responder `ok: true`.

En TikBoost abre **Perfil > Servidor**, pega esa URL y toca **Guardar y cargar configuración**.

## 3. Crear la app en Google Play Console

Usa como package/application id:

```text
com.tikboost.app
```

No cambies ese valor después de crear la ficha en Play Console salvo que también cambies todo el proyecto y el backend.

## 4. Crear Premium

En Play Console crea una suscripción con este ID exacto:

```text
tikboost_premium_monthly
```

Crea un **plan base mensual auto-renovable**, actívalo y define el precio. Google Play entregará el precio localizado al APK; la cifra `S/ 9.90` de la interfaz es solo un fallback mientras Play no devuelve el producto.

## 5. Habilitar Google Play Developer API

1. Crea un proyecto en Google Cloud.
2. Habilita **Google Play Android Developer API**.
3. Crea una cuenta de servicio.
4. Descarga su JSON.
5. En Google Play Console > **Usuarios y permisos**, invita el correo de la cuenta de servicio.
6. Concede los permisos necesarios para las APIs de Billing, incluidos:
   - ver datos financieros/pedidos;
   - administrar pedidos y suscripciones.
7. Copia el JSON completo, en una sola variable, como `GOOGLE_SERVICE_ACCOUNT_JSON` en Vercel.

Nunca pongas ese JSON dentro del APK.

## 6. Probar una compra

Google Play Billing debe probarse con una versión instalada mediante Google Play (por ejemplo, Internal Testing). Un APK instalado manualmente puede no devolver los productos de suscripción correctamente.

1. Sube un AAB a **Internal testing**.
2. Agrega tu correo como tester.
3. Instala TikBoost desde el enlace de prueba de Google Play.
4. Inicia sesión en TikBoost.
5. Abre Premium y realiza una compra de prueba.
6. TikBoost enviará el `purchaseToken` al backend.
7. El backend verificará la compra con Google Play y solo entonces marcará la cuenta como Premium.

## 7. Generar AAB firmado con GitHub Actions

El workflow `.github/workflows/build-release-aab.yml` usa estos Secrets de GitHub:

```text
ANDROID_KEYSTORE_BASE64
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

Convierte tu archivo `.jks`/`.keystore` a Base64 y guarda el resultado en `ANDROID_KEYSTORE_BASE64`. No subas la keystore al repositorio.

Luego entra a **GitHub > Actions > Build TikBoost Release AAB > Run workflow**. El artefacto final será `app-release.aab`.

## 8. Flujo de créditos implementado

- Cada generación exitosa reserva 1 crédito en el servidor.
- Si OpenAI falla, el backend intenta devolver el crédito.
- El límite se reinicia por día.
- Gratis: 5/día por defecto.
- Premium: 100/día por defecto.
- Cambia `FREE_DAILY_LIMIT` y `PREMIUM_DAILY_LIMIT` en Vercel si quieres otros límites.

## 9. Seguridad implementada

- La clave de OpenAI no está en el APK.
- `SUPABASE_SERVICE_ROLE_KEY` no está en el APK.
- El JSON de Google Play no está en el APK.
- La compra se verifica en backend.
- El `purchaseToken` se guarda como identificador único.
- TikBoost usa un hash del ID del usuario como `obfuscatedAccountId` al iniciar la compra.

## Antes de producción

Revisa `PLAY_STORE_CHECKLIST.md`. También conviene agregar recuperación de contraseña, eliminación de cuenta, política de privacidad pública y Real-time Developer Notifications para sincronizar cancelaciones/renovaciones aun cuando el usuario no abra la app.
