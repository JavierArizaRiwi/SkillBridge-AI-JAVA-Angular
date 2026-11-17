FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm install --include=dev --no-audit --no-fund
COPY . .
ARG API_URL=/api
ARG STRIPE_PUBLISHABLE_KEY=
ENV API_URL=$API_URL
ENV STRIPE_PUBLISHABLE_KEY=$STRIPE_PUBLISHABLE_KEY
RUN npm run build

FROM nginx:stable-alpine
ENV BACKEND_URL=http://backend:8080
ENV NGINX_ENVSUBST_FILTER=BACKEND_URL
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=builder /app/dist/skillbridge-ai/browser /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
CMD ["nginx", "-g", "daemon off;"]
