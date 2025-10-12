## Endpoints, payloads and processing flow

This document lists the primary endpoints exposed by the Java and Python services, example payloads, and the async flow between them.

Java (Photo-sorter) endpoints

- POST /ingest-folder
  - Request: { "path": "/abs/path/to/photos", "recursive": true, "batchSize": 50 }
  - Response: { "jobId": "uuid" }
  - Behavior: starts an async job that scans, inserts photo metadata (status=READY) and publishes per-photo messages to Kafka.

- PUT /photo/update-status
  - Request: { "photo_id": 123, "status": "PROCESSING" }
  - Behavior: update photo status (enum: READY, PROCESSING, PROCESSED, FAILED)

- POST /callback/photo-processed
  - Called by Python ML service with analysis results.
  - Example payload:
    {
      "photo_id": 123,
      "status": "PROCESSED",
      "detections": [ { "bbox": [x1,y1,x2,y2], "embedding": "base64..", "confidence": 0.98, "suggested_person_id": 45, "similarity": 0.82 } ],
      "new_persons": [ { "temp_id": "t1", "embedding": "base64.." } ],
      "meta": { "model_version": "insightface-v2" }
    }

- GET /person-dump
  - Query: ?since_id=0&limit=5000
  - Response: compressed payload [{ person_id, representative_embedding, representative_photo_id, meta }]

- GET /photo/{photo_id} and GET /person/{person_id}
  - Return canonical data for UI/CLI and admin tooling.

Python (ML) endpoints

- POST /process-photo or /process
  - Request: { "path": "/abs/path/to/photo.jpg", "callbackUrl": "http://java:8080/callback/photo-processed", "callback_id": "optional-uuid" }
  - Behavior: run detection, compute embeddings, optionally call `GET /person-dump` for local NN; then POST result to `callbackUrl`.

Async flow (end-to-end)

1) User calls Java POST /ingest-folder -> returns jobId.
2) Java scans and inserts photos (status=READY), creates mapping entries and publishes messages to `photos-to-process`.
3) Worker(s) (Java or another process) consume `photos-to-process` and call Python POST /process-photo with { path, callbackUrl }.
4) Python processes, optionally matches locally, and POSTs results to Java POST /callback/photo-processed.
5) Java callback persists results transactionally: create persons/embeddings, update `photo_persons` and `photos.person_ids`, set photo status to PROCESSED.

Idempotency and retries

- Callbacks should include `callback_id` to allow deduplication.
- `photo_ingest_map` and `ingest_batches` track publish attempts and failures. Schedulers can re-publish failed entries to Kafka.
