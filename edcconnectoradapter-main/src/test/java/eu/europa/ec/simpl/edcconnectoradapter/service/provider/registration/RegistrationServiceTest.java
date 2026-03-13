package eu.europa.ec.simpl.edcconnectoradapter.service.provider.registration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.europa.ec.simpl.data1.common.constant.CommonConstants;
import eu.europa.ec.simpl.data1.common.enumeration.OfferType;
import eu.europa.ec.simpl.data1.common.exception.InvalidSDJsonException;
import eu.europa.ec.simpl.data1.common.exception.RemoteServiceErrorException;
import eu.europa.ec.simpl.data1.common.exception.RemoteServiceUnexpectedResponseException;
import eu.europa.ec.simpl.data1.common.util.JsonPathUtil;
import eu.europa.ec.simpl.data1.common.util.SDUtil;
import eu.europa.ec.simpl.edcconnectoradapter.TestSupport;
import eu.europa.ec.simpl.edcconnectoradapter.client.edcconnector.EDCConnectorManagementClient;
import eu.europa.ec.simpl.edcconnectoradapter.model.edc.management.request.EdcAssetDefinition;
import eu.europa.ec.simpl.edcconnectoradapter.model.edc.management.request.EdcContractDefinition;
import eu.europa.ec.simpl.edcconnectoradapter.model.edc.management.request.EdcPolicyDefinition;
import feign.FeignException;
import feign.Request;
import java.io.IOException;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    private static final String NS = SDUtil.toNS(CommonConstants.ECOSYSTEM);

    private static final String DATA_OFFERING_JSON_FILE = "test/frontend-data-offering.json";
    private static final String INFRA_OFFERING_JSON_FILE = "test/frontend-infra-offering.json";

    private RegistrationService registrationService;

    @Mock
    private EDCConnectorManagementClient edcConnectorManagementClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUpEach() {
        registrationService = new RegistrationServiceImpl(edcConnectorManagementClient, objectMapper);
        ReflectionTestUtils.setField(
                registrationService, "edcConnectorTier2BaseUrl", "https://edc-connector-tier2-base-url");
    }

    @Test
    void testEnrich() throws Exception {
        JSONObject sdJsonLd = TestSupport.getResourceAsJsonObj(DATA_OFFERING_JSON_FILE, null);

        JSONObject result = registrationService.enrich(sdJsonLd, CommonConstants.ECOSYSTEM);
        assertNotNull(result, "Result should not be null");
        assertTrue(
                StringUtils.isNotBlank(JsonPathUtil.getStringValue(
                        result, "$." + NS + "edcConnector." + NS + "providerEndpointURL", false)),
                "Provider endpoint URL should not be blank");
    }

    @Test
    void testEnrichWithEmptyEcosystem() throws Exception {
        JSONObject sdJsonLd = TestSupport.getResourceAsJsonObj(DATA_OFFERING_JSON_FILE, null);

        JSONObject result = registrationService.enrich(sdJsonLd, "");
        assertNotNull(result, "Result should not be null");
        assertTrue(
                StringUtils.isNotBlank(
                        JsonPathUtil.getStringValue(result, "$.edcConnector.providerEndpointURL", false)),
                "Provider endpoint URL should not be blank");
    }

    private static Stream<Arguments> TestRegister() {
        // offeringFileName
        return Stream.of(
                Arguments.of(OfferType.DATA, DATA_OFFERING_JSON_FILE),
                Arguments.of(OfferType.INFRASTRUCTURE, INFRA_OFFERING_JSON_FILE));
    }

    @ParameterizedTest
    @MethodSource("TestRegister")
    void testRegister(OfferType offerType, String offeringFile) throws IOException {
        JSONObject sdJsonLd = TestSupport.getResourceAsJsonObj(offeringFile, null);

        String responseBody = "{\"@id\":\"f31452f6-2d41-4edd-bf1f-e06329c244d9\"}";
        when(edcConnectorManagementClient.register(any(EdcAssetDefinition.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
        when(edcConnectorManagementClient.register(any(EdcPolicyDefinition.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
        when(edcConnectorManagementClient.register(any(EdcContractDefinition.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        JSONObject result = registrationService.register(sdJsonLd, CommonConstants.ECOSYSTEM, offerType);

        assertNotNull(result, "Result should not be null");
        assertTrue(
                StringUtils.isNotBlank(
                        JsonPathUtil.getStringValue(result, "$." + NS + "edcRegistration." + NS + "assetId", false)),
                "Asset ID should not be blank");
        assertTrue(
                StringUtils.isNotBlank(JsonPathUtil.getStringValue(
                        result, "$." + NS + "edcRegistration." + NS + "accessPolicyId", false)),
                "Access policy ID should not be blank");
        assertTrue(
                StringUtils.isNotBlank(JsonPathUtil.getStringValue(
                        result, "$." + NS + "edcRegistration." + NS + "servicePolicyId", false)),
                "Service policy ID should not be blank");
        assertTrue(
                StringUtils.isNotBlank(JsonPathUtil.getStringValue(
                        result, "$." + NS + "edcRegistration." + NS + "contractDefinitionId", false)),
                "Contract definition ID should not be blank");

        // verifico che sia stato rimosso generalServiceProperties.providerDataAddress
        assertNull(
                result.getJSONObject(NS + "generalServiceProperties")
                        .optJSONObject(NS + CommonConstants.SD.PROVIDER_DATA_ADDRESS),
                "Provider data address should be removed from general service properties");
    }

    @Test
    void testRegisterWithEmptyEcosystem() throws Exception {
        OfferType offerType = OfferType.DATA;
        JSONObject sdJsonLd = TestSupport.getResourceAsJsonObj(DATA_OFFERING_JSON_FILE, null);

        assertThrows(
                InvalidSDJsonException.class,
                () -> registrationService.register(sdJsonLd, "", offerType),
                "Should throw InvalidSDJsonException when ecosystem is empty");
    }

    @Test
    void testRegisterWithInvalidDataAddressJson() throws Exception {
        OfferType offerType = OfferType.DATA;
        JSONObject sdJsonLd = TestSupport.getResourceAsJsonObj(DATA_OFFERING_JSON_FILE, null);

        // replacing providerDataAddress value with an invalid json string format
        JSONObject propertiesObj = sdJsonLd.getJSONObject(NS + CommonConstants.SD.ASSET_PROPERTIES);
        propertiesObj.put(NS + CommonConstants.SD.PROVIDER_DATA_ADDRESS, "not a valid json");

        assertThrows(
                InvalidSDJsonException.class,
                () -> registrationService.register(sdJsonLd, CommonConstants.ECOSYSTEM, offerType),
                "Should throw InvalidSDJsonException when provider data address is invalid JSON");
    }

    @Test
    void testRegisterNoValueFound() {
        OfferType offerType = OfferType.DATA;
        JSONObject sdJsonLd = new JSONObject("{\"param\":\"value\"}");
        assertThrows(
                InvalidSDJsonException.class,
                () -> registrationService.register(sdJsonLd, CommonConstants.ECOSYSTEM, offerType),
                "Should throw InvalidSDJsonException when required values are not found");
    }

    @Test
    void testRegisterAssetDefinitionMissingAssetProperties() throws Exception {
        OfferType offerType = OfferType.DATA;
        JSONObject sdJsonLd = TestSupport.getResourceAsJsonObj(DATA_OFFERING_JSON_FILE, null);

        // Remove the assetProperties key to trigger the if condition at line 107
        sdJsonLd.remove(NS + CommonConstants.SD.ASSET_PROPERTIES);

        assertThrows(
                InvalidSDJsonException.class,
                () -> registrationService.register(sdJsonLd, CommonConstants.ECOSYSTEM, offerType),
                "Should throw InvalidSDJsonException when assetProperties is missing");
    }

    @Test
    void testRegisterWithInvalidAccessPolicyJson() throws Exception {
        OfferType offerType = OfferType.DATA;
        JSONObject sdJsonLd = TestSupport.getResourceAsJsonObj(DATA_OFFERING_JSON_FILE, null);

        // Replace access-policy with invalid JSON that will cause JsonProcessingException in objectMapper.readValue()
        JSONObject servicePolicyObj = sdJsonLd.getJSONObject(NS + "servicePolicy");
        servicePolicyObj.put(NS + "access-policy", "{invalid json content missing quotes and braces");

        assertThrows(
                InvalidSDJsonException.class,
                () -> registrationService.register(sdJsonLd, CommonConstants.ECOSYSTEM, offerType),
                "Should throw InvalidSDJsonException when access-policy JSON is malformed");
    }

    @Test
    void testRegisterWithInvalidUsagePolicyJson() throws Exception {
        OfferType offerType = OfferType.DATA;
        JSONObject sdJsonLd = TestSupport.getResourceAsJsonObj(DATA_OFFERING_JSON_FILE, null);

        // Replace usage-policy with invalid JSON that will cause JsonProcessingException in objectMapper.readValue()
        JSONObject servicePolicyObj = sdJsonLd.getJSONObject(NS + "servicePolicy");
        servicePolicyObj.put(NS + "usage-policy", "not valid json at all {{{");

        assertThrows(
                InvalidSDJsonException.class,
                () -> registrationService.register(sdJsonLd, CommonConstants.ECOSYSTEM, offerType),
                "Should throw InvalidSDJsonException when usage-policy JSON is malformed");
    }

    @Test
    void testRegisterAssetWithRemoteServiceUnexpectedResponseException() throws Exception {
        OfferType offerType = OfferType.DATA;
        JSONObject sdJsonLd = TestSupport.getResourceAsJsonObj(DATA_OFFERING_JSON_FILE, null);

        ResponseEntity<String> invalidResponseEntity = new ResponseEntity<>("no json content", HttpStatus.OK);
        when(edcConnectorManagementClient.register(any(EdcAssetDefinition.class)))
                .thenReturn(invalidResponseEntity);
        assertThrows(
                RemoteServiceUnexpectedResponseException.class,
                () -> registrationService.register(sdJsonLd, CommonConstants.ECOSYSTEM, offerType),
                "Should throw RemoteServiceUnexpectedResponseException when asset registration returns invalid response");
    }

    private static Stream<Arguments> WithExceptionsParameters() {
        Request request = mock(Request.class);
        return Stream.of(Arguments.of(new FeignException.BadRequest("test", request, "test".getBytes(), null)));
    }

    @ParameterizedTest
    @MethodSource("WithExceptionsParameters")
    void testRegisterAssetWithExceptions(Exception e) throws Exception {
        OfferType offerType = OfferType.DATA;
        JSONObject sdJsonLd = TestSupport.getResourceAsJsonObj(DATA_OFFERING_JSON_FILE, null);

        when(edcConnectorManagementClient.register(any(EdcAssetDefinition.class)))
                .thenThrow(e);
        assertThrows(
                RemoteServiceErrorException.class,
                () -> registrationService.register(sdJsonLd, CommonConstants.ECOSYSTEM, offerType),
                "Should throw RemoteServiceErrorException when asset registration fails with exception");
    }

    @Test
    void testRegisterAssetWithNotOKresponseCode() throws Exception {
        OfferType offerType = OfferType.DATA;
        JSONObject sdJsonLd = TestSupport.getResourceAsJsonObj(DATA_OFFERING_JSON_FILE, null);

        ResponseEntity<String> notOKResponseEntity = new ResponseEntity<>("does not care", HttpStatus.ACCEPTED);
        when(edcConnectorManagementClient.register(any(EdcAssetDefinition.class)))
                .thenReturn(notOKResponseEntity);

        assertThrows(
                RemoteServiceErrorException.class,
                () -> registrationService.register(sdJsonLd, CommonConstants.ECOSYSTEM, offerType),
                "Should throw RemoteServiceErrorException when asset registration returns non-OK status");
    }

    @Test
    void testRegisterPolicyWithRemoteServiceUnexpectedResponseException() throws Exception {
        OfferType offerType = OfferType.DATA;
        JSONObject sdJsonLd = TestSupport.getResourceAsJsonObj(DATA_OFFERING_JSON_FILE, null);

        ResponseEntity<String> validResponseEntity =
                new ResponseEntity<>("{\"@id\": \"fbda41f7-d339-4b44-8919-dce1a89f8e70\"}", HttpStatus.OK);
        when(edcConnectorManagementClient.register(any(EdcAssetDefinition.class)))
                .thenReturn(validResponseEntity);

        ResponseEntity<String> invalidResponseEntity = new ResponseEntity<>("no json content", HttpStatus.OK);
        when(edcConnectorManagementClient.register(any(EdcPolicyDefinition.class)))
                .thenReturn(invalidResponseEntity);

        assertThrows(
                RemoteServiceUnexpectedResponseException.class,
                () -> registrationService.register(sdJsonLd, CommonConstants.ECOSYSTEM, offerType),
                "Should throw RemoteServiceUnexpectedResponseException when policy registration returns invalid response");
    }

    @ParameterizedTest
    @MethodSource("WithExceptionsParameters")
    void testRegisterPolicyWithExceptions(Exception e) throws Exception {
        OfferType offerType = OfferType.DATA;
        JSONObject sdJsonLd = TestSupport.getResourceAsJsonObj(DATA_OFFERING_JSON_FILE, null);

        ResponseEntity<String> validResponseEntity =
                new ResponseEntity<>("{\"@id\": \"fbda41f7-d339-4b44-8919-dce1a89f8e70\"}", HttpStatus.OK);
        when(edcConnectorManagementClient.register(any(EdcAssetDefinition.class)))
                .thenReturn(validResponseEntity);

        when(edcConnectorManagementClient.register(any(EdcPolicyDefinition.class)))
                .thenThrow(e);
        assertThrows(
                RemoteServiceErrorException.class,
                () -> registrationService.register(sdJsonLd, CommonConstants.ECOSYSTEM, offerType),
                "Should throw RemoteServiceErrorException when policy registration fails with exception");
    }

    @Test
    void testRegisterPolicyWithNotOKresponseCode() throws Exception {
        OfferType offerType = OfferType.DATA;
        JSONObject sdJsonLd = TestSupport.getResourceAsJsonObj(DATA_OFFERING_JSON_FILE, null);

        ResponseEntity<String> validResponseEntity =
                new ResponseEntity<>("{\"@id\": \"fbda41f7-d339-4b44-8919-dce1a89f8e70\"}", HttpStatus.OK);
        when(edcConnectorManagementClient.register(any(EdcAssetDefinition.class)))
                .thenReturn(validResponseEntity);

        ResponseEntity<String> notOKResponseEntity = new ResponseEntity<>("does not care", HttpStatus.ACCEPTED);
        when(edcConnectorManagementClient.register(any(EdcPolicyDefinition.class)))
                .thenReturn(notOKResponseEntity);

        assertThrows(
                RemoteServiceErrorException.class,
                () -> registrationService.register(sdJsonLd, CommonConstants.ECOSYSTEM, offerType),
                "Should throw RemoteServiceErrorException when policy registration returns non-OK status");
    }

    @Test
    void testRegisterContractWithRemoteServiceUnexpectedResponseException() throws Exception {
        OfferType offerType = OfferType.DATA;
        JSONObject sdJsonLd = TestSupport.getResourceAsJsonObj(DATA_OFFERING_JSON_FILE, null);

        ResponseEntity<String> validResponseEntity =
                new ResponseEntity<>("{\"@id\": \"fbda41f7-d339-4b44-8919-dce1a89f8e70\"}", HttpStatus.OK);
        when(edcConnectorManagementClient.register(any(EdcAssetDefinition.class)))
                .thenReturn(validResponseEntity);
        when(edcConnectorManagementClient.register(any(EdcPolicyDefinition.class)))
                .thenReturn(validResponseEntity);

        ResponseEntity<String> invalidResponseEntity = new ResponseEntity<>("no json content", HttpStatus.OK);
        when(edcConnectorManagementClient.register(any(EdcContractDefinition.class)))
                .thenReturn(invalidResponseEntity);

        assertThrows(
                RemoteServiceUnexpectedResponseException.class,
                () -> registrationService.register(sdJsonLd, CommonConstants.ECOSYSTEM, offerType),
                "Should throw RemoteServiceUnexpectedResponseException when contract registration returns invalid response");
    }

    @ParameterizedTest
    @MethodSource("WithExceptionsParameters")
    void testRegisterContractWithExceptions(Exception e) throws Exception {
        OfferType offerType = OfferType.DATA;
        JSONObject sdJsonLd = TestSupport.getResourceAsJsonObj(DATA_OFFERING_JSON_FILE, null);

        ResponseEntity<String> validResponseEntity =
                new ResponseEntity<>("{\"@id\": \"fbda41f7-d339-4b44-8919-dce1a89f8e70\"}", HttpStatus.OK);
        when(edcConnectorManagementClient.register(any(EdcAssetDefinition.class)))
                .thenReturn(validResponseEntity);
        when(edcConnectorManagementClient.register(any(EdcPolicyDefinition.class)))
                .thenReturn(validResponseEntity);

        when(edcConnectorManagementClient.register(any(EdcContractDefinition.class)))
                .thenThrow(e);
        assertThrows(
                RemoteServiceErrorException.class,
                () -> registrationService.register(sdJsonLd, CommonConstants.ECOSYSTEM, offerType),
                "Should throw RemoteServiceErrorException when contract registration fails with exception");
    }

    @Test
    void testRegisterContractWithNotOKresponseCode() throws Exception {
        OfferType offerType = OfferType.DATA;
        JSONObject sdJsonLd = TestSupport.getResourceAsJsonObj(DATA_OFFERING_JSON_FILE, null);

        ResponseEntity<String> validResponseEntity =
                new ResponseEntity<>("{\"@id\": \"fbda41f7-d339-4b44-8919-dce1a89f8e70\"}", HttpStatus.OK);
        when(edcConnectorManagementClient.register(any(EdcAssetDefinition.class)))
                .thenReturn(validResponseEntity);
        when(edcConnectorManagementClient.register(any(EdcPolicyDefinition.class)))
                .thenReturn(validResponseEntity);

        ResponseEntity<String> notOKResponseEntity = new ResponseEntity<>("does not care", HttpStatus.ACCEPTED);
        when(edcConnectorManagementClient.register(any(EdcContractDefinition.class)))
                .thenReturn(notOKResponseEntity);

        assertThrows(
                RemoteServiceErrorException.class,
                () -> registrationService.register(sdJsonLd, CommonConstants.ECOSYSTEM, offerType),
                "Should throw RemoteServiceErrorException when contract registration returns non-OK status");
    }
}
