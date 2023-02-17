package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcConverter;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.holdingitems.content.HoldingsItemsConnector;
import dk.dbc.opencat.connector.OpencatBusinessConnector;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.client.BibliographicRecordExtraData;
import dk.dbc.updateservice.client.BibliographicRecordExtraDataDecoder;
import dk.dbc.updateservice.dto.UpdateServiceRequestDTO;
import dk.dbc.updateservice.solr.SolrBasis;
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
import dk.dbc.updateservice.utils.DeferredLogger;
import dk.dbc.updateservice.validate.Validator;
import dk.dbc.vipcore.exception.VipCoreException;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector;
import org.w3c.dom.Node;

import javax.servlet.http.HttpServletRequest;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GlobalActionState {
    private static final DeferredLogger LOGGER = new DeferredLogger(GlobalActionState.class);
    private static final String RECORD_SCHEMA_MARCXCHANGE_1_1 = "info:lc/xmlns/marcxchange-v1";
    private static final String RECORD_PACKING_XML = "xml";

    private UpdateServiceRequestDTO updateServiceRequestDTO = null;
    private Authenticator authenticator = null;
    private RawRepo rawRepo = null;
    private OpencatBusinessConnector opencatBusiness = null;
    private HoldingsItemsConnector holdingsItems = null;
    private VipCoreService vipCoreService = null;
    private SolrFBS solrService = null;
    private SolrBasis solrBasis = null;
    private Validator validator = null;
    private UpdateStore updateStore = null;
    private LibraryRecordsHandler libraryRecordsHandler = null;
    private ResourceBundle messages = null;
    private MarcRecord marcRecord = null;
    private BibliographicRecordExtraData bibliographicRecordExtraData = null;
    private String recordPid = null;
    private LibraryGroup libraryGroup = null;
    private String templateGroup = null;
    private MarcRecordReader marcRecordReader = null;
    private Boolean doubleRecordPossible = null;
    private Boolean recordExists = null;
    private Instant createOverwriteDate = null;
    private Set<String> phLibraries = null;
    private Set<String> ffuLibraries = null;
    private Set<String> lokbibLibraries = null;
    private RecordSorter recordSorter = null;
    private NoteAndSubjectExtensionsHandler noteAndSubjectExtensionsHandler = null;
    private HttpServletRequest request;
    private Boolean isTemplateOverwrite;


    public HttpServletRequest getRequest() {
        return request;
    }

    public void setRequest(HttpServletRequest request) {
        this.request = request;
    }

    public GlobalActionState() {
    }

    public GlobalActionState(UpdateServiceRequestDTO updateServiceRequestDTO,
                             Authenticator authenticator,
                             RawRepo rawRepo,
                             HoldingsItemsConnector holdingsItems,
                             VipCoreService vipCoreService,
                             SolrFBS solrService,
                             SolrBasis solrBasis,
                             Validator validator,
                             UpdateStore updateStore,
                             LibraryRecordsHandler libraryRecordsHandler,
                             ResourceBundle messages,
                             HttpServletRequest request,
                             LibraryGroup libraryGroup) {
        this.updateServiceRequestDTO = updateServiceRequestDTO;
        this.authenticator = authenticator;
        this.rawRepo = rawRepo;
        this.holdingsItems = holdingsItems;
        this.vipCoreService = vipCoreService;
        this.solrService = solrService;
        this.solrBasis = solrBasis;
        this.validator = validator;
        this.updateStore = updateStore;
        this.libraryRecordsHandler = libraryRecordsHandler;
        this.messages = messages;
        this.libraryGroup = libraryGroup;
        this.request = request;
    }

    public GlobalActionState(GlobalActionState globalActionState) {
        this(globalActionState.getUpdateServiceRequestDTO(),
                globalActionState.getAuthenticator(),
                globalActionState.getRawRepo(),
                globalActionState.getHoldingsItems(),
                globalActionState.getVipCoreService(),
                globalActionState.getSolrFBS(),
                globalActionState.getSolrBasis(),
                globalActionState.getValidator(),
                globalActionState.getUpdateStore(),
                globalActionState.getLibraryRecordsHandler(),
                globalActionState.getMessages(),
                globalActionState.getRequest(),
                null);
    }

    private void resetState() {
        recordPid = null;
        marcRecord = null;
        bibliographicRecordExtraData = null;
        marcRecordReader = null;
        doubleRecordPossible = null;
        libraryGroup = null;
    }

    public UpdateServiceRequestDTO getUpdateServiceRequestDTO() {
        return updateServiceRequestDTO;
    }

    public void setUpdateServiceRequestDTO(UpdateServiceRequestDTO updateServiceRequestDTO) {
        resetState();
        this.updateServiceRequestDTO = updateServiceRequestDTO;
    }

    public Authenticator getAuthenticator() {
        return authenticator;
    }

    public void setAuthenticator(Authenticator authenticator) {
        this.authenticator = authenticator;
    }

    public RawRepo getRawRepo() {
        return rawRepo;
    }

    public void setRawRepo(RawRepo rawRepo) {
        this.rawRepo = rawRepo;
    }

    public OpencatBusinessConnector getOpencatBusiness() {
        return opencatBusiness;
    }

    public void setOpencatBusiness(OpencatBusinessConnector opencatBusiness) {
        this.opencatBusiness = opencatBusiness;
    }

    public HoldingsItemsConnector getHoldingsItems() {
        return holdingsItems;
    }

    public Set<Integer> getAgenciesWithHoldings(MarcRecord marcRecord) {
        MarcRecordReader reader = new MarcRecordReader(marcRecord);
        Set<Integer> result = Stream.concat(
                Stream.of(reader.getRecordId()),
                reader.getCentralAliasIds().stream())
                .map(id -> getHoldingsItems().getAgenciesWithHoldings(id))
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        return result;
    }


    public void setHoldingsItems(HoldingsItemsConnector holdingsItems) {
        this.holdingsItems = holdingsItems;
    }

    public VipCoreService getVipCoreService() {
        return vipCoreService;
    }

    public void setVipCoreService(VipCoreService vipCoreService) {
        this.vipCoreService = vipCoreService;
    }

    public SolrFBS getSolrFBS() {
        return solrService;
    }

    public void setSolrService(SolrFBS solrService) {
        this.solrService = solrService;
    }

    public SolrBasis getSolrBasis() {
        return solrBasis;
    }

    public void setSolrBasis(SolrBasis solrBasis) {
        this.solrBasis = solrBasis;
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

    public void setLibraryGroup(LibraryGroup libraryGroup) {
        this.libraryGroup = libraryGroup;
    }

    /*
        During mass corrections (masseret) it is desired to:
         - Use the same groupId for every record despite the record's agency
         - Be allowed to update the record no matter how mangled the record is.

        To this purpose a fake template can be used: superallowall
        Using this template completely bypasses template validate and everything else related to a template (e.g. sorting)

        Only libraries with auth_root permission can use this template and the record being updated must already exist.
     */
    public boolean getIsTemplateOverwrite() throws UpdateException {
        if (this.isTemplateOverwrite == null) {
            try {
                this.isTemplateOverwrite = "superallowall".equals(this.getSchemaName()) &&
                        this.getVipCoreService().hasFeature(this.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), VipCoreLibraryRulesConnector.Rule.AUTH_ROOT);
                // Temporarily disable check
//                if (Boolean.TRUE.equals(this.isTemplateOverwrite)) {
//                    final String bibliographicRecordId = marcRecordReader.getValue("001", "a");
//                    final int agencyId = marcRecordReader.getAgencyIdAsInt();
//                    if (!rawRepo.recordExistsMaybeDeleted(bibliographicRecordId, agencyId)) {
//                        throw new UpdateException(String.format("superallowall is not allowed for records which don't exist (%s:%s)", bibliographicRecordId, agencyId));
//                    }
//                }
            } catch (VipCoreException e) {
                throw new UpdateException("Something went wrong when contacting vipcore", e);
            }
        }

        return this.isTemplateOverwrite;
    }

    public void setTemplateGroup(String templateGroup) {
        this.templateGroup = templateGroup;
    }

    public Instant getCreateOverwriteDate() {
        return createOverwriteDate;
    }

    public void setCreateOverwriteDate(Instant createOverwriteDate) {
        this.createOverwriteDate = createOverwriteDate;
    }

    public void setRecordSorter(RecordSorter recordSorter) {
        this.recordSorter = recordSorter;
    }

    public void setNoteAndSubjectExtensionsHandler(NoteAndSubjectExtensionsHandler noteAndSubjectExtensionsHandler) {
        this.noteAndSubjectExtensionsHandler = noteAndSubjectExtensionsHandler;
    }

    public MarcRecordReader getMarcRecordReader() {
        if (marcRecordReader == null) {
            marcRecordReader = new MarcRecordReader(readRecord());
        }
        return marcRecordReader;
    }

    public boolean isDoubleRecordPossible() throws UpdateException {
        marcRecordReader = getMarcRecordReader();
        if (doubleRecordPossible == null) {
            /*
             If the record lacks 001a and 001b it is not a valid record and will be rejected at a later stage.
             However since we want to perform the double record possible check as soon as possible we have to
             handle empty records - therefore this check/hack.

             False is returned as a record without 001a and 001b can't possibly exist already.
            */
            doubleRecordPossible = false;

            if (marcRecordReader.hasSubfield("001", "a") && marcRecordReader.hasSubfield("001", "b")) {
                boolean markedForDeletion = marcRecordReader.markedForDeletion();
                boolean isDBCMode = getLibraryGroup().isDBC();
                int agencyId = marcRecordReader.getAgencyIdAsInt();
                boolean agencyIdEqualsRawRepoCommonLibrary = agencyId == RawRepo.COMMON_AGENCY;
                doubleRecordPossible = !markedForDeletion && !isDBCMode && !recordExists() && agencyIdEqualsRawRepoCommonLibrary;
            }
        }
        return doubleRecordPossible;
    }


    /**
     * Reads the validation scheme, also known as the template name, of the
     * request.
     *
     * @return The validation scheme if it can be read from the request, the
     * empty string otherwise.
     */
    public String getSchemaName() {
        if (updateServiceRequestDTO != null) {
            return updateServiceRequestDTO.getSchemaName();
        } else {
            LOGGER.use(log -> log.warn("Unable to validate schema from request"));
            return "";
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
        if (marcRecord == null) {
            List<Object> list = null;

            if (updateServiceRequestDTO != null && updateServiceRequestDTO.getBibliographicRecordDTO() != null && updateServiceRequestDTO.getBibliographicRecordDTO().getRecordDataDTO() != null) {
                list = updateServiceRequestDTO.getBibliographicRecordDTO().getRecordDataDTO().getContent();
            } else {
                LOGGER.use(log -> log.warn("Unable to read record from request"));
            }
            if (list != null) {
                for (Object o : list) {
                    if (o instanceof Node) {
                        marcRecord = MarcConverter.createFromMarcXChange(new DOMSource((Node) o));
                        break;
                    } else if (o instanceof String) {
                        String marcString = (String) o;
                        if (!"".equals(marcString.trim())) {
                            marcRecord = MarcConverter.convertFromMarcXChange(marcString);
                            break;
                        }
                    }
                }
            }
        }

        return marcRecord;
    }

    /**
     * Checks if the request contains a valid record scheme.
     * <p>
     * The valid record scheme is defined by the content
     * {@link #RECORD_SCHEMA_MARCXCHANGE_1_1}
     *
     * @return Returns <code>true</code> if the record scheme is equal to
     * {@link #RECORD_SCHEMA_MARCXCHANGE_1_1}, <code>false</code> otherwise.
     */
    public boolean isRecordSchemaValid() {
        boolean result = false;
        if (updateServiceRequestDTO != null && updateServiceRequestDTO.getBibliographicRecordDTO() != null && updateServiceRequestDTO.getBibliographicRecordDTO().getRecordSchema() != null) {
            result = RECORD_SCHEMA_MARCXCHANGE_1_1.equals(updateServiceRequestDTO.getBibliographicRecordDTO().getRecordSchema());
        } else {
            LOGGER.use(log -> log.warn("Unable to record schema from request"));
        }
        return result;
    }

    /**
     * Checks if the request contains a valid record packing.
     * <p>
     * The valid record packing is defined by the content
     * {@link #RECORD_PACKING_XML}
     *
     * @return Returns <code>true</code> if the record packing is equal to
     * {@link #RECORD_PACKING_XML}, <code>false</code> otherwise.
     */
    public boolean isRecordPackingValid() {
        boolean result = false;
        if (updateServiceRequestDTO != null && updateServiceRequestDTO.getBibliographicRecordDTO() != null && updateServiceRequestDTO.getBibliographicRecordDTO().getRecordPacking() != null) {
            result = RECORD_PACKING_XML.equals(updateServiceRequestDTO.getBibliographicRecordDTO().getRecordPacking());
        } else {
            LOGGER.use(log -> log.warn("Unable to record packing from request"));
        }
        return result;
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
        List<Object> list = null;
        if (bibliographicRecordExtraData == null) {
            if (updateServiceRequestDTO != null && updateServiceRequestDTO.getBibliographicRecordDTO() != null && updateServiceRequestDTO.getBibliographicRecordDTO().getExtraRecordDataDTO() != null) {
                list = updateServiceRequestDTO.getBibliographicRecordDTO().getExtraRecordDataDTO().getContent();
            }
            if (list != null) {
                for (Object o : list) {
                    if (o instanceof Node) {
                        bibliographicRecordExtraData = BibliographicRecordExtraDataDecoder.fromXml(new DOMSource((Node) o));
                        break;
                    } else if (o instanceof String) {
                        String extraRecordDataString = (String) o;
                        if (!"".equals(extraRecordDataString.trim())) {
                            bibliographicRecordExtraData = BibliographicRecordExtraDataDecoder.fromXml(new StreamSource(new StringReader(extraRecordDataString)));
                            break;
                        }
                    }
                }
            }
        }

        return bibliographicRecordExtraData;
    }

    public RecordSorter getRecordSorter() {
        if (this.recordSorter == null) {
            this.recordSorter = new RecordSorter(getOpencatBusiness(), getSchemaName());
        }

        return this.recordSorter;
    }

    public NoteAndSubjectExtensionsHandler getNoteAndSubjectExtensionsHandler() {
        if (this.noteAndSubjectExtensionsHandler == null) {
            this.noteAndSubjectExtensionsHandler = new NoteAndSubjectExtensionsHandler(getVipCoreService(), getRawRepo(), messages);
        }

        return this.noteAndSubjectExtensionsHandler;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GlobalActionState state = (GlobalActionState) o;

        if (updateServiceRequestDTO != null ? !updateServiceRequestDTO.equals(state.updateServiceRequestDTO) : state.updateServiceRequestDTO != null)
            return false;
        if (request != null ? !request.equals(state.request) : state.request != null) return false;
        if (authenticator != null ? !authenticator.equals(state.authenticator) : state.authenticator != null)
            return false;
        if (rawRepo != null ? !rawRepo.equals(state.rawRepo) : state.rawRepo != null) return false;
        if (holdingsItems != null ? !holdingsItems.equals(state.holdingsItems) : state.holdingsItems != null)
            return false;
        if (vipCoreService != null ? !vipCoreService.equals(state.vipCoreService) : state.vipCoreService != null)
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
        int result = updateServiceRequestDTO != null ? updateServiceRequestDTO.hashCode() : 0;
        result = 31 * result + (authenticator != null ? authenticator.hashCode() : 0);
        result = 31 * result + (rawRepo != null ? rawRepo.hashCode() : 0);
        result = 31 * result + (opencatBusiness != null ? opencatBusiness.hashCode() : 0);
        result = 31 * result + (holdingsItems != null ? holdingsItems.hashCode() : 0);
        result = 31 * result + (vipCoreService != null ? vipCoreService.hashCode() : 0);
        result = 31 * result + (solrService != null ? solrService.hashCode() : 0);
        result = 31 * result + (solrBasis != null ? solrBasis.hashCode() : 0);
        result = 31 * result + (validator != null ? validator.hashCode() : 0);
        result = 31 * result + (updateStore != null ? updateStore.hashCode() : 0);
        result = 31 * result + (libraryRecordsHandler != null ? libraryRecordsHandler.hashCode() : 0);
        result = 31 * result + (messages != null ? messages.hashCode() : 0);
        result = 31 * result + (marcRecord != null ? marcRecord.hashCode() : 0);
        result = 31 * result + (bibliographicRecordExtraData != null ? bibliographicRecordExtraData.hashCode() : 0);
        result = 31 * result + (recordPid != null ? recordPid.hashCode() : 0);
        result = 31 * result + (request != null ? request.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "GlobalActionState{" +
                "updateServiceRequestDTO=" + updateServiceRequestDTO +
                ", authenticator=" + authenticator +
                ", rawRepo=" + rawRepo +
                ", opencatBusinessConnector=" + opencatBusiness +
                ", holdingsItems=" + holdingsItems +
                ", vipCoreService=" + vipCoreService +
                ", solrService=" + solrService +
                ", solrBasis=" + solrBasis +
                ", validator=" + validator +
                ", updateStore=" + updateStore +
                ", libraryRecordsHandler=" + libraryRecordsHandler +
                ", messages=" + messages +
                ", marcRecord=" + marcRecord +
                ", bibliographicRecordExtraData=" + bibliographicRecordExtraData +
                ", libraryGroup=" + libraryGroup +
                ", recordPid='" + recordPid + '\'' +
                ", request='" + request + '\'' +
                '}';
    }

    public boolean recordExists() throws UpdateException {
        if (this.recordExists == null) {
            this.recordExists = rawRepo.recordExists(marcRecordReader.getRecordId(), marcRecordReader.getAgencyIdAsInt());
        }

        return this.recordExists;
    }

    public boolean isAdmin() {
        String userId = updateServiceRequestDTO.getAuthenticationDTO().getUserId();

        return "admin".equalsIgnoreCase(userId);
    }

    public LibraryGroup getLibraryGroup() throws UpdateException {
        if (libraryGroup == null) {
            final String groupId = updateServiceRequestDTO.getAuthenticationDTO().getGroupId();

            try {
                libraryGroup = vipCoreService.getLibraryGroup(groupId);
            } catch (UpdateException | VipCoreException ex) {
                LOGGER.use(log -> log.error("VipCoreException error: {}", ex.getMessage(), ex));
                throw new UpdateException(ex.getMessage(), ex);
            }

            // Make sure that we didn't get nul
            if (libraryGroup == null) {
                String err = "LibraryGroup not found for groupId " + groupId;
                LOGGER.use(log -> log.error(err));
                throw new UpdateException(err);
            }
        }

        return libraryGroup;
    }

    public String getTemplateGroup() throws UpdateException {
        if (templateGroup == null) {
            final String groupId = updateServiceRequestDTO.getAuthenticationDTO().getGroupId();

            try {
                templateGroup = vipCoreService.getTemplateGroup(groupId);
            } catch (VipCoreException ex) {
                LOGGER.use(log -> log.error("VipCoreException error: " + ex.getMessage(), ex));
                throw new UpdateException(ex.getMessage(), ex);
            }

            // Make sure that we didn't get nul
            if (templateGroup == null) {
                String err = "TemplateGroup not found for groupId " + groupId;
                LOGGER.use(log -> log.error(err));
                throw new UpdateException(err);
            }
        }

        return templateGroup;
    }

    public Set<String> getPHLibraries() throws UpdateException {
        if (phLibraries == null) {
            try {
                phLibraries = vipCoreService.getPHLibraries();
            } catch (VipCoreException ex) {
                LOGGER.use(log -> log.error("VipCoreException error: " + ex.getMessage(), ex));
                throw new UpdateException(ex.getMessage(), ex);
            }
        }

        return phLibraries;
    }

    public Set<String> getFFULibraries() throws UpdateException {
        if (ffuLibraries == null) {
            try {
                ffuLibraries = vipCoreService.getFFULibraries();
            } catch (VipCoreException ex) {
                LOGGER.use(log -> log.error("VipCoreException error: " + ex.getMessage(), ex));
                throw new UpdateException(ex.getMessage(), ex);
            }
        }

        return ffuLibraries;
    }

    public Set<String> getLokbibLibraries() throws UpdateException {
        if (lokbibLibraries == null) {
            try {
                lokbibLibraries = vipCoreService.getLokbibLibraries();
            } catch (VipCoreException ex) {
                LOGGER.use(log -> log.error("VipCoreException error: " + ex.getMessage(), ex));
                throw new UpdateException(ex.getMessage(), ex);
            }
        }

        return lokbibLibraries;
    }

    public String getRawRepoProviderId() throws UpdateException {
        String providerId;
        if (this.getLibraryGroup().isDBC()) {
            providerId = JNDIResources.RAWREPO_PROVIDER_ID_DBC;
        } else {
            providerId = JNDIResources.RAWREPO_PROVIDER_ID_FBS;
        }

        return providerId;
    }

}
