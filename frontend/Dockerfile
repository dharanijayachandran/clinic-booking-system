# Build the Angular app, then serve the static output through nginx, which
# also proxies /api to the backend. Same-origin in production for the same
# reason the dev server proxies: the auth cookies and the CSRF double-submit
# both depend on the browser treating app and API as one origin.
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
