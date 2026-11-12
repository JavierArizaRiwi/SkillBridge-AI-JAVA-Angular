import { mkdirSync, writeFileSync } from 'node:fs';

mkdirSync('public', { recursive: true });

let apiUrl = process.env.API_URL || '/api';

if (!apiUrl.startsWith('/')) {
  console.warn(`API_URL="${apiUrl}" es absoluta. Recuerda que si usas cookies HttpOnly, el backend debe configurar SameSite=None y Secure=true si está en otro dominio.`);
}

writeFileSync('public/env.js', `window.__env = { API_URL: ${JSON.stringify(apiUrl)} };\n`);
console.log(`Generated public/env.js with API_URL=${apiUrl}`);
