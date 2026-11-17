export {};
declare global {
  interface Window {
    __env?: {
      API_URL?: string;
      STRIPE_PUBLISHABLE_KEY?: string;
    };
  }
}
