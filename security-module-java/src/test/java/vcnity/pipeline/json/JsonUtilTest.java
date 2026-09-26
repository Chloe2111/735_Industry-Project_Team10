package vcnity.pipeline.json;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Java has no JSON.parse/JSON.stringify built in like JS does, so this
 * project ships a tiny JSON helper for the optional live-Claude-API
 * path. These tests aren't in the original 26 - they're new coverage
 * for this Java-only plumbing.
 */
public class JsonUtilTest {

    @Test
    public void quoteEscapesSpecialCharacters() {
        assertEquals("\"hello\"", JsonUtil.quote("hello"));
        assertEquals("\"line1\\nline2\"", JsonUtil.quote("line1\nline2"));
        assertEquals("\"she said \\\"hi\\\"\"", JsonUtil.quote("she said \"hi\""));
        assertEquals("\"back\\\\slash\"", JsonUtil.quote("back\\slash"));
    }

    @Test
    public void parsesFlatObject() {
        Map<String, Object> obj = JsonUtil.parseObject("{\"theme\":\"Session length\",\"confidence\":0.88}");
        assertEquals("Session length", obj.get("theme"));
        assertEquals(0.88, (double) (Double) obj.get("confidence"), 0.0001);
    }

    @Test
    public void parsesNestedObjectsAndArrays() {
        Map<String, Object> obj = JsonUtil.parseObject(
                "{\"content\":[{\"type\":\"text\",\"text\":\"hello\"}]}");
        List<?> content = (List<?>) obj.get("content");
        Map<?, ?> first = (Map<?, ?>) content.get(0);
        assertEquals("hello", first.get("text"));
    }

    @Test
    public void parsesEscapedStringsBackToOriginalText() {
        Map<String, Object> obj = JsonUtil.parseObject("{\"quote\":\"she said \\\"hi\\\"\\nnext line\"}");
        assertEquals("she said \"hi\"\nnext line", obj.get("quote"));
    }

    @Test
    public void parsesNullBooleanAndNumberTypes() {
        Map<String, Object> obj = JsonUtil.parseObject(
                "{\"a\":null,\"b\":true,\"c\":false,\"d\":-1.5e2}");
        assertNull(obj.get("a"));
        assertEquals(Boolean.TRUE, obj.get("b"));
        assertEquals(Boolean.FALSE, obj.get("c"));
        assertEquals(-150.0, (double) (Double) obj.get("d"), 0.0001);
    }

    @Test
    public void roundTripsAStringThroughQuoteAndParse() {
        String original = "quotes \" and backslashes \\ and a newline\nhere";
        String json = "{\"value\":" + JsonUtil.quote(original) + "}";
        Map<String, Object> obj = JsonUtil.parseObject(json);
        assertEquals(original, obj.get("value"));
    }
}
