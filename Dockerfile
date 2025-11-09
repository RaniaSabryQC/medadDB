FROM quay.io/keycloak/keycloak:26.3.0 AS builder

# Enable health and metrics support
ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true

# Configure a database vendor
ENV KC_DB=mysql

WORKDIR /opt/keycloak


COPY themes themes/

COPY target/*.jar providers

# for demonstration purposes only, please make sure to use proper certificates in production instead
RUN keytool -genkeypair -storepass password -storetype PKCS12 -keyalg RSA -keysize 2048 -dname "CN=server" -alias server -ext "SAN:c=DNS:localhost,IP:127.0.0.1" -keystore conf/server.keystore

RUN /opt/keycloak/bin/kc.sh build --features=transient-users

FROM quay.io/keycloak/keycloak:26.3.0

COPY --from=builder /opt/keycloak/ /opt/keycloak/
ENV KC_DB=mysql

ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start", "--optimized", "--cache=ispn"]
