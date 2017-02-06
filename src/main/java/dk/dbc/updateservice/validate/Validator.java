package dk.dbc.updateservice.validate;

import dk.dbc.updateservice.dto.SchemaDTO;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.ws.JNDIResources;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

@Stateless
public class Validator {
    private static final XLogger logger = XLoggerFactory.getXLogger(Validator.class);

    @EJB
    private Scripter scripter;

    @Resource(lookup = JNDIResources.SETTINGS_NAME)
    private Properties settings;

    public List<SchemaDTO> getValidateSchemas(String groupId, OpenAgencyService.LibraryGroup libraryGroup) throws ScripterException {
        logger.entry();
        List<SchemaDTO> result = null;
        try {
            result = new ArrayList<>();
            ObjectMapper mapper = new ObjectMapper();
            Object jsResult = scripter.callMethod("getValidateSchemas", groupId, libraryGroup.toString(), settings);
            logger.debug("Result from getValidateSchemas JS ({}): {}", jsResult.getClass().getName(), jsResult);
            SchemaDTO[] names = mapper.readValue(jsResult.toString(), SchemaDTO[].class);
            result.addAll(Arrays.asList(names));
            logger.trace("Number of templates: {}", result.size());
            return result;
        } catch (IOException ex) {
            throw new ScripterException("Error when executing JavaScript function: getValidateSchemas", ex);
        } finally {
            logger.exit(result);
        }
    }
}
