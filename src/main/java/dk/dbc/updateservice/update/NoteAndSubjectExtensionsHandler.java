
package dk.dbc.updateservice.update;

import dk.dbc.common.records.CatalogExtractionCode;
import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcFieldReader;
import dk.dbc.common.records.MarcFieldWriter;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.marcrecord.ExpandCommonMarcRecord;
import dk.dbc.rawrepo.RawRepoException;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.TypeEnumDTO;
import dk.dbc.updateservice.utils.DeferredLogger;
import dk.dbc.updateservice.utils.ResourceBundles;
import dk.dbc.vipcore.exception.VipCoreException;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

public class NoteAndSubjectExtensionsHandler {
    private static final DeferredLogger LOGGER = new DeferredLogger(NoteAndSubjectExtensionsHandler.class);
    private final VipCoreService vipCoreService;
    private final RawRepo rawRepo;
    private final ResourceBundle messages;

    static final String CATALOGUE_CODE_FIELD = "032";
    static final String EXTENDABLE_NOTE_FIELDS = "504|530";
    static final String EXTENDABLE_CONTROLLED_SUBJECT_FIELDS = "600|610|630|666";
    static final String EXTENDABLE_SUBJECT_FIELDS = "631|664|665";
    private static final String CLASSIFICATION_FIELDS = "652";
    private static final String NO_CLASSIFICATION = "uden klassemærke";

    public NoteAndSubjectExtensionsHandler(VipCoreService vipCoreService, RawRepo rawRepo, ResourceBundle messages) {
        this.vipCoreService = vipCoreService;
        this.rawRepo = rawRepo;
        this.messages = messages;
    }

    /**
     * Handle classification fields. The following rules need to be followed.
     * if 652 fields are equal, then just add them
     * if not, then look closer at the content :
     * if current has code "uden klassemærke" then look at the record and the agency's rights
     * if disputas and the agency may add dk5 to such, then do it
     * if not, then error.
     */
    void addDK5Fields(MarcRecord result, MarcRecord marcRecord, MarcRecordReader reader, MarcRecord curRecord, MarcRecordReader curReader , String groupId ) throws UpdateException, VipCoreException {
        LOGGER.<Void, UpdateException, VipCoreException>callChecked2(log -> {
            final List<MarcField> new652Fields = marcRecord.getFields().stream()
                    .filter(field -> field.getName().matches(CLASSIFICATION_FIELDS)).collect(Collectors.toList());
            final List<MarcField> current652Fields = curRecord.getFields().stream()
                    .filter(field -> field.getName().matches(CLASSIFICATION_FIELDS)).collect(Collectors.toList());
            if (marcFieldsEqualsIgnoreAmpersand(new652Fields, current652Fields)) {
                result.getFields().addAll(current652Fields);
            } else {
                // If disputa and there are new 652 fields, then we have to dig deeper
                if (curReader.hasValue("008", "d", "m") && ! new652Fields.isEmpty()) {
                    if (vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ADD_DK5_TO_PHD_ALLOWED)) {
                        final String curr = curReader.getValue("652", "m");
                        if (curr.equalsIgnoreCase(NO_CLASSIFICATION)) {
                            final String newDk5 = reader.getValue("652", "m");
                            if (! newDk5.isEmpty()) {
                                for (MarcField new652Field : new652Fields) {
                                    result.getFields().add(copyWithNewAmpersand(new652Field, groupId));
                                }
                            } else {
                                // This should not be possible due to other protections, but if they for some reason disappears, this
                                // will prevent deleting 652 - please note that there are no testcase to check this
                                final String msg = messages.getString("update.dbc.record.652.no.delete");
                                log.error("Unable to create sub actions due to an error: {}", msg);
                                throw new UpdateException(msg);

                            }
                        } else {
                            final String msg = messages.getString("update.dbc.record.652.no.uden.klassemaerke");
                            log.error("Unable to create sub actions due to an error: {}", msg);
                            throw new UpdateException(msg);

                        }
                    } else {
                        final String msg = messages.getString("update.dbc.record.652.not.allowed");
                        log.error("Unable to create sub actions due to an error: {}", msg);
                        throw new UpdateException(msg);
                    }
                } else {
                    final String msg = messages.getString("update.dbc.record.652");
                    log.error("Unable to create sub actions due to an error: {}", msg);
                    throw new UpdateException(msg);
                }
            }
            return null;
        });
    }

    /**
     * Creates an extended record of some unexpanded record. Please note, this was first done
     * because the callChecked interface couldn't handle three exceptions, but are now used at two
     * places
     * @param marcRecord the record to expand
     * @return expanded record
     * @throws UpdateException there is something rotten in rawrepo
     */
    MarcRecord getExpandedRecord(MarcRecord marcRecord) throws UpdateException {
        MarcRecord result;
        try {
            final MarcRecordReader reader = new MarcRecordReader(marcRecord);
            final String recId = reader.getRecordId();
            final Map<String, MarcRecord> currentRecordCollection = rawRepo.fetchRecordCollection(recId, reader.getAgencyIdAsInt());
            result = ExpandCommonMarcRecord.expandMarcRecord(currentRecordCollection, recId);
        } catch (RawRepoException e) {
            throw new UpdateException("Exception while expanding the records", e);
        }
        return result;
    }

    /**
     * Handles modifying subject fields given in EXTENDABLE_CONTROLLED_SUBJECT_FIELDS
     * The mechanism is pretty simple, if any of the fields are owned by dbc, then it isn't allowed for libraries to modify any
     * If none are dbc fields, then the old fields are deleted and the new ones are added with the updating agency as owner
     * @param result            the resulting record
     * @param marcRecord        the updating record
     * @param curRecord         the record in rawrepo
     * @param groupId           the updating agency id
     * @throws UpdateException  attempt to modify dbc owned subjects detected
     */
    void addSubjectFields(MarcRecord result, MarcRecord marcRecord, MarcRecord curRecord, String groupId ) throws UpdateException {
        final MarcRecord expandedCurrentRecord;
        expandedCurrentRecord = getExpandedRecord(curRecord);
        final List<MarcField> newControlledSubjectFields = marcRecord.getFields().stream()
                .filter(field -> field.getName().matches(EXTENDABLE_CONTROLLED_SUBJECT_FIELDS)).collect(Collectors.toList());
        final List<MarcField> currentControlledSubjectFields = curRecord.getFields().stream()
                .filter(field -> field.getName().matches(EXTENDABLE_CONTROLLED_SUBJECT_FIELDS)).collect(Collectors.toList());
        final List<MarcField> currentExpandedControlledSubjectFields = expandedCurrentRecord.getFields().stream()
                .filter(field -> field.getName().matches(EXTENDABLE_CONTROLLED_SUBJECT_FIELDS)).collect(Collectors.toList());

        if (marcFieldsEqualsIgnoreAmpersand(newControlledSubjectFields, currentExpandedControlledSubjectFields)) {
            // Controlled subject field are identical, so just copy the existing ones over
            result.getFields().addAll(currentControlledSubjectFields);
        } else {
            if (checkStructureForDbc(curRecord, EXTENDABLE_CONTROLLED_SUBJECT_FIELDS, currentControlledSubjectFields)) {
                final String msg = messages.getString("update.dbc.record.dbc.subjects");
                LOGGER.use(log -> log.error("Unable to create sub actions due to an error: {}", msg));
                throw new UpdateException(msg);
            } else {
                for (MarcField newControlledSubjectField : newControlledSubjectFields) {
                    result.getFields().add(copyWithNewAmpersand(newControlledSubjectField, groupId));
                }
            }
        }

        // Handle (non-controlled) subject fields. These fields are handled individually
        final List<MarcField> newSubjectFields = marcRecord.getFields().stream()
                .filter(field -> field.getName().matches(EXTENDABLE_SUBJECT_FIELDS)).collect(Collectors.toList());
        final List<MarcField> currentSubjectFields = expandedCurrentRecord.getFields().stream()
                .filter(field -> field.getName().matches(EXTENDABLE_SUBJECT_FIELDS)).collect(Collectors.toList());

        if (marcFieldsEqualsIgnoreAmpersand(newSubjectFields, currentSubjectFields)) {
            // Subject field are identical, so just copy the existing ones over
            result.getFields().addAll(currentSubjectFields);
        } else {
            final List<MarcField> currentSubjectFieldsCloned = new ArrayList<>();
            currentSubjectFields.forEach(f -> currentSubjectFieldsCloned.add(new MarcField(f)));
            currentSubjectFieldsCloned.forEach(f -> new MarcFieldWriter(f).removeSubfield("&"));
            // If the existing field is unchanged then we need to keep the value of *& which might not be included in the incoming record
            for (MarcField newSubjectField : newSubjectFields) {
                final MarcField newSubjectFieldClone = new MarcField(newSubjectField);
                new MarcFieldWriter(newSubjectFieldClone).removeSubfield("&");
                boolean addNewSubjectField = true;
                for (int i = 0; i < currentSubjectFieldsCloned.size(); i++) {
                    // The position of the fields are the same. If we match a field in the list without ampersand then
                    // copy the existing field from the list with ampersand
                    if (currentSubjectFieldsCloned.get(i).equals(newSubjectFieldClone)) {
                        result.getFields().add(currentSubjectFields.get(i));
                        addNewSubjectField = false;
                        break;
                    }
                }
                if (addNewSubjectField) {
                    newSubjectFieldClone.getSubfields().add(0, new MarcSubField("&", groupId));
                    result.getFields().add(newSubjectFieldClone);
                }
            }
        }
    }

    /**
     * Collect specified fields from the record
     * @param record     The record to collect fields from
     * @param fieldList  The list of fields to collect
     * @return           The found list of fields
     * @throws UpdateException Somehow the expansion of the record failed
     */
    private List<MarcField> collectFields(MarcRecord record, String fieldList) throws UpdateException {
        final MarcRecord expandedCurrentRecord;
        expandedCurrentRecord = getExpandedRecord(record);
        return expandedCurrentRecord.getFields().stream()
                .filter(field -> field.getName().matches(fieldList)).collect(Collectors.toList());
    }

    /**
     * Find and check fields. If it's a record that is part of a volume, then the whole
     * structure is checked.
     * @param record     The record that is to be investigated
     * @param fieldList  List of fields to check
     * @param fields     Current found fields
     * @return           Returns whether there is dbc owned fields or not
     * @throws UpdateException Something went wrong while fetching a record
     */
    private boolean checkStructureForDbc(MarcRecord record, String fieldList, List<MarcField> fields) throws UpdateException {
        boolean result;
        MarcRecord worker = new MarcRecord(record);
        List<MarcField> fullList = new ArrayList<>(fields);
        MarcRecordReader reader = new MarcRecordReader(worker);
        final String recordType = reader.getValue("004", "a");
        if (Arrays.asList("h", "s", "b").contains(recordType)) {
            // her skal der dykkes, surfaces, sidesteppes og andet spændende.
            List<RecordId> recSet = new ArrayList<>();
            if (!"h".equals(recordType)) {
                RecordId id = new RecordId(reader.getRecordId(), reader.getAgencyIdAsInt());
                final Set<RecordId> parents = rawRepo.parents(id);
                final Set<RecordId> commonParents = parents.stream().filter(r -> r.getAgencyId() == RawRepo.COMMON_AGENCY).collect(Collectors.toSet());
                for (RecordId parent : commonParents) {
                    final Record childRecord = rawRepo.fetchRecord(parent.getBibliographicRecordId(), parent.getAgencyId());
                    final MarcRecord childMarcRecord = RecordContentTransformer.decodeRecord(childRecord.getContent());
                    MarcRecordReader r = new MarcRecordReader(childMarcRecord);
                    if ("h".equals(r.getValue("004", "a"))) {
                        recSet.add(new RecordId(r.getRecordId(), r.getAgencyIdAsInt()));
                        worker.setFields(childMarcRecord.getFields());
                        break;
                    }
                }
                // It should not be possible to end here, but we don't want to throw som weird exception
                recSet.add(new RecordId("DUMMY", 0));
            } else {
                recSet.add(new RecordId(reader.getRecordId(), reader.getAgencyIdAsInt()));
            }
            // Add head records field - could also be the single record - type e
            fullList.addAll(collectFields(worker, fieldList));
            final Set<RecordId> children = rawRepo.children(recSet.get(0));
            // And then all the children fields - there may be none
            for (RecordId child : children) {
                final Record childRecord = rawRepo.fetchRecord(child.getBibliographicRecordId(), child.getAgencyId());
                final MarcRecord childMarcRecord = RecordContentTransformer.decodeRecord(childRecord.getContent());
                MarcRecordReader r = new MarcRecordReader(childMarcRecord);
                fullList.addAll(collectFields(childMarcRecord, fieldList));
            }
        }

        result = isDbcField(fullList);
        return result;
    }

    /**
     * See whether there is a *&amp; 7xxxxx subfield or not.
     * Just for the fun of it, some dbc owned fields have subfields *&amp;0 and *&amp;1, so it's not enough to check if *&amp; exists
     * @param fields The field list to check
     * @return return true if there isn't a *&amp;7xxxxx subfield otherwise false
     */
    private boolean isDbcField(List<MarcField> fields) {
        boolean agencyOwned;
        for (MarcField cnf : fields) {
            agencyOwned = false;
            for (MarcSubField cnsf : cnf.getSubfields()) {
                if (cnsf.getName().equals("&") && cnsf.getValue().startsWith("7")) {
                    agencyOwned = true;
                    break;
                }
            }
            if (!agencyOwned) return true;
        }
        return false;
    }

    /**
     * The rules are :
     * Look up the field in current record - if there is a field with *&amp;1 or no *&amp; at all, then it is a dbc field, and it is not allowed
     * to add/modify the field - that is, if the incoming record has a note field that differ from the current note fields,
     * ignoring *&amp; subfields, then return an error.
     * If there are no dbc note field, then add incoming notes (thereby destroying existing)
     * In all cases the *&amp; subfield is given the updating agency as owner
     * Update notes is impossible since we don't receive a "change this note to that" request, just a record with the content the updater want.
     * @param result           The marcrecord containing the result of the function
     * @param marcRecord       The updating record
     * @param curRecord        The record found in rawrepo
     * @param groupId          The updating library's agency id
     * @throws UpdateException Something not allowed has happened
     */
    private void addNoteFields(MarcRecord result, MarcRecord marcRecord, MarcRecord curRecord, String groupId ) throws UpdateException {
        String[] fields = EXTENDABLE_NOTE_FIELDS.split("\\|");
        for (String field : fields) {
            doAddNoteFields(result, marcRecord, curRecord, groupId, field);
        }
    }

    private void doAddNoteFields(MarcRecord result, MarcRecord marcRecord, MarcRecord curRecord, String groupId, String noteField ) throws UpdateException {

        final List<MarcField> newNoteFields = marcRecord.getFields().stream()
                .filter(field -> field.getName().matches(noteField)).collect(Collectors.toList());
        final List<MarcField> currentNoteFields = curRecord.getFields().stream()
                .filter(field -> field.getName().matches(noteField)).collect(Collectors.toList());

        LOGGER.callChecked(log -> {
            if (marcFieldsEqualsIgnoreAmpersand(newNoteFields, currentNoteFields)) {
                result.getFields().addAll(currentNoteFields);
            } else {
                if (checkStructureForDbc(curRecord, noteField, currentNoteFields)) {
                    final String msg = String.format(messages.getString("update.dbc.record.dbc.notes"), noteField);
                    // Business exception which means we don't want the error in the errorlog, so only log as info
                    log.info("Unable to create sub actions due to an error: {}", msg);
                    throw new UpdateException(msg);
                }
                for (MarcField newNoteField : newNoteFields) {
                    result.getFields().add(copyWithNewAmpersand(newNoteField, groupId));
                }
            }
            return null;
        });
    }

    void addCatalogField(MarcRecord result, MarcRecord marcRecord, MarcRecord curRecord, String groupId ) throws UpdateException {
        // Technically "there can be only one" of this field - maybe we can murder the List ? Nah, we keep the list of one
        final List<MarcField> newCatalogCodeFields = marcRecord.getFields().stream()
                .filter(field -> field.getName().matches(CATALOGUE_CODE_FIELD)).collect(Collectors.toList());
        final List<MarcField> currentCatalogCodeFields = curRecord.getFields().stream()
                .filter(field -> field.getName().matches(CATALOGUE_CODE_FIELD)).collect(Collectors.toList());
        List<MarcField> newFields = createCatalogField(newCatalogCodeFields, currentCatalogCodeFields, groupId);
        new MarcRecordWriter(result).removeField("032");
        result.getFields().addAll(newFields);
    }

    /**
     * Update the incoming record with relevant fields - notes, subjects etc.
     *
     * @param marcRecord        The incoming record
     * @param groupId           The library number for the updating library
     * @return                  The (maybe) corrected record
     * @throws UpdateException  A failure is found in the incoming record
     * @throws VipCoreException A problem was met when trying to get information from the vip system
     */
    MarcRecord extensionRecordDataForRawRepo(MarcRecord marcRecord, String groupId) throws UpdateException, VipCoreException {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
        final String recId = reader.getRecordId();
        return LOGGER.<MarcRecord, UpdateException, VipCoreException>callChecked2(log -> {
            if (!rawRepo.recordExists(recId, RawRepo.COMMON_AGENCY)) {
                log.info("No existing record - returning same record");
                if (!"DBC".equals(reader.getValue("996", "a")) && reader.hasField("032")) {
                    if (!vipCoreService.isAuthRootOrCB(groupId)) {
                        final String msg = messages.getString("update.library.record.catalog.codes.not.cb");
                        log.error("Unable to create sub actions due to an error: {}", msg);
                        throw new UpdateException(msg);
                    }
                    validateCatalogCodes(marcRecord);
                    addCatalogField(marcRecord, marcRecord, new MarcRecord(), groupId);
                }
                return marcRecord;
            }
            final MarcRecord curRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchRecord(recId, RawRepo.COMMON_AGENCY).getContent());
            final MarcRecordReader curReader = new MarcRecordReader(curRecord);
            if (!"DBC".equals(curReader.getValue("996", "a"))) {
                log.info("Record is decentral - returning same record");
                validateCatalogCodes(marcRecord);
                return marcRecord;
            }

            // Other libraries are only allowed to enrich note and subject fields if the record is finally produced,
            // i.e. has a weekcode in the record that are older than current time
            // However that will be verified by AuthenticateRecordAction so at this point we assume everything is fine

            final MarcRecord result = new MarcRecord();
            log.info("Record exists and is common national record - setting extension fields");

            String extendableFieldsRx = createExtendableFieldsRx(groupId);

            if (extendableFieldsRx.isEmpty()) {
                log.info("Agency {} doesn't have permission to edit notes, subject or OVE fields - returning same record", groupId);
                return marcRecord;
            }
            extendableFieldsRx += "|" + CLASSIFICATION_FIELDS;
            log.info("Extendable fields: {} ", extendableFieldsRx);

            // Start by handling all the not-note/subject/OVE fields in the existing record
            for (MarcField curField : curRecord.getFields()) {
                if (!curField.getName().matches(extendableFieldsRx)) {
                    final MarcField fieldClone = new MarcField(curField);
                    result.getFields().add(fieldClone);
                }
            }

            // Handling field 032
            if (vipCoreService.isAuthRootOrCB(groupId)) {
                addCatalogField(result, marcRecord, curRecord, groupId);
            }

            // Handle note fields. These fields are handled individually
            // Each field can only be changed if either the field is missing in the existing record or if the field is owned
            // by another FBS library
            if (vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)) {
                addNoteFields(result, marcRecord, curRecord, groupId);
            }

            // Handle controlled subject fields. These fields are handled as a collection.
            // Fields are allowed to be updated if either there isn't a *& subfield or the value of *& starts with 7 (folkebibliotek)
            // If there are changes all fields will have *& updated to be the current library
            if (vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)) {
                addSubjectFields(result, marcRecord, curRecord, groupId);
            }

            addDK5Fields(result, marcRecord, reader, curRecord, curReader, groupId);

            new MarcRecordWriter(result).sort();

            return result;
        });
    }

    private void validateCatalogCodes(MarcRecord record) throws UpdateException {
        final List<MarcField> newCatalogCodeFields = record.getFields().stream()
                .filter(field -> field.getName().matches(CATALOGUE_CODE_FIELD)).collect(Collectors.toList());
        LOGGER.callChecked(log -> {
            for (MarcField field : newCatalogCodeFields) {
                for (MarcSubField subField : field.getSubfields()) {
                    if (subField.getName().equals("a")) {
                        final String msg = messages.getString("update.library.record.catalog.codes.x.only");
                        log.error("Unable to create sub actions due to an error: {}", msg);
                        throw new UpdateException(msg);
                    }
                }
            }
            return null;
        });
    }

    private String getOveCode(MarcField newCatalogField) {
        for (MarcSubField subField : newCatalogField.getSubfields()) {
            if ("x".equals(subField.getName()) && subField.getValue().startsWith("OVE")) return subField.getValue();
        }
        return "";
    }

    List<MarcSubField> updateOveAndAmp(MarcField field, String groupId, String oveCode) {
        List<MarcSubField> newList = new ArrayList<>();
        List<MarcSubField> subs = field.getSubfields();
        for (MarcSubField s1 : subs) {
            if ("&".equals(s1.getName())) continue;
            if ("x".equals(s1.getName()) && s1.getValue().startsWith("OVE")) continue;
            newList.add(s1);
        }
        if (!oveCode.isEmpty()) {
            newList.add(new MarcSubField("&", groupId));
            newList.add(new MarcSubField("x", oveCode));
        }
        return newList;

    }

    List<MarcSubField> clean(List<MarcSubField> subs, boolean all) {
        List<MarcSubField> newList = new ArrayList<>();
        if (all) {
            newList.addAll(subs);
        } else {
            for (MarcSubField s1 : subs) {
                if ("&".equals(s1.getName())) continue;
                if ("x".equals(s1.getName()) && s1.getValue().startsWith("OVE")) continue;
                newList.add(s1);
            }
        }
        return newList;
    }
    /**
     * @param l1  the field 032 in the incoming record
     * @param l2  the field 032 in the existing record
     * @param all if true, all subfields will be matched otherwise *&amp; and *x with OVE
     * @return    true if l1 and l2 contains the same no matter order
     */
    boolean compareCatalogSubFields(MarcField l1, MarcField l2, boolean all) {
        Collection<MarcSubField> s1 = clean(l1.getSubfields(), all);
        Collection<MarcSubField> s2 = clean(l2.getSubfields(), all);
        for (MarcSubField s : s1) {
            if (s2.contains(s)) {
                s2.remove(s);
            } else {
                return false;
            }
        }
        return s2.isEmpty();
    }

    /**
     *
     * @param newCatalogCodeFields      field 032 in incoming record - may be empty
     * @param currentCatalogCodeFields  field 032 in existing record - may also be empty
     * @return                          the new field 032
     * @throws UpdateException          some subfields not containing an OVE code were removed or added which is strictly forbidden
     * This function handle the case where a library attempts to add an OVE code to a DBC owned record. It also expects that there is only one field 032
     * Cases are :
     * 1: If the two fields are totally equal, then return one of them.
     * 2: If the new field is empty, then old, if it exists, may not contain subfields "a" and "x" except for "OVE", that will be an error - if it doesn't, the field
     * will be deleted
     * 3: If the old field is empty, then new field, if it exists, may not contain subfields "a" and "x" without "OVE", that will be an error. If it contains *xOVE then
     * add *&amp; and *xOVE
     * 4: If there are both an old and a new 032 field then it should be checked that there only are difference due to *&amp; and "OVE", that is,
     * if the fields only differ on *&amp; and *xOVE then those in the current shall be removed, the OVE code added and a *&amp; with groupId should be added
     */
    private List<MarcField> createCatalogField(List<MarcField> newCatalogCodeFields, List<MarcField> currentCatalogCodeFields, String groupId) throws UpdateException {
        List <MarcField> resultCatalogCodeFields = new ArrayList<>();
        return LOGGER.callChecked(log -> {
            MarcField currentWork;
            currentWork = currentCatalogCodeFields.isEmpty() ? new MarcField() : currentCatalogCodeFields.get(0);
            MarcField newWork;
            newWork = newCatalogCodeFields.isEmpty() ? new MarcField() : newCatalogCodeFields.get(0);

            // Point 1
            if (compareCatalogSubFields(newWork, currentWork, true)) {
                return currentCatalogCodeFields;
            }

            // Point 2
            if (newCatalogCodeFields.isEmpty()) {
                // No need to check if current is empty - that case is handled in point 1
                if (clean(currentWork.getSubfields(), true).isEmpty()) {
                    // remove any *& and OVE
                    return newCatalogCodeFields;
                } else {
                    final String msg = messages.getString("update.dbc.record.dbc.catalog.codes");
                    log.error("Unable to create sub actions due to an error: {}", msg);
                    throw new UpdateException(msg);
                }
            }

            // Point 3
            // * 3: If the old field is empty, then new field, if it exists, may not contain subfields "a" and "x" without "OVE", that will be an error. If it contains *xOVE then add necessary
            if (currentCatalogCodeFields.isEmpty()) {
                if (clean(newWork.getSubfields(), false).isEmpty()) {
                    String oveCode = getOveCode(newWork);
                    newWork.setSubfields(updateOveAndAmp(newWork, groupId, oveCode));
                    resultCatalogCodeFields.add(newWork);
                    return resultCatalogCodeFields;
                } else {
                    final String msg = messages.getString("update.dbc.record.dbc.catalog.codes");
                    log.error("Unable to create sub actions due to an error: {}", msg);
                    throw new UpdateException(msg);
                }
            }

            // Point 4
            // * 4: If there are both an old and a new 032 field then it should be checked that there only are difference due to *& and "OVE", that is,
            // * if the fields only differ on *& and *xOVE then those in the current shall be removed, the OVE code added and a *& with groupId should be added
            if (compareCatalogSubFields(newWork, currentWork, false)) {
                String oveCode = getOveCode(newWork);
                newWork.setSubfields(updateOveAndAmp(newWork, groupId, oveCode));
                resultCatalogCodeFields.add(newWork);
                return resultCatalogCodeFields;
            } else {
                final String msg = messages.getString("update.dbc.record.dbc.catalog.codes");
                log.error("Unable to create sub actions due to an error: {}", msg);
                throw new UpdateException(msg);
            }
        });
    }

    private MarcField copyWithNewAmpersand(MarcField marcField, String groupId) {
        final MarcField fieldClone = new MarcField(marcField);
        new MarcFieldWriter(fieldClone).removeSubfield("&");
        fieldClone.getSubfields().add(0, new MarcSubField("&", groupId));
        return fieldClone;
    }

    public boolean marcFieldsEqualsIgnoreAmpersand(List<MarcField> l1, List<MarcField> l2) {
        final List<MarcField> l1Clone = new ArrayList<>();
        final List<MarcField> l2Clone = new ArrayList<>();

        l1.forEach(f -> l1Clone.add(new MarcField(f)));
        l1Clone.forEach(f -> new MarcFieldWriter(f).removeSubfield("&"));

        l2.forEach(f -> l2Clone.add(new MarcField(f)));
        l2Clone.forEach(f -> new MarcFieldWriter(f).removeSubfield("&"));

        final List<MarcField> l3Clone = new ArrayList<>(l1Clone);
        l1Clone.forEach(f -> {
            if (l2Clone.contains(f)) {
                l3Clone.remove(f);
                l2Clone.remove(f);
            }
        });
        return l3Clone.equals(l2Clone);
    }

    /**
     * This function checks whether the input field exist and is identical with a field in the record
     *
     * @param field      field to compare
     * @param marcRecord record to compare the field in
     * @return boolean True if the record has a field that matches field
     */
    boolean isFieldChangedInOtherRecord(MarcField field, MarcRecord marcRecord) {
        final MarcRecord cloneMarcRecord = new MarcRecord(marcRecord);
        final MarcRecordReader cloneMarcRecordReader = new MarcRecordReader(cloneMarcRecord);
        final MarcRecordWriter cloneMarcRecordWriter = new MarcRecordWriter(cloneMarcRecord);
        final MarcFieldReader fieldReader = new MarcFieldReader(field);

        if (field.getName().equals("001")) {
            if (fieldReader.hasSubfield("c")) {
                cloneMarcRecordWriter.addOrReplaceSubfield("001", "c", fieldReader.getValue("c"));
            }

            if (fieldReader.hasSubfield("d")) {
                cloneMarcRecordWriter.addOrReplaceSubfield("001", "d", fieldReader.getValue("d"));
            }
        }

        // Handle field which has subfields from expanded authority records which is not allowed in the template
        if (Arrays.asList("900", "910", "945", "952").contains(field.getName())) {
            for (MarcField cloneField : cloneMarcRecordReader.getFieldAll(field.getName())) {
                final MarcFieldWriter cloneFieldWriter = new MarcFieldWriter(cloneField);

                for (String subfieldName : Arrays.asList("w", "x", "z")) {
                    if (fieldReader.hasSubfield(subfieldName)) {
                        cloneFieldWriter.addOrReplaceSubfield(subfieldName, fieldReader.getValue(subfieldName));
                    } else {
                        cloneFieldWriter.removeSubfield(subfieldName);
                    }
                }
            }
        }

        for (MarcField cf : cloneMarcRecordReader.getFieldAll(field.getName())) {
            if (cf.equals(field)) {
                return false;
            }
        }

        return true;
    }

    /**
     * This function returns a list of allowed extendable fields as a regex string
     *
     * @param agencyId AgencyId of the library to check for
     * @return String containing fields which can be used in regex
     * @throws VipCoreException In case VipCore throws exception
     */
    String createExtendableFieldsRx(String agencyId) throws VipCoreException {
        return LOGGER.callChecked(log -> {
            String extendableFields = "";

            if (vipCoreService.isAuthRootOrCB(agencyId)) {
                extendableFields += CATALOGUE_CODE_FIELD;
            }

            if (vipCoreService.hasFeature(agencyId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)) {
                if (!extendableFields.isEmpty()) {
                    extendableFields += "|";
                }
                extendableFields += EXTENDABLE_NOTE_FIELDS;
            }

            if (vipCoreService.hasFeature(agencyId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)) {
                if (!extendableFields.isEmpty()) {
                    extendableFields += "|";
                }
                extendableFields += EXTENDABLE_CONTROLLED_SUBJECT_FIELDS;
                extendableFields += "|";
                extendableFields += EXTENDABLE_SUBJECT_FIELDS;
            }

            log.info("Agency {} can change the following fields in a national common record: {}", agencyId, extendableFields);

            return extendableFields;
        });
    }

    /**
     * Checks if this record is a national common record.
     *
     * @param marcRecord Record.
     * @return {boolean} True / False.
     */
    public boolean isPublishedDBCRecord(MarcRecord marcRecord) throws UpdateException {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);

        final String recordType = reader.getValue("004", "a");

        if (Arrays.asList("h", "s").contains(recordType)) {
            final RecordId recordId = new RecordId(reader.getRecordId(), reader.getAgencyIdAsInt());

            final Set<RecordId> children = rawRepo.children(recordId);
            // Children includes both enrichment and other record types, e.g. article. Underlying records of a 870970
            // record will always have agency id 870970 therefor we filter records with that agency id
            final Set<RecordId> commonChildren = children.stream().filter(r -> r.getAgencyId() == RawRepo.COMMON_AGENCY).collect(Collectors.toSet());

            for (RecordId child : commonChildren) {
                final Record childRecord = rawRepo.fetchRecord(child.getBibliographicRecordId(), child.getAgencyId());
                final MarcRecord childMarcRecord = RecordContentTransformer.decodeRecord(childRecord.getContent());

                if (isPublishedDBCRecord(childMarcRecord)) {
                    return true;
                }
            }
            return false;
        } else {
            return reader.hasValue("996", "a", "DBC") &&
                    CatalogExtractionCode.isPublishedIgnoreCatalogCodes(marcRecord);
        }
    }

    /**
     * Validate whether the record is legal in regard to note and subject fields and the permissions of the group
     *
     * @param marcRecord The incoming record
     * @param groupId    GroupId of the requester
     * @return List of validation errors (ok returns empty list)
     * @throws UpdateException if communication with RawRepo or OpenAgency fails
     */
    public List<MessageEntryDTO> authenticateCommonRecordExtraFields(MarcRecord marcRecord, String groupId)
            throws UpdateException, VipCoreException {
        return LOGGER.<List<MessageEntryDTO>, UpdateException, VipCoreException>callChecked2(log -> {
            final List<MessageEntryDTO> result = new ArrayList<>();
            final MarcRecordReader reader = new MarcRecordReader(marcRecord);

            final ResourceBundle resourceBundle = ResourceBundles.getBundle("messages");

            final String recId = reader.getRecordId();
            if (!rawRepo.recordExists(recId, RawRepo.COMMON_AGENCY)) {
                return result;
            }
            MarcRecord curRecord;
            try {
                final Map<String, MarcRecord> curRecordCollection = rawRepo.fetchRecordCollection(recId, RawRepo.COMMON_AGENCY);
                curRecord = ExpandCommonMarcRecord.expandMarcRecord(curRecordCollection, recId);
            } catch (RawRepoException e) {
                throw new UpdateException("Exception while loading current record", e);
            }
            final MarcRecordReader curReader = new MarcRecordReader(curRecord);

            if (!isPublishedDBCRecord(curRecord)) {
                return result;
            }

            if (!vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)) {
                log.info("AgencyId {} does not have feature AUTH_COMMON_NOTES in vipcore - checking for changed note fields", groupId);
                // Check if all fields in the incoming record are in the existing record
                for (MarcField field : marcRecord.getFields()) {
                    if (field.getName().matches(EXTENDABLE_NOTE_FIELDS) && isFieldChangedInOtherRecord(field, curRecord)) {
                        final String message = String.format(resourceBundle.getString("notes.subjects.edit.field.error"), groupId, field.getName(), recId);
                        result.add(createMessageDTO(message));
                    }
                }
                // Check if all fields in the existing record are in the incoming record
                for (MarcField field : curRecord.getFields()) {
                    if (field.getName().matches(EXTENDABLE_NOTE_FIELDS) && isFieldChangedInOtherRecord(field, marcRecord)) {
                        final String fieldName = field.getName();
                        if (curReader.getFieldAll(fieldName).size() != reader.getFieldAll(fieldName).size()) {
                            final String message = String.format(resourceBundle.getString("notes.subjects.delete.field.error"), groupId, fieldName, recId);
                            result.add(createMessageDTO(message));
                        }
                    }
                }
            }

            if (!vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)) {
                final MarcRecord expandedCurrentRecord;
                expandedCurrentRecord = getExpandedRecord(curRecord);

                log.info("AgencyId {} does not have feature AUTH_COMMON_SUBJECTS in vipcore - checking for changed subject fields", groupId);
                // Check if all fields in the incoming record are in the existing record
                for (MarcField field : marcRecord.getFields()) {
                    if (field.getName().matches(EXTENDABLE_CONTROLLED_SUBJECT_FIELDS) &&
                            isFieldChangedInOtherRecord(field, expandedCurrentRecord)) {
                        final String message = String.format(resourceBundle.getString("notes.subjects.edit.field.error"), groupId, field.getName(), recId);
                        result.add(createMessageDTO(message));
                    }
                }
                // Check if all fields in the existing record are in the incoming record
                for (MarcField field : expandedCurrentRecord.getFields()) {
                    if (field.getName().matches(EXTENDABLE_CONTROLLED_SUBJECT_FIELDS) &&
                            isFieldChangedInOtherRecord(field, marcRecord)) {
                        final String fieldName = field.getName();
                        if (curReader.getFieldAll(fieldName).size() != reader.getFieldAll(fieldName).size()) {
                            final String message = String.format(resourceBundle.getString("notes.subjects.delete.field.error"), groupId, fieldName, recId);
                            result.add(createMessageDTO(message));
                        }
                    }
                }
            }

            if (vipCoreService.getLibraryGroup(groupId).isFBS() && !isPublishedDBCRecord(curRecord)) {
                final String message = String.format(resourceBundle.getString("notes.subjects.not.in.production"), groupId, recId);
                result.add(createMessageDTO(message));
            }

            return result;
        });
    }

    private MessageEntryDTO createMessageDTO(String message) {
        MessageEntryDTO result = new MessageEntryDTO();

        result.setMessage(message);
        result.setType(TypeEnumDTO.ERROR);

        return result;
    }

    public MarcRecord collapse(MarcRecord marcRecord, MarcRecord currentRecord, String groupId, boolean isNationalCommonRecord) throws VipCoreException {
        final MarcRecord collapsedRecord = new MarcRecord(currentRecord);
        final List<String> fieldsToCopy = new ArrayList<>();

        if (isNationalCommonRecord) {
            fieldsToCopy.addAll(Arrays.asList(createExtendableFieldsRx(groupId).split("\\|")));
        } else {
            fieldsToCopy.addAll(Arrays.asList(EXTENDABLE_NOTE_FIELDS.split("\\|")));
            fieldsToCopy.addAll(Arrays.asList(EXTENDABLE_SUBJECT_FIELDS.split("\\|")));
        }

        // We need to copy 996 from the incoming record as well, as that field could have been modified in an earlier action
        // But because the Arrays.asList returns an immutable list we need to copy the content to another list.
        fieldsToCopy.add("245");
        fieldsToCopy.add("521");
        fieldsToCopy.add("652");
        fieldsToCopy.add("996");
        final MarcRecordWriter curWriter = new MarcRecordWriter(collapsedRecord);
        curWriter.removeFields(fieldsToCopy);
        curWriter.copyFieldsFromRecord(fieldsToCopy, marcRecord);

        return collapsedRecord;
    }

}
