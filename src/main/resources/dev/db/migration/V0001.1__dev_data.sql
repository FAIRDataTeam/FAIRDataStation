INSERT INTO public.job (uuid, secret, remote_id, status, started_at, finished_at,
                        callback_event, callback_artifact, train_uri,
                        created_at, updated_at, version)
VALUES ('633879bd-df36-4c93-b455-6b9a56321771', 'verySecret', 'someRemoteId', 'FINISHED', '2022-04-09 21:10:43.000000',
        '2022-04-09 22:03:47.000000', 'https://example.com/events', 'https://example.com/artifacts',
        'https://example.com/trains/x', '2022-04-09 21:09:07.000000', '2022-04-09 21:09:07.000000', 0);
INSERT INTO public.job (uuid, secret, remote_id, status, started_at, finished_at,
                        callback_event, callback_artifact, train_uri,
                        created_at, updated_at, version)
VALUES ('0f8fa3ca-02b6-4cd3-b346-93b156166554', 'anotherSecret', 'anotherRemoteId', 'FINISHED',
        '2022-04-09 21:15:40.000000', '2022-04-09 21:56:44.000000', 'https://example.com/events',
        'https://example.com/artifacts', 'https://example.com/trains/y', '2022-04-09 21:09:56.000000',
        '2022-04-09 21:09:56.000000', 7);

INSERT INTO public.job_event (uuid, result_status, occurred_at, message, job_id, created_at, updated_at)
VALUES ('0fc29c4a-f099-46a7-8d66-90cf76eef59b', 'QUEUED', '2022-04-09 21:18:36.000000',
        'Train queued in the data station', '633879bd-df36-4c93-b455-6b9a56321771', '2022-04-09 21:19:01.000000',
        '2022-04-09 21:19:01.000000');
INSERT INTO public.job_event (uuid, result_status, occurred_at, message, job_id, created_at, updated_at)
VALUES ('e035028e-57ab-4baa-adff-2aa5e1fb04c2', 'RUNNING', '2022-04-09 21:20:00.000000',
        'Started processing the train', '633879bd-df36-4c93-b455-6b9a56321771', '2022-04-09 21:20:27.000000',
        '2022-04-09 21:20:27.000000');
INSERT INTO public.job_event (uuid, result_status, occurred_at, message, job_id, created_at, updated_at)
VALUES ('4fe592ca-b36a-46e5-8f3b-c7b169eb0269', null, '2022-04-09 21:20:52.000000',
        'Checking data access permissions', '633879bd-df36-4c93-b455-6b9a56321771', '2022-04-09 21:21:17.000000',
        '2022-04-09 21:21:17.000000');
INSERT INTO public.job_event (uuid, result_status, occurred_at, message, job_id, created_at, updated_at)
VALUES ('0c6f3cca-f1a7-46db-874b-955cdd3abccc', null, '2022-04-09 21:21:53.000000',
        'Access granted, querying data', '633879bd-df36-4c93-b455-6b9a56321771', '2022-04-09 21:22:17.000000',
        '2022-04-09 21:22:17.000000');
INSERT INTO public.job_event (uuid, result_status, occurred_at, message, job_id, created_at, updated_at)
VALUES ('80b6bfbb-d128-4af2-ac90-849b3574735b', 'FINISHED', '2022-04-09 21:22:49.000000',
        'Query executed successfully', '633879bd-df36-4c93-b455-6b9a56321771', '2022-04-09 21:23:08.000000',
        '2022-04-09 21:23:08.000000');

INSERT INTO public.job_artifact (uuid, display_name, filename, bytesize, hash, content_type, storage, occurred_at, data, job_id, created_at, updated_at)
VALUES ('c667d5f3-6f34-490f-98c6-5890ddf5e456', 'Hello World Document', 'hello-world.txt', 14, 'd9014c4624844aa5bac314773d6b689ad467fa4e1d1a50a1b8a99d5a95f72ff5', 'text/plain', 'POSTGRES', '2022-04-09 21:22:00.000000', 'Hello, world!
', '633879bd-df36-4c93-b455-6b9a56321771', '2022-04-09 21:23:00.000000', '2022-04-04 21:23:00.000000');
