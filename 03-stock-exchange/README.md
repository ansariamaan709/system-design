# Stock Exchange System Design

## Low-Latency Trading Platform - Microsecond Order Matching

---

## 1. Requirements Analysis

### Functional Requirements

- Place market and limit orders (buy/sell)
- Real-time order matching (price-time priority)
- Order book management
- Trade execution and settlement
- Market data feed (Level 1 & Level 2)
- Account/portfolio management
- Risk management and circuit breakers

### Non-Functional Requirements

- **Latency**: < 10 microseconds for order matching
- **Throughput**: 1M+ orders/second
- **Availability**: 99.999% (5.26 min downtime/year)
- **Consistency**: Strong consistency for orders
- **Fairness**: Strict FIFO ordering per price level

### Constraints

- No data loss for orders
- Deterministic matching
- Audit trail for regulatory compliance
- Market hours: 9:30 AM - 4:00 PM EST

---

## 2. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                                    TRADING CLIENTS                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                    │
│  │  FIX Engine  │  │  REST API    │  │  WebSocket   │  │  Direct      │                    │
│  │  (HFT)       │  │  (Retail)    │  │  (Real-time) │  │  Market      │                    │
│  │              │  │              │  │              │  │  Access      │                    │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘                    │
└─────────┼─────────────────┼─────────────────┼─────────────────┼────────────────────────────┘
          │                 │                 │                 │
          └─────────────────┴────────┬────────┴─────────────────┘
                                     │
                    ┌────────────────▼────────────────┐
                    │        ORDER GATEWAY            │
                    │   - Protocol normalization      │
                    │   - Authentication              │
                    │   - Rate limiting               │
                    │   - Pre-trade risk checks       │
                    └────────────────┬────────────────┘
                                     │
                    ┌────────────────▼────────────────┐
                    │       SEQUENCER (Aeron)         │
                    │   - Total ordering              │
                    │   - Replication                 │
                    │   - Exactly-once delivery       │
                    └────────────────┬────────────────┘
                                     │
          ┌──────────────────────────┼──────────────────────────┐
          │                          │                          │
          ▼                          ▼                          ▼
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│ MATCHING ENGINE │      │ MATCHING ENGINE │      │ MATCHING ENGINE │
│    (AAPL)       │      │    (GOOGL)      │      │    (MSFT)       │
│                 │      │                 │      │                 │
│ ┌─────────────┐ │      │ ┌─────────────┐ │      │ ┌─────────────┐ │
│ │ Order Book  │ │      │ │ Order Book  │ │      │ │ Order Book  │ │
│ │  - Bids     │ │      │ │  - Bids     │ │      │ │  - Bids     │ │
│ │  - Asks     │ │      │ │  - Asks     │ │      │ │  - Asks     │ │
│ └─────────────┘ │      │ └─────────────┘ │      │ └─────────────┘ │
└────────┬────────┘      └────────┬────────┘      └────────┬────────┘
         │                        │                        │
         └────────────────────────┼────────────────────────┘
                                  │
          ┌───────────────────────┼───────────────────────┐
          │                       │                       │
          ▼                       ▼                       ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   TRADE         │     │   MARKET DATA   │     │   CLEARING &    │
│   REPORTING     │     │   PUBLISHER     │     │   SETTLEMENT    │
│   (Audit Log)   │     │   (Multicast)   │     │   (T+2)         │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

---

## 3. Core Matching Engine (C++)

### Order Book Implementation

```cpp
// order_book.hpp
#pragma once

#include <map>
#include <list>
#include <unordered_map>
#include <memory>
#include <cstdint>
#include <atomic>

namespace exchange {

enum class Side : uint8_t {
    BUY = 0,
    SELL = 1
};

enum class OrderType : uint8_t {
    LIMIT = 0,
    MARKET = 1,
    IOC = 2,      // Immediate or Cancel
    FOK = 3,      // Fill or Kill
    STOP = 4,
    STOP_LIMIT = 5
};

enum class OrderStatus : uint8_t {
    NEW = 0,
    PARTIALLY_FILLED = 1,
    FILLED = 2,
    CANCELLED = 3,
    REJECTED = 4
};

struct Order {
    uint64_t order_id;
    uint64_t client_id;
    uint32_t symbol_id;
    Side side;
    OrderType type;
    OrderStatus status;
    int64_t price;          // Price in cents (fixed-point)
    uint64_t quantity;
    uint64_t filled_quantity;
    uint64_t timestamp;     // Nanoseconds since epoch

    // Intrusive list pointers for O(1) removal
    Order* prev_at_price;
    Order* next_at_price;

    uint64_t remaining() const { return quantity - filled_quantity; }
    bool is_filled() const { return filled_quantity >= quantity; }
};

struct PriceLevel {
    int64_t price;
    uint64_t total_quantity;
    Order* head;
    Order* tail;
    uint32_t order_count;

    void add_order(Order* order);
    void remove_order(Order* order);
};

struct Trade {
    uint64_t trade_id;
    uint64_t buy_order_id;
    uint64_t sell_order_id;
    uint64_t buyer_id;
    uint64_t seller_id;
    uint32_t symbol_id;
    int64_t price;
    uint64_t quantity;
    uint64_t timestamp;
};

class OrderBook {
public:
    explicit OrderBook(uint32_t symbol_id);

    // Core operations - must be < 1 microsecond
    std::vector<Trade> add_order(Order* order);
    bool cancel_order(uint64_t order_id);
    bool modify_order(uint64_t order_id, int64_t new_price, uint64_t new_quantity);

    // Market data
    int64_t best_bid() const;
    int64_t best_ask() const;
    int64_t spread() const;
    uint64_t bid_depth(int levels) const;
    uint64_t ask_depth(int levels) const;

    // Level 2 data
    std::vector<std::pair<int64_t, uint64_t>> get_bids(int depth) const;
    std::vector<std::pair<int64_t, uint64_t>> get_asks(int depth) const;

private:
    uint32_t symbol_id_;

    // Red-black tree for price levels (sorted)
    // Bids: highest price first (reverse order)
    std::map<int64_t, PriceLevel, std::greater<int64_t>> bids_;
    // Asks: lowest price first
    std::map<int64_t, PriceLevel> asks_;

    // O(1) order lookup by ID
    std::unordered_map<uint64_t, Order*> orders_;

    // Trade ID generator
    std::atomic<uint64_t> next_trade_id_{1};

    std::vector<Trade> match_order(Order* order);
    void add_to_book(Order* order);
};

// order_book.cpp
OrderBook::OrderBook(uint32_t symbol_id) : symbol_id_(symbol_id) {}

std::vector<Trade> OrderBook::add_order(Order* order) {
    order->timestamp = get_nanoseconds();
    order->status = OrderStatus::NEW;

    std::vector<Trade> trades;

    if (order->type == OrderType::MARKET || order->type == OrderType::LIMIT) {
        trades = match_order(order);
    }

    // Add remaining quantity to book (for limit orders)
    if (!order->is_filled() && order->type == OrderType::LIMIT) {
        add_to_book(order);
    } else if (order->type == OrderType::IOC && !order->is_filled()) {
        // IOC: Cancel remaining
        order->status = OrderStatus::CANCELLED;
    } else if (order->type == OrderType::FOK) {
        // FOK: All or nothing (checked before matching)
        if (!order->is_filled()) {
            order->status = OrderStatus::REJECTED;
        }
    }

    return trades;
}

std::vector<Trade> OrderBook::match_order(Order* incoming) {
    std::vector<Trade> trades;
    trades.reserve(16);  // Pre-allocate for common case

    auto& opposite_side = (incoming->side == Side::BUY) ? asks_ : bids_;

    while (!opposite_side.empty() && incoming->remaining() > 0) {
        auto it = opposite_side.begin();
        PriceLevel& level = it->second;

        // Price check for limit orders
        if (incoming->type == OrderType::LIMIT) {
            if (incoming->side == Side::BUY && level.price > incoming->price) {
                break;  // Best ask too expensive
            }
            if (incoming->side == Side::SELL && level.price < incoming->price) {
                break;  // Best bid too low
            }
        }

        // Match against orders at this price level (FIFO)
        Order* resting = level.head;
        while (resting != nullptr && incoming->remaining() > 0) {
            Order* next = resting->next_at_price;

            uint64_t fill_qty = std::min(incoming->remaining(), resting->remaining());

            // Create trade
            Trade trade;
            trade.trade_id = next_trade_id_.fetch_add(1, std::memory_order_relaxed);
            trade.price = resting->price;  // Price at resting order
            trade.quantity = fill_qty;
            trade.timestamp = get_nanoseconds();
            trade.symbol_id = symbol_id_;

            if (incoming->side == Side::BUY) {
                trade.buy_order_id = incoming->order_id;
                trade.sell_order_id = resting->order_id;
                trade.buyer_id = incoming->client_id;
                trade.seller_id = resting->client_id;
            } else {
                trade.buy_order_id = resting->order_id;
                trade.sell_order_id = incoming->order_id;
                trade.buyer_id = resting->client_id;
                trade.seller_id = incoming->client_id;
            }

            trades.push_back(trade);

            // Update quantities
            incoming->filled_quantity += fill_qty;
            resting->filled_quantity += fill_qty;
            level.total_quantity -= fill_qty;

            // Update statuses
            incoming->status = incoming->is_filled() ?
                OrderStatus::FILLED : OrderStatus::PARTIALLY_FILLED;
            resting->status = resting->is_filled() ?
                OrderStatus::FILLED : OrderStatus::PARTIALLY_FILLED;

            // Remove filled resting order
            if (resting->is_filled()) {
                level.remove_order(resting);
                orders_.erase(resting->order_id);
            }

            resting = next;
        }

        // Remove empty price level
        if (level.order_count == 0) {
            opposite_side.erase(it);
        }
    }

    return trades;
}

void OrderBook::add_to_book(Order* order) {
    auto& side = (order->side == Side::BUY) ? bids_ : asks_;

    auto [it, inserted] = side.try_emplace(order->price, PriceLevel{order->price, 0, nullptr, nullptr, 0});
    it->second.add_order(order);

    orders_[order->order_id] = order;
}

bool OrderBook::cancel_order(uint64_t order_id) {
    auto it = orders_.find(order_id);
    if (it == orders_.end()) {
        return false;
    }

    Order* order = it->second;
    auto& side = (order->side == Side::BUY) ? bids_ : asks_;

    auto level_it = side.find(order->price);
    if (level_it != side.end()) {
        level_it->second.remove_order(order);

        if (level_it->second.order_count == 0) {
            side.erase(level_it);
        }
    }

    order->status = OrderStatus::CANCELLED;
    orders_.erase(it);

    return true;
}

void PriceLevel::add_order(Order* order) {
    order->prev_at_price = tail;
    order->next_at_price = nullptr;

    if (tail) {
        tail->next_at_price = order;
    } else {
        head = order;
    }
    tail = order;

    total_quantity += order->remaining();
    order_count++;
}

void PriceLevel::remove_order(Order* order) {
    if (order->prev_at_price) {
        order->prev_at_price->next_at_price = order->next_at_price;
    } else {
        head = order->next_at_price;
    }

    if (order->next_at_price) {
        order->next_at_price->prev_at_price = order->prev_at_price;
    } else {
        tail = order->prev_at_price;
    }

    total_quantity -= order->remaining();
    order_count--;
}

int64_t OrderBook::best_bid() const {
    return bids_.empty() ? 0 : bids_.begin()->first;
}

int64_t OrderBook::best_ask() const {
    return asks_.empty() ? INT64_MAX : asks_.begin()->first;
}

int64_t OrderBook::spread() const {
    return best_ask() - best_bid();
}

std::vector<std::pair<int64_t, uint64_t>> OrderBook::get_bids(int depth) const {
    std::vector<std::pair<int64_t, uint64_t>> result;
    result.reserve(depth);

    int count = 0;
    for (const auto& [price, level] : bids_) {
        if (count++ >= depth) break;
        result.emplace_back(price, level.total_quantity);
    }

    return result;
}

std::vector<std::pair<int64_t, uint64_t>> OrderBook::get_asks(int depth) const {
    std::vector<std::pair<int64_t, uint64_t>> result;
    result.reserve(depth);

    int count = 0;
    for (const auto& [price, level] : asks_) {
        if (count++ >= depth) break;
        result.emplace_back(price, level.total_quantity);
    }

    return result;
}

} // namespace exchange
```

### Lock-Free Order Pool

```cpp
// order_pool.hpp
#pragma once

#include <atomic>
#include <memory>

namespace exchange {

// Lock-free object pool for zero-allocation order creation
template<typename T, size_t PoolSize = 1000000>
class LockFreePool {
public:
    LockFreePool() {
        // Pre-allocate all objects
        pool_ = std::make_unique<T[]>(PoolSize);

        // Initialize free list
        for (size_t i = 0; i < PoolSize - 1; ++i) {
            reinterpret_cast<Node*>(&pool_[i])->next =
                reinterpret_cast<Node*>(&pool_[i + 1]);
        }
        reinterpret_cast<Node*>(&pool_[PoolSize - 1])->next = nullptr;

        head_.store(reinterpret_cast<Node*>(&pool_[0]), std::memory_order_relaxed);
    }

    T* allocate() {
        Node* old_head = head_.load(std::memory_order_acquire);

        while (old_head != nullptr) {
            Node* new_head = old_head->next;
            if (head_.compare_exchange_weak(old_head, new_head,
                    std::memory_order_release, std::memory_order_acquire)) {
                return reinterpret_cast<T*>(old_head);
            }
        }

        return nullptr;  // Pool exhausted
    }

    void deallocate(T* ptr) {
        if (ptr == nullptr) return;

        Node* node = reinterpret_cast<Node*>(ptr);
        Node* old_head = head_.load(std::memory_order_acquire);

        do {
            node->next = old_head;
        } while (!head_.compare_exchange_weak(old_head, node,
                std::memory_order_release, std::memory_order_acquire));
    }

private:
    struct Node {
        Node* next;
    };

    std::unique_ptr<T[]> pool_;
    std::atomic<Node*> head_;
};

// SPSC (Single Producer Single Consumer) Queue for order sequencing
template<typename T, size_t Capacity>
class SPSCQueue {
public:
    static_assert((Capacity & (Capacity - 1)) == 0, "Capacity must be power of 2");

    SPSCQueue() : head_(0), tail_(0) {
        buffer_ = std::make_unique<T[]>(Capacity);
    }

    bool push(const T& item) {
        size_t tail = tail_.load(std::memory_order_relaxed);
        size_t next_tail = (tail + 1) & (Capacity - 1);

        if (next_tail == head_.load(std::memory_order_acquire)) {
            return false;  // Queue full
        }

        buffer_[tail] = item;
        tail_.store(next_tail, std::memory_order_release);
        return true;
    }

    bool pop(T& item) {
        size_t head = head_.load(std::memory_order_relaxed);

        if (head == tail_.load(std::memory_order_acquire)) {
            return false;  // Queue empty
        }

        item = buffer_[head];
        head_.store((head + 1) & (Capacity - 1), std::memory_order_release);
        return true;
    }

    bool empty() const {
        return head_.load(std::memory_order_acquire) ==
               tail_.load(std::memory_order_acquire);
    }

private:
    alignas(64) std::atomic<size_t> head_;
    alignas(64) std::atomic<size_t> tail_;
    std::unique_ptr<T[]> buffer_;
};

} // namespace exchange
```

---

## 4. FIX Protocol Gateway

```cpp
// fix_gateway.hpp
#pragma once

#include <string>
#include <string_view>
#include <unordered_map>
#include <functional>

namespace exchange {

// FIX 4.4 message parsing (zero-copy)
class FIXMessage {
public:
    static constexpr char SOH = '\x01';  // Field separator

    explicit FIXMessage(std::string_view raw) : raw_(raw) {
        parse();
    }

    std::string_view get_field(int tag) const {
        auto it = fields_.find(tag);
        if (it != fields_.end()) {
            return it->second;
        }
        return {};
    }

    int get_int(int tag) const {
        auto sv = get_field(tag);
        if (sv.empty()) return 0;
        return std::stoi(std::string(sv));
    }

    int64_t get_price(int tag) const {
        // Parse fixed-point price (e.g., "123.45" -> 12345 cents)
        auto sv = get_field(tag);
        if (sv.empty()) return 0;

        int64_t integer_part = 0;
        int64_t decimal_part = 0;
        bool in_decimal = false;
        int decimal_places = 0;

        for (char c : sv) {
            if (c == '.') {
                in_decimal = true;
            } else if (c >= '0' && c <= '9') {
                if (in_decimal) {
                    decimal_part = decimal_part * 10 + (c - '0');
                    decimal_places++;
                } else {
                    integer_part = integer_part * 10 + (c - '0');
                }
            }
        }

        // Normalize to cents (2 decimal places)
        while (decimal_places < 2) {
            decimal_part *= 10;
            decimal_places++;
        }
        while (decimal_places > 2) {
            decimal_part /= 10;
            decimal_places--;
        }

        return integer_part * 100 + decimal_part;
    }

    // FIX Tag constants
    static constexpr int TAG_MSG_TYPE = 35;
    static constexpr int TAG_SENDER_COMP_ID = 49;
    static constexpr int TAG_TARGET_COMP_ID = 56;
    static constexpr int TAG_CL_ORD_ID = 11;
    static constexpr int TAG_ORIG_CL_ORD_ID = 41;
    static constexpr int TAG_ORDER_ID = 37;
    static constexpr int TAG_SYMBOL = 55;
    static constexpr int TAG_SIDE = 54;
    static constexpr int TAG_ORD_TYPE = 40;
    static constexpr int TAG_PRICE = 44;
    static constexpr int TAG_ORDER_QTY = 38;
    static constexpr int TAG_TIME_IN_FORCE = 59;
    static constexpr int TAG_EXEC_TYPE = 150;
    static constexpr int TAG_ORD_STATUS = 39;

private:
    std::string_view raw_;
    std::unordered_map<int, std::string_view> fields_;

    void parse() {
        size_t pos = 0;
        while (pos < raw_.size()) {
            // Find tag
            size_t eq_pos = raw_.find('=', pos);
            if (eq_pos == std::string_view::npos) break;

            int tag = 0;
            for (size_t i = pos; i < eq_pos; ++i) {
                tag = tag * 10 + (raw_[i] - '0');
            }

            // Find value
            size_t soh_pos = raw_.find(SOH, eq_pos + 1);
            if (soh_pos == std::string_view::npos) {
                soh_pos = raw_.size();
            }

            fields_[tag] = raw_.substr(eq_pos + 1, soh_pos - eq_pos - 1);
            pos = soh_pos + 1;
        }
    }
};

class FIXMessageBuilder {
public:
    FIXMessageBuilder& add_field(int tag, std::string_view value) {
        body_ += std::to_string(tag);
        body_ += '=';
        body_ += value;
        body_ += FIXMessage::SOH;
        return *this;
    }

    FIXMessageBuilder& add_field(int tag, int64_t value) {
        return add_field(tag, std::to_string(value));
    }

    std::string build(std::string_view msg_type) {
        std::string header;
        header += "8=FIX.4.4";
        header += FIXMessage::SOH;

        // Body length (calculated after)
        std::string body_with_type = "35=";
        body_with_type += msg_type;
        body_with_type += FIXMessage::SOH;
        body_with_type += body_;

        header += "9=";
        header += std::to_string(body_with_type.size());
        header += FIXMessage::SOH;

        std::string message = header + body_with_type;

        // Checksum
        int checksum = 0;
        for (char c : message) {
            checksum += static_cast<unsigned char>(c);
        }
        checksum %= 256;

        char checksum_str[4];
        snprintf(checksum_str, sizeof(checksum_str), "%03d", checksum);

        message += "10=";
        message += checksum_str;
        message += FIXMessage::SOH;

        return message;
    }

private:
    std::string body_;
};

// FIX Session management
class FIXSession {
public:
    FIXSession(int socket_fd, std::string comp_id)
        : socket_fd_(socket_fd), comp_id_(std::move(comp_id)) {}

    void handle_message(const FIXMessage& msg) {
        auto msg_type = msg.get_field(FIXMessage::TAG_MSG_TYPE);

        if (msg_type == "A") {  // Logon
            handle_logon(msg);
        } else if (msg_type == "D") {  // New Order Single
            handle_new_order(msg);
        } else if (msg_type == "F") {  // Order Cancel Request
            handle_cancel(msg);
        } else if (msg_type == "G") {  // Order Modification Request
            handle_modify(msg);
        } else if (msg_type == "0") {  // Heartbeat
            send_heartbeat();
        } else if (msg_type == "1") {  // Test Request
            send_heartbeat();
        } else if (msg_type == "5") {  // Logout
            handle_logout(msg);
        }
    }

    void send_execution_report(const Order& order, const Trade* trade = nullptr);

private:
    int socket_fd_;
    std::string comp_id_;
    int64_t msg_seq_num_ = 1;

    void handle_logon(const FIXMessage& msg);
    void handle_new_order(const FIXMessage& msg);
    void handle_cancel(const FIXMessage& msg);
    void handle_modify(const FIXMessage& msg);
    void handle_logout(const FIXMessage& msg);
    void send_heartbeat();

    void send_message(const std::string& msg);
};

void FIXSession::handle_new_order(const FIXMessage& msg) {
    Order order;
    order.client_id = client_id_;
    order.symbol_id = symbol_to_id(msg.get_field(FIXMessage::TAG_SYMBOL));

    auto side = msg.get_int(FIXMessage::TAG_SIDE);
    order.side = (side == 1) ? Side::BUY : Side::SELL;

    auto ord_type = msg.get_int(FIXMessage::TAG_ORD_TYPE);
    switch (ord_type) {
        case 1: order.type = OrderType::MARKET; break;
        case 2: order.type = OrderType::LIMIT; break;
        case 3: order.type = OrderType::STOP; break;
        case 4: order.type = OrderType::STOP_LIMIT; break;
        default: order.type = OrderType::LIMIT;
    }

    order.price = msg.get_price(FIXMessage::TAG_PRICE);
    order.quantity = msg.get_int(FIXMessage::TAG_ORDER_QTY);

    // Submit to sequencer
    sequencer_->submit(order);
}

void FIXSession::send_execution_report(const Order& order, const Trade* trade) {
    FIXMessageBuilder builder;

    builder.add_field(FIXMessage::TAG_ORDER_ID, order.order_id)
           .add_field(FIXMessage::TAG_CL_ORD_ID, order.client_order_id)
           .add_field(FIXMessage::TAG_SYMBOL, id_to_symbol(order.symbol_id))
           .add_field(FIXMessage::TAG_SIDE, order.side == Side::BUY ? 1 : 2)
           .add_field(FIXMessage::TAG_ORD_STATUS, static_cast<int>(order.status));

    if (trade) {
        builder.add_field(FIXMessage::TAG_EXEC_TYPE, "F")  // Trade
               .add_field(32, trade->quantity)  // LastQty
               .add_field(31, trade->price);    // LastPx
    }

    send_message(builder.build("8"));
}

} // namespace exchange
```

---

## 5. Sequencer (Total Ordering)

```cpp
// sequencer.hpp
#pragma once

#include <aeron/Aeron.h>
#include <aeron/Publication.h>
#include <aeron/Subscription.h>
#include <atomic>
#include <thread>

namespace exchange {

// Aeron-based sequencer for total ordering with replication
class Sequencer {
public:
    static constexpr int ORDERS_STREAM_ID = 1;
    static constexpr int EVENTS_STREAM_ID = 2;

    Sequencer(const std::string& aeron_dir, bool is_leader);
    ~Sequencer();

    // Submit order for sequencing (returns sequence number)
    uint64_t submit(const Order& order);

    // Process sequenced orders
    void poll_orders(std::function<void(uint64_t seq, const Order&)> handler);

    // Leadership
    bool is_leader() const { return is_leader_.load(); }
    void become_leader();
    void step_down();

private:
    std::shared_ptr<aeron::Aeron> aeron_;
    std::shared_ptr<aeron::Publication> order_publication_;
    std::shared_ptr<aeron::Subscription> order_subscription_;

    std::atomic<bool> is_leader_;
    std::atomic<uint64_t> sequence_number_;

    // Disk journal for durability
    int journal_fd_;

    void replicate_to_followers(uint64_t seq, const Order& order);
};

// Binary serialization for orders (no allocation)
struct alignas(64) SerializedOrder {
    uint64_t sequence;
    uint64_t order_id;
    uint64_t client_id;
    uint32_t symbol_id;
    uint8_t side;
    uint8_t type;
    uint8_t padding[2];
    int64_t price;
    uint64_t quantity;
    uint64_t timestamp;
    uint32_t checksum;

    static SerializedOrder from_order(uint64_t seq, const Order& order) {
        SerializedOrder s;
        s.sequence = seq;
        s.order_id = order.order_id;
        s.client_id = order.client_id;
        s.symbol_id = order.symbol_id;
        s.side = static_cast<uint8_t>(order.side);
        s.type = static_cast<uint8_t>(order.type);
        s.price = order.price;
        s.quantity = order.quantity;
        s.timestamp = order.timestamp;
        s.checksum = calculate_checksum(s);
        return s;
    }

    Order to_order() const {
        Order o;
        o.order_id = order_id;
        o.client_id = client_id;
        o.symbol_id = symbol_id;
        o.side = static_cast<Side>(side);
        o.type = static_cast<OrderType>(type);
        o.price = price;
        o.quantity = quantity;
        o.timestamp = timestamp;
        return o;
    }

    static uint32_t calculate_checksum(const SerializedOrder& s) {
        // CRC32 of order data
        const uint8_t* data = reinterpret_cast<const uint8_t*>(&s);
        return crc32(data, offsetof(SerializedOrder, checksum));
    }
};

static_assert(sizeof(SerializedOrder) == 64, "SerializedOrder must be cache-line aligned");

Sequencer::Sequencer(const std::string& aeron_dir, bool is_leader)
    : is_leader_(is_leader), sequence_number_(0) {

    aeron::Context context;
    context.aeronDir(aeron_dir);
    aeron_ = aeron::Aeron::connect(context);

    // Leader publishes to orders channel
    order_publication_ = aeron_->addPublication("aeron:ipc", ORDERS_STREAM_ID);

    // All nodes subscribe to orders for replication
    order_subscription_ = aeron_->addSubscription("aeron:ipc", ORDERS_STREAM_ID);

    // Open journal file (O_DIRECT for bypassing OS cache)
    journal_fd_ = open("/mnt/nvme/journal.bin", O_RDWR | O_CREAT | O_DIRECT, 0644);

    // Recover sequence number from journal
    recover_from_journal();
}

uint64_t Sequencer::submit(const Order& order) {
    if (!is_leader_.load(std::memory_order_acquire)) {
        throw std::runtime_error("Not the leader");
    }

    uint64_t seq = sequence_number_.fetch_add(1, std::memory_order_relaxed) + 1;
    SerializedOrder serialized = SerializedOrder::from_order(seq, order);

    // Write to disk journal first (fsync'd)
    pwrite(journal_fd_, &serialized, sizeof(serialized), seq * sizeof(serialized));
    fdatasync(journal_fd_);

    // Publish to Aeron (replicates to followers)
    while (order_publication_->offer(
            reinterpret_cast<const uint8_t*>(&serialized),
            sizeof(serialized)) < 0) {
        // Back pressure - retry
        std::this_thread::yield();
    }

    return seq;
}

void Sequencer::poll_orders(std::function<void(uint64_t seq, const Order&)> handler) {
    auto fragment_handler = [&handler](
            const uint8_t* buffer,
            util::index_t offset,
            util::index_t length,
            const aeron::Header& header) {

        if (length != sizeof(SerializedOrder)) {
            return;
        }

        const SerializedOrder* serialized =
            reinterpret_cast<const SerializedOrder*>(buffer + offset);

        // Verify checksum
        if (SerializedOrder::calculate_checksum(*serialized) != serialized->checksum) {
            // Corrupted message - request retransmission
            return;
        }

        handler(serialized->sequence, serialized->to_order());
    };

    order_subscription_->poll(fragment_handler, 100);
}

} // namespace exchange
```

---

## 6. Market Data Publisher

```cpp
// market_data.hpp
#pragma once

#include <cstdint>
#include <array>
#include <netinet/in.h>
#include <sys/socket.h>

namespace exchange {

// Level 1 market data (top of book)
struct alignas(32) L1MarketData {
    uint32_t symbol_id;
    int64_t best_bid;
    int64_t best_ask;
    uint64_t bid_size;
    uint64_t ask_size;
    int64_t last_price;
    uint64_t last_size;
    uint64_t timestamp;
    uint32_t sequence;
    uint32_t padding;
};

// Level 2 market data (depth)
struct L2PriceLevel {
    int64_t price;
    uint64_t quantity;
    uint32_t order_count;
    uint32_t padding;
};

struct alignas(64) L2MarketData {
    uint32_t symbol_id;
    uint32_t bid_levels;
    uint32_t ask_levels;
    uint32_t sequence;
    uint64_t timestamp;
    std::array<L2PriceLevel, 10> bids;
    std::array<L2PriceLevel, 10> asks;
};

class MarketDataPublisher {
public:
    MarketDataPublisher(const std::string& multicast_group, int port);

    void publish_l1(const L1MarketData& data);
    void publish_l2(const L2MarketData& data);
    void publish_trade(const Trade& trade);

private:
    int socket_fd_;
    struct sockaddr_in multicast_addr_;
    uint32_t sequence_ = 0;

    // Ring buffer for recovery requests
    std::array<std::vector<uint8_t>, 65536> message_buffer_;
};

MarketDataPublisher::MarketDataPublisher(const std::string& multicast_group, int port) {
    socket_fd_ = socket(AF_INET, SOCK_DGRAM, 0);

    // Set multicast TTL
    int ttl = 32;
    setsockopt(socket_fd_, IPPROTO_IP, IP_MULTICAST_TTL, &ttl, sizeof(ttl));

    // Enable loopback for local subscribers
    int loopback = 1;
    setsockopt(socket_fd_, IPPROTO_IP, IP_MULTICAST_LOOP, &loopback, sizeof(loopback));

    memset(&multicast_addr_, 0, sizeof(multicast_addr_));
    multicast_addr_.sin_family = AF_INET;
    multicast_addr_.sin_port = htons(port);
    inet_pton(AF_INET, multicast_group.c_str(), &multicast_addr_.sin_addr);
}

void MarketDataPublisher::publish_l1(const L1MarketData& data) {
    L1MarketData msg = data;
    msg.sequence = ++sequence_;
    msg.timestamp = get_nanoseconds();

    // Store for recovery
    message_buffer_[sequence_ % 65536].assign(
        reinterpret_cast<const uint8_t*>(&msg),
        reinterpret_cast<const uint8_t*>(&msg) + sizeof(msg)
    );

    sendto(socket_fd_, &msg, sizeof(msg), 0,
           reinterpret_cast<struct sockaddr*>(&multicast_addr_),
           sizeof(multicast_addr_));
}

void MarketDataPublisher::publish_l2(const L2MarketData& data) {
    L2MarketData msg = data;
    msg.sequence = ++sequence_;
    msg.timestamp = get_nanoseconds();

    message_buffer_[sequence_ % 65536].assign(
        reinterpret_cast<const uint8_t*>(&msg),
        reinterpret_cast<const uint8_t*>(&msg) + sizeof(msg)
    );

    sendto(socket_fd_, &msg, sizeof(msg), 0,
           reinterpret_cast<struct sockaddr*>(&multicast_addr_),
           sizeof(multicast_addr_));
}

// Optimized batch publisher using io_uring
class AsyncMarketDataPublisher {
public:
    AsyncMarketDataPublisher(const std::string& multicast_group, int port);
    ~AsyncMarketDataPublisher();

    void queue_message(const void* data, size_t size);
    void flush();

private:
    struct io_uring ring_;
    int socket_fd_;
    struct sockaddr_in multicast_addr_;

    struct Message {
        std::vector<uint8_t> data;
        struct msghdr msg;
        struct iovec iov;
    };

    std::vector<Message> pending_;
};

} // namespace exchange
```

---

## 7. Risk Management

```cpp
// risk_manager.hpp
#pragma once

#include <unordered_map>
#include <atomic>
#include <shared_mutex>

namespace exchange {

struct RiskLimits {
    int64_t max_order_value;          // Max single order value
    int64_t max_position_value;       // Max total position
    int64_t max_daily_loss;           // Stop loss threshold
    uint64_t max_order_rate;          // Orders per second
    uint64_t max_message_rate;        // Messages per second
    double max_price_deviation;       // % from reference price
};

struct ClientRiskState {
    std::atomic<int64_t> position_value;
    std::atomic<int64_t> realized_pnl;
    std::atomic<int64_t> unrealized_pnl;
    std::atomic<uint64_t> order_count_window;
    std::atomic<uint64_t> message_count_window;
    uint64_t last_window_reset;
};

class RiskManager {
public:
    enum class RiskResult {
        APPROVED,
        REJECTED_ORDER_SIZE,
        REJECTED_POSITION_LIMIT,
        REJECTED_LOSS_LIMIT,
        REJECTED_RATE_LIMIT,
        REJECTED_PRICE_DEVIATION,
        REJECTED_CIRCUIT_BREAKER
    };

    RiskManager();

    // Pre-trade risk check (must be fast)
    RiskResult check_order(const Order& order);

    // Update state after trade
    void on_trade(const Trade& trade);

    // Circuit breakers
    void trigger_circuit_breaker(uint32_t symbol_id, int level);
    bool is_halted(uint32_t symbol_id) const;

    // Admin
    void set_client_limits(uint64_t client_id, const RiskLimits& limits);
    void set_symbol_limits(uint32_t symbol_id, const RiskLimits& limits);

private:
    std::unordered_map<uint64_t, RiskLimits> client_limits_;
    std::unordered_map<uint64_t, ClientRiskState> client_state_;
    std::unordered_map<uint32_t, std::atomic<int64_t>> reference_prices_;
    std::unordered_map<uint32_t, std::atomic<bool>> halted_symbols_;

    mutable std::shared_mutex mutex_;

    int64_t get_reference_price(uint32_t symbol_id) const;
};

RiskManager::RiskResult RiskManager::check_order(const Order& order) {
    // Get client limits (fast path - read lock)
    RiskLimits limits;
    {
        std::shared_lock lock(mutex_);
        auto it = client_limits_.find(order.client_id);
        if (it == client_limits_.end()) {
            limits = default_limits_;
        } else {
            limits = it->second;
        }
    }

    // Check circuit breaker
    if (is_halted(order.symbol_id)) {
        return RiskResult::REJECTED_CIRCUIT_BREAKER;
    }

    // Check order size
    int64_t order_value = order.price * static_cast<int64_t>(order.quantity);
    if (order_value > limits.max_order_value) {
        return RiskResult::REJECTED_ORDER_SIZE;
    }

    // Check price deviation
    int64_t ref_price = get_reference_price(order.symbol_id);
    if (ref_price > 0) {
        double deviation = std::abs(static_cast<double>(order.price - ref_price) / ref_price);
        if (deviation > limits.max_price_deviation) {
            return RiskResult::REJECTED_PRICE_DEVIATION;
        }
    }

    // Check rate limit
    auto& state = client_state_[order.client_id];
    uint64_t now = get_milliseconds();

    if (now - state.last_window_reset > 1000) {
        state.order_count_window.store(0, std::memory_order_relaxed);
        state.last_window_reset = now;
    }

    uint64_t current_rate = state.order_count_window.fetch_add(1, std::memory_order_relaxed);
    if (current_rate >= limits.max_order_rate) {
        return RiskResult::REJECTED_RATE_LIMIT;
    }

    // Check position limit
    int64_t new_position = state.position_value.load(std::memory_order_relaxed);
    if (order.side == Side::BUY) {
        new_position += order_value;
    } else {
        new_position -= order_value;
    }

    if (std::abs(new_position) > limits.max_position_value) {
        return RiskResult::REJECTED_POSITION_LIMIT;
    }

    // Check daily loss
    int64_t total_pnl = state.realized_pnl.load(std::memory_order_relaxed) +
                        state.unrealized_pnl.load(std::memory_order_relaxed);
    if (total_pnl < -limits.max_daily_loss) {
        return RiskResult::REJECTED_LOSS_LIMIT;
    }

    return RiskResult::APPROVED;
}

void RiskManager::trigger_circuit_breaker(uint32_t symbol_id, int level) {
    halted_symbols_[symbol_id].store(true, std::memory_order_release);

    // Schedule re-open based on level
    int delay_seconds;
    switch (level) {
        case 1: delay_seconds = 300; break;   // 5 minutes
        case 2: delay_seconds = 900; break;   // 15 minutes
        case 3: delay_seconds = 0; break;     // Market close
        default: delay_seconds = 300;
    }

    if (delay_seconds > 0) {
        scheduler_->schedule_after(std::chrono::seconds(delay_seconds), [this, symbol_id]() {
            halted_symbols_[symbol_id].store(false, std::memory_order_release);
        });
    }
}

} // namespace exchange
```

---

## 8. Database Schema (Trade Settlement)

```sql
-- PostgreSQL schema for trade settlement (T+2)

CREATE TABLE accounts (
    account_id      BIGSERIAL PRIMARY KEY,
    client_id       BIGINT NOT NULL REFERENCES clients(id),
    account_type    VARCHAR(20) NOT NULL,  -- CASH, MARGIN
    currency        CHAR(3) NOT NULL DEFAULT 'USD',
    cash_balance    DECIMAL(20, 4) NOT NULL DEFAULT 0,
    buying_power    DECIMAL(20, 4) NOT NULL DEFAULT 0,
    margin_used     DECIMAL(20, 4) NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE positions (
    position_id     BIGSERIAL PRIMARY KEY,
    account_id      BIGINT NOT NULL REFERENCES accounts(account_id),
    symbol_id       INTEGER NOT NULL,
    quantity        BIGINT NOT NULL DEFAULT 0,
    avg_cost        DECIMAL(20, 4) NOT NULL DEFAULT 0,
    market_value    DECIMAL(20, 4) NOT NULL DEFAULT 0,
    unrealized_pnl  DECIMAL(20, 4) NOT NULL DEFAULT 0,
    realized_pnl    DECIMAL(20, 4) NOT NULL DEFAULT 0,

    CONSTRAINT unique_position UNIQUE (account_id, symbol_id)
);

CREATE INDEX idx_positions_account ON positions(account_id);

CREATE TABLE orders (
    order_id            BIGINT PRIMARY KEY,
    client_id           BIGINT NOT NULL,
    account_id          BIGINT NOT NULL REFERENCES accounts(account_id),
    client_order_id     VARCHAR(64) NOT NULL,
    symbol_id           INTEGER NOT NULL,
    side                CHAR(1) NOT NULL,  -- B, S
    order_type          VARCHAR(10) NOT NULL,
    status              VARCHAR(20) NOT NULL,
    price               DECIMAL(20, 4),
    quantity            BIGINT NOT NULL,
    filled_quantity     BIGINT NOT NULL DEFAULT 0,
    avg_fill_price      DECIMAL(20, 4),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT unique_client_order UNIQUE (client_id, client_order_id)
);

CREATE INDEX idx_orders_client ON orders(client_id, created_at DESC);
CREATE INDEX idx_orders_status ON orders(status) WHERE status IN ('NEW', 'PARTIALLY_FILLED');

CREATE TABLE trades (
    trade_id            BIGINT PRIMARY KEY,
    buy_order_id        BIGINT NOT NULL REFERENCES orders(order_id),
    sell_order_id       BIGINT NOT NULL REFERENCES orders(order_id),
    buyer_id            BIGINT NOT NULL,
    seller_id           BIGINT NOT NULL,
    symbol_id           INTEGER NOT NULL,
    price               DECIMAL(20, 4) NOT NULL,
    quantity            BIGINT NOT NULL,
    trade_value         DECIMAL(20, 4) NOT NULL,
    executed_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    settlement_date     DATE NOT NULL,  -- T+2
    settlement_status   VARCHAR(20) NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX idx_trades_settlement ON trades(settlement_date, settlement_status);
CREATE INDEX idx_trades_buyer ON trades(buyer_id, executed_at DESC);
CREATE INDEX idx_trades_seller ON trades(seller_id, executed_at DESC);

-- Audit log (append-only, for regulatory compliance)
CREATE TABLE audit_log (
    log_id          BIGSERIAL PRIMARY KEY,
    event_type      VARCHAR(50) NOT NULL,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       BIGINT NOT NULL,
    client_id       BIGINT,
    old_value       JSONB,
    new_value       JSONB,
    ip_address      INET,
    user_agent      TEXT,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
) PARTITION BY RANGE (created_at);

-- Create monthly partitions
CREATE TABLE audit_log_2024_01 PARTITION OF audit_log
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
```

---

## 9. Performance Tuning

### Kernel Bypass with DPDK

```cpp
// dpdk_network.cpp
#include <rte_eal.h>
#include <rte_ethdev.h>
#include <rte_mbuf.h>

class DPDKNetwork {
public:
    DPDKNetwork(int argc, char** argv) {
        // Initialize EAL
        int ret = rte_eal_init(argc, argv);
        if (ret < 0) {
            throw std::runtime_error("EAL init failed");
        }

        // Configure port
        uint16_t port_id = 0;
        struct rte_eth_conf port_conf = {};
        port_conf.rxmode.mq_mode = RTE_ETH_MQ_RX_RSS;
        port_conf.rx_adv_conf.rss_conf.rss_key = nullptr;
        port_conf.rx_adv_conf.rss_conf.rss_hf = RTE_ETH_RSS_IP | RTE_ETH_RSS_TCP;

        rte_eth_dev_configure(port_id, 1, 1, &port_conf);

        // Setup RX queue
        struct rte_mempool* mbuf_pool = rte_pktmbuf_pool_create(
            "MBUF_POOL", 8191, 250, 0,
            RTE_MBUF_DEFAULT_BUF_SIZE, rte_socket_id()
        );

        rte_eth_rx_queue_setup(port_id, 0, 512, rte_eth_dev_socket_id(port_id), nullptr, mbuf_pool);
        rte_eth_tx_queue_setup(port_id, 0, 512, rte_eth_dev_socket_id(port_id), nullptr);

        rte_eth_dev_start(port_id);
    }

    void poll_rx(std::function<void(struct rte_mbuf*)> handler) {
        struct rte_mbuf* bufs[32];
        uint16_t nb_rx = rte_eth_rx_burst(0, 0, bufs, 32);

        for (uint16_t i = 0; i < nb_rx; i++) {
            handler(bufs[i]);
            rte_pktmbuf_free(bufs[i]);
        }
    }
};
```

### CPU Pinning and NUMA

```cpp
// cpu_affinity.cpp
#include <pthread.h>
#include <sched.h>
#include <numa.h>

void pin_thread_to_core(int core_id) {
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    CPU_SET(core_id, &cpuset);

    pthread_t current = pthread_self();
    pthread_setaffinity_np(current, sizeof(cpu_set_t), &cpuset);
}

void configure_realtime_priority() {
    struct sched_param param;
    param.sched_priority = 99;  // Highest priority
    sched_setscheduler(0, SCHED_FIFO, &param);
}

// Memory allocation on specific NUMA node
void* numa_alloc_on_node(size_t size, int node) {
    return numa_alloc_onnode(size, node);
}
```

---

## 10. Metrics & Monitoring

```yaml
# Prometheus metrics
exchange_order_latency_nanoseconds:
  type: histogram
  buckets: [100, 500, 1000, 5000, 10000, 50000, 100000]
  labels: [symbol, order_type]

exchange_matching_latency_nanoseconds:
  type: histogram
  buckets: [100, 500, 1000, 2000, 5000, 10000]
  labels: [symbol]

exchange_orders_total:
  type: counter
  labels: [symbol, side, status]

exchange_trades_total:
  type: counter
  labels: [symbol]

exchange_trade_volume_total:
  type: counter
  labels: [symbol]

exchange_order_book_depth:
  type: gauge
  labels: [symbol, side]

exchange_spread_cents:
  type: gauge
  labels: [symbol]

exchange_message_rate:
  type: gauge
  labels: [protocol] # FIX, REST, WebSocket

exchange_circuit_breaker_triggered:
  type: counter
  labels: [symbol, level]
```

This completes the Stock Exchange system. Ready for the next one?
