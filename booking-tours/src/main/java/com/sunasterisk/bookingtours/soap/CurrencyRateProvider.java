package com.sunasterisk.bookingtours.soap;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Cung cấp tỷ giá hối đoái tĩnh (demo).
 * Trong production sẽ replace bằng lời gọi API tỷ giá thực tế.
 */
@Service
public class CurrencyRateProvider {

    // Tỷ giá quy về VND (1 đơn vị ngoại tệ = ? VND)
    private static final Map<String, BigDecimal> RATES_TO_VND = Map.of(
            "VND", BigDecimal.ONE,
            "USD", new BigDecimal("25000"),
            "EUR", new BigDecimal("27000")
    );

    /**
     * Quy đổi {@code amount} từ {@code fromCurrency} sang {@code toCurrency}.
     *
     * @throws IllegalArgumentException nếu currency không được hỗ trợ
     */
    public CurrencyConversionResponse convert(BigDecimal amount,
                                               String fromCurrency,
                                               String toCurrency) {
        BigDecimal fromRate = resolveRate(fromCurrency);
        BigDecimal toRate   = resolveRate(toCurrency);

        // amount * fromRate → VND → /toRate
        BigDecimal amountInVnd    = amount.multiply(fromRate);
        BigDecimal convertedAmount = amountInVnd.divide(toRate, 4, RoundingMode.HALF_UP);
        BigDecimal rate            = fromRate.divide(toRate, 8, RoundingMode.HALF_UP);

        return new CurrencyConversionResponse(convertedAmount, rate, fromCurrency, toCurrency);
    }

    private BigDecimal resolveRate(String currency) {
        BigDecimal rate = RATES_TO_VND.get(currency.toUpperCase());
        if (rate == null) {
            throw new IllegalArgumentException("Unsupported currency: " + currency);
        }
        return rate;
    }
}
