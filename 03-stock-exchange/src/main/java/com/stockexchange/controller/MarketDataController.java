package com.stockexchange.controller;

import com.stockexchange.dto.MarketDataMessage;
import com.stockexchange.dto.MarketDataResponse;
import com.stockexchange.dto.TradeResponse;
import com.stockexchange.service.MarketDataService;
import com.stockexchange.service.TradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * REST API for market data.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/market-data")
@RequiredArgsConstructor
@Tag(name = "Market Data", description = "Real-time and historical market data")
public class MarketDataController {

    private final MarketDataService marketDataService;
    private final TradeService tradeService;

    @GetMapping("/quote/{symbol}")
    @Operation(summary = "Get quote", description = "Get Level 1 quote (best bid/ask) for a symbol")
    @ApiResponse(responseCode = "200", description = "Quote retrieved")
    @ApiResponse(responseCode = "404", description = "Symbol not found")
    public ResponseEntity<MarketDataResponse> getQuote(
            @PathVariable String symbol) {

        MarketDataResponse response = marketDataService.getQuote(symbol.toUpperCase());
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/depth/{symbol}")
    @Operation(summary = "Get order book depth", description = "Get Level 2 order book depth")
    public ResponseEntity<MarketDataResponse> getDepth(
            @PathVariable String symbol,
            @Parameter(description = "Number of price levels") @RequestParam(defaultValue = "10") int levels) {

        MarketDataResponse response = marketDataService.getDepth(symbol.toUpperCase(), levels);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ticker/{symbol}")
    @Operation(summary = "Get ticker", description = "Get ticker summary for a symbol")
    public ResponseEntity<MarketDataMessage.Ticker> getTicker(
            @PathVariable String symbol) {

        MarketDataMessage.Ticker ticker = marketDataService.getTicker(symbol.toUpperCase());
        if (ticker == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ticker);
    }

    @GetMapping("/tickers")
    @Operation(summary = "Get multiple tickers", description = "Get tickers for multiple symbols")
    public ResponseEntity<List<MarketDataMessage.Ticker>> getTickers(
            @RequestParam List<String> symbols) {

        List<String> upperSymbols = symbols.stream()
                .map(String::toUpperCase)
                .toList();

        List<MarketDataMessage.Ticker> tickers = marketDataService.getTickers(upperSymbols);
        return ResponseEntity.ok(tickers);
    }

    @GetMapping("/tickers/all")
    @Operation(summary = "Get all tickers", description = "Get tickers for all tradable symbols")
    public ResponseEntity<List<MarketDataMessage.Ticker>> getAllTickers() {
        List<MarketDataMessage.Ticker> tickers = marketDataService.getAllTickers();
        return ResponseEntity.ok(tickers);
    }

    @GetMapping("/last-price/{symbol}")
    @Operation(summary = "Get last price", description = "Get last traded price for a symbol")
    public ResponseEntity<BigDecimal> getLastPrice(
            @PathVariable String symbol) {

        BigDecimal price = marketDataService.getLastPrice(symbol.toUpperCase());
        if (price == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(price);
    }

    @GetMapping("/trades/{symbol}")
    @Operation(summary = "Get recent trades", description = "Get recent trades for a symbol")
    public ResponseEntity<List<TradeResponse>> getRecentTrades(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "50") int limit) {

        List<TradeResponse> trades = tradeService.getRecentTrades(symbol.toUpperCase(), limit);
        return ResponseEntity.ok(trades);
    }

    @GetMapping("/stats/{symbol}")
    @Operation(summary = "Get daily statistics", description = "Get daily trading statistics")
    public ResponseEntity<TradeService.DailyStats> getDailyStats(
            @PathVariable String symbol) {

        TradeService.DailyStats stats = tradeService.getDailyStats(symbol.toUpperCase());
        return ResponseEntity.ok(stats);
    }
}
