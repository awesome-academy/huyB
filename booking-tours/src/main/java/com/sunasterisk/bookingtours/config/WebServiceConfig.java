package com.sunasterisk.bookingtours.config;

import com.sunasterisk.bookingtours.soap.CurrencyConversionRequest;
import com.sunasterisk.bookingtours.soap.CurrencyConversionResponse;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

// Kích hoạt Spring-WS infrastructure (endpoint mapping, marshalling, v.v.)
@EnableWs
@Configuration
public class WebServiceConfig {

    /** Đăng ký SOAP dispatcher servlet trên /soap/* */
    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(
            ApplicationContext applicationContext) {
        // Tạo servlet chuyên xử lý SOAP message của Spring-WS
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        // Gắn ApplicationContext để servlet có thể tìm endpoint và bean
        servlet.setApplicationContext(applicationContext);
        // Tự động điều chỉnh địa chỉ trong WSDL theo host thực tế khi serve
        servlet.setTransformWsdlLocations(true);
        // Map servlet lên /soap/* để nhận toàn bộ SOAP request
        return new ServletRegistrationBean<>(servlet, "/soap/*");
    }

    /** WSDL tự sinh từ XSD — truy cập tại /soap/currency.wsdl */
    // Bean name "currency" → WSDL expose tại /soap/currency.wsdl
    @Bean(name = "currency")
    public DefaultWsdl11Definition defaultWsdl11Definition(XsdSchema currencySchema) {
        DefaultWsdl11Definition def = new DefaultWsdl11Definition();
        // Tên port type trong WSDL, dùng để các client biết gọi operation nào
        def.setPortTypeName("CurrencyPort");
        // URI base của service, ghi vào thẻ <soap:address> trong WSDL
        def.setLocationUri("/soap");
        // Namespace của WSDL phải khớp với namespace trong XSD và SOAP message
        def.setTargetNamespace(CurrencyConversionRequest.NAMESPACE);
        // XSD schema làm nguồn sinh WSDL types
        def.setSchema(currencySchema);
        return def;
    }

    /** XSD schema định nghĩa cấu trúc request/response cho SOAP currency service. */
    @Bean
    public XsdSchema currencySchema() {
        // Đọc file XSD từ classpath (src/main/resources/wsdl/currency.xsd)
        return new SimpleXsdSchema(new ClassPathResource("wsdl/currency.xsd"));
    }

    /** Marshaller dùng chung cho client lẫn endpoint. */
    @Bean
    public Jaxb2Marshaller currencyMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        // Khai báo các class JAXB cần bind để marshaller biết serialize/deserialize type nào
        marshaller.setClassesToBeBound(
                CurrencyConversionRequest.class,  // DTO cho SOAP request
                CurrencyConversionResponse.class); // DTO cho SOAP response
        return marshaller;
    }
}
