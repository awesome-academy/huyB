package com.sunasterisk.bookingtours.soap;

import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

// Đánh dấu class là SOAP endpoint, được Spring-WS quản lý
@Endpoint
// Tự sinh constructor inject rateProvider qua Lombok
@RequiredArgsConstructor
public class CurrencyConversionEndpoint {

    // Namespace dùng để định tuyến SOAP message đến đúng endpoint
    private static final String NAMESPACE_URI = CurrencyConversionRequest.NAMESPACE;

    // Service thực hiện tra cứu tỉ giá và tính toán
    private final CurrencyRateProvider rateProvider;

    // Định tuyến SOAP message có localPart = "CurrencyConversionRequest" trong NAMESPACE_URI đến method này
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "CurrencyConversionRequest")
    // Kết quả trả về được serialize thành XML response payload
    @ResponsePayload
    public CurrencyConversionResponse convert(
            // Deserialize XML request payload thành Java object
            @RequestPayload CurrencyConversionRequest request) {
        return rateProvider.convert(
                request.getAmount(),       // Số tiền cần quy đổi
                request.getFromCurrency(), // Đơn vị tiền tệ gốc
                request.getToCurrency());  // Đơn vị tiền tệ đích
    }
}
