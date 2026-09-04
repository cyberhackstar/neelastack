# --- Build stage ---
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# --- Runtime stage (Angular SSR via Node/Express) ---
FROM node:22-alpine
WORKDIR /app

RUN addgroup -S neelastack && adduser -S neelastack -G neelastack
COPY --from=build /app/dist/neelastack-frontend ./dist/neelastack-frontend
COPY --from=build /app/package*.json ./
RUN npm ci --omit=dev
RUN chown -R neelastack:neelastack /app
USER neelastack

EXPOSE 4000
ENV PORT=4000

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD node -e "fetch('http://localhost:4000').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "dist/neelastack-frontend/server/server.mjs"]
