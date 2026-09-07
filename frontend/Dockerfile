#build
FROM node:lts-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .

# Vite inlines the endpoints it is given at build time, and a real environment
# variable outranks the committed .env.production. Left unset — how CI builds this
# image — neither is defined and .env.production applies unchanged; the local
# compose stack passes its own so the SPA calls the backend beside it.
ARG VITE_API_URL
ARG VITE_IMG_URL
RUN npm run build

#production
FROM nginx:stable-alpine as production
# nginx.conf serves a single `listen 80` server; TLS is terminated upstream.
EXPOSE 80
COPY nginx.conf /etc/nginx/conf.d/default.conf
RUN rm -rf /usr/share/nginx/html/*
COPY --from=build /app/dist /usr/share/nginx/html
CMD ["nginx", "-g", "daemon off;"]