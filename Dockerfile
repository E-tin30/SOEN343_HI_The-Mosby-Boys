# Demo-platform image: one container with nginx (frontend build + /api proxy, port 80)
# and the Spring Boot backend (port 8080, internal). Build context: the repository root.
# The previous backend-only image for docker compose is Dockerfile.backend.

# 1. Frontend production build
FROM node:22-alpine AS web
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY index.html tsconfig.json ./
COPY src/main/java/com/mosbyboys/dishesbecrazy/ui ./src/main/java/com/mosbyboys/dishesbecrazy/ui
# Browser keys are frozen into the bundle at build time and are empty by default.
# Stripe card payment and the Google map stay disabled until a (test-mode, referrer-restricted) key is set here.
ARG VITE_STRIPE_PUBLISHABLE_KEY=""
ARG VITE_GOOGLE_MAPS_API_KEY=""
ENV VITE_STRIPE_PUBLISHABLE_KEY=$VITE_STRIPE_PUBLISHABLE_KEY \
    VITE_GOOGLE_MAPS_API_KEY=$VITE_GOOGLE_MAPS_API_KEY
RUN npm run build

# 2. Backend jar
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp package -Dmaven.test.skip=true

# 3. Runtime
FROM eclipse-temurin:21-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends nginx \
 && rm -rf /var/lib/apt/lists/* /etc/nginx/sites-enabled/default /etc/nginx/conf.d/default.conf
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=web /app/dist /usr/share/nginx/html
COPY --from=backend /build/target/dishes-be-crazy-0.0.1-SNAPSHOT.jar /app/app.jar
COPY docker/start.sh /app/start.sh
EXPOSE 80
CMD ["/app/start.sh"]
