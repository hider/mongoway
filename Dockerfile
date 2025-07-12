FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.source=https://github.com/hider/mongoway
LABEL org.opencontainers.image.description="MongoWay is a Database Change Management Tool for MongoDB"
LABEL org.opencontainers.image.licenses=GPL-3.0-or-later

COPY build/install /opt

ENTRYPOINT ["/opt/mongoway/bin/mongoway"]
CMD ["help"]
