package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcConverter;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.client.BibliographicRecordExtraData;
import dk.dbc.updateservice.client.BibliographicRecordExtraDataDecoder;
import dk.dbc.updateservice.dto.UpdateServiceRequestDto;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrService;
import dk.dbc.updateservice.update.UpdateStore;
import dk.dbc.updateservice.validate.Validator;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.w3c.dom.Node;

import javax.xml.transform.dom.DOMSource;
import javax.xml.ws.WebServiceContext;
import java.util.List;
import java.util.ResourceBundle;

public class GlobalActionState {
    private static final XLogger logger = XLoggerFactory.getXLogger(GlobalActionState.class);
    public static final String RECORD_SCHEMA_MARCXCHANGE_1_1 = "info:lc/xmlns/marcxchange-v1";
    public static final String RECORD_PACKING_XML = "xml";

    private UpdateServiceRequestDto updateServiceRequestDto = null;
    private WebServiceContext wsContext = null;
    private Authenticator authenticator = null;
    private Scripter scripter = null;
    private RawRepo rawRepo = null;
    private HoldingsItems holdingsItems = null;
    private OpenAgencyService openAgencyService = null;
    private SolrService solrService = null;
    private Validator validator = null;
    private UpdateStore updateStore = null;
    private LibraryRecordsHandler libraryRecordsHandler = null;
    private ResourceBundle messages = null;
    private MarcRecord marcRecord = null;
    private BibliographicRecordExtraData bibliographicRecordExtraData = null;
    private String recordPid = null;
    private UpdateMode updateMode = null;

    public GlobalActionState() {
    }

    public GlobalActionState(UpdateServiceRequestDto updateServiceRequestDto, WebServiceContext wsContext, Authenticator authenticator, Scripter scripter, RawRepo rawRepo, HoldingsItems holdingsItems, OpenAgencyService openAgencyService, SolrService solrService, Validator validator, UpdateStore updateStore, LibraryRecordsHandler libraryRecordsHandler, ResourceBundle messages, UpdateMode updateMode) {
        this.updateServiceRequestDto = updateServiceRequestDto;
        this.wsContext = wsContext;
        this.authenticator = authenticator;
        this.scripter = scripter;
        this.rawRepo = rawRepo;
        this.holdingsItems = holdingsItems;
        this.openAgencyService = openAgencyService;
        this.solrService = solrService;
        this.validator = validator;
        this.updateStore = updateStore;
        this.libraryRecordsHandler = libraryRecordsHandler;
        this.messages = messages;
        this.updateMode = updateMode;
    }

    public GlobalActionState(GlobalActionState globalActionState) {
        this(globalActionState.getUpdateServiceRequestDto(), globalActionState.getWsContext(), globalActionState.getAuthenticator(), globalActionState.getScripter(), globalActionState.getRawRepo(), globalActionState.getHoldingsItems(), globalActionState.getOpenAgencyService(), globalActionState.getSolrService(), globalActionState.getValidator(), globalActionState.getUpdateStore(), globalActionState.getLibraryRecordsHandler(), globalActionState.getMessages(), globalActionState.getUpdateMode());
    }

    private void resetState() {
        recordPid = null;
        marcRecord = null;
        bibliographicRecordExtraData = null;
    }

    public UpdateServiceRequestDto getUpdateServiceRequestDto() {
        return updateServiceRequestDto;
    }

    public void setUpdateServiceRequestDto(UpdateServiceRequestDto updateServiceRequestDto) {
        resetState();
        this.updateServiceRequestDto = updateServiceRequestDto;
    }

    public WebServiceContext getWsContext() {
        return wsContext;
    }

    public void setWsContext(WebServiceContext wsContext) {
        this.wsContext = wsContext;
    }

    public Authenticator getAuthenticator() {
        return authenticator;
    }

    public void setAuthenticator(Authenticator authenticator) {
        this.authenticator = authenticator;
    }

    public Scripter getScripter() {
        return scripter;
    }

    public void setScripter(Scripter scripter) {
        this.scripter = scripter;
    }

    public RawRepo getRawRepo() {
        return rawRepo;
    }

    public void setRawRepo(RawRepo rawRepo) {
        this.rawRepo = rawRepo;
    }

    public HoldingsItems getHoldingsItems() {
        return holdingsItems;
    }

    public void setHoldingsItems(HoldingsItems holdingsItems) {
        this.holdingsItems = holdingsItems;
    }

    public OpenAgencyService getOpenAgencyService() {
        return openAgencyService;
    }

    public void setOpenAgencyService(OpenAgencyService openAgencyService) {
        this.openAgencyService = openAgencyService;
    }

    public SolrService getSolrService() {
        return solrService;
    }

    public void setSolrService(SolrService solrService) {
        this.solrService = solrService;
    }

    public Validator getValidator() {
        return validator;
    }

    public void setValidator(Validator validator) {
        this.validator = validator;
    }

    public UpdateStore getUpdateStore() {
        return updateStore;
    }

    public void setUpdateStore(UpdateStore updateStore) {
        this.updateStore = updateStore;
    }

    public LibraryRecordsHandler getLibraryRecordsHandler() {
        return libraryRecordsHandler;
    }

    public void setLibraryRecordsHandler(LibraryRecordsHandler libraryRecordsHandler) {
        this.libraryRecordsHandler = libraryRecordsHandler;
    }

    public ResourceBundle getMessages() {
        return messages;
    }

    public void setMessages(ResourceBundle messages) {
        this.messages = messages;
    }

    public MarcRecord getMarcRecord() {
        return marcRecord;
    }

    public void setMarcRecord(MarcRecord marcRecord) {
        this.marcRecord = marcRecord;
    }

    public UpdateMode getUpdateMode() { return this.updateMode; }

    public void setUpdateMode(UpdateMode updateMode) { this.updateMode = updateMode; }

    public String getRecordPid() {
        logger.entry();
        try {
            if (recordPid == null) {
                String faustNbr = "faust";
                String agencyId = "agency";
                MarcRecord record = readRecord();
                if (record != null) {
                    MarcRecordReader reader = new MarcRecordReader(record);
                    String newFaustNbr = reader.recordId();
                    if (newFaustNbr != null) {
                        faustNbr = newFaustNbr;
                    }
                    String newAgencyId = reader.agencyId();
                    if (newAgencyId != null) {
                        agencyId = newAgencyId;
                    }
                }
                recordPid = faustNbr + ":" + agencyId;
            }
            return recordPid;
        } finally {
            logger.exit(recordPid);
        }
    }

    /**
     * Reads the validation scheme, also known as the template name, of the
     * request.
     *
     * @return The validation scheme if it can be read from the request, the
     * empty string otherwise.
     */
    public String getSchemaName() {
        logger.entry();
        String result = "";
        try {
            if (updateServiceRequestDto != null) {
                result = updateServiceRequestDto.getSchemaName();
            } else {
                logger.warn("Unable to validate schema from request");
            }
            return result;
        } finally {
            logger.exit(result);
        }
    }

    /**
     * Reads the SRU record from the request and returns it.
     * If the request contains more than one record, then <code>null</code> is
     * returned.
     *
     * @return The found record as a {@link MarcRecord} or <code>null</code>
     * <p>
     * if the can not be converted or if no records exists.
     */
    public MarcRecord readRecord() {
        logger.entry();
        List<Object> list = null;
        try {
            if (marcRecord == null) {
                if (updateServiceRequestDto != null && updateServiceRequestDto.getBibliographicRecordDto() != null && updateServiceRequestDto.getBibliographicRecordDto().getRecordDataDto() != null) {
                    list = updateServiceRequestDto.getBibliographicRecordDto().getRecordDataDto().getContent();
                } else {
                    logger.warn("Unable to read record from request");
                }
                if (list != null) {
                    for (Object o : list) {
                        if (o instanceof Node) {
                            marcRecord = MarcConverter.createFromMarcXChange(new DOMSource((Node) o));
                            break;
                        }
                    }
                }
            }
            return marcRecord;
        } finally {
            logger.exit(marcRecord);
        }
    }

    /**
     * Checks if the request contains a valid record scheme.
     * <p>
     * The valid record scheme is defined by the contant
     * {@link #RECORD_SCHEMA_MARCXCHANGE_1_1}
     *
     * @return Returns <code>true</code> if the record scheme is equal to
     * {@link #RECORD_SCHEMA_MARCXCHANGE_1_1}, <code>false</code> otherwise.
     */
    public boolean isRecordSchemaValid() {
        logger.entry();
        boolean result = false;
        try {
            if (updateServiceRequestDto != null && updateServiceRequestDto.getBibliographicRecordDto() != null && updateServiceRequestDto.getBibliographicRecordDto().getRecordSchema() != null) {
                result = RECORD_SCHEMA_MARCXCHANGE_1_1.equals(updateServiceRequestDto.getBibliographicRecordDto().getRecordSchema());
            } else {
                logger.warn("Unable to record schema from request");
            }
            return result;
        } finally {
            logger.exit(result);
        }
    }

    /**
     * Checks if the request contains a valid record packing.
     * <p>
     * The valid record packing is defined by the contant
     * {@link #RECORD_PACKING_XML}
     *
     * @return Returns <code>true</code> if the record packing is equal to
     * {@link #RECORD_PACKING_XML}, <code>false</code> otherwise.
     */
    public boolean isRecordPackingValid() {
        logger.entry();
        boolean result = false;
        try {
            if (updateServiceRequestDto != null && updateServiceRequestDto.getBibliographicRecordDto() != null && updateServiceRequestDto.getBibliographicRecordDto().getRecordPacking() != null) {
                result = RECORD_PACKING_XML.equals(updateServiceRequestDto.getBibliographicRecordDto().getRecordPacking());
            } else {
                logger.warn("Unable to record packing from request");
            }
            return result;
        } finally {
            logger.exit(result);
        }
    }

    /**
     * Reads any extra data associated with the SRU record.
     * <p>
     * If the request contains more than one record, then <code>null</code> is
     * returned.
     *
     * @return The found records extra data as a {@link BibliographicRecordExtraData} or <code>null</code>
     * if the can not be converted or if no records exists.
     */
    public BibliographicRecordExtraData getRecordExtraData() {
        logger.entry();
        List<Object> list = null;
        try {
            if (bibliographicRecordExtraData == null) {
                if (updateServiceRequestDto != null && updateServiceRequestDto.getBibliographicRecordDto() != null && updateServiceRequestDto.getBibliographicRecordDto().getExtraRecordDataDto() != null) {
                    list = updateServiceRequestDto.getBibliographicRecordDto().getExtraRecordDataDto().getContent();
                } else {
                    logger.warn("Unable to read record from request");
                }
                if (list != null) {
                    for (Object o : list) {
                        if (o instanceof Node) {
                            bibliographicRecordExtraData = BibliographicRecordExtraDataDecoder.fromXml(new DOMSource((Node) o));
                            break;
                        }
                    }
                }
            }
            return bibliographicRecordExtraData;
        } finally {
            logger.exit(bibliographicRecordExtraData);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GlobalActionState state = (GlobalActionState) o;

        if (updateServiceRequestDto != null ? !updateServiceRequestDto.equals(state.updateServiceRequestDto) : state.updateServiceRequestDto != null)
            return false;
        if (wsContext != null ? !wsContext.equals(state.wsContext) : state.wsContext != null) return false;
        if (authenticator != null ? !authenticator.equals(state.authenticator) : state.authenticator != null)
            return false;
        if (scripter != null ? !scripter.equals(state.scripter) : state.scripter != null) return false;
        if (rawRepo != null ? !rawRepo.equals(state.rawRepo) : state.rawRepo != null) return false;
        if (holdingsItems != null ? !holdingsItems.equals(state.holdingsItems) : state.holdingsItems != null)
            return false;
        if (openAgencyService != null ? !openAgencyService.equals(state.openAgencyService) : state.openAgencyService != null)
            return false;
        if (solrService != null ? !solrService.equals(state.solrService) : state.solrService != null) return false;
        if (validator != null ? !validator.equals(state.validator) : state.validator != null) return false;
        if (updateStore != null ? !updateStore.equals(state.updateStore) : state.updateStore != null) return false;
        if (libraryRecordsHandler != null ? !libraryRecordsHandler.equals(state.libraryRecordsHandler) : state.libraryRecordsHandler != null)
            return false;
        if (messages != null ? !messages.equals(state.messages) : state.messages != null) return false;
        if (marcRecord != null ? !marcRecord.equals(state.marcRecord) : state.marcRecord != null) return false;
        if (bibliographicRecordExtraData != null ? !bibliographicRecordExtraData.equals(state.bibliographicRecordExtraData) : state.bibliographicRecordExtraData != null)
            return false;
        return recordPid != null ? recordPid.equals(state.recordPid) : state.recordPid == null;

    }

    @Override
    public int hashCode() {
        int result = updateServiceRequestDto != null ? updateServiceRequestDto.hashCode() : 0;
        result = 31 * result + (wsContext != null ? wsContext.hashCode() : 0);
        result = 31 * result + (authenticator != null ? authenticator.hashCode() : 0);
        result = 31 * result + (scripter != null ? scripter.hashCode() : 0);
        result = 31 * result + (rawRepo != null ? rawRepo.hashCode() : 0);
        result = 31 * result + (holdingsItems != null ? holdingsItems.hashCode() : 0);
        result = 31 * result + (openAgencyService != null ? openAgencyService.hashCode() : 0);
        result = 31 * result + (solrService != null ? solrService.hashCode() : 0);
        result = 31 * result + (validator != null ? validator.hashCode() : 0);
        result = 31 * result + (updateStore != null ? updateStore.hashCode() : 0);
        result = 31 * result + (libraryRecordsHandler != null ? libraryRecordsHandler.hashCode() : 0);
        result = 31 * result + (messages != null ? messages.hashCode() : 0);
        result = 31 * result + (marcRecord != null ? marcRecord.hashCode() : 0);
        result = 31 * result + (bibliographicRecordExtraData != null ? bibliographicRecordExtraData.hashCode() : 0);
        result = 31 * result + (recordPid != null ? recordPid.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "GlobalActionState{" +
                "updateServiceRequestDto=" + updateServiceRequestDto +
                ", wsContext=" + wsContext +
                ", authenticator=" + authenticator +
                ", scripter=" + scripter +
                ", rawRepo=" + rawRepo +
                ", holdingsItems=" + holdingsItems +
                ", openAgencyService=" + openAgencyService +
                ", solrService=" + solrService +
                ", validator=" + validator +
                ", updateStore=" + updateStore +
                ", libraryRecordsHandler=" + libraryRecordsHandler +
                ", messages=" + messages +
                ", marcRecord=" + marcRecord +
                ", bibliographicRecordExtraData=" + bibliographicRecordExtraData +
                ", recordPid='" + recordPid + '\'' +
                '}';
    }
}
