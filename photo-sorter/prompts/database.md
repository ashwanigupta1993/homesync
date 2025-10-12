## Database schema and Kafka topics

This document lists the primary tables required for the photo-sorter POC, their columns, and which endpoints use them. It also enumerates Kafka topics and producers/consumers.

Tables

1) photos
- id BIGINT PK AUTO_INCREMENT
- path VARCHAR(1024) UNIQUE
- filename VARCHAR(512)
- filesize BIGINT
- width INT NULL
- height INT NULL
- inserted_at DATETIME
- modified_at DATETIME
- status ENUM('READY','PROCESSING','PROCESSED','FAILED') DEFAULT 'READY'
- person_ids JSON NULL

Used by: `POST /ingest-folder` (insert-ignore), `GET /photo/{photo_id}`, `POST /callback/photo-processed` (update status and person_ids)

2) persons
- id BIGINT PK AUTO_INCREMENT
- created_at DATETIME
- display_name VARCHAR(255) NULL
- primary_embedding_id BIGINT NULL

Used by: `POST /callback/photo-processed` (create/lookup persons), `GET /person/{person_id}`

3) embeddings
- id BIGINT PK AUTO_INCREMENT
- person_id BIGINT FK -> persons.id
- source_photo_id BIGINT FK -> photos.id
- embedding BLOB (or VARBINARY) / or base64 TEXT
- created_at DATETIME

Used by: `POST /callback/photo-processed` (store embeddings), `GET /person-dump` (representative embedding)

4) photo_persons (join table)
- photo_id BIGINT FK -> photos.id
- person_id BIGINT FK -> persons.id
- PRIMARY KEY (photo_id, person_id)

Used by: `POST /callback/photo-processed`, `GET /photo/{photo_id}`

5) ingest_jobs
- job_id UUID PK
- job_name VARCHAR
- path VARCHAR
- recursive BOOLEAN
- requested_by VARCHAR
- created_at DATETIME
- status ENUM('SCANNED','PUBLISHED','COMPLETED','FAILED')
- scanned_count INT
- inserted_count INT
- published_count INT
- finished_at DATETIME

Used by: `POST /ingest-folder`, `GET /ingest-status/{jobId}`

6) ingest_batches
- batch_id BIGINT PK
- job_id UUID FK -> ingest_jobs.job_id
- batch_index INT
- photo_count INT
- status ENUM('READY','PUBLISHED','FAILED')
- inserted_at DATETIME
- published_at DATETIME

Used by: internal ingest workflow and retry schedulers

7) photo_ingest_map
- map_id BIGINT PK
- photo_id BIGINT FK -> photos.id
- batch_id BIGINT FK -> ingest_batches.batch_id
- job_id UUID FK -> ingest_jobs.job_id
- inserted_at DATETIME
- publish_attempts INT DEFAULT 0
- last_publish_error TEXT NULL
- status ENUM('PENDING','PUBLISHED','FAILED')

Used by: to track which photos belong to which batch/job and to implement retries (retry scheduler reads FAILED rows here and republishes)

Kafka topics

- photos-to-process
  - Producer: Java ingest (publishes per-photo messages)
  - Consumer: worker(s) that call Python ML service

- photos-processed (optional monitoring topic)
  - Producer: worker or callback handler for lightweight notifications
  - Consumer: monitoring or downstream services

- photos-to-process-dlq
  - Producer: ingest when publishing fails after retries
  - Consumer: manual inspection or automated repair job

Security notes
- `GET /person-dump` must be protected (local network, mTLS or shared secret). The dump contains embeddings which are privacy-sensitive.
