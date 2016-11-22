package dk.dbc.updateservice.ws;

import dk.dbc.updateservice.dto.AuthenticationDto;
import dk.dbc.updateservice.dto.BibliographicRecordDto;
import dk.dbc.updateservice.dto.ExtraRecordDataDto;
import dk.dbc.updateservice.dto.OptionEnumDto;
import dk.dbc.updateservice.dto.OptionsDto;
import dk.dbc.updateservice.dto.RecordDataDto;
import dk.dbc.updateservice.dto.UpdateServiceRequestDto;
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
        UpdateServiceRequestDto res = null;
        if (updateRecordRequest != null) {
            res = new UpdateServiceRequestDto();
            res.setDoubleRecordKey(updateRecordRequest.getDoubleRecordKey());
            res.setSchemaName(updateRecordRequest.getSchemaName());
            res.setTrackingId(updateRecordRequest.getTrackingId());
            AuthenticationDto authenticationDto = convertExternalAuthenticationToInternalAuthenticationDto(updateRecordRequest.getAuthentication());
            res.setAuthenticationDto(authenticationDto);
            OptionsDto optionsDto = convertExternalOptionsToInternalOptionsDto(updateRecordRequest);
            res.setOptionsDto(optionsDto);
            BibliographicRecordDto bibliographicRecordDto = convertExternalBibliographicRecordToInternalBibliographicRecordDto(updateRecordRequest);
            res.setBibliographicRecordDto(bibliographicRecordDto);
        }
        return res;
    }

    private BibliographicRecordDto convertExternalBibliographicRecordToInternalBibliographicRecordDto(UpdateRecordRequest updateRecordRequest) {
        BibliographicRecordDto res = null;
        if (updateRecordRequest != null && updateRecordRequest.getBibliographicRecord() != null) {
            res = convertExternalBibliographicRecordToInternalBibliographicRecordDto(updateRecordRequest.getBibliographicRecord());
        }
        return res;
    }

    public static BibliographicRecordDto convertExternalBibliographicRecordToInternalBibliographicRecordDto(BibliographicRecord bibliographicRecord) {
        BibliographicRecordDto res = null;
        if (bibliographicRecord != null) {
            res = new BibliographicRecordDto();
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
