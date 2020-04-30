package dk.dbc.updateservice.ws;

import dk.dbc.oss.ns.catalogingbuild.BibliographicRecord;
import dk.dbc.oss.ns.catalogingbuild.BuildResult;
import dk.dbc.oss.ns.catalogingbuild.BuildStatusEnum;
import dk.dbc.oss.ns.catalogingbuild.ExtraRecordData;
import dk.dbc.oss.ns.catalogingbuild.RecordData;
import dk.dbc.updateservice.dto.BibliographicRecordDTO;
import dk.dbc.updateservice.dto.BuildResponseDTO;
import dk.dbc.updateservice.dto.BuildStatusEnumDTO;

public class OpenBuildResultWriter {

    public static BuildResult get(BuildResponseDTO buildResponseDTO) {
        BuildResult buildResult = new BuildResult();
        buildResult.setBuildStatus(get(buildResponseDTO.getBuildStatusEnumDTO()));
        if (buildResponseDTO.getBuildStatusEnumDTO() == BuildStatusEnumDTO.OK) {
            BibliographicRecordDTO bibliographicRecordDTO = buildResponseDTO.getBibliographicRecordDTO();
            BibliographicRecord bibliographicRecord = new BibliographicRecord();

            RecordData recordData = new RecordData();
            recordData.getContent().addAll(bibliographicRecordDTO.getRecordDataDTO().getContent());
            bibliographicRecord.setRecordData(recordData);

            if (bibliographicRecordDTO.getExtraRecordDataDTO() != null) {
                ExtraRecordData extraRecordData = new ExtraRecordData();
                extraRecordData.getContent().addAll(bibliographicRecordDTO.getExtraRecordDataDTO().getContent());
                bibliographicRecord.setExtraRecordData(extraRecordData);
            }
            bibliographicRecord.setRecordPacking(bibliographicRecordDTO.getRecordPacking());
            bibliographicRecord.setRecordSchema(bibliographicRecordDTO.getRecordSchema());

            buildResult.setBibliographicRecord(bibliographicRecord);
        }
        return buildResult;
    }

    private static BuildStatusEnum get(BuildStatusEnumDTO buildStatusEnumDTO) {
        switch (buildStatusEnumDTO) {
            case OK: return BuildStatusEnum.OK;
            case FAILED_INTERNAL_ERROR: return BuildStatusEnum.FAILED_INTERNAL_ERROR;
            case FAILED_INVALID_SCHEMA: return BuildStatusEnum.FAILED_INVALID_SCHEMA;
            case FAILED_INVALID_RECORD_SCHEMA: return BuildStatusEnum.FAILED_INVALID_RECORD_SCHEMA;
            case FAILED_UPDATE_INTERNAL_ERROR: return BuildStatusEnum.FAILED_UPDATE_INTERNAL_ERROR;
            case FAILED_INVALID_RECORD_PACKING: return BuildStatusEnum.FAILED_INVALID_RECORD_PACKING;
        }
        return null;
    }

}
