import { getUserFromRequest, consumeGeneration, refundGeneration } from '../lib/supabase.js';

const allowCors = (res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
};

const ideaSchema = {
  type: 'object',
  additionalProperties: false,
  properties: {
    title: { type: 'string' },
    cat: { type: 'string' },
    emoji: { type: 'string' },
    desc: { type: 'string' },
    hook: { type: 'string' },
    script: { type: 'string' },
    shot: { type: 'string' },
    text: { type: 'string' },
    tags: { type: 'string' },
    strategy: { type: 'string' }
  },
  required: ['title','cat','emoji','desc','hook','script','shot','text','tags','strategy']
};

function clean(value, max = 500) {
  return String(value ?? '').trim().slice(0, max);
}

export default async function handler(req, res) {
  allowCors(res);
  if (req.method === 'OPTIONS') return res.status(204).end();
  if (req.method !== 'POST') return res.status(405).json({ error: 'Método no permitido' });

  const apiKey = process.env.OPENAI_API_KEY;
  if (!apiKey) return res.status(500).json({ error: 'Falta OPENAI_API_KEY en el servidor.' });

  let userId = null;
  let usage = null;
  try {
    const auth = await getUserFromRequest(req);
    if (!auth.user) return res.status(401).json({ error: auth.error });
    userId = auth.user.id;

    usage = await consumeGeneration(userId);
    if (!usage.allowed) {
      return res.status(402).json({
        error: usage.plan === 'premium'
          ? 'Llegaste al límite diario de Premium. Vuelve mañana.'
          : 'Usaste tus créditos gratuitos de hoy. Puedes volver mañana o activar Premium.',
        account: usage
      });
    }

    const body = req.body || {};
    const niche = clean(body.niche, 120);
    const topic = clean(body.topic, 220);
    const goal = clean(body.goal, 100) || 'Aumentar alcance y participación';
    const audience = clean(body.audience, 140) || 'Público general';
    const tone = clean(body.tone, 80) || 'Natural y dinámico';
    const platform = clean(body.platform, 50) || 'TikTok';
    const duration = clean(body.duration, 30) || '30 segundos';

    if (!niche) {
      await refundGeneration(userId);
      return res.status(400).json({ error: 'Escribe el nicho o tipo de negocio.' });
    }

    const instructions = [
      'Eres TikBoost, un estratega de contenido corto para redes sociales.',
      'Crea UNA idea original, concreta y grabable con celular.',
      'Escribe en español natural, sin prometer viralidad ni resultados garantizados.',
      'El gancho debe funcionar en los primeros 2 segundos.',
      'El guion debe ser breve, claro y dividido por acciones usando flechas o separadores.',
      'Qué grabar debe indicar planos sencillos.',
      'El texto/caption debe ser corto y conversacional.',
      'Los hashtags deben ser relevantes, máximo 7, en una sola cadena.',
      'La estrategia debe explicar en 1 o 2 frases por qué la idea puede captar atención.'
    ].join(' ');

    const input = `Negocio o nicho: ${niche}\nTema específico: ${topic || 'elige uno que tenga potencial'}\nObjetivo: ${goal}\nAudiencia: ${audience}\nTono: ${tone}\nPlataforma: ${platform}\nDuración objetivo: ${duration}`;

    const r = await fetch('https://api.openai.com/v1/responses', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${apiKey}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        model: process.env.OPENAI_MODEL || 'gpt-5.6-luna',
        instructions,
        input,
        max_output_tokens: 900,
        text: {
          format: {
            type: 'json_schema',
            name: 'tikboost_idea',
            strict: true,
            schema: ideaSchema
          }
        }
      })
    });

    const data = await r.json();
    if (!r.ok) {
      await refundGeneration(userId);
      const msg = data?.error?.message || `OpenAI respondió ${r.status}`;
      return res.status(502).json({ error: msg });
    }

    const raw = data.output_text;
    if (!raw) {
      await refundGeneration(userId);
      return res.status(502).json({ error: 'La IA no devolvió contenido.' });
    }

    let idea;
    try { idea = JSON.parse(raw); }
    catch {
      await refundGeneration(userId);
      return res.status(502).json({ error: 'La IA devolvió un formato inesperado.' });
    }

    return res.status(200).json({ ok: true, idea, account: usage });
  } catch (error) {
    if (userId && usage?.allowed) await refundGeneration(userId);
    return res.status(500).json({ error: error?.message || 'No se pudo generar la idea.' });
  }
}
