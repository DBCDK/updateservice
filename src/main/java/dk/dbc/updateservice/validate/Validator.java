/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.validate;

import dk.dbc.jsonb.JSONBException;
import dk.dbc.opencat.connector.OpencatBusinessConnector;
import dk.dbc.opencat.connector.OpencatBusinessConnectorException;
import dk.dbc.updateservice.dto.SchemaDTO;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.update.JNDIResources;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Stateless
public class Validator {
    private static final XLogger logger = XLoggerFactory.getXLogger(Validator.class);

    @Inject
    private OpencatBusinessConnector opencatBusinessConnector;

    public List<SchemaDTO> getValidateSchemas(String templateGroup, Set<String> allowedLibraryRules) throws ScripterException {
        logger.entry();
        List<SchemaDTO> result = null;
        try {
            final List<SchemaDTO> names = opencatBusinessConnector.getValidateSchemas(templateGroup, allowedLibraryRules);
            result = new ArrayList<>(names);
            logger.trace("Number of templates: {}", result.size());
            return result;
        } catch (OpencatBusinessConnectorException | JSONBException ex) {
            throw new ScripterException("Error when executing JavaScript function: getValidateSchemas", ex);
        } finally {
            logger.exit(result);
        }
    }
}
