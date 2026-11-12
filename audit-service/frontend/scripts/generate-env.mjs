import { mkdirSync, writeFileSync } from 'node:fs';

mkdirSync('public', { recursive: true });

let auditApiUrl = process.env.AUDIT_API_URL || '/api/audit';
let authApiUrl = process.env.AUTH_API_URL || '/api/auth/login';

if (!auditApiUrl.startsWith('/') || !authApiUrl.startsWith('/')) {
  console.warn(`Una o más URLs son absolutas. Recuerda que si usas cookies HttpOnly, el backend debe configurar SameSite=None y Secure=true si está en otro dominio.`);
}

writeFileSync('public/env.js', `window.__env = { 
  AUDIT_API_URL: ${JSON.stringify(auditApiUrl)},
  AUTH_API_URL: ${JSON.stringify(authApiUrl)}
};\n`);
console.log(`Generated public/env.js with AUDIT_API_URL=${auditApiUrl} and AUTH_API_URL=${authApiUrl}`);
