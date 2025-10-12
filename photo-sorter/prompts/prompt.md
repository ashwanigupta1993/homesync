```markdown
## API / Service contract and async processing design
We will use a hybrid asynchronous design where the Python ML service performs the face detection and embedding work and calls back the Java service with results. The Java service is the authoritative source of truth for DB writes and exposes both ingestion endpoints and a callback endpoint for the Python service to invoke when analysis is complete.
High-level responsibilities
- Python ML service: accepts a photo path and a callback URL, performs detection and embedding, optionally asks the Java service for a dump of person metadata/representative embeddings needed for nearest-neighbor search, and when finished invokes the callback with the analysis results.
- Java service: exposes ingestion endpoints, a callback endpoint to receive results, and a Kafka-based ingestion/worker pipeline to enable parallel processing and resilience.
Minimal Java endpoints and operations (photo-sorter)

The Java service is responsible for scanning directories, inserting canonical photo metadata, publishing work messages to Kafka, exposing a callback endpoint for ML results, and returning person dumps for local NN matching. Below are the endpoints, behavior, and example payloads.

 - POST /ingest-folder
   - Request body: { "path": "/abs/path/to/photos", "recursive": true|false, "batchSize": 50, "jobName": "optional" }
   - Response: { "jobId": "uuid" }
   - Purpose: create a durable ingestion job that scans the filesystem, inserts canonical photo metadata in batches, and publishes per-photo work messages to Kafka for downstream workers. The endpoint returns immediately with a jobId and performs the heavy work asynchronously.

  Behavior (detailed, production-ready)

  1) Job creation
     - Generate a stable `jobId` (uuid) and insert an `ingest_jobs` row: { job_id, job_name, path, recursive, requested_by, created_at, status: SCANNED }.
     - Return { jobId } immediately to the caller so the ingest runs asynchronously.

  2) Directory scan (async worker triggered by job)
     - Walk the directory using a streaming scanner (yield entries). For each file run a light filter: extension whitelist (jpg/jpeg/png/heic/heif/nef/cr2) and optional magic-bytes check.
     - Collect metadata: absolute `path`, `filename`, `filesize`, `modified_at` and a fast sample hash (xxhash64 or sha1 of first N bytes) for cheap dedupe. Optionally compute full-file SHA1 asynchronously.

  3) Batch insert into DB
     - Group files into batches of `batchSize` (configurable; default 50).
     - Photos table policy: do not store batch or job state in the `photos` table. We keep `photos` canonical and use a separate mapping table for batch/job relationships (see `photo_ingest_map` below).
     - For each batch do a multi-row INSERT IGNORE into `photos` to avoid duplicate photos by path/filename. When a row is ignored (duplicate), skip further processing for that photo (do not publish it again).
     - After INSERT IGNORE, resolve `photo_id` for each file (via RETURNING or a SELECT by path). Insert rows into `photo_ingest_map` linking photo_id -> batch_id -> job_id. These map rows drive publishing and retries.
     - Insert an `ingest_batches` record per batch: { batch_id, job_id, batch_index, photo_count, status: READY }.

  4) Publish to Kafka
     - After a successful batch insert, publish one message per-photo to `photos-to-process` with schema: { "photo_id": 12345, "path": "/abs/path/to/photo.jpg", "job_id": "...", "ingest_batch_id": 7, "created_at": "..." }.
     - Partitioning strategy: use hash(photo_id) % partitions or a directory-based key to co-locate related photos. Choose partitions based on consumer parallelism.
     - Use producer acks=all and retries/backoff. On persistent publish failures mark the batch as FAILED

  5) Job lifecycle updates
     - Update `ingest_batches.status` to PUBLISHED after publishing. When all batches are published update `ingest_jobs.status` to PUBLISHED (or COMPLETED if you also want to wait for downstream processing).
     - Expose `GET /ingest-status/{jobId}` returning aggregated counts: scanned, inserted, published, failed_batches and optionally progress by batch.

TODO (schedulers)
        - lets create a scheduler which checks the database for photos which are FAILED, and use retry column to retry and mark them as ready again.
        - lets have another scheduler which will mark processing status which has been in processing state for a prolonged time ( configurable like 10mins).
    

  Reliability and idempotency
     - Deduplicate on insert using a unique constraint on (path) or (sample_hash). Use upserts so repeated ingests skip existing photos.
     - If a file's modified_at or filesize changed, use filehash comparison to decide whether to re-ingest.
     - Make publishing idempotent by storing ingest_batch_id and photo_id in the message and tracking published batches.

  Backpressure and scaling
     - Stream scanning + bounded queue: do not read the next batch until prior batch insert + publish completes or queue is below threshold.
     - Limit concurrent publisher threads to avoid overloading DB/Kafka.

  Monitoring and observability (not required)
     - Emit metrics: files_scanned, files_inserted, batches_published, kafka_publish_failures, avg_batch_time.
     - Provide `GET /ingest-jobs` to list recent jobs and statuses.

  Suggested DB tables
     - ingest_jobs (job_id PK, job_name, path, recursive, requested_by, created_at, status, scanned_count, inserted_count, published_count, finished_at)
     - ingest_batches (batch_id PK, job_id FK, batch_index, photo_count, status:READY|PUBLISHED|FAILED, inserted_at, published_at)

  Additional operational notes
     - Support a dry-run mode to estimate counts and time without inserting/publishing.
     - For very large datasets compute full-file hashes in a background pass to avoid slowing down the initial ingest.
     - Support resume after crash: store last successfully published batch_id and resume from the next batch.

  Example publish message (for `photos-to-process`)
  {
    "photo_id": 12345,
    "path": "/abs/path/to/photo.jpg",
    "job_id": "uuid",
    "ingest_batch_id": 7,
    "created_at": "2025-10-05T12:34:56Z"
  }

  - Notes on partitioning: choose partitions according to expected consumer parallelism. If ordering across photos is not required, use a simple hash(photo_id) partitioning for even load.

- PUT /photo/update-status
   - Request body: { "photo_id": 123, "status": "PROCESSING" }
   - Allowed statuses (Java enum PhotoStatus): READY, PROCESSING, PROCESSED, FAILED
   - Behavior: update the `status` column for the photo. This endpoint is useful for workers or admin tooling to mark progress when Kafka-based workflows are not used or for manual retry.

- POST /callback/photo-processed
   - Called by the Python ML service (or a worker proxy) when analysis completes.
   - Example request body (JSON):
      {
         "photo_id": 123,
         "path": "/abs/path/to/photo.jpg",
         "status": "PROCESSED", // or "FAILED"
         "detections": [
            {
               "bbox": [x1, y1, x2, y2],
               "embedding": "base64...",
               "confidence": 0.98,
               "suggested_person_id": 45, // optional: python's local NN suggestion
               "similarity": 0.82
            }
         ],
         "new_persons": [
            {
               "temp_id": "t-1",
               "embedding": "base64...",
               "suggested_display_name": "unknown"
            }
         ],
         "meta": { "model_version": "insightface-v2" }
      }

   - Behavior (canonical persistence):
      1) Validate the payload and begin a DB transaction.
      2) For each detection: if `suggested_person_id` is present and valid, link that person; otherwise create a new `persons` row and create an `embeddings` row. Store the embedding blob (or base64) and link to the person and source photo.
      3) Update `photos.person_ids` (and `photo_persons` join table) with the final list of person ids for the photo.
      4) Set photo `status` to PROCESSED (or FAILED if status indicates failure) and commit the transaction.
      5) Return 200 with { "ok": true, "created_person_ids": [..] }.

   - Notes:
      - The callback is the authoritative write path. Workers must not perform these writes directly.
      - Support idempotency: accept a `callback_id` or use (photo_id + model_version + timestamp) to dedupe repeated callbacks.

- GET /person/{person_id}
   - Returns person metadata and example photo paths (and optionally representative embedding if requested by an authorized consumer).

- GET /photo/{photo_id}
   - Returns photo metadata including status (READY | PROCESSING | PROCESSED | FAILED) and, when processed, the `person_ids` list and `detections` details.

- GET /person-dump
   - Purpose: allow the Python service to fetch representative embeddings for nearest-neighbor matching locally.
   - Query parameters: ?since_id=0&limit=5000 or pagination tokens.
   - Response: compressed payload with entries [{ person_id, representative_embedding: base64, representative_photo_id, meta }].
   - Security: this endpoint must be restricted to local network or require a shared secret/header token. Support gzipped responses and pagination for large datasets.

Persistence and concurrency notes

- All creates/updates in `callback/photo-processed` must be transactional to avoid partial writes.
- When multiple workers request Python processing for the same photo concurrently, the callback path should detect duplicate processing attempts (use idempotency keys or check photo status) and ignore or reconcile duplicates.


Kafka topics and flow (async) (currently part of photo-sorter module) (define seperate packages)
- photos-to-process:
    - producer:
        published by the ingest endpoint when new photos are inserted. Messages: { photo_id }
    - consumer
        - consumed by one of the consumer on the topic and a unique consumer for a partition.
        - after it consumes the message the status column should be marked as PROCESSING
        - it will offload the processing task to the pythos HTTP service which will do the AI/ML work for us
        - if it fails/5XX/4XX (any other failure) to call the AI/ML work it will mark the status as FAILED. by calling the PUT /photo/update-status API


Worker behavior
- One or more worker processes subscribe to `photos-to-process` partitions. Each worker:
   1) Receives a message (photo to process).
   2) Calls the Python ML service (HTTP) with { path, callbackUrl } where callbackUrl is the Java callback endpoint `POST /callback/photo-processed`.
   3) The Python service performs detection/embedding and may ask Java for a dump of persons/representative embeddings (see below) to do local NN matching.
   4) The Python service invokes the callback when analysis completes. The Java callback persists the results.
   
Notes on authoritative persistence
The canonical write path is the Java callback endpoint. Workers should not write person/photo DB records themselves to avoid race conditions; instead, persistence occurs when the Java callback receives the analysis payload from the Python service. This simplifies concurrency control and makes the callback atomic.
## ML approach and local model choices (revised for callback + DB-dump flow)
We will rely on a Python microservice (FastAPI) to perform face detection and embedding generation. The Python service accepts a photo path and a callback URL and will invoke the callback once analysis completes. To enable accurate nearest-neighbor matching locally, the Python service can request a dump of the current `persons` table (representative embeddings and identifiers) from the Java service before or during processing.
Recommended two-step approach:
1) Face detection + cropping: Python runs an accurate detector (e.g., MTCNN, RetinaFace) to extract aligned face thumbnails.
2) Embeddings: Python computes embeddings using a model such as FaceNet or ArcFace (via `facenet-pytorch` or `insightface`). Embeddings should be L2-normalized and serialized (base64 or binary) when transmitted in callbacks.
3) Nearest-neighbor matching: The Python service may optionally perform NN matching locally if given a dump of representative person embeddings from Java (recommended for latency). Matching uses cosine similarity; threshold configurable. However, the Python service should not be authoritative for DB writes — it will return its suggested matches in the callback and the Java callback handler will perform canonical persistence (create person rows, save embeddings, update photo.person_ids) to avoid race conditions.
Database dump endpoint (Java -> Python interaction)
To support efficient local NN search, Java will expose an endpoint for the Python service to request a dump of { person_id, representative_embedding, optional metadata }. The dump endpoint should be protected and performant; consider pagination or compressed binary payloads for large datasets.
Local-run model option (recommended)
- Option A (chosen): Python microservice (FastAPI) with `facenet-pytorch` or `insightface` for detection + embeddings. The Python service receives { path, callbackUrl } and invokes the callback with analysis. It can call Java's dump endpoint to retrieve representative embeddings for matching.
This option keeps model development and ML dependencies in Python (best model availability) and the Java service focused on orchestration and persistence.
## Implementation plan (step-by-step) — revised for Kafka and callback flow
1) Create DB migration scripts for the schema above (MySQL/Docker in repo already). Add SQL files to `photo-sorter/src/main/resources/db/migration` (Flyway or Liquibase is recommended).
2) Implement the Python ML microservice (FastAPI) in `photo-sorter/ml-service/`:
   - Endpoint to accept processing requests: POST /process-photo { path, callbackUrl }
   - Endpoint to request Java person-dump: GET /person-dump (Java exposes the dump endpoint; Python calls it)
   - Behavior: detect faces, compute embeddings, optionally match locally using the dumped person embeddings, and POST results to callbackUrl when finished.
   - Health/version endpoints and model setup scripts. Document model download on first run.
3) Implement the Java Spring Boot service in `photo-sorter` with modules:
   - IngestController: POST /ingest-folder (scans folder, inserts `photos` metadata rows with person_ids empty, publishes per-photo messages to Kafka `photos-to-process`).
   - CallbackController: POST /callback/photo-processed (receives results from Python and performs canonical persistence: create persons, save embeddings, update `photos.person_ids` in a transaction).
   - PersonDumpController: GET /person-dump (returns compressed representative embeddings and person ids for Python to use). Protect this endpoint (local network only or with a shared secret).
   - Kafka configuration: producers for `photos-to-process`, optional `photos-processed` producer; consumers for processed-topic if you want further workflows.
   - Services/Repositories: PhotosRepository, PersonsRepository, EmbeddingsRepository, IngestService, WorkerService (lightweight process that pulls messages and orchestrates Python call), CallbackService.
4) Worker service (could be part of the Java app or a separate JVM process): subscribe to `photos-to-process`, for each message call Python `/process-photo` with { path, callbackUrl }. Do not write to DB directly — let the callback endpoint persist.
5) Post-processing / processed-topic: workers may publish an informational message to `photos-processed` after sending the request to Python (or after receiving a callback acknowledgement) for monitoring and downstream consumers. The primary database persistence remains in the callback handler.
6) Add admin endpoints and CLI utilities for merging person rows, re-indexing embeddings, and re-processing photos.
## Goal and high-level understanding

We want a local photo-classification system that, given a path to a folder of photos, will:

- Insert each photo (metadata and path) into a database.
- Identify which people appear in each photo and store a stable person identifier (person_id) for each person detected in the photo.
- Ensure that the same real person appearing in multiple photos gets the same person_id across the whole dataset (consistent identity resolution).
- Provide a Java service that performs the AI/ML classification and integrates with the database. The ML model(s) must run locally (no external cloud calls).

This document specifies the requirements, data model, ML choices, system architecture, API contract, edge cases, acceptance criteria, and an implementation plan that a developer (or an AI agent) can follow.

## Contract (short)

- Input: local filesystem folder path containing image files (jpg, png, heic, etc.).
- Output: database populated with photos and persons; each photo record includes a column with a list of person_id values present in the photo.
- Error modes: unreadable files, image without faces, multiple faces, face detection/embedding failures.
- Success: for a dataset with clear frontal faces, the same person appearing in multiple photos is assigned the same person_id at least 95% of the time (configurable threshold).

## Data model / Database schema

Use a relational database (MySQL is present in this repo's docker folder — acceptable). Proposed minimal schema:

1) photos
- id (bigint, PK, auto-increment)
- path (varchar) — absolute or relative filesystem path
- filename (varchar)
- inserted_at (datetime)
- width (int), height (int) — optional
- person_ids (json) — list of person_id integers present in the photo (updated after classification)

2) persons
- id (bigint, PK, auto-increment) — person_id
- created_at (datetime)
- display_name (varchar, optional) — user-supplied name or null
- primary_embedding_id (bigint, FK -> embeddings.id) — optional

3) embeddings
- id (bigint, PK, auto-increment)
- person_id (bigint, FK -> persons.id) — the person this embedding was associated with
- embedding (blob or varbinary) — serialized float32[] or base64
- source_photo_id (bigint, FK -> photos.id)
- created_at (datetime)

4) photo_persons (optional normalized join table)
- photo_id, person_id

Notes:
- Storing embeddings lets us refine and re-compute representative embeddings per-person (e.g., mean or medoid).
- The `person_ids` JSON column in `photos` is a convenient denormalized cache for quick lookups.

## ML approach and local model choices

We need deterministic identity matching across photos. Two-step approach:

1) Face detection + cropping: detect faces and crop aligned face thumbnails.
   - Recommended libraries: OpenCV (JavaCV) for basic detection or use a stronger detector (MTCNN, RetinaFace). For local accuracy, use a Python-based detector (MTCNN/RetinaFace) or a Java-binding capable detector. Using a Python microservice is acceptable if the Java service calls it locally (HTTP/REST or gRPC).

2) Embeddings: compute a fixed-length face embedding for each detected face using a face-recognition model.
   - Recommended models: FaceNet, ArcFace, or models available through Deep Java Library (DJL) or by running a Python service using `facenet-pytorch` or `insightface`.
   - Embedding size often 128 or 512 floats.

3) Matching / ID assignment: use nearest-neighbor search (cosine similarity) against stored person embeddings.
   - If similarity with any known person passes a configurable threshold (e.g., cosine similarity >= 0.5–0.75 depending on model), assign that person_id and optionally store the new embedding linked to the person for incremental learning.
   - If no match passes threshold, create a new `persons` row and store the embedding.

Local-run model options and tradeoffs:
- Option A (recommended): Python microservice locally (Flask/FastAPI) that uses `facenet-pytorch` or `insightface` to do detection + embedding. Java service calls local HTTP endpoints. Advantages: fastest to implement, best model availability, reproducible.
- Option B: Pure Java using DJL (Deep Java Library) to load a pretrained FaceNet/ArcFace model and compute embeddings entirely in Java. Advantage: single JVM; slightly more complex to set up but cleaner deployment for Java-only stacks.

Either option is acceptable; this prompt will assume Option A (Python microservice) unless you prefer DJL — both are documented in the Implementation Steps.

## API / Service contract (Java service)

The Java service coordinates ingestion and classification. Minimal endpoints/operations:

- POST /ingest-folder
  - Request body: { path: "/abs/path/to/photos", recursive: true|false }
  - Response: { jobId }

- GET /ingest-status/{jobId}
  - Returns progress and final counts (photos scanned, faces detected, persons created/matched).

- POST /classify-photo
  - Request body: { photo_id } or { path }
  - Response: { photo_id, person_ids: [..], faces: [{bbox, person_id, similarity}] }

- GET /person/{person_id}
  - Returns person metadata and example photo paths and embeddings (embedding might be excluded for privacy).

Internally the Java service will:
- Insert the photo record into `photos` (with person_ids empty/null).
- Call the local ML model (HTTP or local JNI) to detect faces and get embeddings.
- For each embedding, run matching against existing embeddings and either assign existing person_id or create a new person.
- Insert embedding into `embeddings` table and update `photos.person_ids` (and `photo_persons` if normalized).

## Identity consistency strategy

- Use a deterministic matching algorithm (cosine similarity with threshold) against a persistent store of person representative embeddings.
- When a face matches an existing person, append the new embedding to `embeddings` and optionally recompute the person’s representative embedding (mean or medoid). Recompute offline or incrementally to avoid hiccups.
- Provide a small CLI / admin UI to manually merge person records when the automated system created duplicates.

## Edge cases and mitigations

- Low-quality images/no faces: leave `person_ids` empty and mark the photo as `no_faces=true`.
- Multiple faces of the same person (mirrors/reflections): bounding-box heuristics and duplicate embeddings detection (if high similarity within same photo) — store all occurrences but de-duplicate when updating `photos.person_ids`.
- Very similar twins or lookalikes: handle via human-in-the-loop manual merge/verify.
- Mis-detections due to pose/occlusion: allow configurable similarity threshold and re-processing pipeline after adding more embeddings to a person.

## Acceptance criteria / success metrics

- System inserts all photos into DB and sets `person_ids` for each photo within the job run.
- For a curated test set with clear faces, identity assignment consistency across photos >= 95% (tunable by threshold).
- Local-only operation: no network dependency other than optional model downloads when setting up (documented). The runtime classification must not call external APIs.

## Implementation plan (step-by-step)

1) Create DB migration scripts for the schema above (MySQL/Docker in repo already). Add SQL files to `photo-sorter/src/main/resources/db/migration`.
2) Implement a small local Python microservice (FastAPI) with endpoints:
   - POST /embeddings/from-image — accepts image bytes or local path, returns list of detections: [{bbox, embedding: base64}]
   - Health and version endpoints
   Keep this service in `photo-sorter/ml-service/` or an adjacent directory.
3) Add Java client code to call the Python service (or use DJL directly if choosing Java-only). The Java service will be a Spring Boot service under `photo-sorter`:
   - Services: IngestService, PhotoService, PersonService, EmbeddingService
   - Repositories: PhotosRepository, PersonsRepository, EmbeddingsRepository
4) Ingest workflow:
   - Walk folder, for each file insert `photos` row.
   - Submit the photo to ML service, get embeddings.
   - For each embedding, run NN search (use Faiss standalone or naive SQL-backed search for small datasets; for larger datasets, integrate an index like Annoy or HNSW via a local library).
   - Assign or create person and persist embedding.
   - Update `photos.person_ids`.
5) Add a small admin CLI (or endpoints) for merging persons and re-indexing embeddings.

## Implementation details & helpful tips

- Embedding serialization: store as base64 or binary float32 array. Keep consistent dtype and normalization (L2 normalize embeddings before storing).
- Similarity measure: cosine similarity of normalized embeddings is recommended.
- Threshold tuning: start with 0.6–0.75 depending on model. Provide configuration in `application.yml`.
- Concurrency: ingest jobs can be parallelized per-photo; however, matching and person creation must use transactions/locks to avoid duplicate person creation on concurrent matches.
- Backfill: provide a re-processing job to recompute embeddings and person assignments when model or threshold changes.

## Developer prompt (what to implement from this specification)

Create an MVP consisting of:

1) SQL migration that creates `photos`, `persons`, `embeddings`, and `photo_persons` tables.
2) A local ML microservice (Python + FastAPI) using a known open-source face embedding model (facenet-pytorch or insightface) that exposes a small embedding endpoint.
3) A Spring Boot Java service in `photo-sorter` that implements:
   - Folder ingestion endpoint and worker that inserts photos and triggers classification.
   - Classification flow: detect faces -> get embeddings -> nearest neighbor match -> persist mapping.
4) Unit tests for the Java service (happy path + no-face edge case) and a small integration test that runs the ML service locally (can use a small set of sample images in `src/test/resources`).
5) Documentation: update `README.md` in `photo-sorter` with setup steps for starting the ML service and the Java service.

## Files to add / update (suggested)

- `photo-sorter/prompt.md` (this file)
- `photo-sorter/ml-service/` (python service)
- `photo-sorter/src/main/java/...` (spring boot services and repositories)
- `photo-sorter/src/main/resources/db/migration/V1__initial.sql` (schema)
- `photo-sorter/README.md` (setup and run steps)

## Notes on local-only requirement and model downloads

- Models may be large (>50MB). The implementation can download pretrained weights on first run (document this). This still satisfies "local model" runtime requirement as no inference requests leave the machine.

## Example flow (user perspective)

1) Start the ML microservice: `cd photo-sorter/ml-service && ./start_ml_service.sh` (or Docker compose)
2) Start the Java service: `mvn spring-boot:run` (from `photo-sorter`)
3) POST /ingest-folder { "path": "/Users/me/Pictures/Family", "recursive": true }
4) Monitor job status and then query the `photos` table to see `person_ids` for each photo.

## Acceptance checklist

- [ ] Schema migrations applied and DB reachable
- [ ] ML microservice runs locally and returns embeddings
- [ ] Java ingestion endpoint inserts photos and updates person_ids
- [ ] IDs remain consistent across multiple ingestions of overlapping photo sets
- [ ] Tests and README present

## Next steps if you want me to implement

Tell me whether you prefer:

- Option A (Python microservice with best-available models) — fastest to implement and highest accuracy
- Option B (Pure Java using DJL) — single JVM, no Python dependency

Also confirm DB choice (MySQL) and whether you want embeddings stored as binary or base64 JSON. After that I can generate the SQL migration and the `prompt.md` is ready as the spec to implement.

```
