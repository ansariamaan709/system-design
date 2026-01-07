# ELK Stack Setup Guide

This guide explains how to set up and use the ELK (Elasticsearch, Logstash, Kibana) stack for centralized logging in this project.

## 1. Prerequisites

- Docker and Docker Compose installed
- Project cloned locally

## 2. ELK Stack Services

- **Elasticsearch**: Stores and indexes logs
- **Logstash**: Collects, parses, and forwards logs
- **Kibana**: Visualizes logs and analytics

## 3. Configuration Files

- `docker/elk/elasticsearch.yml`: Elasticsearch config
- `docker/elk/kibana.yml`: Kibana config
- `docker/elk/logstash.conf`: Logstash pipeline config

## 4. Starting the ELK Stack

From the project root, run:

```sh
# Windows PowerShell
./start-services.ps1
```

Or directly with Docker Compose:

```sh
docker-compose up -d elasticsearch logstash kibana
```

## 5. Accessing the Services

- **Elasticsearch**: [http://localhost:9200](http://localhost:9200)
- **Kibana**: [http://localhost:5601](http://localhost:5601)

## 6. Sending Logs to Logstash

- Logstash listens on port `5044` for logs (e.g., from Filebeat or app log shippers)
- Example Logstash pipeline is in `docker/elk/logstash.conf`

## 7. Customizing Log Collection

- Integrate Filebeat or your app to send logs to Logstash (`logstash:5044`)
- Update `logstash.conf` for custom parsing/output

## 8. Stopping the ELK Stack

```sh
docker-compose stop elasticsearch logstash kibana
```

## Quick local run (recommended)

Start the entire stack (all services defined in `docker-compose.yml`):

```powershell
# from project root
docker-compose up -d
./start-services.ps1
```

Start only ELK services:

```powershell
docker-compose up -d elasticsearch logstash kibana
```

Verify services are up:

```powershell
Invoke-RestMethod http://localhost:9200
Test-NetConnection -ComputerName localhost -Port 5044
Invoke-RestMethod http://localhost:5601/status
```

## 9. Troubleshooting

- Check container logs with `docker logs <container_name>`
- Ensure ports 9200, 5044, 5601 are free

---

For advanced configuration, see the official [Elastic documentation](https://www.elastic.co/guide/index.html).
