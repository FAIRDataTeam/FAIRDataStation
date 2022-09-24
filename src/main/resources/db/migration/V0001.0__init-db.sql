CREATE TYPE job_status AS ENUM (
    'PREPARED',
    'QUEUED',
    'RUNNING',
    'FINISHED',
    'ABORTING',
    'ABORTED',
    'ERRORED',
    'FAILED'
);

CREATE CAST (character varying AS job_status) WITH INOUT AS ASSIGNMENT;

CREATE TYPE artifact_storage AS ENUM (
    'POSTGRES',
    'S3',
    'LOCALFS'
);

CREATE CAST (character varying AS artifact_storage) WITH INOUT AS ASSIGNMENT;

CREATE TABLE IF NOT EXISTS job
(
    uuid           UUID         NOT NULL
    CONSTRAINT job_pk PRIMARY KEY,
    secret         VARCHAR(255) NOT NULL,
    remote_id      TEXT,
    status         job_status   NOT NULL,
    started_at     TIMESTAMP,
    finished_at    TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL,
    version        BIGINT       NOT NULL
);

CREATE TABLE IF NOT EXISTS job_event
(
    uuid          UUID           NOT NULL
    CONSTRAINT job_event_pk PRIMARY KEY,
    result_status job_status,
    occurred_at   TIMESTAMP,
    message       TEXT           NOT NULL,
    job_id        UUID           NOT NULL,
    created_at    TIMESTAMP      NOT NULL,
    updated_at    TIMESTAMP      NOT NULL
);

ALTER TABLE ONLY job_event
    ADD CONSTRAINT job_event_job_fk FOREIGN KEY (job_id) REFERENCES job (uuid);

CREATE TABLE IF NOT EXISTS job_artifact
(
    uuid           UUID               NOT NULL
    CONSTRAINT job_artifact_pk PRIMARY KEY,
    display_name   VARCHAR            NOT NULL,
    filename       VARCHAR            NOT NULL,
    bytesize       BIGINT             NOT NULL,
    hash           VARCHAR(64)        NOT NULL,
    content_type   VARCHAR            NOT NULL,
    storage        artifact_storage   NOT NULL,
    occurred_at    TIMESTAMP          NOT NULL,
    data           BYTEA,
    job_id         UUID               NOT NULL,
    created_at     TIMESTAMP          NOT NULL,
    updated_at     TIMESTAMP          NOT NULL
    );

ALTER TABLE ONLY job_artifact
    ADD CONSTRAINT job_artifact_job_fk FOREIGN KEY (job_id) REFERENCES job (uuid);
