/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.jsonb.JSONBContext;
import dk.dbc.jsonb.JSONBException;
import dk.dbc.openagency.http.OpenAgencyException;
import dk.dbc.openagency.http.VipCoreHttpClient;
import dk.dbc.vipcore.marshallers.LibraryRule;
import dk.dbc.vipcore.marshallers.LibraryRules;
import dk.dbc.vipcore.marshallers.LibraryRulesResponse;
import dk.dbc.vipcore.marshallers.LibraryRulesRequest;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Stateless
public class VipCoreService {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(VipCoreService.class);

    private static final JSONBContext jsonbContext = new JSONBContext();

    @EJB
    private VipCoreHttpClient vipCoreHttpClient;

    public enum Rule {
        CREATE_ENRICHMENTS("create_enrichments"),
        PART_OF_BIBLIOTEK_DK("part_of_bibliotek_dk"),
        PART_OF_DANBIB("part_of_danbib"),
        USE_ENRICHMENTS("use_enrichments"),
        USE_LOCALDATA_STREAM("use_localdata_stream"),
        USE_HOLDINGS_ITEM("use_holdings_item"),
        AUTH_ROOT("auth_root"),
        AUTH_COMMON_SUBJECTS("auth_common_subjects"),
        AUTH_COMMON_NOTES("auth_common_notes"),
        AUTH_DBC_RECORDS("auth_dbc_records"),
        AUTH_PUBLIC_LIB_COMMON_RECORD("auth_public_lib_common_record"),
        AUTH_RET_RECORD("auth_ret_record"),
        AUTH_AGENCY_COMMON_RECORD("auth_agency_common_record"),
        AUTH_EXPORT_HOLDINGS("auth_export_holdings"),
        AUTH_CREATE_COMMON_RECORD("auth_create_common_record"),
        AUTH_ADD_DK5_TO_PHD_ALLOWED("auth_create_common_record"),
        AUTH_METACOMPASS("auth_metacompass"),
        CATALOGING_TEMPLATE_SET("cataloging_template_set");

        private final String value;

        private Rule(String value) {
            this.value = value;
        }

        public String getValue() {
            return this.value;
        }

        public String toString() {
            return this.getValue();
        }
    }

    public boolean hasFeature(String agencyId, Rule feature) throws OpenAgencyException {
        LOGGER.entry(agencyId, feature);
        StopWatch watch = new Log4JStopWatch("service.vipcore.hasFeature");

        try {
            final List<LibraryRules> libraryRules = getLibraryRulesByAgencyId(agencyId);

            for (LibraryRule libraryRule : libraryRules.get(0).getLibraryRule()) {
                if (libraryRule.getName().equals(feature.getValue())) {
                    LOGGER.debug("LibraryRule {} for {} is {}", agencyId, feature.getValue(), libraryRule);
                    if (libraryRule.getBool() != null) {
                        return libraryRule.getBool();
                    }
                }
            }

            return false;
        } finally {
            watch.stop();
            LOGGER.exit();
        }
    }

    public LibraryGroup getLibraryGroup(String agencyId) throws OpenAgencyException, UpdateException {
        LOGGER.entry(agencyId);
        StopWatch watch = new Log4JStopWatch("service.vipcore.getLibraryGroup");
        String reply = "";
        LibraryGroup result;
        try {
            final List<LibraryRules> libraryRules = getLibraryRulesByAgencyId(agencyId);

            for (LibraryRule libraryRule : libraryRules.get(0).getLibraryRule()) {
                if (libraryRule.getName().equals(Rule.CATALOGING_TEMPLATE_SET.getValue())) {
                    LOGGER.debug("LibraryRule {} for {} is {}", agencyId, Rule.CATALOGING_TEMPLATE_SET.getValue(), libraryRule);
                    reply = libraryRule.getString();
                }
            }

            switch (reply) {
                case "dbc":
                case "ffu":
                case "lokbib":
                    result = LibraryGroup.DBC;
                    break;
                case "ph":
                    result = LibraryGroup.PH;
                    break;
                case "fbs":
                case "fbslokal":
                case "skole":
                    result = LibraryGroup.FBS;
                    break;
                default:
                    throw new UpdateException("Unknown library group: " + reply);
            }

            LOGGER.info("Agency '{}' has LibraryGroup {}", agencyId, result);
            return result;
        } finally {
            watch.stop();
            LOGGER.exit();
        }
    }

    public String getTemplateGroup(String agencyId) throws OpenAgencyException, UpdateException {
        LOGGER.entry(agencyId);
        StopWatch watch = new Log4JStopWatch("service.vipcore.getTemplateGroup");
        String reply = "";
        LibraryGroup result;
        try {
            final List<LibraryRules> libraryRules = getLibraryRulesByAgencyId(agencyId);

            for (LibraryRule libraryRule : libraryRules.get(0).getLibraryRule()) {
                if (libraryRule.getName().equals(Rule.CATALOGING_TEMPLATE_SET.getValue())) {
                    LOGGER.debug("LibraryRule {} for {} is {}", agencyId, Rule.CATALOGING_TEMPLATE_SET.getValue(), libraryRule);
                    LOGGER.info("Agency '{}' has LibraryGroup {}", agencyId, libraryRule.getString());
                    return libraryRule.getString();
                }
            }

            throw new UpdateException("Could not find templateGroup for " + agencyId);
        } finally {
            watch.stop();
            LOGGER.exit();
        }
    }

    public Set<String> getLokbibLibraries() throws OpenAgencyException {
        LOGGER.entry();
        Set<String> result = null;
        try {
            result = getLibrariesByCatalogingTemplateSet("lokbib");
            return result;
        } finally {
            LOGGER.exit(result);
        }
    }

    public Set<String> getPHLibraries() throws OpenAgencyException {
        LOGGER.entry();
        Set<String> result = null;
        try {
            result = getLibrariesByCatalogingTemplateSet("ph");
            return result;
        } finally {
            LOGGER.exit(result);
        }
    }

    public Set<String> getFFULibraries() throws OpenAgencyException {
        LOGGER.entry();
        Set<String> result = null;
        try {
            result = getLibrariesByCatalogingTemplateSet("ffu");
            return result;
        } finally {
            LOGGER.exit(result);
        }
    }

    public Set<String> getAllowedLibraryRules(String agencyId) throws OpenAgencyException {
        LOGGER.entry(agencyId, agencyId);
        StopWatch watch = new Log4JStopWatch("service.vipcore.getAllowedLibraryRules");
        final Set<String> result = new HashSet<>();
        try {
            List<LibraryRules> libraryRules = getLibraryRulesByAgencyId(agencyId);

            for (LibraryRule libraryRule : libraryRules.get(0).getLibraryRule()) {
                if (libraryRule.getBool() != null && libraryRule.getBool()) {
                    result.add(libraryRule.getName());
                }
            }

            return result;
        } finally {
            watch.stop();
            LOGGER.exit();
        }
    }

    private List<LibraryRules> getLibraryRulesByAgencyId(String agencyId) throws OpenAgencyException {
        LOGGER.entry(agencyId);
        StopWatch watch = new Log4JStopWatch("service.vipcore.getLibraryRulesByAgencyId");

        try {
            final LibraryRulesRequest libraryRulesRequest = new LibraryRulesRequest();
            libraryRulesRequest.setAgencyId(agencyId);

            final String response = vipCoreHttpClient.postToVipCore(jsonbContext.marshall(libraryRulesRequest), VipCoreHttpClient.LIBRARY_RULES_PATH);
            final LibraryRulesResponse libraryRulesResponse = jsonbContext.unmarshall(response, LibraryRulesResponse.class);

            return libraryRulesResponse.getLibraryRules();
        } catch (JSONBException e) {
            LOGGER.error("Caught unexpected JSONBException", e);
            throw new OpenAgencyException("Caught unexpected JSONBException", e);
        } finally {
            watch.stop();
            LOGGER.exit();
        }
    }

    private List<LibraryRules> getLibraryRulesByLibraryRule(Rule rule, String value) throws OpenAgencyException {
        LOGGER.entry(rule);
        final StopWatch watch = new Log4JStopWatch("service.vipcore.getLibraryRulesByLibraryRule");
        try {
            final LibraryRule libraryRuleRequest = new LibraryRule();
            libraryRuleRequest.setName(rule.getValue());
            libraryRuleRequest.setString(value);
            final List<LibraryRule> libraryRuleListRequest = Collections.singletonList(libraryRuleRequest);
            final LibraryRulesRequest libraryRulesRequest = new LibraryRulesRequest();
            libraryRulesRequest.setLibraryRule(libraryRuleListRequest);

            final String response = vipCoreHttpClient.postToVipCore(jsonbContext.marshall(libraryRulesRequest), VipCoreHttpClient.LIBRARY_RULES_PATH);
            final LibraryRulesResponse libraryRulesResponse = jsonbContext.unmarshall(response, LibraryRulesResponse.class);

            return libraryRulesResponse.getLibraryRules();
        } catch (JSONBException e) {
            LOGGER.error("Caught unexpected JSONBException", e);
            throw new OpenAgencyException("Caught unexpected JSONBException", e);
        } finally {
            watch.stop();
            LOGGER.exit();
        }
    }

    private Set<String> getLibrariesByCatalogingTemplateSet(String catalogingTemplateSet) throws OpenAgencyException {
        LOGGER.entry(catalogingTemplateSet);
        final StopWatch watch = new Log4JStopWatch("service.vipcore.getLibrariesByCatalogingTemplateSet");
        final Set<String> result = new HashSet<>();
        try {
            final List<LibraryRules> libraryRulesList = getLibraryRulesByLibraryRule(Rule.CATALOGING_TEMPLATE_SET, catalogingTemplateSet);

            for (LibraryRules libraryRules : libraryRulesList) {
                result.add(libraryRules.getAgencyId());
            }

            return result;
        } finally {
            watch.stop();
            LOGGER.exit();
        }
    }
}
