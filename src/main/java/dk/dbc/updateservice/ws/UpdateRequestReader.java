/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.ws;

import dk.dbc.updateservice.dto.*;
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
    private UpdateServiceRequestDTO updateServiceRequestDTO;

    public UpdateRequestReader(UpdateRecordRequest updateRecordRequest) {
        updateServiceRequestDTO = convertRequestFromExternalFormatToInternalFormat(updateRecordRequest);
    }

    public UpdateServiceRequestDTO getUpdateServiceRequestDTO() {
        return updateServiceRequestDTO;
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

    private UpdateServiceRequestDTO convertRequestFromExternalFormatToInternalFormat(UpdateRecordRequest updateRecordRequest) {
        UpdateServiceRequestDTO updateServiceRequestDTO = null;
        if (updateRecordRequest != null) {
            updateServiceRequestDTO = new UpdateServiceRequestDTO();
            updateServiceRequestDTO.setDoubleRecordKey(updateRecordRequest.getDoubleRecordKey());
            updateServiceRequestDTO.setSchemaName(updateRecordRequest.getSchemaName());
            updateServiceRequestDTO.setTrackingId(updateRecordRequest.getTrackingId());
            AuthenticationDTO authenticationDTO = convertExternalAuthenticationToInternalAuthenticationDto(updateRecordRequest.getAuthentication());
            updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
            OptionsDTO optionsDTO = convertExternalOptionsToInternalOptionsDto(updateRecordRequest);
            updateServiceRequestDTO.setOptionsDTO(optionsDTO);
            BibliographicRecordDTO bibliographicRecordDTO = convertExternalBibliographicRecordToInternalBibliographicRecordDto(updateRecordRequest);
            updateServiceRequestDTO.setBibliographicRecordDTO(bibliographicRecordDTO);
        }
        return updateServiceRequestDTO;
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

    private OptionsDTO convertExternalOptionsToInternalOptionsDto(UpdateRecordRequest updateRecordRequest) {
        OptionsDTO res = null;
        if (updateRecordRequest != null && updateRecordRequest.getOptions() != null) {
            res = new OptionsDTO();
            for (UpdateOptionEnum uoe : updateRecordRequest.getOptions().getOption()) {
                res.getOption().add(OptionEnumDTO.fromValue(uoe.value()));
            }
        }
        return res;
    }
}
