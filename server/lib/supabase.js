const required = (name) => {
  const v = process.env[name];
  if (!v) throw new Error(`Falta ${name} en el servidor.`);
  return v.replace(/\/$/, '');
};

export function publicSupabaseConfig() {
  return {
    supabaseUrl: required('SUPABASE_URL'),
    supabaseAnonKey: required('SUPABASE_ANON_KEY')
  };
}

function serviceConfig() {
  return {
    url: required('SUPABASE_URL'),
    serviceKey: required('SUPABASE_SERVICE_ROLE_KEY')
  };
}

export async function getUserFromRequest(req) {
  const auth = String(req.headers?.authorization || '');
  const token = auth.startsWith('Bearer ') ? auth.slice(7).trim() : '';
  if (!token) return { user: null, token: '', error: 'Inicia sesión para continuar.' };

  const { supabaseUrl, supabaseAnonKey } = publicSupabaseConfig();
  const r = await fetch(`${supabaseUrl}/auth/v1/user`, {
    headers: {
      apikey: supabaseAnonKey,
      Authorization: `Bearer ${token}`
    }
  });
  const data = await r.json().catch(() => ({}));
  if (!r.ok || !data?.id) return { user: null, token, error: 'La sesión no es válida o expiró.' };
  return { user: data, token, error: null };
}

async function adminFetch(path, options = {}) {
  const { url, serviceKey } = serviceConfig();
  const headers = {
    apikey: serviceKey,
    Authorization: `Bearer ${serviceKey}`,
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };
  const r = await fetch(`${url}${path}`, { ...options, headers });
  const text = await r.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!r.ok) {
    const msg = data?.message || data?.error_description || data?.hint || `Supabase respondió ${r.status}`;
    throw new Error(msg);
  }
  return data;
}

export async function ensureProfile(userId) {
  await adminFetch('/rest/v1/profiles?on_conflict=user_id', {
    method: 'POST',
    headers: { Prefer: 'resolution=merge-duplicates,return=minimal' },
    body: JSON.stringify({ user_id: userId })
  });
}

export async function getAccount(userId) {
  await ensureProfile(userId);
  const rows = await adminFetch(`/rest/v1/profiles?user_id=eq.${encodeURIComponent(userId)}&select=user_id,plan,daily_used,daily_date,premium_until`);
  const row = Array.isArray(rows) && rows[0] ? rows[0] : {
    user_id: userId,
    plan: 'free',
    daily_used: 0,
    daily_date: null,
    premium_until: null
  };

  const today = new Date().toISOString().slice(0, 10);
  const used = row.daily_date === today ? Number(row.daily_used || 0) : 0;
  const premiumValid = row.plan === 'premium' && row.premium_until && new Date(row.premium_until).getTime() > Date.now();
  const plan = premiumValid ? 'premium' : 'free';
  const freeLimit = Number(process.env.FREE_DAILY_LIMIT || 5);
  const premiumLimit = Number(process.env.PREMIUM_DAILY_LIMIT || 100);
  const dailyLimit = plan === 'premium' ? premiumLimit : freeLimit;
  return {
    user_id: userId,
    plan,
    used,
    daily_limit: dailyLimit,
    remaining: Math.max(0, dailyLimit - used),
    premium_until: premiumValid ? row.premium_until : null
  };
}

export async function consumeGeneration(userId) {
  await ensureProfile(userId);
  const data = await adminFetch('/rest/v1/rpc/consume_generation', {
    method: 'POST',
    body: JSON.stringify({
      p_user_id: userId,
      p_free_limit: Number(process.env.FREE_DAILY_LIMIT || 5),
      p_premium_limit: Number(process.env.PREMIUM_DAILY_LIMIT || 100)
    })
  });
  const row = Array.isArray(data) ? data[0] : data;
  if (!row) throw new Error('No se pudo calcular el límite de uso.');
  return {
    allowed: Boolean(row.allowed),
    plan: row.plan,
    used: Number(row.used || 0),
    daily_limit: Number(row.daily_limit || 0),
    remaining: Number(row.remaining || 0),
    premium_until: row.premium_until || null
  };
}

export async function refundGeneration(userId) {
  try {
    await adminFetch('/rest/v1/rpc/refund_generation', {
      method: 'POST',
      body: JSON.stringify({ p_user_id: userId })
    });
  } catch {
    // El reembolso de crédito es best-effort; no tapa el error original.
  }
}

export async function activatePremium(userId, premiumUntil, purchaseToken) {
  await adminFetch('/rest/v1/profiles?on_conflict=user_id', {
    method: 'POST',
    headers: { Prefer: 'resolution=merge-duplicates,return=minimal' },
    body: JSON.stringify({
      user_id: userId,
      plan: 'premium',
      premium_until: premiumUntil,
      purchase_token: purchaseToken
    })
  });
  return getAccount(userId);
}
