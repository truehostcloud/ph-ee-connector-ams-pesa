package org.mifos.connector.ams.pesacore.util;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mifos.connector.ams.pesacore.pesacore.dto.PesacoreRequestDTO;

/**
 * Test class for {@link PesacoreUtils}
 *
 * @author amy.muhimpundu
 */
class PesacoreUtilsTest {

    @Test
    @DisplayName("Test for converting a paybill payload to a Pesacore payload")
    void convertPaybillPayloadToAmsPesacorePayload_HappyPath() {
        JSONObject payload = new JSONObject();
        payload.put("secondaryIdentifier", new JSONObject().put("value", "123456789"));
        payload.put("primaryIdentifier", new JSONObject().put("value", "987654321"));
        JSONArray customData = new JSONArray();
        customData.put(new JSONObject().put("key", "transactionId").put("value", "transId"));
        customData.put(new JSONObject().put("key", "currency").put("value", "USD"));
        customData.put(new JSONObject().put("key", "amount").put("value", "1000"));
        payload.put("customData", customData);

        PesacoreRequestDTO result = PesacoreUtils.convertPaybillPayloadToAmsPesacorePayload(payload);

        assertNotNull(result);
        assertEquals("987654321", result.getAccount());
        assertEquals(1000, result.getAmount());
        assertEquals("USD", result.getCurrency());
        assertEquals("transId", result.getRemoteTransactionId());
        assertEquals("123456789", result.getPhoneNumber());
        assertNull(result.getStatus());
    }

    @Test
    void convertPaybillPayloadToAmsPesacorePayload_EmptyPayload() {
        JSONObject payload = new JSONObject();

        assertThatThrownBy(() -> PesacoreUtils.convertPaybillPayloadToAmsPesacorePayload(payload))
                .isInstanceOf(JSONException.class).hasMessageContaining("JSONObject[\"customData\"] not found");

    }

    @Test
    @DisplayName("Test for converting a paybill payload to a Pesacore payload with no amount")
    void convertPaybillPayloadToAmsPesacorePayload_WithNoAmount_ExpectZeroAsAmount() {
        JSONObject payload = new JSONObject();
        payload.put("secondaryIdentifier", new JSONObject().put("value", "123456789"));
        payload.put("primaryIdentifier", new JSONObject().put("value", "987654321"));
        JSONArray customData = new JSONArray();
        customData.put(new JSONObject().put("key", "transactionId").put("value", "transId"));
        customData.put(new JSONObject().put("key", "currency").put("value", "USD"));
        payload.put("customData", customData);

        PesacoreRequestDTO result = PesacoreUtils.convertPaybillPayloadToAmsPesacorePayload(payload);

        assertNotNull(result);
        assertEquals("987654321", result.getAccount());
        assertEquals(0, result.getAmount());
        assertNull(result.getStatus());
    }

}
