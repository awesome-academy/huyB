package com.sunasterisk.bookingtours.soap;

import jakarta.xml.bind.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * SOAP request DTO cho operation quy đổi tiền tệ.
 * Viết tay thay vì generate từ XSD để tránh phụ thuộc plugin JAXB.
 */
@Getter
@NoArgsConstructor
@XmlRootElement(name = "CurrencyConversionRequest",
        namespace = CurrencyConversionRequest.NAMESPACE)
@XmlAccessorType(XmlAccessType.FIELD)
public class CurrencyConversionRequest {

    public static final String NAMESPACE = "http://bookingtours.sunasterisk.com/currency";

    @XmlElement(required = true)
    private BigDecimal amount;

    @XmlElement(required = true)
    private String fromCurrency;

    @XmlElement(required = true)
    private String toCurrency;

    public CurrencyConversionRequest(BigDecimal amount, String fromCurrency, String toCurrency) {
        this.amount = amount;
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
    }
}
