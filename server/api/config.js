import { publicSupabaseConfig } from '../lib/supabase.js';

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
    const { supabaseUrl, supabaseAnonKey } = publicSupabaseConfig();
    return res.status(200).json({
      ok: true,
      supabaseUrl,
      supabaseAnonKey,
      freeDailyLimit: Number(process.env.FREE_DAILY_LIMIT || 5),
      premiumDailyLimit: Number(process.env.PREMIUM_DAILY_LIMIT || 100),
      premiumProductId: process.env.PLAY_PRODUCT_ID || 'tikboost_premium_monthly'
    });
  } catch (e) {
    return res.status(500).json({ error: e.message || 'Configuración incompleta' });
  }
}
