# Module Documentation: Web Frontend (`frontend/`)

The Web Frontend is a responsive Single-Page Application (SPA) built with Angular 20, TypeScript 5.9, and Tailwind CSS v4. It provides customer, provider, and admin portals for the SkillBridge AI platform.

---

## 1. Module Overview & Entry Points

- **Root Directory**: `frontend/`
- **Application Bootstrap**: [frontend/src/main.ts](../../frontend/src/main.ts)
- **Root Component**: [frontend/src/app/app.component.ts](../../frontend/src/app/app.component.ts)
- **Routing Configuration**: [frontend/src/app/app.routes.ts](../../frontend/src/app/app.routes.ts)
- **Styles Entry Point**: [frontend/src/styles.css](../../frontend/src/styles.css) (imports `@import "tailwindcss";`)

---

## 2. Directory Layout & Architecture

```text
frontend/src/
├── app/
│   ├── core/                    # Core singleton services, HTTP interceptors, API utilities
│   │   ├── api.ts               # Base API URL resolver (reads window.__env.API_URL)
│   │   ├── auth.service.ts      # Authentication state via Angular Signals, session loader
│   │   ├── auth.interceptor.ts  # Adds credentials, Bearer fallback, CSRF headers
│   │   ├── booking.service.ts   # Booking creation, retrieval, and cancellation calls
│   │   ├── offering.service.ts  # Catalog fetching, offering CRUD and activation
│   │   ├── category.service.ts  # Category lookup and management
│   │   ├── admin-user.service.ts# User status and role management
│   │   └── stripe.service.ts    # Stripe Elements initialization and payment confirmation
│   ├── features/                # Feature routed components
│   │   ├── home.component.ts    # Public landing page and service catalog
│   │   ├── login.component.ts   # User login and registration form
│   │   ├── ai.component.ts      # Multimodal AI recommendation assistant
│   │   ├── booking.component.ts # Booking creation form with date/offering picker
│   │   ├── my-bookings.component.ts # Customer bookings list and cancellation button
│   │   ├── provider-offerings.component.ts # Provider offering creation and state toggle
│   │   ├── admin-users.component.ts # Admin user management dashboard
│   │   └── payment-modal/       # Stripe card payment modal dialog
│   ├── guards/                  # Functional route guards
│   │   ├── auth-guard.ts        # Restricts routes to authenticated users
│   │   ├── guest-guard.ts       # Restricts login route for already logged-in users
│   │   └── role-guard.ts        # Verifies role permissions ('PROVIDER', 'ADMIN')
│   ├── app.component.ts         # Navigation bar, footer, and session bootstrap
│   └── app.routes.ts            # Application route table
├── scripts/
│   └── generate-env.mjs         # Generates public/env.js from build environment variables
└── public/
    └── env.js                   # Runtime injected environment configuration
```

---

## 3. Important Services & Components

### 3.1. Core Services & State Management
- [auth.service.ts](../../frontend/src/app/core/auth.service.ts):
  - Manages reactive session state using Angular Signals (`signal<AuthResponse | null>`).
  - Provides derived computed state: `authenticated = computed(...)`, `role = computed(...)`.
  - Performs session verification on startup by calling `GET /api/auth/me`.
- [auth.interceptor.ts](../../frontend/src/app/core/auth.interceptor.ts):
  - Automatically attaches `withCredentials: true` to ensure the HttpOnly `access_token` cookie is transmitted.
  - Extracts the `XSRF-TOKEN` cookie and attaches the `X-XSRF-TOKEN` header to outgoing mutating HTTP requests.
- [api.ts](../../frontend/src/app/core/api.ts):
  - Encapsulates `apiBase()` which resolves `window.__env?.API_URL || '/api'`. Allows dynamic API URL swapping without recompiling Angular.

### 3.2. Routed Feature Views
- [ai.component.ts](../../frontend/src/app/features/ai.component.ts): Supports text chatting with the AI advisor, voice input (microphone recording submitted as `multipart/form-data` to `/voice`), image upload (`/image`), and playback of audio synthesized by ElevenLabs (`/tts`).
- [payment-modal.component.ts](../../frontend/src/app/features/payment-modal.component.ts): Integrates with `@stripe/stripe-js` to mount Stripe Card Elements and confirm payments using client secrets received from the backend.

---

## 4. Runtime Configuration & Environment Loading

In containerized and cloud deployments, environment variables are injected at startup rather than baked into compile-time bundles:
1. `generate-env.mjs` runs during `prebuild` or container initialization.
2. It generates `public/env.js`, defining `window.__env = { API_URL: "...", STRIPE_PUBLISHABLE_KEY: "..." }`.
3. [index.html](../../frontend/src/index.html) loads `/env.js` before Angular bundles execute.

---

## 5. Concrete Files to Inspect Before Modifying

1. **Before modifying Navigation, Layout, or App Startup**:
   - [app.component.ts](../../frontend/src/app/app.component.ts)
   - [app.routes.ts](../../frontend/src/app/app.routes.ts)
2. **Before modifying API Client Calls or Security Headers**:
   - [auth.interceptor.ts](../../frontend/src/app/core/auth.interceptor.ts)
   - [auth.service.ts](../../frontend/src/app/core/auth.service.ts)
   - [api.ts](../../frontend/src/app/core/api.ts)
3. **Before modifying Payment Flows**:
   - [payment-modal.component.ts](../../frontend/src/app/features/payment-modal.component.ts)
   - [stripe.service.ts](../../frontend/src/app/core/stripe.service.ts)

---

## 6. Testing & Build Instructions

```bash
cd frontend

# Install dependencies strictly from package-lock.json
npm ci

# Run unit tests headlessly (Karma/Jasmine with ChromeHeadless)
npm test -- --watch=false --browsers=ChromeHeadless

# Build production artifacts (outputs to dist/)
npm run build
```
