# SkillBridge Audit Frontend

The frontend for the **SkillBridge Audit Service**. It provides a professional, responsive, and real-time dashboard for administrators to monitor domain events.

## Features

- **Modern UI**: Styled with **Tailwind CSS** for a clean, admin dashboard aesthetic.
- **Angular 20 & Signals**: Utilizes the latest Angular features for performant reactive state.
- **Real-Time Polling**: Auto-updates metrics and events every 10 seconds.
- **Filtering & Search**: Client-side filtering by Event Type, Action, Role, Resource, and deep search capabilities.
- **Event Inspection**: Modal viewer to analyze the raw JSON payload and event metadata.
- **Security**: Respects the existing SkillBridge JWT authentication via an HTTP interceptor.

## Technical Details

- **Tailwind Config**: We use Tailwind v4 out of the box natively integrated into the Angular 19+ builder. The configuration relies entirely on `@import "tailwindcss";` in `styles.css`.
- **Components**: The UI logic and structure are encapsulated within the main standalone `DashboardComponent`.
- **API Consumed**:
  - `GET /api/audit/events`
  - `GET /api/audit/events/{eventId}`
  - `GET /api/audit/stats`
- **Authentication**: JWT is securely intercepted from `localStorage.getItem('skillbridge_token')` and injected as a Bearer token in backend requests.

## Setup & Running Locally

1. Install dependencies:
   ```bash
   npm install
   ```

2. Start the development server:
   ```bash
   npm start
   ```

3. Navigate to `http://localhost:4200`.

## Building for Production

Run `npm run build` to compile the project. The build artifacts will be stored in the `dist/` directory, fully optimized and bundled.
