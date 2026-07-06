# docker

## Purpose

Local development infrastructure for running the full Continuum cheminformatics stack. Brings up all platform dependencies so the worker can be run and tested locally without a remote environment.

## Ownership

Managed alongside the module by whoever works on it locally. Not deployed to production — dev environment only.

## Local Contracts

- Entry point: `docker-compose.yml` with environment from `.env`
- Start: `cd docker && docker compose up -d`
- Services and their local ports:
  - **Temporal** (workflow orchestration): `:7233` (gRPC), UI at `:38081`
  - **PostgreSQL** (Temporal backend): `:35432`
  - **Kafka** (event streaming, 3-node KRaft cluster): `:39092`, `:39093`, `:39094`
  - **Schema Registry**: `:38080`
  - **Kafka UI**: `:38082`
  - **Mosquitto** (MQTT broker): `:31883` (MQTT), `:31884` (websocket)
  - **MinIO** (S3-compatible object storage): `:39000` (API), `:39001` (console)
  - **continuum-api-server**: `:8080`
  - **continuum-message-bridge**: `:8081`
- Temporal search attributes `Continuum:ExecutionStatus` (Int) and `Continuum:WorkflowFileName` (Keyword) are registered on first startup by the `temporal-search-attributes-init` container
- Mosquitto config is at `mosquitto/config/mosquitto.conf`; Temporal dynamic config is at `temporal/dynamicconfig/development-sql.yaml`

## Work Guidance

- When adding a new service, add it to `docker-compose.yml` and document its port in this file's Local Contracts section
- Do not change port assignments without updating `application.yaml` in `worker/`
- `.env` controls image version pins; update image versions there, not inline in compose

## Verification

`docker compose ps` — all containers should show `running`. Temporal UI at `localhost:38081` should show the `default` namespace healthy.

## Child DOX Index

No child AGENTS.md.
