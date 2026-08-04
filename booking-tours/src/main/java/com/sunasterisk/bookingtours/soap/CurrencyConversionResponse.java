package com.sunasterisk.bookingtours.soap;

import jakarta.xml.bind.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@XmlRootElement(name = "CurrencyConversionResponse",
        namespace = CurrencyConversionRequest.NAMESPACE)
@XmlAccessorType(XmlAccessType.FIELD)
public class CurrencyConversionResponse {

    @XmlElement(required = true)
    private BigDecimal convertedAmount;

    @XmlElement(required = true)
    private BigDecimal rate;

    @XmlElement(required = true)
    private String fromCurrency;

    @XmlElement(required = true)
    private String toCurrency;

    public CurrencyConversionResponse(BigDecimal convertedAmount, BigDecimal rate,
                                       String fromCurrency, String toCurrency) {
        this.convertedAmount = convertedAmount;
        this.rate            = rate;
        this.fromCurrency    = fromCurrency;
        this.toCurrency      = toCurrency;
    }
}
