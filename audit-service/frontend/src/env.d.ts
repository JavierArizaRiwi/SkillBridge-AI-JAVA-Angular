export {};
declare global {
  interface Window {
    __env?: { 
      AUDIT_API_URL?: string;
      AUTH_API_URL?: string;
    };
  }
}
