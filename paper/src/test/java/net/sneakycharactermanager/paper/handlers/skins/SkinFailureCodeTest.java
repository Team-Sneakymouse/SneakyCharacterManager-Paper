package net.sneakycharactermanager.paper.handlers.skins;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkinFailureCodeTest {
    @SuppressWarnings("unchecked")
    private static JSONObject failedJob(Object... codes) {
        JSONArray errors = new JSONArray();
        for (Object code : codes) {
            JSONObject error = new JSONObject();
            if (code != null) error.put("code", code);
            errors.add(error);
        }
        JSONObject response = new JSONObject();
        response.put("errors", errors);
        return response;
    }

    @Test
    void readsTheFirstErrorCode() {
        assertEquals("invalid_skin_data", SkinData.failureCode(failedJob("invalid_skin_data", "other")));
    }

    @Test
    void missingErrorsAreUnknown() {
        assertEquals("unknown", SkinData.failureCode(null));
        assertEquals("unknown", SkinData.failureCode(new JSONObject()));
        assertEquals("unknown", SkinData.failureCode(failedJob()));
        assertEquals("unknown", SkinData.failureCode(failedJob((Object) null)));
    }

    @Test
    void onlyImageErrorsFailAtOnce() {
        assertFalse(SkinData.retryableFailure("invalid_image"));
        assertFalse(SkinData.retryableFailure("invalid_image_url"));
        assertTrue(SkinData.retryableFailure("invalid_skin_data"));
        assertTrue(SkinData.retryableFailure("unknown"));
    }
}
