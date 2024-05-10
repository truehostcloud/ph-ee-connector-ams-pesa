package org.mifos.connector.ams.pesacore.util;

import com.google.gson.Gson;
import org.apache.camel.util.json.JsonObject;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mifos.connector.ams.pesacore.pesacore.dto.PesacoreRequestDTO;
import org.mifos.connector.ams.pesacore.zeebe.ZeebeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.mifos.connector.ams.pesacore.zeebe.ZeebeVariables.CUSTOM_DATA;

public class PesacoreUtils {

    private static Logger logger = LoggerFactory.getLogger(ZeebeUtil.class);

    public static String parseErrorDescriptionFromJsonPayload(String errorJson) {
        if (errorJson == null || errorJson.isEmpty()) {
            return "Internal Server Error";
        }
        try {
        JsonObject jsonObject = (new Gson()).fromJson(errorJson, JsonObject.class);
        String[] keyList = {"Message", "error", "errorDescription", "errorMessage", "description"};
        for (String s : keyList) {
            String data = jsonObject.getString(s);
            if (data != null && !data.isEmpty()) {
                return data;
            }
        }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return "Internal Server Error";
    }

    public static PesacoreRequestDTO convertPaybillPayloadToAmsPesacorePayload(JSONObject payload) {
        JSONArray customData = payload.getJSONArray(CUSTOM_DATA);
        String transactionId = convertCustomData(customData, "transactionId");
        String currency = convertCustomData(customData, "currency");
        String wallet_msisdn = payload.getJSONObject("secondaryIdentifier").getString("value");
        String accountID = payload.getJSONObject("primaryIdentifier").getString("value");
        String amount = convertCustomData(customData, "amount");
        Long amountLong = Objects.nonNull(amount)? Double.valueOf(amount.trim()).longValue(): 0;

        PesacoreRequestDTO validationRequestDTO = new PesacoreRequestDTO();
        validationRequestDTO.setAccount(accountID);
        validationRequestDTO.setAmount(amountLong);
        validationRequestDTO.setCurrency(currency);
        validationRequestDTO.setRemoteTransactionId(transactionId);
        validationRequestDTO.setPhoneNumber(wallet_msisdn);
        validationRequestDTO.setStatus(null);
        validationRequestDTO.setGetAccountDetails(retrieveGetAccountDetailsFromCustomData(customData));
        return validationRequestDTO;
    }
    public static String convertCustomData(JSONArray customData, String key)
    {
        for(Object obj: customData)
        {
            JSONObject item = (JSONObject) obj;
            try {
                String filter = item.getString("key");
                if (filter != null && filter.equalsIgnoreCase(key)) {
                    Object val = item.get("value");
                    return val != null ? val.toString() : null;
                }
            } catch (Exception e){
                logger.error("Error while converting customdata for key {} within the object {}. Exception is: {}", key, customData, e);
            }
        }
        return null;
    }

    private static boolean retrieveGetAccountDetailsFromCustomData(JSONArray customData) {
        boolean getAccountDetailsFlag = false;
        if (customData != null) {
            String getAccountDetailsCustomData = convertCustomData(customData, "getAccountDetails");
            List<String> acceptedValues = new ArrayList<>();
            acceptedValues.add("true");
            acceptedValues.add("false");
            if (StringUtils.hasText(getAccountDetailsCustomData)
                    && acceptedValues.contains(getAccountDetailsCustomData)) {
               getAccountDetailsFlag= Boolean.parseBoolean(getAccountDetailsCustomData);
            }
        }
        return getAccountDetailsFlag;
    }
}
