package org.mifos.connector.ams.pesacore.camel.route;

import static org.mifos.connector.ams.pesacore.camel.config.CamelProperties.ACCOUNT_NUMBER;
import static org.mifos.connector.ams.pesacore.camel.config.CamelProperties.AMOUNT;
import static org.mifos.connector.ams.pesacore.camel.config.CamelProperties.CHANNEL_REQUEST;
import static org.mifos.connector.ams.pesacore.camel.config.CamelProperties.CLIENT_NAME_VARIABLE_NAME;
import static org.mifos.connector.ams.pesacore.camel.config.CamelProperties.CURRENCY;
import static org.mifos.connector.ams.pesacore.camel.config.CamelProperties.CUSTOM_DATA_VARIABLE_NAME;
import static org.mifos.connector.ams.pesacore.camel.config.CamelProperties.GET_ACCOUNT_DETAILS_FLAG;
import static org.mifos.connector.ams.pesacore.zeebe.ZeebeVariables.*;

import java.util.Objects;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mifos.connector.ams.pesacore.camel.config.PesaCoreProperties;
import org.mifos.connector.ams.pesacore.pesacore.dto.PesacoreRequestDTO;
import org.mifos.connector.ams.pesacore.pesacore.dto.RosterGetClientResponseDTO;
import org.mifos.connector.ams.pesacore.util.ConnectionUtils;
import org.mifos.connector.ams.pesacore.util.PesacoreUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class PesaRouteBuilder extends RouteBuilder {

    Logger logger = LoggerFactory.getLogger(this.getClass());

    private final PesaCoreProperties pesaCoreProperties;
    private final RestTemplate restTemplate;

    @Value("${pesacore.base-url}")
    private String pesacoreBaseUrl;

    @Value("${pesacore.endpoint.verification}")
    private String verificationEndpoint;

    @Value("${pesacore.endpoint.confirmation}")
    private String confirmationEndpoint;

    @Value("${pesacore.endpoint.client-details}")
    private String clientDetailsEndpoint;

    @Value("${pesacore.auth-header}")
    private String authHeader;

    @Value("${ams.timeout}")
    private Integer amsTimeout;

    public PesaRouteBuilder(PesaCoreProperties pesaCoreProperties, RestTemplate restTemplate) {
        this.pesaCoreProperties = pesaCoreProperties;
        this.restTemplate = restTemplate;
    }

    @Override
    public void configure() {

        from("rest:POST:/api/paymentHub/Verification").process(exchange -> {
            JSONObject channelRequest = new JSONObject(exchange.getIn().getBody(String.class));
            String transactionId = "123";
            exchange.setProperty(CHANNEL_REQUEST, channelRequest);
            exchange.setProperty(TRANSACTION_ID, transactionId);
        }).to("direct:transfer-validation-base");

        from("rest:POST:/api/paymentHub/Confirmation").process(exchange -> {
            JSONObject channelRequest = new JSONObject(exchange.getIn().getBody(String.class));
            String transactionId = "123";
            exchange.setProperty(CHANNEL_REQUEST, channelRequest);
            exchange.setProperty(TRANSACTION_ID, transactionId);
        }).to("direct:transfer-settlement-base");

        from("rest:POST:/api/v1/paybill/validate/roster").id("validate-user")
                .log(LoggingLevel.INFO, "## Roster user validation").setBody(e -> {
                    String body = e.getIn().getBody(String.class);
                    logger.debug("Body : {}", body);
                    String accountHoldingInstitutionId = String
                            .valueOf(e.getIn().getHeader("accountHoldingInstitutionId"));
                    e.setProperty("accountHoldingInstitutionId", accountHoldingInstitutionId);
                    return body;
                }).to("direct:transfer-validation-base").process(e -> {
                    String transactionId = e.getProperty(TRANSACTION_ID).toString();
                    logger.debug("Transaction Id : " + transactionId);
                    logger.debug("Response received from validation base : {}", e.getIn().getBody());
                    // Building the response
                    JSONObject responseObject = new JSONObject();
                    responseObject.put("reconciled", e.getProperty(PARTY_LOOKUP_FAILED).equals(false));
                    responseObject.put("amsName", "roster");
                    responseObject.put("accountHoldingInstitutionId", e.getProperty("accountHoldingInstitutionId"));
                    responseObject.put(TRANSACTION_ID, e.getProperty(TRANSACTION_ID));
                    responseObject.put("amount", e.getProperty("amount"));
                    responseObject.put(CURRENCY, e.getProperty("currency"));
                    responseObject.put("msisdn", e.getProperty("msisdn"));
                    responseObject.put(CLIENT_NAME_VARIABLE_NAME, e.getProperty(CLIENT_NAME_VARIABLE_NAME));
                    responseObject.put(CUSTOM_DATA_VARIABLE_NAME, e.getProperty(CUSTOM_DATA_VARIABLE_NAME));
                    logger.debug("response object " + responseObject);
                    e.getIn().setBody(responseObject.toString());
                });

        from("direct:transfer-validation-base").id("transfer-validation-base")
                .log(LoggingLevel.INFO, "## Starting transfer Validation base route").to("direct:transfer-validation")
                .choice().when(header("CamelHttpResponseCode").isEqualTo("200"))
                .log(LoggingLevel.INFO, "Validation successful").process(exchange -> {
                    // processing success case
                    exchange.setProperty(PARTY_LOOKUP_FAILED, false);
                    exchange.setProperty("accountHoldingInstitutionId",
                            exchange.getProperty("accountHoldingInstitutionId"));
                    exchange.setProperty(TRANSACTION_ID, exchange.getProperty(TRANSACTION_ID));
                    exchange.setProperty("amount", exchange.getProperty("amount"));
                    exchange.setProperty(CURRENCY, exchange.getProperty("currency"));
                    exchange.setProperty("msisdn", exchange.getProperty("msisdn"));
                    logger.debug("Pesacore Validation Success");
                }).choice().when(exchangeProperty(GET_ACCOUNT_DETAILS_FLAG).isEqualTo(true))
                .to("direct:get-client-details").process(e -> {
                    log.debug("Roster get client details api response: {}", e.getIn().getBody());
                    try {
                        RosterGetClientResponseDTO clientDetailsResponse = e.getIn()
                                .getBody(RosterGetClientResponseDTO.class);
                        if (clientDetailsResponse != null) {
                            e.setProperty(CLIENT_NAME_VARIABLE_NAME,
                                    clientDetailsResponse.getFirstName() + " " + clientDetailsResponse.getLastName());
                            e.setProperty(CUSTOM_DATA_VARIABLE_NAME,
                                    RosterGetClientResponseDTO.convertToCustomData(clientDetailsResponse));
                        }
                    } catch (Exception exception) {
                        log.error("Unable to fetch client details from API response. Error: {}", exception);
                    }

                }).endChoice().otherwise().log(LoggingLevel.ERROR, "Validation unsuccessful").process(exchange -> {
                    // processing unsuccessful case
                    exchange.setProperty(PARTY_LOOKUP_FAILED, true);
                    exchange.setProperty("accountHoldingInstitutionId",
                            exchange.getProperty("accountHoldingInstitutionId"));
                    exchange.setProperty(TRANSACTION_ID, exchange.getProperty(TRANSACTION_ID));
                    exchange.setProperty("amount", exchange.getProperty("amount"));
                    exchange.setProperty(CURRENCY, exchange.getProperty("currency"));
                    exchange.setProperty("msisdn", exchange.getProperty("msisdn"));
                    logger.debug("Pesacore Validation Failure");
                });

        from("direct:transfer-validation").id("transfer-validation")
                .log(LoggingLevel.INFO, "## Starting transfer Validation route").removeHeader("*")
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader("Content-Type", constant("application/json"))
                .setHeader("Authorization", simple("Token " + authHeader)).setBody(exchange -> {
                    PesacoreRequestDTO verificationRequestDTO;
                    if (exchange.getProperty(CHANNEL_REQUEST) != null) {
                        JSONObject channelRequest = (JSONObject) exchange.getProperty(CHANNEL_REQUEST);
                        String transactionId = exchange.getProperty(TRANSACTION_ID, String.class);

                        verificationRequestDTO = buildPesacoreDtoFromChannelRequest(channelRequest, transactionId);
                    } else {
                        JSONObject paybillRequest = new JSONObject(exchange.getIn().getBody(String.class));
                        verificationRequestDTO = PesacoreUtils
                                .convertPaybillPayloadToAmsPesacorePayload(paybillRequest);

                        log.debug(verificationRequestDTO.toString());
                        exchange.setProperty(TRANSACTION_ID, verificationRequestDTO.getRemoteTransactionId());
                        exchange.setProperty("amount", verificationRequestDTO.getAmount());
                        exchange.setProperty(CURRENCY, verificationRequestDTO.getCurrency());
                        exchange.setProperty("msisdn", verificationRequestDTO.getPhoneNumber());
                        exchange.setProperty("accountHoldingInstitutionId",
                                exchange.getProperty("accountHoldingInstitutionId"));
                    }

                    logger.debug("Validation request DTO: \n\n\n" + verificationRequestDTO);
                    exchange.setProperty(ACCOUNT_NUMBER, verificationRequestDTO.getAccount());
                    exchange.setProperty(GET_ACCOUNT_DETAILS_FLAG, verificationRequestDTO.isGetAccountDetails());
                    return verificationRequestDTO;
                }).marshal().json(JsonLibrary.Jackson)
                .toD(getVerificationEndpoint() + "?bridgeEndpoint=true&throwExceptionOnFailure=false&"
                        + ConnectionUtils.getConnectionTimeoutDsl(amsTimeout))
                .log(LoggingLevel.INFO, "Pesacore verification api response: \n\n..\n\n..\n\n.. ${body}");

        from("direct:transfer-settlement-base").id("transfer-settlement-base")
                .log(LoggingLevel.INFO, "## Transfer Settlement route").to("direct:transfer-settlement").choice()
                .when(header("CamelHttpResponseCode").isEqualTo("200")).log(LoggingLevel.INFO, "Settlement successful")
                .process(exchange -> {
                    // processing success case

                    JSONObject body = new JSONObject(exchange.getIn().getBody(String.class));

                    if (body.getString("status").equals("CONFIRMED")) {
                        exchange.setProperty(TRANSFER_SETTLEMENT_FAILED, false);
                    } else {
                        exchange.setProperty(TRANSFER_SETTLEMENT_FAILED, true);
                    }
                }).otherwise().log(LoggingLevel.ERROR, "Settlement unsuccessful").process(exchange -> {
                    // processing unsuccessful case
                    exchange.setProperty(TRANSFER_SETTLEMENT_FAILED, true);
                });

        from("direct:transfer-settlement").id("transfer-settlement")
                .log(LoggingLevel.INFO, "## Starting transfer settlement route").removeHeader("*")
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader("Content-Type", constant("application/json"))
                .setHeader("Authorization", simple("Token " + authHeader)).setBody(exchange -> {
                    JSONObject channelRequest = (JSONObject) exchange.getProperty(CHANNEL_REQUEST);
                    String transactionId = exchange.getProperty(TRANSACTION_ID, String.class);
                    String receiptNumber = exchange.getProperty(EXTERNAL_ID, String.class);
                    receiptNumber = Objects.nonNull(receiptNumber) ? receiptNumber
                            : channelRequest.getString(EXTERNAL_ID);

                    PesacoreRequestDTO confirmationRequestDTO = buildPesacoreDtoFromChannelRequest(channelRequest,
                            receiptNumber);
                    confirmationRequestDTO.setStatus("successful");
                    confirmationRequestDTO.setReceiptId(receiptNumber);

                    logger.debug("Confirmation request DTO: \n\n\n" + confirmationRequestDTO);
                    return confirmationRequestDTO;
                }).marshal().json(JsonLibrary.Jackson)
                .toD(getConfirmationEndpoint() + "?bridgeEndpoint=true&throwExceptionOnFailure=false&"
                        + ConnectionUtils.getConnectionTimeoutDsl(amsTimeout))
                .log(LoggingLevel.INFO, "Pesacore verification api response: \n\n..\n\n..\n\n.. ${body}");

        from("direct:get-client-details").id("get-client-details")
                .log(LoggingLevel.INFO, "## Starting get client details route")
                .setHeader(Exchange.HTTP_METHOD, constant("GET")).process(e -> {
                    e.getIn().setHeader("accountNumber", e.getProperty(ACCOUNT_NUMBER, String.class));
                    e.getIn().setHeader("clientCountry", getCountryIdByCurrency(e.getProperty(CURRENCY, String.class)));
                    e.getIn().setHeader("clientDetailsUrl", getClientDetailsUrl());

                    UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(getClientDetailsUrl())
                            // Add query parameter
                            .queryParam("account", e.getProperty(ACCOUNT_NUMBER, String.class))
                            .queryParam("country", getCountryIdByCurrency(e.getProperty(CURRENCY, String.class)));
                    ResponseEntity<RosterGetClientResponseDTO> exchange = restTemplate
                            .getForEntity(builder.toUriString(), RosterGetClientResponseDTO.class);
                    e.getIn().setBody(exchange.getBody());
                })
                .log(LoggingLevel.INFO, "Roster get client details api response: \n ${body}");

    }

    // returns the complete URL for verification request
    private String getVerificationEndpoint() {
        return pesacoreBaseUrl + verificationEndpoint;
    }

    // returns the complete URL for confirmation request
    private String getConfirmationEndpoint() {
        return pesacoreBaseUrl + confirmationEndpoint;
    }

    /**
     * Returns the complete URL for getting client details endpoint.
     *
     * @return the full url to be used in getting client details requests
     */
    private String getClientDetailsUrl() {
        return pesacoreBaseUrl + clientDetailsEndpoint;
    }

    private PesacoreRequestDTO buildPesacoreDtoFromChannelRequest(JSONObject channelRequest, String transactionId) {
        PesacoreRequestDTO pesacoreRequestDTO = new PesacoreRequestDTO();

        String phoneNumber = getPartyIdIdentifier(channelRequest, "payer");
        String accountId = getPartyIdIdentifier(channelRequest, "payee");
        Object amountObj = channelRequest.get(AMOUNT);

        Long amount = null;
        String currency = null;
        if (amountObj != null) {
            if (amountObj instanceof String) {

                amount = Long.valueOf((String) amountObj);
                currency = String.valueOf(channelRequest.get(CURRENCY));
            } else {
                JSONObject amountJson = (JSONObject) amountObj;
                amount = Long.valueOf(amountJson.getString(AMOUNT));
                currency = amountJson.getString(CURRENCY);
            }
        }

        pesacoreRequestDTO.setRemoteTransactionId(transactionId);
        pesacoreRequestDTO.setAmount(amount);
        pesacoreRequestDTO.setPhoneNumber(phoneNumber);
        pesacoreRequestDTO.setCurrency(currency);
        pesacoreRequestDTO.setAccount(accountId);

        return pesacoreRequestDTO;
    }

    /**
     * Get the country id by currency from the properties
     *
     * @param currency
     *            the currency code
     * @return the country id
     */
    private String getCountryIdByCurrency(String currency) {
        if (StringUtils.hasText(currency)) {
            return pesaCoreProperties.getCountryByCurrency(currency).getCountryId();
        }
        return "";
    }

    /**
     * Get party identifier from the {@link JSONObject}
     *
     * @param party
     *            {@link JSONObject}
     * @param partyType
     *            the party type
     * @return
     */
    private static String getPartyIdIdentifier(JSONObject party, String partyType) {
        Object type = party.get(partyType);
        if (type instanceof JSONArray) {
            return ((JSONArray) type).getJSONObject(0).getString("partyIdIdentifier");
        } else {
            return ((JSONObject) type).getJSONObject("partyIdInfo").getString("partyIdentifier");
        }
    }
}
