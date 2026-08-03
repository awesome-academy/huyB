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

@EnableWs
@Configuration
public class WebServiceConfig {

    /** Đăng ký SOAP dispatcher servlet trên /soap/* */
    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(
            ApplicationContext applicationContext) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(applicationContext);
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/soap/*");
    }

    /** WSDL tự sinh từ XSD — truy cập tại /soap/currency.wsdl */
    @Bean(name = "currency")
    public DefaultWsdl11Definition defaultWsdl11Definition(XsdSchema currencySchema) {
        DefaultWsdl11Definition def = new DefaultWsdl11Definition();
        def.setPortTypeName("CurrencyPort");
        def.setLocationUri("/soap");
        def.setTargetNamespace(CurrencyConversionRequest.NAMESPACE);
        def.setSchema(currencySchema);
        return def;
    }

    @Bean
    public XsdSchema currencySchema() {
        return new SimpleXsdSchema(new ClassPathResource("wsdl/currency.xsd"));
    }

    /** Marshaller dùng chung cho client lẫn endpoint. */
    @Bean
    public Jaxb2Marshaller currencyMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setClassesToBeBound(
                CurrencyConversionRequest.class,
                CurrencyConversionResponse.class);
        return marshaller;
    }
}
