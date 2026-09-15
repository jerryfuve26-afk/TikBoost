# Backend TikBoost 3.0

Endpoints:

- `GET /api/health`
- `GET /api/config`
- `GET /api/account` — requiere Bearer token de Supabase
- `POST /api/generate` — requiere Bearer token, consume crédito
- `POST /api/billing/verify` — requiere Bearer token, verifica Google Play y activa Premium

Ejecuta primero `supabase.sql` en Supabase y configura las variables de `.env.example`.
