## Services and responsibilities

This project uses a small microservice architecture with two primary services:

1) Photo-sorter (Java Spring Boot)
- Responsibilities:
  - Directory scanning and ingestion job orchestration.
  - Persisting canonical photo metadata and person records to the database.
  - Publishing per-photo work messages to Kafka (`photos-to-process`).
  - Exposing callback endpoints for the Python ML service to persist analysis results.
  - Exposing a `person-dump` endpoint for Python to fetch representative embeddings when local NN matching is desired.
  - Providing admin endpoints for merging, re-indexing and retrying failed batches.

2) Python ML Service (FastAPI)
- Responsibilities:
  - Run local ML models (e.g. facenet-pytorch or insightface) to detect faces and compute embeddings.
  - Accept a photo path + callback URL and perform analysis asynchronously.
  - Optionally fetch representative person embeddings from Java via `GET /person-dump` to perform local NN matching for speed.
  - Invoke the Java callback endpoint with results (detections, embeddings, suggested matches).

Deployment notes
- Both services can run locally on the same host. Python service can be Dockerized or run as a virtualenv. Java is a Spring Boot app.
- Kafka and MySQL are required infra; the repo contains a `docker/mysql` folder. Consider a lightweight docker-compose for local dev: MySQL + Kafka + Zookeeper + Python service + Java service.
