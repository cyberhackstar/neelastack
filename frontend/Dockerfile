FROM node:22-alpine3.24 AS build

WORKDIR /app

# Bring Alpine security packages to the latest patched versions
RUN apk upgrade --no-cache

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build


FROM node:22-alpine3.24 AS runtime

WORKDIR /app

# Keep runtime OS packages patched
RUN apk upgrade --no-cache \
    && addgroup -S neelastack \
    && adduser -S neelastack -G neelastack

COPY --from=build /app/dist/neelastack-frontend ./dist/neelastack-frontend
COPY --from=build /app/package*.json ./

RUN npm ci --omit=dev \
    && npm cache clean --force \
    && rm -rf /usr/local/lib/node_modules/npm \
              /opt/yarn-v1.22.22 \
              /usr/local/lib/node_modules/corepack \
    && chown -R neelastack:neelastack /app

ENV NODE_ENV=production
ENV PORT=4000

USER neelastack

EXPOSE 4000

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD node -e "fetch('http://localhost:4000').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "dist/neelastack-frontend/server/server.mjs"]