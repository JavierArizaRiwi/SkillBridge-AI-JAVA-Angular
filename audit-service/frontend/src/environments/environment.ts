export const environment = {
  production: true,
  get auditApiUrl() { return window.__env?.AUDIT_API_URL || '/api/audit'; },
  get authApiUrl() { return window.__env?.AUTH_API_URL || '/api/auth/login'; }
};
