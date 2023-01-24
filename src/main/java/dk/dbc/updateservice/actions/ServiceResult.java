/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.updateservice.dto.DoubleRecordFrontendDTO;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.TypeEnumDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines the result from a ServiceAction.
 * <p/>
 * The purpose of this class is to collect results from ServiceAction's so it
 * is possible to construct a valid webservice response for the updateRecord()
 * web service operation.
 * <p/>
 * Data entries:
 * <ol>
 * <li>Status</li>
 * <li>Service entries</li>
 * </ol>
 */
public class ServiceResult {
    private UpdateStatusEnumDTO status = UpdateStatusEnumDTO.OK;
    private List<MessageEntryDTO> entries = null;
    private List<DoubleRecordFrontendDTO> doubleRecordFrontendDTOS = null;
    private String doubleRecordKey = null;

    public UpdateStatusEnumDTO getStatus() {
        return status;
    }

    public void setStatus(UpdateStatusEnumDTO status) {
        this.status = status;
    }

    public List<MessageEntryDTO> getEntries() {
        return entries;
    }

    public void setEntries(List<MessageEntryDTO> messageEntryDTOS) {
        this.entries = messageEntryDTOS;
    }

    public List<DoubleRecordFrontendDTO> getDoubleRecordFrontendDTOS() {
        return doubleRecordFrontendDTOS;
    }

    public String getDoubleRecordKey() {
        return doubleRecordKey;
    }

    public void setDoubleRecordKey(String doubleRecordKey) {
        this.doubleRecordKey = doubleRecordKey;
    }

    public void addServiceResult(ServiceResult serviceResult) {
        if (serviceResult != null) {
            addMessageEntryDtos(serviceResult);
            addDoubleRecordFrontendDtos(serviceResult);
            if (serviceResult.getDoubleRecordKey() != null) {
                doubleRecordKey = serviceResult.getDoubleRecordKey();
            }
            calculateAndAddUpdateStatusEnumDtoValue(serviceResult.getStatus());
        }
    }

    private void calculateAndAddUpdateStatusEnumDtoValue(UpdateStatusEnumDTO updateStatusEnumDTO) {
        if (updateStatusEnumDTO != UpdateStatusEnumDTO.OK) {
            status = updateStatusEnumDTO;
        }
    }


    private void addMessageEntryDtos(ServiceResult serviceResult) {
        if (serviceResult != null) {
            addMessageEntryDtos(serviceResult.getEntries());
        }
    }

    private void addMessageEntryDto(MessageEntryDTO messageEntryDTO) {
        if (messageEntryDTO != null) {
            if (entries == null) {
                entries = new ArrayList<>();
            }
            entries.add(messageEntryDTO);
        }
    }

    public void addMessageEntryDtos(List<MessageEntryDTO> messageEntryDTOS) {
        if (messageEntryDTOS != null && !messageEntryDTOS.isEmpty()) {
            if (entries == null) {
                entries = new ArrayList<>();
            }
            entries.addAll(messageEntryDTOS);
        }
    }

    private void addDoubleRecordFrontendDto(DoubleRecordFrontendDTO doubleRecordFrontendDTO) {
        if (doubleRecordFrontendDTO != null) {
            if (doubleRecordFrontendDTOS == null) {
                doubleRecordFrontendDTOS = new ArrayList<>();
            }
            doubleRecordFrontendDTOS.add(doubleRecordFrontendDTO);
        }
    }

    private void addDoubleRecordFrontendDtos(ServiceResult serviceResult) {
        if (serviceResult != null) {
            addDoubleRecordFrontendDtos(serviceResult.getDoubleRecordFrontendDTOS());
        }
    }

    private void addDoubleRecordFrontendDtos(List<DoubleRecordFrontendDTO> doubleRecordFrontendDTOS) {
        if (doubleRecordFrontendDTOS != null && !doubleRecordFrontendDTOS.isEmpty()) {
            if (this.doubleRecordFrontendDTOS == null) {
                this.doubleRecordFrontendDTOS = new ArrayList<>();
            }
            this.doubleRecordFrontendDTOS.addAll(doubleRecordFrontendDTOS);
        }
    }

    public List<MessageEntryDTO> getServiceErrorList() {
        List<MessageEntryDTO> entryErrors = null;
        if (entries != null) {
            for (MessageEntryDTO entry : entries) {
                if (entry.getType() == TypeEnumDTO.ERROR) {
                    if (entryErrors == null) {
                        entryErrors = new ArrayList<>();
                    }
                    entryErrors.add(entry);
                }
            }
        }
        return entryErrors;
    }

    public boolean hasErrors() {
        boolean res = false;
        if (entries != null) {
            for (MessageEntryDTO entry : entries) {
                if (entry.getType() == TypeEnumDTO.ERROR) {
                    res = true;
                }
            }
        }
        return res;
    }

    public static ServiceResult newOkResult() {
        ServiceResult serviceResult = new ServiceResult();
        serviceResult.setStatus(UpdateStatusEnumDTO.OK);
        return serviceResult;
    }


    public static ServiceResult newStatusResult(UpdateStatusEnumDTO status) {
        ServiceResult serviceResult = new ServiceResult();
        serviceResult.setStatus(status);
        return serviceResult;
    }

    public static ServiceResult newAuthErrorResult() {
        return newEntryResult(UpdateStatusEnumDTO.FAILED, TypeEnumDTO.ERROR, "Authentication error");
    }

    public static ServiceResult newAuthErrorResult(String message) {
        return newEntryResult(UpdateStatusEnumDTO.FAILED, TypeEnumDTO.ERROR, message);
    }

    public static ServiceResult newErrorResult(UpdateStatusEnumDTO status, String message) {
        return newEntryResult(status, TypeEnumDTO.ERROR, message);
    }

    public static ServiceResult newFatalResult(UpdateStatusEnumDTO status, String message) {
        return newEntryResult(status, TypeEnumDTO.FATAL, message);
    }

    public static ServiceResult newWarningResult(UpdateStatusEnumDTO status, String message) {
        return newEntryResult(status, TypeEnumDTO.WARNING, message);
    }

    public static ServiceResult newEntryResult(UpdateStatusEnumDTO status, TypeEnumDTO type, String message) {
        ServiceResult serviceResult = new ServiceResult();
        serviceResult.setStatus(status);
        MessageEntryDTO messageEntryDTO = new MessageEntryDTO();
        serviceResult.addMessageEntryDto(messageEntryDTO);
        messageEntryDTO.setType(type);
        messageEntryDTO.setMessage(message);
        return serviceResult;
    }

    public static ServiceResult newDoubleRecordErrorResult(UpdateStatusEnumDTO status, DoubleRecordFrontendDTO doubleRecordFrontendDTO) {
        ServiceResult serviceResult = new ServiceResult();
        serviceResult.setStatus(status);
        serviceResult.addDoubleRecordFrontendDto(doubleRecordFrontendDTO);
        return serviceResult;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ServiceResult that = (ServiceResult) o;

        if (status != that.status) return false;
        if (entries != null ? !entries.equals(that.entries) : that.entries != null) return false;
        if (doubleRecordFrontendDTOS != null ? !doubleRecordFrontendDTOS.equals(that.doubleRecordFrontendDTOS) : that.doubleRecordFrontendDTOS != null)
            return false;
        return doubleRecordKey != null ? doubleRecordKey.equals(that.doubleRecordKey) : that.doubleRecordKey == null;
    }

    @Override
    public int hashCode() {
        int result = status != null ? status.hashCode() : 0;
        result = 31 * result + (entries != null ? entries.hashCode() : 0);
        result = 31 * result + (doubleRecordFrontendDTOS != null ? doubleRecordFrontendDTOS.hashCode() : 0);
        result = 31 * result + (doubleRecordKey != null ? doubleRecordKey.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ServiceResult{" +
                "status=" + status +
                ", entries=" + entries +
                ", doubleRecordFrontendDTOS=" + doubleRecordFrontendDTOS +
                ", doubleRecordKey='" + doubleRecordKey + '\'' +
                '}';
    }
}
