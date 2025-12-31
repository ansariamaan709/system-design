package com.stockexchange.controller;

import com.stockexchange.dto.MarketDataMessage;
import com.stockexchange.dto.MarketDataResponse;
import com.stockexchange.dto.TradeResponse;
import com.stockexchange.service.MarketDataService;
import com.stockexchange.service.TradeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for MarketDataController.
 */
@WebMvcTest(MarketDataController.class)
class MarketDataControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private MarketDataService marketDataService;

        @MockBean
        private TradeService tradeService;

        @Nested
        @DisplayName("GET /api/v1/market-data/quote/{symbol}")
        class GetQuoteTests {

                @Test
                @DisplayName("Should return quote for valid symbol")
                void shouldReturnQuoteForValidSymbol() throws Exception {
                        MarketDataResponse response = MarketDataResponse.builder()
                                        .symbol("AAPL")
                                        .bestBid(new BigDecimal("149.95"))
                                        .bestAsk(new BigDecimal("150.05"))
                                        .bestBidSize(1000L)
                                        .bestAskSize(800L)
                                        .lastPrice(new BigDecimal("150.00"))
                                        .lastSize(100L)
                                        .volume(5000000L)
                                        .timestamp(Instant.now())
                                        .build();

                        when(marketDataService.getQuote("AAPL")).thenReturn(response);

                        mockMvc.perform(get("/api/v1/market-data/quote/AAPL"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.symbol").value("AAPL"))
                                        .andExpect(jsonPath("$.bestBid").value(149.95))
                                        .andExpect(jsonPath("$.bestAsk").value(150.05));
                }

                @Test
                @DisplayName("Should return 404 for unknown symbol")
                void shouldReturn404ForUnknownSymbol() throws Exception {
                        when(marketDataService.getQuote("INVALID")).thenReturn(null);

                        mockMvc.perform(get("/api/v1/market-data/quote/INVALID"))
                                        .andExpect(status().isNotFound());
                }
        }

        @Nested
        @DisplayName("GET /api/v1/market-data/depth/{symbol}")
        class GetDepthTests {

                @Test
                @DisplayName("Should return order book depth")
                void shouldReturnOrderBookDepth() throws Exception {
                        MarketDataResponse response = MarketDataResponse.builder()
                                        .symbol("AAPL")
                                        .bids(Arrays.asList(
                                                        MarketDataResponse.PriceLevel.builder()
                                                                        .price(new BigDecimal("149.95"))
                                                                        .quantity(1000L)
                                                                        .orderCount(5)
                                                                        .build()))
                                        .asks(Arrays.asList(
                                                        MarketDataResponse.PriceLevel.builder()
                                                                        .price(new BigDecimal("150.05"))
                                                                        .quantity(800L)
                                                                        .orderCount(3)
                                                                        .build()))
                                        .timestamp(Instant.now())
                                        .build();

                        when(marketDataService.getDepth("AAPL", 10)).thenReturn(response);

                        mockMvc.perform(get("/api/v1/market-data/depth/AAPL")
                                        .param("levels", "10"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.symbol").value("AAPL"))
                                        .andExpect(jsonPath("$.bids").isArray())
                                        .andExpect(jsonPath("$.asks").isArray());
                }

                @Test
                @DisplayName("Should return 404 for unknown symbol")
                void shouldReturn404ForUnknownSymbol() throws Exception {
                        when(marketDataService.getDepth("INVALID", 10)).thenReturn(null);

                        mockMvc.perform(get("/api/v1/market-data/depth/INVALID"))
                                        .andExpect(status().isNotFound());
                }
        }

        @Nested
        @DisplayName("GET /api/v1/market-data/ticker/{symbol}")
        class GetTickerTests {

                @Test
                @DisplayName("Should return ticker data")
                void shouldReturnTickerData() throws Exception {
                        MarketDataMessage.Ticker ticker = MarketDataMessage.Ticker.builder()
                                        .symbol("AAPL")
                                        .lastPrice(new BigDecimal("150.00"))
                                        .change(new BigDecimal("2.50"))
                                        .changePercent(new BigDecimal("1.69"))
                                        .high(new BigDecimal("151.00"))
                                        .low(new BigDecimal("148.00"))
                                        .open(new BigDecimal("147.50"))
                                        .volume(5000000L)
                                        .timestamp(System.currentTimeMillis())
                                        .build();

                        when(marketDataService.getTicker("AAPL")).thenReturn(ticker);

                        mockMvc.perform(get("/api/v1/market-data/ticker/AAPL"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.symbol").value("AAPL"))
                                        .andExpect(jsonPath("$.lastPrice").value(150.00));
                }

                @Test
                @DisplayName("Should return 404 for unknown symbol")
                void shouldReturn404ForUnknownSymbol() throws Exception {
                        when(marketDataService.getTicker("INVALID")).thenReturn(null);

                        mockMvc.perform(get("/api/v1/market-data/ticker/INVALID"))
                                        .andExpect(status().isNotFound());
                }
        }

        @Nested
        @DisplayName("GET /api/v1/market-data/tickers")
        class GetTickersTests {

                @Test
                @DisplayName("Should return multiple tickers")
                void shouldReturnMultipleTickers() throws Exception {
                        List<MarketDataMessage.Ticker> tickers = Arrays.asList(
                                        MarketDataMessage.Ticker.builder()
                                                        .symbol("AAPL")
                                                        .lastPrice(new BigDecimal("150.00"))
                                                        .build(),
                                        MarketDataMessage.Ticker.builder()
                                                        .symbol("GOOGL")
                                                        .lastPrice(new BigDecimal("2800.00"))
                                                        .build());

                        when(marketDataService.getTickers(Arrays.asList("AAPL", "GOOGL"))).thenReturn(tickers);

                        mockMvc.perform(get("/api/v1/market-data/tickers")
                                        .param("symbols", "AAPL", "GOOGL"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$").isArray())
                                        .andExpect(jsonPath("$.length()").value(2));
                }
        }

        @Nested
        @DisplayName("GET /api/v1/market-data/last-price/{symbol}")
        class GetLastPriceTests {

                @Test
                @DisplayName("Should return last price")
                void shouldReturnLastPrice() throws Exception {
                        when(marketDataService.getLastPrice("AAPL")).thenReturn(new BigDecimal("150.00"));

                        mockMvc.perform(get("/api/v1/market-data/last-price/AAPL"))
                                        .andExpect(status().isOk())
                                        .andExpect(content().string("150.00"));
                }

                @Test
                @DisplayName("Should return 404 when no price available")
                void shouldReturn404WhenNoPriceAvailable() throws Exception {
                        when(marketDataService.getLastPrice("INVALID")).thenReturn(null);

                        mockMvc.perform(get("/api/v1/market-data/last-price/INVALID"))
                                        .andExpect(status().isNotFound());
                }
        }

        @Nested
        @DisplayName("GET /api/v1/market-data/trades/{symbol}")
        class GetRecentTradesTests {

                @Test
                @DisplayName("Should return recent trades")
                void shouldReturnRecentTrades() throws Exception {
                        List<TradeResponse> trades = Arrays.asList(
                                        TradeResponse.builder()
                                                        .tradeId(1L)
                                                        .symbol("AAPL")
                                                        .price(new BigDecimal("150.00"))
                                                        .quantity(100L)
                                                        .build(),
                                        TradeResponse.builder()
                                                        .tradeId(2L)
                                                        .symbol("AAPL")
                                                        .price(new BigDecimal("150.10"))
                                                        .quantity(200L)
                                                        .build());

                        when(tradeService.getRecentTrades("AAPL", 50)).thenReturn(trades);

                        mockMvc.perform(get("/api/v1/market-data/trades/AAPL"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$").isArray())
                                        .andExpect(jsonPath("$.length()").value(2));
                }
        }

        @Nested
        @DisplayName("GET /api/v1/market-data/stats/{symbol}")
        class GetDailyStatsTests {

                @Test
                @DisplayName("Should return daily statistics")
                void shouldReturnDailyStatistics() throws Exception {
                        TradeService.DailyStats stats = TradeService.DailyStats.builder()
                                        .symbol("AAPL")
                                        .volume(5000000L)
                                        .value(new BigDecimal("750000000"))
                                        .tradeCount(10000)
                                        .vwap(new BigDecimal("150.00"))
                                        .openPrice(new BigDecimal("148.00"))
                                        .lastPrice(new BigDecimal("150.00"))
                                        .build();

                        when(tradeService.getDailyStats("AAPL")).thenReturn(stats);

                        mockMvc.perform(get("/api/v1/market-data/stats/AAPL"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.symbol").value("AAPL"))
                                        .andExpect(jsonPath("$.volume").value(5000000));
                }
        }
}
