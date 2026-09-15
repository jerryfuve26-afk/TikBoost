import express from 'express';
import generateHandler from './api/generate.js';
import accountHandler from './api/account.js';
import configHandler from './api/config.js';
import verifyHandler from './api/billing/verify.js';

const app = express();
app.use(express.json({ limit: '200kb' }));
app.use((req, res, next) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  if (req.method === 'OPTIONS') return res.sendStatus(204);
  next();
});

app.get('/api/health', (_req, res) => res.json({ ok: true, service: 'TikBoost 3.0' }));
app.get('/api/config', (req, res) => configHandler(req, res));
app.get('/api/account', (req, res) => accountHandler(req, res));
app.post('/api/generate', (req, res) => generateHandler(req, res));
app.post('/api/billing/verify', (req, res) => verifyHandler(req, res));

const port = Number(process.env.PORT || 3000);
app.listen(port, () => console.log(`TikBoost 3.0 en http://localhost:${port}`));
