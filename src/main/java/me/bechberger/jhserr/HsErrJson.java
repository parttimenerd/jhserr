package me.bechberger.jhserr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON serialization and deserialization for {@link HsErrReport}.
 */
public final class HsErrJson {

    private static final ObjectMapper MAPPER = createMapper();

    private HsErrJson() {}

    private static ObjectMapper createMapper() {
        ObjectMapper m = new ObjectMapper();
        m.enable(SerializationFeature.INDENT_OUTPUT);
        m.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return m;
    }

    /** Returns the shared ObjectMapper (for schema generation etc.). */
    public static ObjectMapper mapper() { return MAPPER; }

    /** Serialize a report to pretty-printed JSON. */
    public static String toJson(HsErrReport report) throws JsonProcessingException {
        return MAPPER.writeValueAsString(report);
    }

    /** Write a report as JSON to a file. */
    public static void toJsonFile(HsErrReport report, Path path) throws IOException {
        MAPPER.writeValue(path.toFile(), report);
    }

    /** Deserialize a report from a JSON string. */
    public static HsErrReport fromJson(String json) throws JsonProcessingException {
        return MAPPER.readValue(json, HsErrReport.class);
    }

    /** Read a report from a JSON file. */
    public static HsErrReport fromJsonFile(Path path) throws IOException {
        return MAPPER.readValue(path.toFile(), HsErrReport.class);
    }
}
