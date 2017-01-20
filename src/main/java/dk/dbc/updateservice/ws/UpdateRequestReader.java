package dk.dbc.updateservice.ws;

import dk.dbc.updateservice.dto.*;
import dk.dbc.updateservice.dto.BibliographicRecordDTO;
import dk.dbc.updateservice.service.api.Authentication;
import dk.dbc.updateservice.service.api.BibliographicRecord;
import dk.dbc.updateservice.service.api.UpdateOptionEnum;
import dk.dbc.updateservice.service.api.UpdateRecordRequest;

/**
 * Helper class to read the contents of an DBC UpdateRecordRequest.
 * <p>
 * The UpdateRecordRequest contains the arguments of an updateRecord
 * request of the web service UpdateService#updateRecord(UpdateRecordRequest).
 * <p>
 * This class provides helper functions to read information from the request
 * and will include checks to ensure the information is valid.
 */
public class UpdateRequestReader extends CommonReader {
    private UpdateServiceRequestDto updateServiceRequestDto;

    public UpdateRequestReader(UpdateRecordRequest updateRecordRequest) {
        updateServiceRequestDto = convertRequestFromExternalFormatToInternalFormat(updateRecordRequest);
    }

    public UpdateServiceRequestDto getUpdateServiceRequestDto() {
        return updateServiceRequestDto;
    }

    public static UpdateRecordRequest cloneWithoutPassword(UpdateRecordRequest updateRecordRequest) {
        UpdateRecordRequest res = null;
        if (updateRecordRequest != null) {
            res = new UpdateRecordRequest();
            if (updateRecordRequest.getAuthentication() != null) {
                res.setAuthentication(new Authentication());
                res.getAuthentication().setGroupIdAut(updateRecordRequest.getAuthentication().getGroupIdAut());
                res.getAuthentication().setPasswordAut("***");
                res.getAuthentication().setUserIdAut(updateRecordRequest.getAuthentication().getUserIdAut());
            }
            res.setBibliographicRecord(updateRecordRequest.getBibliographicRecord());
            res.setDoubleRecordKey(updateRecordRequest.getDoubleRecordKey());
            res.setOptions(updateRecordRequest.getOptions());
            res.setSchemaName(updateRecordRequest.getSchemaName());
            res.setTrackingId(updateRecordRequest.getTrackingId());
        }
        return res;
    }

    private UpdateServiceRequestDto convertRequestFromExternalFormatToInternalFormat(UpdateRecordRequest updateRecordRequest) {
        UpdateServiceRequestDto updateServiceRequestDto = null;
        if (updateRecordRequest != null) {
            updateServiceRequestDto = new UpdateServiceRequestDto();
            updateServiceRequestDto.setDoubleRecordKey(updateRecordRequest.getDoubleRecordKey());
            updateServiceRequestDto.setSchemaName(updateRecordRequest.getSchemaName());
            updateServiceRequestDto.setTrackingId(updateRecordRequest.getTrackingId());
            AuthenticationDTO authenticationDTO = convertExternalAuthenticationToInternalAuthenticationDto(updateRecordRequest.getAuthentication());
            updateServiceRequestDto.setAuthenticationDTO(authenticationDTO);
            OptionsDto optionsDto = convertExternalOptionsToInternalOptionsDto(updateRecordRequest);
            updateServiceRequestDto.setOptionsDto(optionsDto);
            BibliographicRecordDTO bibliographicRecordDTO = convertExternalBibliographicRecordToInternalBibliographicRecordDto(updateRecordRequest);
            updateServiceRequestDto.setBibliographicRecordDTO(bibliographicRecordDTO);
        }
        return updateServiceRequestDto;
    }

    private BibliographicRecordDTO convertExternalBibliographicRecordToInternalBibliographicRecordDto(UpdateRecordRequest updateRecordRequest) {
        BibliographicRecordDTO res = null;
        if (updateRecordRequest != null && updateRecordRequest.getBibliographicRecord() != null) {
            res = convertExternalBibliographicRecordToInternalBibliographicRecordDto(updateRecordRequest.getBibliographicRecord());
        }
        return res;
    }

    public static BibliographicRecordDTO convertExternalBibliographicRecordToInternalBibliographicRecordDto(BibliographicRecord bibliographicRecord) {
        BibliographicRecordDTO res = null;
        if (bibliographicRecord != null) {
            res = new BibliographicRecordDTO();
            res.setRecordPacking(bibliographicRecord.getRecordPacking());
            res.setRecordSchema(bibliographicRecord.getRecordSchema());
            if (bibliographicRecord.getRecordData() != null) {
                RecordDataDto recordDataDto = new RecordDataDto();
                res.setRecordDataDto(recordDataDto);
                recordDataDto.setContent(bibliographicRecord.getRecordData().getContent());
            }
            if (bibliographicRecord.getExtraRecordData() != null) {
                ExtraRecordDataDto extraRecordDataDto = new ExtraRecordDataDto();
                res.setExtraRecordDataDto(extraRecordDataDto);
                extraRecordDataDto.setContent(bibliographicRecord.getExtraRecordData().getContent());
            }
        }
        return res;
    }

    private OptionsDto convertExternalOptionsToInternalOptionsDto(UpdateRecordRequest updateRecordRequest) {
        OptionsDto res = null;
        if (updateRecordRequest != null && updateRecordRequest.getOptions() != null) {
            res = new OptionsDto();
            for (UpdateOptionEnum uoe : updateRecordRequest.getOptions().getOption()) {
                res.getOption().add(OptionEnumDto.fromValue(uoe.value()));
            }
        }
        return res;
    }
}
