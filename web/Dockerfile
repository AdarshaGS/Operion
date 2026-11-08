## Frontend (Vite/React) — build stage
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

# The API base URL is baked into the bundle at build time (src/api/client.ts), and when it is
# unset the bundle silently falls back to http://localhost:8080 - which is wrong for anything but a
# developer machine. So the build refuses to run without it being stated explicitly:
#   --build-arg VITE_API_BASE_URL=https://api.example.com   the API's public origin
#   --build-arg VITE_API_BASE_URL=                          empty = same origin, via nginx's /api/ proxy
# (docker-compose.yml passes http://localhost:8080 for the local dev stack.)
ARG VITE_API_BASE_URL
RUN if [ -z "${VITE_API_BASE_URL+x}" ]; then \
      echo "ERROR: VITE_API_BASE_URL build arg is not set. Pass --build-arg VITE_API_BASE_URL=<api origin> (or an empty value for same-origin via the nginx /api proxy). Refusing to bake the localhost default into the image." >&2; \
      exit 1; \
    fi
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

COPY . .
RUN npm run build

## Frontend — runtime stage (static files served by unprivileged nginx)
# nginx-unprivileged runs as the non-root `nginx` user and listens on 8080 (not 80), so this image
# never runs as root. nginx.conf's `listen 8080` and docker-compose's `5173:8080` mapping match.
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 8080
