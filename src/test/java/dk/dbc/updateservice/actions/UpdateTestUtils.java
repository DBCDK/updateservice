package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.dto.AuthenticationDTO;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.TypeEnumDTO;
import dk.dbc.updateservice.dto.UpdateServiceRequestDTO;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrService;
import dk.dbc.updateservice.update.UpdateStore;
import dk.dbc.updateservice.validate.Validator;
import dk.dbc.updateservice.ws.JNDIResources;

import java.io.IOException;
import java.util.ArrayList;
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
        globalActionState.setSolrService(mock(SolrService.class));
        globalActionState.setMessages(ResourceBundles.getBundle("actions"));
        globalActionState.setRawRepo(mock(RawRepo.class));
        globalActionState.setOpenAgencyService(mock(OpenAgencyService.class));
        globalActionState.setValidator(mock(Validator.class));
        globalActionState.setUpdateStore(mock(UpdateStore.class));
        globalActionState.setLibraryRecordsHandler(mock(LibraryRecordsHandler.class));
        globalActionState.setUpdateMode(mock(UpdateMode.class));
        if (marcRecordName != null) {
            globalActionState.setMarcRecord(AssertActionsUtil.loadRecord(marcRecordName));
        }
        return globalActionState;
    }

    public Properties getSettings() {
        Properties settings = new Properties();
        settings.put(JNDIResources.RAWREPO_PROVIDER_ID, "xxx");
        settings.put(JNDIResources.UPDATE_PROD_STATE_KEY, "true");
        return settings;
    }


}
