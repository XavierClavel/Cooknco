#build
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json .npmrc ./
RUN npm ci
COPY . .
ENV NODE_ENV=production
RUN npm run build

#production
FROM node:22-alpine AS production
WORKDIR /app
# Nitro bundles its own dependencies into .output, so there is nothing to
# install here.
COPY --from=build /app/.output ./.output
ENV NODE_ENV=production \
    HOST=0.0.0.0 \
    PORT=3000 \
    NITRO_HOST=0.0.0.0 \
    NITRO_PORT=3000
# 3000 rather than 80: the server runs unprivileged and cannot bind a port
# below 1024. Compose publishes it on 80; the k8s Service/Deployment
# containerPort (defined outside this repo) needs the same change.
EXPOSE 3000
USER node
CMD ["node", ".output/server/index.mjs"]
