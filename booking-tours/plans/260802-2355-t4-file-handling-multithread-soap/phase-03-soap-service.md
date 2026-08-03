# Phase 03 — Spring WS SOAP Currency Service + Tour Detail

## Overview
- **Priority:** High
- **Status:** COMPLETED
- **Tasks:** T4.6, T4.7, T4.8
- **Completed:** 2026-08-03

Expose a SOAP currency conversion endpoint, build a client bean, display tour price in VND/USD/EUR on tour detail page.

## Requirements
- WSDL accessible at `GET /ws/currency.wsdl`
- Contract-first: define XSD, generate JAXB classes via plugin
- Mock rates (hardcoded): VND=1, USD=25500, EUR=27800, JPY=170, KRW=18.5 (all relative to VND)
- Tour detail page shows: `5,000,000 VND ≈ $196 USD ≈ €179 EUR`
- SOAP client handles unknown currency with graceful fallback (return null, skip display)

## Related Code Files

**Modify:**
- `pom.xml` — add spring-ws-core, wsdl4j, JAXB deps + jaxb2-maven-plugin
- `src/main/java/com/sunasterisk/bookingtours/controller/TourController.java`
- `src/main/resources/templates/tours/detail.html`

**Create:**
- `src/main/resources/wsdl/currency.xsd`
- `src/main/java/com/sunasterisk/bookingtours/config/WebServiceConfig.java`
- `src/main/java/com/sunasterisk/bookingtours/soap/CurrencyConversionEndpoint.java`
- `src/main/java/com/sunasterisk/bookingtours/soap/CurrencyRateProvider.java`
- `src/main/java/com/sunasterisk/bookingtours/soap/CurrencyConversionClient.java`

## Implementation Steps

1. **pom.xml** — add dependencies. PREFER the Boot-managed starter (matches existing pom style of using starters, no explicit versions where Boot manages them):
   ```xml
   <!-- Spring WS (pulls spring-ws-core, transitively) -->
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-web-services</artifactId>
   </dependency>
   <!-- WSDL generation runtime -->
   <dependency>
       <groupId>wsdl4j</groupId>
       <artifactId>wsdl4j</artifactId>
   </dependency>
   ```
   JAXB runtime (jakarta): `jakarta.xml.bind:jakarta.xml.bind-api` + `org.glassfish.jaxb:jaxb-runtime` (jakarta-namespace impl; NOT `com.sun.xml.bind:jaxb-impl` which targets the old `javax` namespace and will clash on Java 21 / Boot 4).
   Add a JAXB codegen plugin (`org.codehaus.mojo:jaxb2-maven-plugin` v3.x — jakarta-capable, OR `com.evolveum.axiom`/`hi.dev` alt) bound to `generate-sources`, reading `src/main/resources/wsdl/currency.xsd`, output `target/generated-sources/jaxb`, package `com.sunasterisk.bookingtours.soap.generated`. VERIFY generated classes use `jakarta.xml.bind.*` imports (not `javax.*`) — this is the main compatibility risk.

2. **currency.xsd** — at `src/main/resources/wsdl/currency.xsd`:
   ```xml
   <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
              targetNamespace="http://bookingtours.sunasterisk.com/currency"
              xmlns:tns="http://bookingtours.sunasterisk.com/currency"
              elementFormDefault="qualified">

     <xs:element name="CurrencyConversionRequest">
       <xs:complexType>
         <xs:sequence>
           <xs:element name="amount" type="xs:decimal"/>
           <xs:element name="fromCurrency" type="xs:string"/>
           <xs:element name="toCurrency" type="xs:string"/>
         </xs:sequence>
       </xs:complexType>
     </xs:element>

     <xs:element name="CurrencyConversionResponse">
       <xs:complexType>
         <xs:sequence>
           <xs:element name="convertedAmount" type="xs:decimal"/>
           <xs:element name="rate" type="xs:decimal"/>
           <xs:element name="fromCurrency" type="xs:string"/>
           <xs:element name="toCurrency" type="xs:string"/>
         </xs:sequence>
       </xs:complexType>
     </xs:element>
   </xs:schema>
   ```

3. **WebServiceConfig** — `@EnableWs`, extends `WsConfigurerAdapter`:
   - Bean `MessageDispatcherServlet` mapped to `/ws/*`, `transformWsdlLocations=true`
   - Bean `DefaultWsdl11Definition` named `currency`:
     - `setPortTypeName("CurrencyConversionPort")`
     - `setLocationUri("/ws")`
     - `setTargetNamespace("http://bookingtours.sunasterisk.com/currency")`
     - `setSchema(xsdSchema)` — inject `XsdSchema` from `currency.xsd`
   - Exposes WSDL at `/ws/currency.wsdl`

4. **CurrencyRateProvider** — `@Component`:
   - `private static final Map<String, BigDecimal> RATES_TO_VND` (VND=1, USD=25500, EUR=27800, JPY=170, KRW=18.5)
   - Method: `BigDecimal convert(BigDecimal amount, String from, String to)` — convert via VND base: `amount * RATES_TO_VND[from] / RATES_TO_VND[to]`, scale 2, `HALF_UP`
   - Throw `IllegalArgumentException` for unknown currency codes

5. **CurrencyConversionEndpoint** — `@Endpoint`:
   - Namespace constant: `"http://bookingtours.sunasterisk.com/currency"`
   - `@PayloadRoot(namespace = NAMESPACE, localPart = "CurrencyConversionRequest")`
   - Method signature: `@ResponsePayload CurrencyConversionResponse convert(@RequestPayload CurrencyConversionRequest request)`
   - Delegate to `CurrencyRateProvider.convert()`
   - Build and return `CurrencyConversionResponse` (JAXB generated class)

6. **CurrencyConversionClient** — `@Component`, extends `WebServiceGatewaySupport`:
   - `@PostConstruct` sets `defaultUri` to `http://localhost:${server.port:8080}/ws`
   - Method: `BigDecimal convertPrice(BigDecimal amount, String from, String to)`:
     - Build `CurrencyConversionRequest` (JAXB generated)
     - `marshalSendAndReceive(getDefaultUri(), request)` → cast to `CurrencyConversionResponse`
     - Return `response.getConvertedAmount()`
     - Wrap in try/catch — log warn, return `null` on error

7. **TourController** — in `GET /tours/{id}` handler:
   - Inject `CurrencyConversionClient`
   - After fetching tour: call `client.convertPrice(tour.getPrice(), "VND", "USD")` and `"EUR"`
   - Add to model: `model.addAttribute("priceUsd", priceUsd)` and `"priceEur"`
   - If client returns null (error), skip — template handles null gracefully

8. **templates/tours/detail.html** — add price display section:
   ```html
   <div class="price-block">
     <span class="price-vnd" th:text="${#numbers.formatInteger(tour.price, 0, 'COMMA') + ' VND'}"></span>
     <span th:if="${priceUsd != null}" class="text-muted small">
       ≈ $<span th:text="${#numbers.formatDecimal(priceUsd, 0, 2)}"></span> USD
     </span>
     <span th:if="${priceEur != null}" class="text-muted small">
       ≈ €<span th:text="${#numbers.formatDecimal(priceEur, 0, 2)}"></span> EUR
     </span>
   </div>
   ```

## Todo
- [x] Add spring-ws-core, wsdl4j, JAXB deps to pom.xml
- [x] Add jaxb2-maven-plugin for code generation
- [x] Create currency.xsd
- [x] Create WebServiceConfig
- [x] Create CurrencyRateProvider
- [x] Create CurrencyConversionEndpoint
- [x] Create CurrencyConversionClient
- [x] Modify TourController (inject client, add prices to model)
- [x] Modify tours/detail.html (3-currency price display)

## Success Criteria
- `GET /ws/currency.wsdl` returns valid WSDL XML
- SOAP request via Postman/curl returns correct converted amount
- `/tours/{id}` shows price in 3 currencies (VND / USD / EUR)
- If SOAP call fails, page still loads without error (null-safe in template)
- `mvn compile` regenerates JAXB classes with `jakarta.xml.bind.*` imports

## Risk Assessment
- **JAXB codegen javax vs jakarta (High):** on Java 21 / Boot 4, generated classes must use `jakarta.xml.bind`. Use a jakarta-capable plugin + `jaxb-runtime` (glassfish), NOT `com.sun.xml.bind:jaxb-impl`. Verify by grepping generated sources.
- **Client self-call latency (Med):** `CurrencyConversionClient` calls the app's own `/ws` over HTTP for every tour detail view → 2 extra round-trips per page. Acceptable for mock scope; if slow, `CurrencyRateProvider` could be called directly (but spec mandates SOAP client path for T4.8, so keep the SOAP call). Wrap in try/catch, return null → template degrades gracefully.
- **Security already permits `/ws/**` (verified in `SecurityConfig`): CSRF-ignored + `permitAll`. No change needed.

## Rollback
- Remove `soap/` package, `WebServiceConfig`, `currency.xsd`, the 3 pom deps + plugin; revert `TourController` (drop `priceUsd`/`priceEur` model attrs) and `detail.html` price block. No DB change in this phase → clean revert.
