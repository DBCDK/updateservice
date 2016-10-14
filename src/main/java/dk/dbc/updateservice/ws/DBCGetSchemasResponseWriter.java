package dk.dbc.updateservice.ws;

import dk.dbc.updateservice.dto.SchemaDto;
import dk.dbc.updateservice.dto.SchemasResponseDto;
import dk.dbc.updateservice.service.api.GetSchemasResult;
import dk.dbc.updateservice.service.api.MessageEntry;
import dk.dbc.updateservice.service.api.Messages;
import dk.dbc.updateservice.service.api.Schema;
import dk.dbc.updateservice.service.api.Type;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;

public class DBCGetSchemasResponseWriter {
    private GetSchemasResult getSchemasResult;

    public DBCGetSchemasResponseWriter(SchemasResponseDto schemasResponseDto) {
        getSchemasResult = convertResponseFromInternalFormatToExternalFormat(schemasResponseDto);
    }

    public GetSchemasResult getGetSchemasResult() {
        return getSchemasResult;
    }

    @SuppressWarnings("Duplicates")
    private GetSchemasResult convertResponseFromInternalFormatToExternalFormat(SchemasResponseDto schemasResponseDto) {
        getSchemasResult = new GetSchemasResult();
        if (schemasResponseDto != null) {
            if (schemasResponseDto.isError()) {
                getSchemasResult.setUpdateStatus(UpdateStatusEnum.FAILED);
                Messages messages = new Messages();
                MessageEntry messageEntry = new MessageEntry();
                messages.getMessageEntry().add(messageEntry);
                messageEntry.setType(Type.ERROR);
                messageEntry.setMessage(schemasResponseDto.getErrorMessage());
                getGetSchemasResult().setMessages(messages);
            } else {
                getSchemasResult.setUpdateStatus(UpdateStatusEnum.OK);
                Schema schema;
                for (SchemaDto schemaDto : schemasResponseDto.getSchemaDtoList()) {
                    schema = new Schema();
                    schema.setSchemaInfo(schemaDto.getSchemaInfo());
                    schema.setSchemaName(schemaDto.getSchemaName());
                    getSchemasResult.getSchema().add(schema);
                }
            }
        }
        return getSchemasResult;
    }
}
