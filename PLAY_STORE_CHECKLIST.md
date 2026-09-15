# Checklist TikBoost antes de publicar en Google Play

- [ ] Crear cuenta de desarrollador de Google Play.
- [ ] Crear app con package `com.tikboost.app`.
- [ ] Activar Play App Signing.
- [ ] Crear suscripción `tikboost_premium_monthly` y plan base mensual.
- [ ] Configurar Google Play Developer API y cuenta de servicio.
- [ ] Crear Supabase y ejecutar `server/supabase.sql`.
- [ ] Configurar variables privadas en Vercel.
- [ ] Confirmar `/api/health` y `/api/config`.
- [ ] Probar registro e inicio de sesión.
- [ ] Probar límite de créditos Gratis.
- [ ] Probar generación con OpenAI.
- [ ] Probar compra con licencia/tester de Google Play.
- [ ] Probar Restaurar compra.
- [ ] Probar que un usuario Gratis no puede superar su límite.
- [ ] Probar que una compra de otra cuenta no se reutiliza.
- [ ] Crear política de privacidad pública.
- [ ] Completar Data safety en Play Console según los datos realmente usados.
- [ ] Añadir un flujo visible de eliminación de cuenta si corresponde a las políticas aplicables.
- [ ] Preparar capturas, icono 512 × 512 y gráfico de funciones.
- [ ] Subir primero a Internal testing.
- [ ] Revisar errores en Pre-launch report.
- [ ] Publicar producción solo después de las pruebas.

## Recomendado para una versión 3.1

Agregar **Real-time Developer Notifications (RTDN)** de Google Play para que el servidor reciba renovaciones, cancelaciones, reembolsos y cambios de estado incluso si el usuario no abre TikBoost.
