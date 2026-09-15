import { getUserFromRequest, getAccount } from '../lib/supabase.js';

const cors = (res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
};

export default async function handler(req, res) {
  cors(res);
  if (req.method === 'OPTIONS') return res.status(204).end();
  if (req.method !== 'GET') return res.status(405).json({ error: 'Método no permitido' });
  try {
    const auth = await getUserFromRequest(req);
    if (!auth.user) return res.status(401).json({ error: auth.error });
    const account = await getAccount(auth.user.id);
    return res.status(200).json({ ok: true, account });
  } catch (e) {
    return res.status(500).json({ error: e.message || 'No se pudo cargar la cuenta' });
  }
}
