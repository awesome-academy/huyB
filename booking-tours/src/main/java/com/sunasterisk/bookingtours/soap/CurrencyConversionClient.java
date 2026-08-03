package com.sunasterisk.bookingtours.soap;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;

import java.math.BigDecimal;

/**
 * Client gọi SOAP endpoint quy đổi tiền tệ nội bộ.
 * URI mặc định trỏ đến /ws của chính ứng dụng; override qua {@code soap.currency.url}.
 */
@Slf4j
@Component
public class CurrencyConversionClient extends WebServiceGatewaySupport {

    @Value("${soap.currency.url:http://localhost:8080/soap}")
    private String soapUrl;

    @Autowired
    public void configureMarshaller(Jaxb2Marshaller currencyMarshaller) {
        getWebServiceTemplate().setMarshaller(currencyMarshaller);
        getWebServiceTemplate().setUnmarshaller(currencyMarshaller);
    }

    @PostConstruct
    public void init() {
        setDefaultUri(soapUrl);
    }

    /**
     * Gọi SOAP endpoint để quy đổi {@code amount} từ {@code from} sang {@code to}.
     * Trả về {@code null} và log lỗi nếu gọi thất bại (tránh làm vỡ trang detail).
     */
    public CurrencyConversionResponse convertCurrency(BigDecimal amount,
                                                       String from,
                                                       String to) {
        try {
            CurrencyConversionRequest request = new CurrencyConversionRequest(amount, from, to);
            return (CurrencyConversionResponse) getWebServiceTemplate()
                    .marshalSendAndReceive(soapUrl, request);
        } catch (Exception e) {
            log.error("SOAP currency conversion failed ({} {} → {}): {}", amount, from, to, e.getMessage());
            return null;
        }
    }
}
