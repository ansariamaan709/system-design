package com.stockexchange.service;

import com.stockexchange.dto.MarketDataMessage;
import com.stockexchange.dto.MarketDataResponse;
import com.stockexchange.engine.MatchingEngine;
import com.stockexchange.engine.OrderBook;
import com.stockexchange.entity.Instrument;
import com.stockexchange.repository.InstrumentRepository;
import com.stockexchange.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MarketDataService.
 */
@ExtendWith(MockitoExtension.class)
class MarketDataServiceTest {

        @Mock
        private TradeRepository tradeRepository;

        @Mock
        private InstrumentRepository instrumentRepository;

        @Mock
        private MatchingEngine matchingEngine;

        @Mock
        private SimpMessagingTemplate messagingTemplate;

        @Mock
        private RedisTemplate<String, Object> redisTemplate;

        @Mock
        private ValueOperations<String, Object> valueOperations;

        @InjectMocks
        private MarketDataService marketDataService;

        private Instrument testInstrument;
        private OrderBook testOrderBook;

        @BeforeEach
        void setUp() {
                testInstrument = Instrument.builder()
                                .symbol("AAPL")
                                .name("Apple Inc.")
                                .lastPrice(new BigDecimal("150.00"))
                                .openPrice(new BigDecimal("148.00"))
                                .highPrice(new BigDecimal("152.00"))
                                .lowPrice(new BigDecimal("147.50"))
                                .volumeToday(5000000L)
                                .prevClose(new BigDecimal("148.00"))
                                .build();

                testOrderBook = new OrderBook("AAPL");
        }

        @Nested
        @DisplayName("Get Quote Tests")
        class GetQuoteTests {

                @Test
                @DisplayName("Should return quote for valid symbol")
                void shouldReturnQuoteForValidSymbol() {
                        when(instrumentRepository.findBySymbol("AAPL"))
                                        .thenReturn(Optional.of(testInstrument));
                        when(matchingEngine.getOrderBook("AAPL"))
                                        .thenReturn(testOrderBook);

                        MarketDataResponse quote = marketDataService.getQuote("AAPL");

                        assertThat(quote).isNotNull();
                        assertThat(quote.getSymbol()).isEqualTo("AAPL");
                }

                @Test
                @DisplayName("Should return null for unknown symbol")
                void shouldReturnNullForUnknownSymbol() {
                        when(instrumentRepository.findBySymbol("INVALID"))
                                        .thenReturn(Optional.empty());
                        when(matchingEngine.getOrderBook("INVALID"))
                                        .thenReturn(null);

                        MarketDataResponse quote = marketDataService.getQuote("INVALID");

                        assertThat(quote).isNull();
                }
        }

        @Nested
        @DisplayName("Get Depth Tests")
        class GetDepthTests {

                @Test
                @DisplayName("Should return order book depth")
                void shouldReturnOrderBookDepth() {
                        when(matchingEngine.getOrderBook("AAPL")).thenReturn(testOrderBook);
                        when(instrumentRepository.findBySymbol("AAPL"))
                                        .thenReturn(Optional.of(testInstrument));

                        MarketDataResponse depth = marketDataService.getDepth("AAPL", 5);

                        assertThat(depth).isNotNull();
                        assertThat(depth.getSymbol()).isEqualTo("AAPL");
                }

                @Test
                @DisplayName("Should return null for non-existent book")
                void shouldReturnNullForNonExistentBook() {
                        when(matchingEngine.getOrderBook("INVALID")).thenReturn(null);
                        when(instrumentRepository.findBySymbol("INVALID"))
                                        .thenReturn(Optional.empty());

                        MarketDataResponse depth = marketDataService.getDepth("INVALID", 5);

                        assertThat(depth).isNull();
                }
        }

        @Nested
        @DisplayName("Get Last Price Tests")
        class GetLastPriceTests {

                @Test
                @DisplayName("Should return last price from instrument")
                void shouldReturnLastPriceFromInstrument() {
                        when(instrumentRepository.findBySymbol("AAPL"))
                                        .thenReturn(Optional.of(testInstrument));
                        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
                        when(valueOperations.get(anyString())).thenReturn(null);

                        BigDecimal lastPrice = marketDataService.getLastPrice("AAPL");

                        assertThat(lastPrice).isEqualByComparingTo(new BigDecimal("150.00"));
                }

                @Test
                @DisplayName("Should return null when no price available")
                void shouldReturnNullWhenNoPriceAvailable() {
                        when(instrumentRepository.findBySymbol("INVALID"))
                                        .thenReturn(Optional.empty());
                        when(matchingEngine.getOrderBook("INVALID"))
                                        .thenReturn(null);
                        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
                        when(valueOperations.get(anyString())).thenReturn(null);

                        BigDecimal lastPrice = marketDataService.getLastPrice("INVALID");

                        assertThat(lastPrice).isNull();
                }
        }

        @Nested
        @DisplayName("Get Ticker Tests")
        class GetTickerTests {

                @Test
                @DisplayName("Should return ticker data")
                void shouldReturnTickerData() {
                        when(instrumentRepository.findBySymbol("AAPL"))
                                        .thenReturn(Optional.of(testInstrument));
                        when(tradeRepository.calculateVWAP(eq("AAPL"), any()))
                                        .thenReturn(new BigDecimal("150.00"));

                        MarketDataMessage.Ticker ticker = marketDataService.getTicker("AAPL");

                        assertThat(ticker).isNotNull();
                        assertThat(ticker.getSymbol()).isEqualTo("AAPL");
                        assertThat(ticker.getLastPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
                }

                @Test
                @DisplayName("Should return null for unknown symbol")
                void shouldReturnNullForUnknownSymbol() {
                        when(instrumentRepository.findBySymbol("INVALID"))
                                        .thenReturn(Optional.empty());

                        MarketDataMessage.Ticker ticker = marketDataService.getTicker("INVALID");

                        assertThat(ticker).isNull();
                }
        }

        @Nested
        @DisplayName("Get Tickers Tests")
        class GetTickersTests {

                @Test
                @DisplayName("Should return tickers for multiple symbols")
                void shouldReturnTickersForMultipleSymbols() {
                        Instrument googleInstrument = Instrument.builder()
                                        .symbol("GOOGL")
                                        .lastPrice(new BigDecimal("2800.00"))
                                        .prevClose(new BigDecimal("2750.00"))
                                        .build();

                        when(instrumentRepository.findBySymbol("AAPL"))
                                        .thenReturn(Optional.of(testInstrument));
                        when(instrumentRepository.findBySymbol("GOOGL"))
                                        .thenReturn(Optional.of(googleInstrument));
                        when(tradeRepository.calculateVWAP(anyString(), any()))
                                        .thenReturn(null);

                        List<MarketDataMessage.Ticker> tickers = marketDataService
                                        .getTickers(Arrays.asList("AAPL", "GOOGL"));

                        assertThat(tickers).hasSize(2);
                }

                @Test
                @DisplayName("Should skip unknown symbols in batch")
                void shouldSkipUnknownSymbolsInBatch() {
                        when(instrumentRepository.findBySymbol("AAPL"))
                                        .thenReturn(Optional.of(testInstrument));
                        when(instrumentRepository.findBySymbol("INVALID"))
                                        .thenReturn(Optional.empty());
                        when(tradeRepository.calculateVWAP(eq("AAPL"), any()))
                                        .thenReturn(null);

                        List<MarketDataMessage.Ticker> tickers = marketDataService
                                        .getTickers(Arrays.asList("AAPL", "INVALID"));

                        assertThat(tickers).hasSize(1);
                        assertThat(tickers.get(0).getSymbol()).isEqualTo("AAPL");
                }
        }
}
