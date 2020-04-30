package dk.dbc.updateservice.ws;

import dk.dbc.oss.ns.catalogingbuild.BibliographicRecord;
import dk.dbc.oss.ns.catalogingbuild.BuildRequest;
import dk.dbc.updateservice.dto.BibliographicRecordDTO;
import dk.dbc.updateservice.dto.BuildRequestDTO;
import dk.dbc.updateservice.dto.ExtraRecordDataDTO;
import dk.dbc.updateservice.dto.RecordDataDTO;

public class OpenBuildRequestReader {
    static public BuildRequestDTO getDTO(BuildRequest buildRequest) {
        BuildRequestDTO buildRequestDTO = new BuildRequestDTO();
        buildRequestDTO.setSchemaName(buildRequest.getSchemaName());
        buildRequestDTO.setBibliographicRecordDTO(getDTO(buildRequest.getBibliographicRecord()));
        buildRequestDTO.setTrackingId(buildRequest.getTrackingId());
        return buildRequestDTO;
    }

    static private BibliographicRecordDTO getDTO(BibliographicRecord bibliographicRecord) {
        BibliographicRecordDTO res = null;
        if (bibliographicRecord != null) {
            res = new BibliographicRecordDTO();
            res.setRecordPacking(bibliographicRecord.getRecordPacking());
            res.setRecordSchema(bibliographicRecord.getRecordSchema());
            if (bibliographicRecord.getRecordData() != null) {
                RecordDataDTO recordDataDTO = new RecordDataDTO();
                res.setRecordDataDTO(recordDataDTO);
                recordDataDTO.setContent(bibliographicRecord.getRecordData().getContent());
            }
            if (bibliographicRecord.getExtraRecordData() != null) {
                ExtraRecordDataDTO extraRecordDataDTO = new ExtraRecordDataDTO();
                res.setExtraRecordDataDTO(extraRecordDataDTO);
                extraRecordDataDTO.setContent(bibliographicRecord.getExtraRecordData().getContent());
            }
        }
        return res;
    }
}
