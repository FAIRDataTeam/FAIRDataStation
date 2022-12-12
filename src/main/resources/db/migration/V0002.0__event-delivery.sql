CREATE TABLE IF NOT EXISTS event_delivery
(
    uuid            UUID      NOT NULL
        CONSTRAINT event_delivery_pk PRIMARY KEY,
    delivered       BOOLEAN   NOT NULL DEFAULT FALSE,
    message         TEXT      NOT NULL,
    dispatch_at     TIMESTAMP NOT NULL,
    dispatched_at   TIMESTAMP,
    retry_number    INT       NOT NULL DEFAULT 0,
    priority        INT       NOT NULL DEFAULT 0,
    job_artifact_id UUID,
    job_event_id    UUID,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL
);

ALTER TABLE ONLY event_delivery
    ADD CONSTRAINT event_delivery_job_artifact_fk FOREIGN KEY (job_artifact_id) REFERENCES job_artifact (uuid);

ALTER TABLE ONLY event_delivery
    ADD CONSTRAINT event_delivery_job_event_fk FOREIGN KEY (job_event_id) REFERENCES job_event (uuid);
