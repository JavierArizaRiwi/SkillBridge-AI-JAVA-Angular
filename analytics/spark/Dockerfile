# SkillBridge AI — Spark analytics engine (HU-25)
# Bundles JDK 17 + PySpark 3.5.4 so no host-side JDK install is needed.
# Build: docker build -t skillbridge-analytics analytics/spark/
# Run (example):
#   docker run --rm \
#     -v /path/to/dataset:/data/dataset:ro \
#     -v /path/to/output:/data/output \
#     skillbridge-analytics \
#     --dataset /data/dataset --output /data/output

FROM python:3.11-slim

LABEL org.opencontainers.image.title="SkillBridge Analytics Engine"
LABEL org.opencontainers.image.description="PySpark batch job for HU-25 demand analytics"
LABEL org.opencontainers.image.source="https://github.com/Danilo-Doria/SkillBridge-AI-JAVA-Angular"

# JDK 17 (required by PySpark 3.5)
RUN apt-get update && apt-get install -y --no-install-recommends \
        openjdk-17-jdk-headless \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PYSPARK_PYTHON=python3
ENV PYSPARK_DRIVER_PYTHON=python3

WORKDIR /app

COPY requirements.txt setup.cfg ./
COPY src/ src/

RUN pip install --no-cache-dir -r requirements.txt \
    && pip install --no-cache-dir -e .

# Default entrypoint: the job CLI
ENTRYPOINT ["python", "-m", "skillbridge_analytics.job"]
