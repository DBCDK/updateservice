/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.updateservice.solr.SolrFBS;
import dk.dbc.updateservice.utils.ResourceBundles;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.dto.AuthenticationDTO;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.TypeEnumDTO;
import dk.dbc.updateservice.dto.UpdateServiceRequestDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.NoteAndSubjectExtensionsHandler;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.RecordSorter;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.update.UpdateStore;
import dk.dbc.updateservice.validate.Validator;
import dk.dbc.updateservice.ws.JNDIResources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import static org.mockito.Mockito.mock;

public class UpdateTestUtils {
    public static String GROUP_ID = "700100";
    public static String USER_ID = "netpunkt";

    public static List<MessageEntryDTO> createMessageEntryList(TypeEnumDTO typeEnumDTO, String message) {
        List<MessageEntryDTO> messageEntryDTOS = new ArrayList<>();
        MessageEntryDTO messageEntryDTO = new MessageEntryDTO();
        messageEntryDTOS.add(messageEntryDTO);
        messageEntryDTO.setType(typeEnumDTO);
        messageEntryDTO.setMessage(message);
        return messageEntryDTOS;
    }

    public static ServiceResult createFailedServiceResult(String message) {
        ServiceResult expected = new ServiceResult();
        expected.setStatus(UpdateStatusEnumDTO.FAILED);

        List<MessageEntryDTO> messageEntryDTOList = new ArrayList<>();

        MessageEntryDTO MessageEntryDTO = new MessageEntryDTO();
        MessageEntryDTO.setMessage(message);
        MessageEntryDTO.setType(TypeEnumDTO.ERROR);

        messageEntryDTOList.add(MessageEntryDTO);

        expected.addMessageEntryDtos(messageEntryDTOList);

        return expected;
    }

    public GlobalActionState getGlobalActionStateMockObject() throws IOException {
        return getGlobalActionStateMockObject(null);
    }


    public GlobalActionState getGlobalActionStateMockObject(String marcRecordName) throws IOException {
        GlobalActionState globalActionState = new GlobalActionState();
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        AuthenticationDTO AuthenticationDTO = new AuthenticationDTO();
        updateServiceRequestDTO.setAuthenticationDTO(AuthenticationDTO);
        AuthenticationDTO.setGroupId(GROUP_ID);
        AuthenticationDTO.setUserId(USER_ID);
        AuthenticationDTO.setPassword("passwd");
        globalActionState.setUpdateServiceRequestDTO(updateServiceRequestDTO);
        globalActionState.setAuthenticator(mock(Authenticator.class));
        globalActionState.setHoldingsItems(mock(HoldingsItems.class));
        globalActionState.setScripter(mock(Scripter.class));
        globalActionState.setSolrService(mock(SolrFBS.class));
        globalActionState.setMessages(ResourceBundles.getBundle("actions"));
        globalActionState.setRawRepo(mock(RawRepo.class));
        globalActionState.setOpenAgencyService(mock(OpenAgencyService.class));
        globalActionState.setValidator(mock(Validator.class));
        globalActionState.setUpdateStore(mock(UpdateStore.class));
        globalActionState.setLibraryRecordsHandler(mock(LibraryRecordsHandler.class));
        globalActionState.setRecordSorter(new RecordSorterMock());
        globalActionState.setNoteAndSubjectExtensionsHandler(mock(NoteAndSubjectExtensionsHandler.class));
        if (marcRecordName != null) {
            globalActionState.setMarcRecord(AssertActionsUtil.loadRecord(marcRecordName));
        }
        // You have to change this is in the actual test if anything other than fbs is needed
        globalActionState.setLibraryGroup(OpenAgencyService.LibraryGroup.FBS);
        return globalActionState;
    }

    public Properties getSettings() {
        Properties settings = new Properties();
        settings.put(JNDIResources.RAWREPO_PROVIDER_ID_DBC, "RAWREPO_PROVIDER_ID_DBC");
        settings.put(JNDIResources.RAWREPO_PROVIDER_ID_FBS, "RAWREPO_PROVIDER_ID_FBS");
        settings.put(JNDIResources.RAWREPO_PROVIDER_ID_PH, "RAWREPO_PROVIDER_ID_PH");
        settings.put(JNDIResources.RAWREPO_PROVIDER_ID_PH_HOLDINGS, "RAWREPO_PROVIDER_ID_PH_HOLDINGS");
        settings.put(JNDIResources.UPDATE_PROD_STATE_KEY, "true");
        return settings;
    }

    // This record sorter is only able to sort the fields by field name, and not also sort subfields
    // The sorting is needed because expand function might change the order of the fields
    private class RecordSorterMock extends RecordSorter {
        @Override
        public MarcRecord sortRecord(MarcRecord record, Properties properties) throws UpdateException {

            Collections.sort(record.getFields(), new Comparator<MarcField>() {
                @Override
                public int compare(MarcField f1, MarcField f2) {
                    return Integer.parseInt(f1.getName()) - Integer.parseInt(f2.getName());
                }
            });

            return record;
        }
    }

}
