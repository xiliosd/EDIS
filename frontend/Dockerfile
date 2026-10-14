# syntax=docker/dockerfile:1
FROM node:22.23.3-slim AS build
WORKDIR /app
COPY package.json package-lock.json .npmrc ./
RUN npm ci --ignore-scripts
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.30.3-alpine3.23
LABEL org.opencontainers.image.title="formulario-edi-frontend" \
      org.opencontainers.image.description="Interfaz web del Formulario EDI/EDID" \
      org.opencontainers.image.authors="Oscar Dueñas" \
      org.opencontainers.image.documentation="README.md del repositorio formulario-web"
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
ENV BACKEND_URL=http://backend:8080
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -q --spider http://127.0.0.1:8080/ || exit 1
