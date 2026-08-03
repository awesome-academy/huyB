package com.sunasterisk.bookingtours.soap;

import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class CurrencyConversionEndpoint {

    private static final String NAMESPACE_URI = CurrencyConversionRequest.NAMESPACE;

    private final CurrencyRateProvider rateProvider;

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "CurrencyConversionRequest")
    @ResponsePayload
    public CurrencyConversionResponse convert(
            @RequestPayload CurrencyConversionRequest request) {
        return rateProvider.convert(
                request.getAmount(),
                request.getFromCurrency(),
                request.getToCurrency());
    }
}
