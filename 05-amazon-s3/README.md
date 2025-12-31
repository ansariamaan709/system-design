# Amazon S3 Clone - Object Storage System

A production-grade distributed object storage system inspired by Amazon S3.

## Features

### Core Storage

- **Bucket Management**: Create, delete, list buckets with naming validation
- **Object Operations**: PUT, GET, DELETE, HEAD, COPY objects
- **Multipart Upload**: Large file uploads with parallel parts
- **Versioning**: Object version control with delete markers
- **Metadata**: Custom metadata and system metadata support

### Access Control

- **Bucket Policies**: JSON-based access policies
- **ACLs**: Access Control Lists for fine-grained permissions
- **Presigned URLs**: Time-limited access to private objects
- **CORS**: Cross-Origin Resource Sharing configuration

### Advanced Features

- **Lifecycle Policies**: Automatic object expiration and transitions
- **Storage Classes**: Standard, Infrequent Access, Archive tiers
- **Object Lock**: WORM (Write Once Read Many) compliance
- **Replication**: Cross-region replication support
- **Event Notifications**: Webhook/Kafka notifications on object events

### Performance

- **Chunked Streaming**: Efficient large file handling
- **Content-Addressable Storage**: Deduplication via content hashing
- **Caching**: Redis-based metadata caching
- **Parallel Transfers**: Concurrent multipart uploads

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          API Gateway                                 │
│                    (Authentication, Rate Limiting)                   │
└─────────────────────────────┬───────────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────────┐
│                        S3 API Layer                                  │
│         (REST API: Bucket, Object, Multipart Operations)            │
└─────────────────────────────┬───────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│    Bucket     │    │    Object     │    │   Multipart   │
│   Service     │    │   Service     │    │   Service     │
└───────┬───────┘    └───────┬───────┘    └───────┬───────┘
        │                     │                     │
        └─────────────────────┼─────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────────┐
│                      Storage Engine                                  │
│           (Content-Addressable Storage, Chunking)                   │
└─────────────────────────────┬───────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│  PostgreSQL   │    │     Redis     │    │  File System  │
│  (Metadata)   │    │   (Cache)     │    │   (Objects)   │
└───────────────┘    └───────────────┘    └───────────────┘
```

## Data Model

### Bucket

- Bucket name (globally unique)
- Owner account ID
- Region
- Versioning status
- Lifecycle rules
- CORS configuration
- Bucket policy

### Object

- Bucket + Key (composite key)
- Version ID
- ETag (MD5/SHA256)
- Size
- Content-Type
- Storage class
- Custom metadata
- ACL

### Multipart Upload

- Upload ID
- Parts with ETag
- Part ordering
- Completion/Abort handling

## API Endpoints

### Bucket Operations

| Method | Path                   | Description           |
| ------ | ---------------------- | --------------------- |
| PUT    | `/{bucket}`            | Create bucket         |
| DELETE | `/{bucket}`            | Delete bucket         |
| GET    | `/`                    | List buckets          |
| HEAD   | `/{bucket}`            | Check bucket exists   |
| GET    | `/{bucket}?versioning` | Get versioning status |
| PUT    | `/{bucket}?versioning` | Set versioning        |

### Object Operations

| Method | Path                    | Description         |
| ------ | ----------------------- | ------------------- |
| PUT    | `/{bucket}/{key}`       | Upload object       |
| GET    | `/{bucket}/{key}`       | Download object     |
| DELETE | `/{bucket}/{key}`       | Delete object       |
| HEAD   | `/{bucket}/{key}`       | Get object metadata |
| GET    | `/{bucket}?list-type=2` | List objects        |
| PUT    | `/{bucket}/{key}?copy`  | Copy object         |

### Multipart Operations

| Method | Path                                  | Description        |
| ------ | ------------------------------------- | ------------------ |
| POST   | `/{bucket}/{key}?uploads`             | Initiate multipart |
| PUT    | `/{bucket}/{key}?partNumber&uploadId` | Upload part        |
| POST   | `/{bucket}/{key}?uploadId`            | Complete multipart |
| DELETE | `/{bucket}/{key}?uploadId`            | Abort multipart    |

## Tech Stack

- **Framework**: Spring Boot 3.2
- **Language**: Java 21
- **Database**: PostgreSQL 15 (metadata)
- **Cache**: Redis 7 (metadata cache)
- **Storage**: Local filesystem / MinIO compatible
- **Messaging**: Kafka (event notifications)
- **Build**: Maven

## Quick Start

```powershell
# Start infrastructure
.\start-services.ps1

# Setup database
.\setup-database.ps1

# Run application
.\run-local.ps1
```

## Configuration

```yaml
storage:
  root-path: ./data/objects
  temp-path: ./data/temp
  chunk-size: 5MB
  max-object-size: 5TB

s3:
  max-buckets-per-account: 100
  max-keys-per-request: 1000
  presigned-url-expiry: 3600
  multipart:
    min-part-size: 5MB
    max-part-size: 5GB
    max-parts: 10000
```

## Performance Targets

- Object PUT latency: < 100ms (small objects)
- Object GET latency: < 50ms (cache hit)
- List operations: < 200ms (1000 objects)
- Throughput: > 1000 ops/sec per node
