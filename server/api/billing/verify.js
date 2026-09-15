import crypto from 'node:crypto';
import { GoogleAuth } from 'google-auth-library';
import { getUserFromRequest, activatePremium } from '../../lib/supabase.js';

const cors = (res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
};

function parseServiceAccount() {
  const raw = process.env.GOOGLE_SERVICE_ACCOUNT_JSON;
  if (!raw) throw new Error('Falta GOOGLE_SERVICE_ACCOUNT_JSON en el servidor.');
  const credentials = JSON.parse(raw);
  if (!credentials.client_email || !credentials.private_key) throw new Error('La cuenta de servicio de Google no es válida.');
  return credentials;
}

async function androidPublisherToken() {
  const auth = new GoogleAuth({
    credentials: parseServiceAccount(),
    scopes: ['https://www.googleapis.com/auth/androidpublisher']
  });
  const client = await auth.getClient();
  const token = await client.getAccessToken();
  if (!token?.token) throw new Error('No se pudo autenticar con Google Play Developer API.');
  return token.token;
}

const hashUser = (id) => crypto.createHash('sha256').update(String(id)).digest('hex');

export default async function handler(req, res) {
  cors(res);
  if (req.method === 'OPTIONS') return res.status(204).end();
  if (req.method !== 'POST') return res.status(405).json({ error: 'Método no permitido' });

  try {
    const authUser = await getUserFromRequest(req);
    if (!authUser.user) return res.status(401).json({ error: authUser.error });

    const purchaseToken = String(req.body?.purchaseToken || '').trim();
    const productId = String(req.body?.productId || process.env.PLAY_PRODUCT_ID || 'tikboost_premium_monthly').trim();
    const packageName = process.env.ANDROID_PACKAGE_NAME || 'com.tikboost.app';
    const expectedProduct = process.env.PLAY_PRODUCT_ID || 'tikboost_premium_monthly';

    if (!purchaseToken) return res.status(400).json({ error: 'Falta purchaseToken.' });
    if (productId !== expectedProduct) return res.status(400).json({ error: 'Producto de Google Play no reconocido.' });

    const accessToken = await androidPublisherToken();
    const verifyUrl = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(packageName)}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`;
    const vr = await fetch(verifyUrl, { headers: { Authorization: `Bearer ${accessToken}` } });
    const purchase = await vr.json().catch(() => ({}));
    if (!vr.ok) {
      return res.status(502).json({ error: purchase?.error?.message || `Google Play respondió ${vr.status}` });
    }

    const allowedStates = new Set([
      'SUBSCRIPTION_STATE_ACTIVE',
      'SUBSCRIPTION_STATE_IN_GRACE_PERIOD',
      'SUBSCRIPTION_STATE_CANCELED'
    ]);
    if (!allowedStates.has(purchase.subscriptionState)) {
      return res.status(402).json({ error: `La suscripción no está activa (${purchase.subscriptionState || 'estado desconocido'}).` });
    }

    const lineItems = Array.isArray(purchase.lineItems) ? purchase.lineItems : [];
    const matching = lineItems.filter(x => x?.productId === expectedProduct && x?.expiryTime);
    if (!matching.length) return res.status(400).json({ error: 'La compra no corresponde al producto Premium de TikBoost.' });

    const premiumUntil = matching
      .map(x => new Date(x.expiryTime))
      .filter(d => Number.isFinite(d.getTime()))
      .sort((a, b) => b.getTime() - a.getTime())[0];

    if (!premiumUntil || premiumUntil.getTime() <= Date.now()) {
      return res.status(402).json({ error: 'La suscripción ya venció.' });
    }

    const externalId = purchase?.externalAccountIdentifiers?.obfuscatedExternalAccountId;
    if (externalId && externalId !== hashUser(authUser.user.id)) {
      return res.status(403).json({ error: 'Esta compra pertenece a otra cuenta TikBoost.' });
    }

    const account = await activatePremium(authUser.user.id, premiumUntil.toISOString(), purchaseToken);

    if (purchase.acknowledgementState === 'ACKNOWLEDGEMENT_STATE_PENDING') {
      const ackUrl = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(packageName)}/purchases/subscriptions/${encodeURIComponent(expectedProduct)}/tokens/${encodeURIComponent(purchaseToken)}:acknowledge`;
      const ar = await fetch(ackUrl, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${accessToken}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ externalAccountIds: { obfuscatedAccountId: hashUser(authUser.user.id) } })
      });
      if (!ar.ok) {
        const ad = await ar.json().catch(() => ({}));
        return res.status(502).json({
          error: ad?.error?.message || 'Premium fue verificado, pero Google Play no pudo confirmar la compra. Intenta restaurarla.'
        });
      }
    }

    return res.status(200).json({ ok: true, verified: true, premiumUntil: premiumUntil.toISOString(), account });
  } catch (e) {
    return res.status(500).json({ error: e.message || 'No se pudo verificar la compra.' });
  }
}
