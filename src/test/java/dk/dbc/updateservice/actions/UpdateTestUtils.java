package dk.dbc.updateservice.actions;

import dk.dbc.holdingitems.content.HoldingsItemsConnector;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.opencat.connector.OpencatBusinessConnector;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.dto.AuthenticationDTO;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.TypeEnumDTO;
import dk.dbc.updateservice.dto.UpdateServiceRequestDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.solr.SolrFBS;
import dk.dbc.updateservice.update.JNDIResources;
import dk.dbc.updateservice.update.LibraryGroup;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.NoteAndSubjectExtensionsHandler;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.RecordSorter;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.update.UpdateStore;
import dk.dbc.updateservice.update.VipCoreService;
import dk.dbc.updateservice.utils.ResourceBundles;
import dk.dbc.updateservice.validate.Validator;

import java.io.IOException;
import java.util.ArrayList;
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

    public GlobalActionState getGlobalActionStateMockObject() throws IOException, UpdateException {
        // If the object isn't initialized as a null string it can't figure out which of the overloaded functions to call
        return getGlobalActionStateMockObject((String) null);
    }

    public GlobalActionState getGlobalActionStateMockObject(String marcRecordName) throws IOException, UpdateException {
        GlobalActionState globalActionState = new GlobalActionState();
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        AuthenticationDTO AuthenticationDTO = new AuthenticationDTO();
        updateServiceRequestDTO.setAuthenticationDTO(AuthenticationDTO);
        AuthenticationDTO.setGroupId(GROUP_ID);
        AuthenticationDTO.setUserId(USER_ID);
        AuthenticationDTO.setPassword("passwd");
        globalActionState.setUpdateServiceRequestDTO(updateServiceRequestDTO);
        globalActionState.setAuthenticator(mock(Authenticator.class));
        globalActionState.setHoldingsItems(mock(HoldingsItemsConnector.class));
        globalActionState.setSolrService(mock(SolrFBS.class));
        globalActionState.setOpencatBusiness(mock(OpencatBusinessConnector.class));
        globalActionState.setMessages(ResourceBundles.getBundle("actions"));
        globalActionState.setRawRepo(mock(RawRepo.class));
        globalActionState.setVipCoreService(mock(VipCoreService.class));
        globalActionState.setValidator(mock(Validator.class));
        globalActionState.setUpdateStore(mock(UpdateStore.class));
        globalActionState.setLibraryRecordsHandler(mock(LibraryRecordsHandler.class));
        globalActionState.setRecordSorter(new RecordSorterMock());
        globalActionState.setNoteAndSubjectExtensionsHandler(mock(NoteAndSubjectExtensionsHandler.class));
        if (marcRecordName != null) {
            globalActionState.setMarcRecord(AssertActionsUtil.loadRecord(marcRecordName));
        }
        // You have to change this is in the actual test if anything other than fbs is needed
        globalActionState.setLibraryGroup(LibraryGroup.FBS);
        return globalActionState;
    }

    public GlobalActionState getGlobalActionStateMockObject(UpdateServiceRequestDTO updateServiceRequestDTO) {
        GlobalActionState globalActionState = new GlobalActionState();
        globalActionState.setUpdateServiceRequestDTO(updateServiceRequestDTO);
        globalActionState.setAuthenticator(mock(Authenticator.class));
        globalActionState.setHoldingsItems(mock(HoldingsItemsConnector.class));
        globalActionState.setSolrService(mock(SolrFBS.class));
        globalActionState.setMessages(ResourceBundles.getBundle("actions"));
        globalActionState.setRawRepo(mock(RawRepo.class));
        globalActionState.setVipCoreService(mock(VipCoreService.class));
        globalActionState.setValidator(mock(Validator.class));
        globalActionState.setUpdateStore(mock(UpdateStore.class));
        globalActionState.setLibraryRecordsHandler(mock(LibraryRecordsHandler.class));
        globalActionState.setRecordSorter(new RecordSorterMock());
        globalActionState.setNoteAndSubjectExtensionsHandler(mock(NoteAndSubjectExtensionsHandler.class));
        // You have to change this is in the actual test if anything other than fbs is needed
        globalActionState.setLibraryGroup(LibraryGroup.FBS);
        return globalActionState;
    }

    public Properties getSettings() {
        Properties settings = new Properties();
        settings.put(JNDIResources.RAWREPO_PROVIDER_ID_DBC, "RAWREPO_PROVIDER_ID_DBC");
        settings.put(JNDIResources.RAWREPO_PROVIDER_ID_FBS, "RAWREPO_PROVIDER_ID_FBS");
        settings.put(JNDIResources.RAWREPO_PROVIDER_ID_PH, "RAWREPO_PROVIDER_ID_PH");
        settings.put(JNDIResources.RAWREPO_PROVIDER_ID_PH_HOLDINGS, "RAWREPO_PROVIDER_ID_PH_HOLDINGS");
        settings.put(JNDIResources.UPDATE_PROD_STATE, "true");
        settings.put(JNDIResources.DBC_OVERWRITE_AGENCIES, "010100;700300");
        return settings;
    }

    // This record sorter is only able to sort the fields by field name, and not also sort subfields
    // The sorting is needed because expand function might change the order of the fields
    private static class RecordSorterMock extends RecordSorter {
        @Override
        public MarcRecord sortRecord(MarcRecord marcRecord) {
            marcRecord.getFields().sort(Comparator.comparingInt(f -> Integer.parseInt(f.getTag())));

            return marcRecord;
        }
    }

}
