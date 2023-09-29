package dk.dbc.updateservice.json;

import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;

public class JsonMapper {
    private static final JsonMapper instance = new JsonMapper();
    private final ObjectMapper mapper;

    public JsonMapper() {
        this.mapper = new ObjectMapper();
    }

    public String writePrettyValue(Object value) throws IOException {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }

    public static String encodePretty(Object value) throws IOException {
        return instance.writePrettyValue(value);
    }
}
