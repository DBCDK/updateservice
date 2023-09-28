package dk.dbc.updateservice.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.rawrepo.dto.RecordEntryDTO;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.update.UpdateRecordContentTransformer;

import java.time.Instant;

public class RecordDTOMapper {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void toRecord(RecordEntryDTO dto, Record rawRecord) throws JsonProcessingException, UpdateException {
        final MarcRecord marcRecord = getMarcRecord(dto);
        rawRecord.setContent(UpdateRecordContentTransformer.encodeRecord(marcRecord));
        rawRecord.setContentJson(UpdateRecordContentTransformer.encodeRecordToJson(marcRecord));
        if (rawRecord.isOriginal()) {
            rawRecord.setCreated(Instant.parse(dto.getCreated()));
        }
        rawRecord.setDeleted(dto.isDeleted());
        rawRecord.setMimeType(dto.getMimetype());
        rawRecord.setTrackingId(dto.getTrackingId());
        rawRecord.setModified(Instant.parse(dto.getModified())); // TrackingId must be updated last, as the other set-methods updates the value
    }

    public static MarcRecord getMarcRecord(RecordEntryDTO dto) throws JsonProcessingException {
        return objectMapper.treeToValue(dto.getContent(), MarcRecord.class);
    }

    public static RecordId getRecordId(RecordEntryDTO dto) {
        return new RecordId(dto.getRecordId().getBibliographicRecordId(), dto.getRecordId().getAgencyId());
    }
}
