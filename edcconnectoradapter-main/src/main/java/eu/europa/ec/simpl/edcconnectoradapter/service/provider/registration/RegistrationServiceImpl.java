package eu.europa.ec.simpl.edcconnectoradapter.service.provider.registration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import eu.europa.ec.simpl.data1.common.constant.CommonConstants;
import eu.europa.ec.simpl.data1.common.enumeration.OfferType;
import eu.europa.ec.simpl.data1.common.exception.InvalidSDJsonException;
import eu.europa.ec.simpl.data1.common.exception.RemoteServiceUnexpectedResponseException;
import eu.europa.ec.simpl.data1.common.model.ld.odrl.OdrlPermission;
import eu.europa.ec.simpl.data1.common.model.ld.odrl.OdrlPolicy;
import eu.europa.ec.simpl.data1.common.util.JsonPathUtil;
import eu.europa.ec.simpl.data1.common.util.RemoteServiceUtil;
import eu.europa.ec.simpl.data1.common.util.SDUtil;
import eu.europa.ec.simpl.edcconnectoradapter.client.edcconnector.EDCConnectorManagementClient;
import eu.europa.ec.simpl.edcconnectoradapter.enumeration.EDCErrorType;
import eu.europa.ec.simpl.edcconnectoradapter.model.edc.management.request.EdcAssetDefinition;
import eu.europa.ec.simpl.edcconnectoradapter.model.edc.management.request.EdcContractDefinition;
import eu.europa.ec.simpl.edcconnectoradapter.model.edc.management.request.EdcPolicyDefinition;
import feign.FeignException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Profile({"provider", "build", "test"})
@Service
@Log4j2
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    private static final String OPERATION_FAILED_FORMAT = "%s failed";

    private static final String RESPONSE_ID_PATH = "$.@id";

    private static final String NS = "{NS}";

    private static final String ASSET_DATA_ADRESS_JSON_PATH =
            "$." + NS + "{propertiesName}." + NS + "providerDataAddress";
    private static final String ACCESS_POLICY_PATH = "$." + NS + "servicePolicy." + NS + "access-policy";
    private static final String USAGE_POLICY_PATH = "$." + NS + "servicePolicy." + NS + "usage-policy";

    @Value("${edc-connector.tier2-base-url}")
    private String edcConnectorTier2BaseUrl;

    private final EDCConnectorManagementClient edcConnectorClient;
    private final ObjectMapper objectMapper;

    @Override
    public JSONObject enrich(JSONObject sdJsonLd, String ecosystem) {
        JSONObject edcConnectorObj = new JSONObject();
        String ns = SDUtil.toNS(ecosystem);
        sdJsonLd.put(ns + "edcConnector", edcConnectorObj);
        edcConnectorObj.put(ns + "providerEndpointURL", edcConnectorTier2BaseUrl);
        return sdJsonLd;
    }

    @Override
    public JSONObject register(JSONObject sdJsonLd, String ecosystem, OfferType offerType)
            throws JsonProcessingException {
        log.debug("register() for ecosystem '{}', offerType '{}' and sdJsonLd {}", ecosystem, offerType, sdJsonLd);

        String ns = SDUtil.toNS(ecosystem);

        OdrlPolicy accessPolicy = getOdrlPolicy(sdJsonLd, replaceNS(ACCESS_POLICY_PATH, ns));
        OdrlPolicy usagePolicy = getOdrlPolicy(sdJsonLd, replaceNS(USAGE_POLICY_PATH, ns));

        String assetRegistrationId = registerAssetDefinition(ns, sdJsonLd);
        setTarget(accessPolicy, assetRegistrationId);
        setTarget(usagePolicy, assetRegistrationId);

        String accessPolicyRegistrationId = registerPolicyDefinition("accessPolicy", accessPolicy);
        String usagePolicyRegistrationId = registerPolicyDefinition("usagePolicy", usagePolicy);
        String registerContractDefinitionId =
                registerContractDefinition(assetRegistrationId, accessPolicyRegistrationId, usagePolicyRegistrationId);

        Map<String, String> map = Map.of(
                ns + "assetId", assetRegistrationId,
                ns + "accessPolicyId", accessPolicyRegistrationId,
                ns + "servicePolicyId", usagePolicyRegistrationId,
                ns + "contractDefinitionId", registerContractDefinitionId);
        sdJsonLd.put(ns + "edcRegistration", map);

        JSONObject servicePolicyObj = sdJsonLd.getJSONObject(ns + "servicePolicy");
        servicePolicyObj.put(ns + "access-policy", objectMapper.writeValueAsString(accessPolicy));
        servicePolicyObj.put(ns + "usage-policy", objectMapper.writeValueAsString(usagePolicy));

        log.debug("register(): returning sdJsonLd {}", sdJsonLd);
        return sdJsonLd;
    }

    private String registerAssetDefinition(String ns, JSONObject sdJsonLd) {
        String assetPropertiesName = CommonConstants.SD.ASSET_PROPERTIES;
        String assetPropertiesKey = ns + assetPropertiesName;

        if (!sdJsonLd.has(assetPropertiesKey)) {
            log.error(
                    "registerAssetDefinition() failed cause property '{}' not found in the SD JSON-LD",
                    assetPropertiesName);
            throw new InvalidSDJsonException("property '" + assetPropertiesName + "'not found");
        }

        EdcAssetDefinition assetDefinition = createAssetDefinition(CommonConstants.SD.ASSET_PROPERTIES, ns, sdJsonLd);

        String operation = "register (asset)";
        try {
            log.debug("registerAssetDefinition() invoking edcConnectorClient.register() for {}", assetDefinition);
            ResponseEntity<String> response = edcConnectorClient.register(assetDefinition);
            String responseBody = getRegistrationResponseBody(operation, response);
            log.debug("registerAssetDefinition() received response body {}", responseBody);

            return JsonPath.read(responseBody, RESPONSE_ID_PATH);

        } catch (FeignException e) {
            log.error("registerAssetDefinition() failed cause FeignException", e);
            throw RemoteServiceUtil.toRemoteServiceErrorException(
                    EDCErrorType.REMOTE_EDC_CONNECTOR_ERROR, String.format(OPERATION_FAILED_FORMAT, operation), e);
        } catch (JsonPathException e) {
            log.error("registerAssetDefinition() failed cause JsonPathException", e);
            throw new RemoteServiceUnexpectedResponseException(
                    EDCErrorType.REMOTE_EDC_CONNECTOR_ERROR, String.format(OPERATION_FAILED_FORMAT, operation), e);
        }
    }

    private String registerPolicyDefinition(String type, OdrlPolicy odrlPolicy) {
        String operation = "register (" + type + ")";
        odrlPolicy.setType("Set");
        EdcPolicyDefinition policyDefinition = new EdcPolicyDefinition(odrlPolicy);
        try {
            log.debug("registerPolicyDefinition(): invoking edcConnectorClient.register() for {}", policyDefinition);
            ResponseEntity<String> response = edcConnectorClient.register(policyDefinition);
            String responseBody = getRegistrationResponseBody(operation, response);
            log.debug("registerPolicyDefinition(): received response body {}", responseBody);

            return JsonPath.read(responseBody, RESPONSE_ID_PATH);

        } catch (FeignException e) {
            log.error("registerPolicyDefinition() failed cause FeignException", e);
            throw RemoteServiceUtil.toRemoteServiceErrorException(
                    EDCErrorType.REMOTE_EDC_CONNECTOR_ERROR, String.format(OPERATION_FAILED_FORMAT, operation), e);
        } catch (JsonPathException e) {
            log.error("registerPolicyDefinition() failed cause JsonPathException", e);
            throw new RemoteServiceUnexpectedResponseException(
                    EDCErrorType.REMOTE_EDC_CONNECTOR_ERROR, String.format(OPERATION_FAILED_FORMAT, operation), e);
        }
    }

    private String registerContractDefinition(
            String assetRegistrationId, String accessPolicyRegistrationId, String usagePolicyRegistrationId) {
        String operation = "register (contract)";
        EdcContractDefinition contractDefinition =
                new EdcContractDefinition(assetRegistrationId, accessPolicyRegistrationId, usagePolicyRegistrationId);
        try {
            log.debug(
                    "registerContractDefinition(): invoking edcConnectorClient.register() for assetRegistrationId '{}', accessPolicyRegistrationId '{}' and usagePolicyRegistrationId '{}'",
                    assetRegistrationId,
                    accessPolicyRegistrationId,
                    usagePolicyRegistrationId);
            ResponseEntity<String> response = edcConnectorClient.register(contractDefinition);
            String responseBody = getRegistrationResponseBody(operation, response);
            log.debug("registerContractDefinition(): received response body {}", responseBody);

            return JsonPath.read(responseBody, RESPONSE_ID_PATH);
        } catch (FeignException e) {
            log.error("registerContractDefinition() failed cause FeignException", e);
            throw RemoteServiceUtil.toRemoteServiceErrorException(
                    EDCErrorType.REMOTE_EDC_CONNECTOR_ERROR, String.format(OPERATION_FAILED_FORMAT, operation), e);
        } catch (JsonPathException e) {
            log.error("registerContractDefinition() failed cause JsonPathException", e);
            throw new RemoteServiceUnexpectedResponseException(
                    EDCErrorType.REMOTE_EDC_CONNECTOR_ERROR, String.format(OPERATION_FAILED_FORMAT, operation), e);
        }
    }

    private EdcAssetDefinition createAssetDefinition(String propertiesName, String ns, JSONObject sdJsonLd)
            throws InvalidSDJsonException {
        // get asset dataAddress from SD json as json string
        JsonNode dataAddress = getDataAddress(propertiesName, ns, sdJsonLd);
        EdcAssetDefinition assetDefinition = new EdcAssetDefinition();
        assetDefinition.setDataAddress(dataAddress);
        return assetDefinition;
    }

    private JsonNode getDataAddress(String propertiesName, String ns, JSONObject sdJsonLd)
            throws InvalidSDJsonException {
        String message = "no valid asset dataAddress json provided: ";
        try {
            String dataAddressName = ASSET_DATA_ADRESS_JSON_PATH.replace("{propertiesName}", propertiesName);
            String dataAddressAsString = JsonPathUtil.getStringValue(sdJsonLd, replaceNS(dataAddressName, ns), true);
            return objectMapper.readTree(dataAddressAsString);
        } catch (JsonProcessingException e) {
            throw new InvalidSDJsonException(message + e.getMessage(), e);
        }
    }

    private static String getRegistrationResponseBody(String operation, ResponseEntity<String> response) {
        int responseCode = response.getStatusCode().value();
        if (responseCode != HttpStatus.OK.value()) {
            RemoteServiceUtil.toRemoteServiceErrorException(
                    EDCErrorType.REMOTE_EDC_CONNECTOR_ERROR,
                    operation + " operation failed",
                    response.getBody(),
                    responseCode,
                    null);
        }
        return response.getBody();
    }

    private static String replaceNS(String assetUrlPath, String ns) {
        return assetUrlPath.replace(NS, ns);
    }

    private OdrlPolicy getOdrlPolicy(JSONObject sdJsonLd, String path) throws InvalidSDJsonException {
        try {
            String policyJson = JsonPathUtil.getStringValue(sdJsonLd, path, true);
            return objectMapper.readValue(policyJson, OdrlPolicy.class);
        } catch (JsonProcessingException e) {
            throw new InvalidSDJsonException("invalid policy value with path '" + path + "'", e);
        }
    }

    private static void setTarget(OdrlPolicy policy, String value) {
        policy.setTarget(value);
        List<OdrlPermission> permissions = policy.getPermissions();
        if (permissions != null) {
            permissions.forEach(e -> e.setTarget(value));
        }
    }
}
