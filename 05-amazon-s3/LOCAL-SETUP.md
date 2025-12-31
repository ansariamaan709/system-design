# Local Setup Guide - Amazon S3 Clone

This guide will help you set up and run the Amazon S3 Clone locally.

## Prerequisites

- **Java 21** - Required for running the application
- **Docker Desktop** - Required for infrastructure services
- **Maven** (or use included wrapper) - For building the project

## Quick Start

### 1. Start Infrastructure Services

```powershell
.\start-services.ps1
```

This starts:

- **PostgreSQL 15** on port 5432 (database: s3db)
- **Redis 7** on port 6379 (metadata caching)
- **Redpanda** on port 9092 (Kafka-compatible event streaming)

### 2. Setup Database

```powershell
.\setup-database.ps1
```

This applies the database schema and creates Kafka topics.

### 3. Run the Application

```powershell
.\run-local.ps1
```

The application will be available at:

- **S3 API**: http://localhost:9000
- **Swagger UI**: http://localhost:9000/swagger-ui.html
- **Actuator**: http://localhost:9000/actuator

### 4. Stop Services

```powershell
.\stop-services.ps1
```

## Testing the API

### Using cURL

#### Create a Bucket

```bash
curl -X PUT http://localhost:9000/my-bucket
```

#### List Buckets

```bash
curl http://localhost:9000/
```

#### Upload an Object

```bash
curl -X PUT http://localhost:9000/my-bucket/hello.txt \
  -H "Content-Type: text/plain" \
  -d "Hello, S3!"
```

#### Get an Object

```bash
curl http://localhost:9000/my-bucket/hello.txt
```

#### List Objects in Bucket

```bash
curl "http://localhost:9000/my-bucket?list-type=2"
```

#### Delete an Object

```bash
curl -X DELETE http://localhost:9000/my-bucket/hello.txt
```

#### Delete a Bucket

```bash
curl -X DELETE http://localhost:9000/my-bucket
```

### Multipart Upload (for large files)

#### Initiate Multipart Upload

```bash
curl -X POST "http://localhost:9000/my-bucket/large-file.zip?uploads"
```

#### Upload Part

```bash
curl -X PUT "http://localhost:9000/my-bucket/large-file.zip?uploadId=<id>&partNumber=1" \
  -H "Content-Type: application/octet-stream" \
  --data-binary @part1.bin
```

#### Complete Multipart Upload

```bash
curl -X POST "http://localhost:9000/my-bucket/large-file.zip?uploadId=<id>" \
  -H "Content-Type: application/xml" \
  -d '<CompleteMultipartUpload>
    <Part><PartNumber>1</PartNumber><ETag>"etag1"</ETag></Part>
  </CompleteMultipartUpload>'
```

### Using AWS CLI

Configure AWS CLI to point to local S3:

```bash
aws configure --profile local
# Access Key: test
# Secret Key: test
# Region: us-east-1
```

```bash
# Create bucket
aws --endpoint-url http://localhost:9000 s3 mb s3://my-bucket --profile local

# Upload file
aws --endpoint-url http://localhost:9000 s3 cp myfile.txt s3://my-bucket/ --profile local

# List objects
aws --endpoint-url http://localhost:9000 s3 ls s3://my-bucket/ --profile local

# Download file
aws --endpoint-url http://localhost:9000 s3 cp s3://my-bucket/myfile.txt ./downloaded.txt --profile local
```

## Configuration

### Application Profiles

- **default**: Production configuration
- **local**: Local development with Docker infrastructure
- **test**: Test configuration with H2 database

### Environment Variables

| Variable        | Description              | Default        |
| --------------- | ------------------------ | -------------- |
| `DB_HOST`       | PostgreSQL host          | localhost      |
| `DB_PORT`       | PostgreSQL port          | 5432           |
| `DB_NAME`       | Database name            | s3db           |
| `REDIS_HOST`    | Redis host               | localhost      |
| `REDIS_PORT`    | Redis port               | 6379           |
| `KAFKA_SERVERS` | Kafka bootstrap servers  | localhost:9092 |
| `STORAGE_PATH`  | Object storage base path | ./data/storage |
| `TEMP_PATH`     | Temporary upload path    | ./data/temp    |

## Storage Architecture

```
data/
├── storage/                    # Content-addressable object storage
│   ├── 2a/                     # First 2 chars of SHA-256 hash
│   │   ├── 4b/                 # Next 2 chars
│   │   │   └── 2a4b...abc      # Full hash as filename
│   │   └── ...
│   └── ...
└── temp/                       # Temporary upload files
    ├── upload-12345.tmp
    └── ...
```

Objects are stored using content-addressable storage:

- SHA-256 hash of content determines storage location
- Automatic deduplication (same content = same file)
- Two-level directory structure for filesystem performance

## Troubleshooting

### Port Conflicts

If you see port conflicts, check which services are using the ports:

```powershell
netstat -ano | findstr :5432
netstat -ano | findstr :6379
netstat -ano | findstr :9092
```

### Container Issues

Reset all containers and volumes:

```powershell
docker-compose down -v
.\start-services.ps1
.\setup-database.ps1
```

### Check Service Health

```powershell
docker-compose ps
docker logs s3-postgres
docker logs s3-redis
docker logs s3-redpanda
```

### Application Logs

The application logs to console by default. Check for:

- Connection errors to PostgreSQL/Redis/Kafka
- Storage permission errors
- Port binding issues

## Development

### Running Tests

```powershell
.\mvnw.cmd test
```

### Building

```powershell
.\mvnw.cmd package -DskipTests
```

### Docker Build

```powershell
docker build -t s3-clone .
```

### Full Docker Deployment

```powershell
docker-compose up --build
```
