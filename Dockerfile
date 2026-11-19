FROM ubuntu:24.04

# Run Debian in non interactive mode
ENV DEBIAN_FRONTEND=noninteractive

# Configure env
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64/
ENV JAVA_OPTIONS="-Dfile.encoding=UTF-8 -Xmx1g"
ARG JETTY_VERSION=latest
ENV JETTY_HOME=/opt/jetty

# Install packages
RUN apt-get update && apt-get upgrade libgnutls30 -y -q && \
    apt-get -y -q --no-install-recommends install \
    vim less procps unzip wget tzdata openjdk-21-jdk \
    ffmpeg \
    mediainfo \
    tesseract-ocr \
    tesseract-ocr-ara \
    tesseract-ocr-ces \
    tesseract-ocr-chi-sim \
    tesseract-ocr-chi-tra \
    tesseract-ocr-dan \
    tesseract-ocr-deu \
    tesseract-ocr-fin \
    tesseract-ocr-fra \
    tesseract-ocr-heb \
    tesseract-ocr-hin \
    tesseract-ocr-hun \
    tesseract-ocr-ita \
    tesseract-ocr-jpn \
    tesseract-ocr-kor \
    tesseract-ocr-lav \
    tesseract-ocr-nld \
    tesseract-ocr-nor \
    tesseract-ocr-pol \
    tesseract-ocr-por \
    tesseract-ocr-rus \
    tesseract-ocr-spa \
    tesseract-ocr-swe \
    tesseract-ocr-tha \
    tesseract-ocr-tur \
    tesseract-ocr-ukr \
    tesseract-ocr-vie \
    tesseract-ocr-sqi \
    tesseract-ocr-srp \
    tesseract-ocr-srp-latn \
    && apt-get clean && \
    rm -rf /var/lib/apt/lists/*
RUN dpkg-reconfigure -f noninteractive tzdata

# Install Jetty (resolves latest Jetty 12.x stable release when JETTY_VERSION=latest)
RUN if [ "$JETTY_VERSION" = "latest" ] || [ -z "$JETTY_VERSION" ]; then \
        JETTY_VERSION=$(wget -qO- "https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-home/maven-metadata.xml" \
            | grep -oE '<version>12\.[0-9]+\.[0-9]+</version>' \
            | sed -E 's#</?version>##g' \
            | sort -V \
            | tail -n 1); \
    fi \
    && test -n "$JETTY_VERSION" \
    && echo "Installing Eclipse Jetty ${JETTY_VERSION}..." \
    && wget -nv -O /tmp/jetty.tar.gz \
        "https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-home/${JETTY_VERSION}/jetty-home-${JETTY_VERSION}.tar.gz" \
    && tar xzf /tmp/jetty.tar.gz -C /opt \
    && rm -f /tmp/jetty.tar.gz \
    && mv /opt/jetty* /opt/jetty \
    && useradd jetty -U -s /bin/false \
    && chown -R jetty:jetty /opt/jetty \
    && mkdir /opt/jetty/webapps \
    && chmod +x /opt/jetty/bin/jetty.sh

EXPOSE 8080

# Install app
RUN mkdir /app && \
    cd /app && \
    java -jar /opt/jetty/start.jar --add-modules=server,http,ee10-webapp,ee10-deploy

ADD docs.xml /app/webapps/docs.xml
ADD docs-web/target/docs-web-*.war /app/webapps/docs.war

WORKDIR /app

CMD ["java", "-jar", "/opt/jetty/start.jar"]
