# Apache Kafka Clone - Distributed Event Streaming Platform

A production-grade implementation of a distributed event streaming platform inspired by Apache Kafka.

## 1️⃣ Problem Statement & Core Requirements

### What is Kafka?

Apache Kafka is a distributed event streaming platform capable of handling trillions of events per day. It provides:

- **Publish-Subscribe messaging** with durable storage
- **Stream processing** for real-time data pipelines
- **Event sourcing** for microservices architectures

### Functional Requirements

1. **Producers** can publish messages to named topics
2. **Consumers** can subscribe to topics and consume messages
3. **Topics** are partitioned for parallelism
4. **Messages** are persisted durably with configurable retention
5. **Consumer Groups** for load balancing and fault tolerance
6. **Offset Management** for exactly-once/at-least-once semantics
7. **Replication** for fault tolerance
8. **Ordering** guaranteed within partitions

### Non-Functional Requirements

| Requirement      | Target            | Real-World Numbers            |
| ---------------- | ----------------- | ----------------------------- |
| **Throughput**   | Millions msgs/sec | LinkedIn: 7 trillion msgs/day |
| **Latency**      | < 10ms p99        | Typical: 2-5ms                |
| **Durability**   | No data loss      | Replication factor 3          |
| **Availability** | 99.99%            | Multi-datacenter              |
| **Retention**    | Days to years     | Configurable per topic        |
| **Message Size** | Up to 1MB         | Default 1MB, configurable     |

## 2️⃣ High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              KAFKA CLUSTER                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐                   │
│  │  Broker 1   │     │  Broker 2   │     │  Broker 3   │                   │
│  │  (Leader)   │────▶│  (Follower) │────▶│  (Follower) │                   │
│  │             │     │             │     │             │                   │
│  │ Partition 0 │     │ Partition 0 │     │ Partition 0 │                   │
│  │ Partition 1 │     │ Partition 1 │     │ Partition 1 │                   │
│  │ Partition 2 │     │ Partition 2 │     │ Partition 2 │                   │
│  └─────────────┘     └─────────────┘     └─────────────┘                   │
│         │                   │                   │                           │
│         └───────────────────┼───────────────────┘                           │
│                             │                                                │
│                    ┌────────┴────────┐                                      │
│                    │   Controller    │                                      │
│                    │   (ZooKeeper/   │                                      │
│                    │    KRaft)       │                                      │
│                    └─────────────────┘                                      │
└─────────────────────────────────────────────────────────────────────────────┘
         ▲                    │                    ▲
         │                    │                    │
    ┌────┴────┐          ┌────┴────┐         ┌────┴────┐
    │Producer │          │Producer │         │Producer │
    │   App   │          │   App   │         │   App   │
    └─────────┘          └─────────┘         └─────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           TOPIC: orders                                      │
├────────────────┬────────────────┬────────────────┬────────────────┬─────────┤
│   Partition 0  │   Partition 1  │   Partition 2  │   Partition 3  │   ...   │
│ [0][1][2][3]   │ [0][1][2][3]   │ [0][1][2]      │ [0][1][2][3][4]│         │
│    ▲           │    ▲           │    ▲           │    ▲           │         │
│    │           │    │           │    │           │    │           │         │
│ Consumer A     │ Consumer B     │ Consumer C     │ Consumer D     │         │
│ (Group: orders)│ (Group: orders)│ (Group: orders)│ (Group: orders)│         │
└────────────────┴────────────────┴────────────────┴────────────────┴─────────┘
```

### Core Components

1. **Broker**: Server that stores and serves messages
2. **Topic**: Named stream of records (logical grouping)
3. **Partition**: Ordered, immutable sequence of records
4. **Producer**: Publishes messages to topics
5. **Consumer**: Reads messages from topics
6. **Consumer Group**: Set of consumers sharing workload
7. **Controller**: Manages cluster metadata (leader election)
8. **Offset**: Position of message in partition

### Message Flow

```
Producer Flow:
1. Producer serializes message
2. Partitioner determines target partition (by key hash or round-robin)
3. Message sent to partition leader
4. Leader writes to local log
5. Followers replicate message
6. Ack returned when ISR quorum met

Consumer Flow:
1. Consumer joins consumer group
2. Group coordinator assigns partitions
3. Consumer fetches messages from offset
4. Processes messages
5. Commits offset (auto or manual)
6. Rebalance on consumer add/remove
```

## 3️⃣ Detailed Data Model

### Storage Architecture

```
Log Storage Structure:
────────────────────────────────────────
data/
├── orders-0/                          # Topic-Partition directory
│   ├── 00000000000000000000.log       # Segment file (messages)
│   ├── 00000000000000000000.index     # Offset index
│   ├── 00000000000000000000.timeindex # Timestamp index
│   ├── 00000000000012345678.log       # Next segment
│   ├── 00000000000012345678.index
│   └── 00000000000012345678.timeindex
├── orders-1/
│   └── ...
└── __consumer_offsets-0/              # Internal topic for offsets
    └── ...
```

### Message Format

```
Record Batch (On-Disk Format):
┌──────────────────────────────────────────────────────────────┐
│ Base Offset (8 bytes)                                        │
│ Batch Length (4 bytes)                                       │
│ Partition Leader Epoch (4 bytes)                             │
│ Magic (1 byte) - Version                                     │
│ CRC (4 bytes) - Checksum                                     │
│ Attributes (2 bytes) - Compression, Timestamp Type           │
│ Last Offset Delta (4 bytes)                                  │
│ First Timestamp (8 bytes)                                    │
│ Max Timestamp (8 bytes)                                      │
│ Producer ID (8 bytes) - For idempotence                      │
│ Producer Epoch (2 bytes)                                     │
│ Base Sequence (4 bytes)                                      │
│ Records Count (4 bytes)                                      │
├──────────────────────────────────────────────────────────────┤
│ Record 1                                                     │
│ ├── Length (varint)                                          │
│ ├── Attributes (1 byte)                                      │
│ ├── Timestamp Delta (varint)                                 │
│ ├── Offset Delta (varint)                                    │
│ ├── Key Length (varint)                                      │
│ ├── Key (bytes)                                              │
│ ├── Value Length (varint)                                    │
│ ├── Value (bytes)                                            │
│ └── Headers (array)                                          │
├──────────────────────────────────────────────────────────────┤
│ Record 2...N                                                 │
└──────────────────────────────────────────────────────────────┘
```

### Metadata Storage (PostgreSQL for our implementation)

```sql
-- Topics metadata
-- Partitions with leader/replica assignment
-- Consumer groups and offsets
-- Broker registry
```

## 4️⃣ API Design

### Producer API

- `send(topic, key, value)` - Async send with future
- `send(topic, partition, key, value)` - Explicit partition
- `flush()` - Wait for all pending sends
- `close()` - Graceful shutdown

### Consumer API

- `subscribe(topics)` - Subscribe to topics
- `poll(timeout)` - Fetch messages
- `commitSync()` - Synchronous offset commit
- `commitAsync()` - Asynchronous offset commit
- `seek(partition, offset)` - Reset position
- `pause(partitions)` / `resume(partitions)`

### Admin API

- `createTopic(name, partitions, replicas)`
- `deleteTopic(name)`
- `listTopics()`
- `describeTopics(names)`
- `alterConfigs()`

## 5️⃣ Core Algorithms

### Partitioning

- **Default**: `murmur2(key) % numPartitions`
- **Round-robin**: For null keys
- **Custom**: User-defined partitioner

### Replication Protocol

- **ISR (In-Sync Replicas)**: Replicas caught up to leader
- **High Watermark**: Offset replicated to all ISR
- **Leader Epoch**: Fencing for split-brain prevention

### Consumer Rebalancing

- **Range**: Contiguous partition ranges per consumer
- **Round-Robin**: Even distribution
- **Sticky**: Minimize partition movement
- **Cooperative**: Incremental rebalancing

## 6️⃣ Scaling Strategy

| Component  | Scaling Approach                   |
| ---------- | ---------------------------------- |
| Brokers    | Horizontal - Add more brokers      |
| Partitions | Horizontal - Increase parallelism  |
| Consumers  | Horizontal - Max = partition count |
| Producers  | Horizontal - Unlimited             |
| Storage    | Tiered storage to object stores    |

## 7️⃣ Consistency & Fault Tolerance

### Delivery Semantics

- **At-most-once**: Fire and forget (`acks=0`)
- **At-least-once**: Retry on failure (`acks=1` or `all`)
- **Exactly-once**: Idempotent producer + transactions

### Failure Scenarios

- Broker failure → Leader election
- Network partition → ISR shrinks
- Consumer failure → Rebalance
- Producer failure → Retry with idempotence

## 8️⃣ Project Structure

```
06-kafka/
├── src/main/java/com/kafka/
│   ├── KafkaApplication.java
│   ├── broker/
│   │   ├── Broker.java
│   │   ├── BrokerConfig.java
│   │   └── BrokerState.java
│   ├── storage/
│   │   ├── Log.java
│   │   ├── LogSegment.java
│   │   ├── LogIndex.java
│   │   └── LogCleaner.java
│   ├── partition/
│   │   ├── Partition.java
│   │   ├── PartitionManager.java
│   │   └── ReplicaManager.java
│   ├── producer/
│   │   ├── Producer.java
│   │   ├── ProducerConfig.java
│   │   ├── RecordAccumulator.java
│   │   └── Partitioner.java
│   ├── consumer/
│   │   ├── Consumer.java
│   │   ├── ConsumerConfig.java
│   │   ├── ConsumerGroup.java
│   │   ├── OffsetManager.java
│   │   └── Fetcher.java
│   ├── coordinator/
│   │   ├── GroupCoordinator.java
│   │   ├── TransactionCoordinator.java
│   │   └── RebalanceProtocol.java
│   ├── controller/
│   │   ├── Controller.java
│   │   ├── ControllerState.java
│   │   └── LeaderElection.java
│   ├── protocol/
│   │   ├── Request.java
│   │   ├── Response.java
│   │   ├── ProduceRequest.java
│   │   ├── FetchRequest.java
│   │   └── MetadataRequest.java
│   ├── network/
│   │   ├── NetworkServer.java
│   │   ├── RequestHandler.java
│   │   └── SocketServer.java
│   ├── entity/
│   ├── repository/
│   ├── service/
│   ├── controller/ (REST)
│   └── config/
├── src/main/resources/
│   ├── application.yml
│   └── schema.sql
└── docker-compose.yml
```

## Quick Start

```powershell
# Start infrastructure
.\start-services.ps1

# Setup database
.\setup-database.ps1

# Run application
.\run-local.ps1
```

## API Examples

```bash
# Create topic
curl -X POST http://localhost:9092/api/topics \
  -H "Content-Type: application/json" \
  -d '{"name": "orders", "partitions": 6, "replicationFactor": 3}'

# Produce message
curl -X POST http://localhost:9092/api/topics/orders/messages \
  -H "Content-Type: application/json" \
  -d '{"key": "order-123", "value": {"orderId": 123, "amount": 99.99}}'

# Consume messages
curl "http://localhost:9092/api/topics/orders/messages?group=order-processor&timeout=5000"
```
