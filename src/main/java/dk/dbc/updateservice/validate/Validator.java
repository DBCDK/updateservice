/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.validate;

import dk.dbc.updateservice.dto.SchemaDTO;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.ws.JNDIResources;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;

@Stateless
public class Validator {
    private static final XLogger logger = XLoggerFactory.getXLogger(Validator.class);

    @EJB
    private Scripter scripter;

    private Properties settings = JNDIResources.getProperties();

    public List<SchemaDTO> getValidateSchemas(String templateGroup, Set<String> allowedLibraryRules) throws ScripterException {
        logger.entry();
        List<SchemaDTO> result = null;
        try {
            result = new ArrayList<>();
            ObjectMapper mapper = new ObjectMapper();
            Object jsResult = scripter.callMethod("getValidateSchemas", templateGroup, allowedLibraryRules, settings);
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
