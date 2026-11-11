import { mkdirSync, writeFileSync } from 'node:fs';

mkdirSync('public', { recursive: true });

let apiUrl = process.env.API_URL || '/api';
let stripePublishableKey = process.env.STRIPE_PUBLISHABLE_KEY || '';

if (!apiUrl.startsWith('/')) {
  console.warn(`API_URL="${apiUrl}" es absoluta. Recuerda que si usas cookies HttpOnly, el backend debe configurar SameSite=None y Secure=true si está en otro dominio.`);
}

const envConfig = {
  API_URL: apiUrl,
  STRIPE_PUBLISHABLE_KEY: stripePublishableKey
};

writeFileSync('public/env.js', `window.__env = ${JSON.stringify(envConfig, null, 2)};\n`);
console.log(`Generated public/env.js with API_URL=${apiUrl} and STRIPE_PUBLISHABLE_KEY=${stripePublishableKey ? 'SET' : 'NOT SET'}`);
