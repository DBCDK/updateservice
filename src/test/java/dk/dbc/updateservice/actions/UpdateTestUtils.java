package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.service.api.Authentication;
import dk.dbc.updateservice.service.api.Entry;
import dk.dbc.updateservice.service.api.Param;
import dk.dbc.updateservice.service.api.Params;
import dk.dbc.updateservice.service.api.Type;
import dk.dbc.updateservice.service.api.UpdateRecordRequest;
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

    public static List<Entry> createEntryList(Type type, String message) {
        List<Entry> entryList = new ArrayList<>();
        Entry entry = new Entry();
        entryList.add(entry);
        entry.setType(type);
        Params params = new Params();
        Param param = new Param();
        param.setKey("message");
        param.setValue(message);
        params.getParam().add(param);
        entry.setParams(params);
        return entryList;
    }

    public GlobalActionState getGlobalActionStateMockObject() throws IOException {
        return getGlobalActionStateMockObject(null);
    }

    public GlobalActionState getGlobalActionStateMockObject(String marcRecordName) throws IOException {
        GlobalActionState globalActionState = new GlobalActionState();
        UpdateRecordRequest updateRecordRequest = new UpdateRecordRequest();
        Authentication authentication = new Authentication();
        updateRecordRequest.setAuthentication(authentication);
        authentication.setGroupIdAut(GROUP_ID);
        authentication.setUserIdAut(USER_ID);
        authentication.setPasswordAut("passwd");
        globalActionState.setUpdateRecordRequest(updateRecordRequest);
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
