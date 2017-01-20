package dk.dbc.updateservice.ws;

import dk.dbc.updateservice.dto.SchemaDTO;
import dk.dbc.updateservice.dto.SchemasResponseDTO;
import dk.dbc.updateservice.service.api.GetSchemasResult;
import dk.dbc.updateservice.service.api.MessageEntry;
import dk.dbc.updateservice.service.api.Messages;
import dk.dbc.updateservice.service.api.Schema;
import dk.dbc.updateservice.service.api.Type;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;

public class GetSchemasResponseWriter {
    private GetSchemasResult getSchemasResult;

    public GetSchemasResponseWriter(SchemasResponseDTO schemasResponseDTO) {
        getSchemasResult = convertResponseFromInternalFormatToExternalFormat(schemasResponseDTO);
    }

    public GetSchemasResult getGetSchemasResult() {
        return getSchemasResult;
    }

    @SuppressWarnings("Duplicates")
    private GetSchemasResult convertResponseFromInternalFormatToExternalFormat(SchemasResponseDTO schemasResponseDTO) {
        getSchemasResult = new GetSchemasResult();
        if (schemasResponseDTO != null) {
            if (schemasResponseDTO.isError()) {
                getSchemasResult.setUpdateStatus(UpdateStatusEnum.FAILED);
                Messages messages = new Messages();
                MessageEntry messageEntry = new MessageEntry();
                messages.getMessageEntry().add(messageEntry);
                messageEntry.setType(Type.ERROR);
                messageEntry.setMessage(schemasResponseDTO.getErrorMessage());
                getGetSchemasResult().setMessages(messages);
            } else {
                getSchemasResult.setUpdateStatus(UpdateStatusEnum.OK);
                Schema schema;
                for (SchemaDTO schemaDTO : schemasResponseDTO.getSchemaDTOList()) {
                    schema = new Schema();
                    schema.setSchemaInfo(schemaDTO.getSchemaInfo());
                    schema.setSchemaName(schemaDTO.getSchemaName());
                    getSchemasResult.getSchema().add(schema);
                }
            }
        }
        return getSchemasResult;
    }
}
