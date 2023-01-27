/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.updateservice.utils.DeferredLogger;
import dk.dbc.vipcore.exception.VipCoreException;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector;
import dk.dbc.vipcore.marshallers.LibraryRule;
import dk.dbc.vipcore.marshallers.LibraryRules;
import dk.dbc.vipcore.marshallers.LibraryRulesRequest;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Stateless
public class VipCoreService {
    private static final DeferredLogger LOGGER = new DeferredLogger(VipCoreService.class);
    private static final String LIBRARY_RULE_MESSAGE = "LibraryRule {} for {} is {}";

    @Inject
    private VipCoreLibraryRulesConnector vipCoreLibraryRulesConnector;

    /**
     * Could be more effectve with a variant of hasFeature that checks for the two states
     * but the code look cleaner, and we imagine that the cache will be fast.
     * @param agencyId the agency that is to be checked
     * @return return true if one of the two rules are set, otherwise false
     * @throws VipCoreException something went horribly wrong in the call to vipcore
     */
    public boolean isAuthRootOrCB(String agencyId) throws VipCoreException {
        final StopWatch watch = new Log4JStopWatch("service.vipcore.hasFeature");
        try {
            return hasFeature(agencyId, VipCoreLibraryRulesConnector.Rule.REGIONAL_OBLIGATIONS) ||
                    hasFeature(agencyId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT);
            } finally {
            watch.stop();
        }
    }

    public boolean hasFeature(String agencyId, VipCoreLibraryRulesConnector.Rule feature) throws VipCoreException {
        final StopWatch watch = new Log4JStopWatch("service.vipcore.hasFeature");
        try {
            final LibraryRules libraryRules = vipCoreLibraryRulesConnector.getLibraryRulesByAgencyId(agencyId);
            return LOGGER.call(log -> {
                for (LibraryRule libraryRule : libraryRules.getLibraryRule()) {
                    if (libraryRule.getName().equals(feature.getValue())) {
                        log.debug(LIBRARY_RULE_MESSAGE, agencyId, feature.getValue(), libraryRule);
                        if (libraryRule.getBool() != null) {
                            return libraryRule.getBool();
                        }
                    }
                }
                return false;
            });
        } finally {
            watch.stop();
        }
    }

    public LibraryGroup getLibraryGroup(String agencyId) throws VipCoreException, UpdateException {
        final StopWatch watch = new Log4JStopWatch("service.vipcore.getLibraryGroup");
        try {
            final LibraryRules libraryRules = vipCoreLibraryRulesConnector.getLibraryRulesByAgencyId(agencyId);
            String ruleGroupName = libraryRules.getLibraryRule().stream()
                    .filter(libraryRule -> libraryRule.getName().equals(VipCoreLibraryRulesConnector.Rule.CATALOGING_TEMPLATE_SET.getValue()))
                    .findFirst()
                    .map(LibraryRule::getString)
                    .orElseThrow(() -> new UpdateException("Found no library rule for agencyId : " + agencyId));
            LibraryGroup libraryGroup = LibraryGroup.fromRule(ruleGroupName)
                    .orElseThrow(() -> new UpdateException("Got an unknown library group: " + ruleGroupName + " for agencyId: " + agencyId));
            LOGGER.use(log -> log.info("Agency '{}' has LibraryGroup {}", agencyId, libraryGroup));
            return libraryGroup;
        } finally {
            watch.stop();
        }
    }

    public String getTemplateGroup(String agencyId) throws VipCoreException, UpdateException {
        StopWatch watch = new Log4JStopWatch("service.vipcore.getTemplateGroup");
        try {
            final LibraryRules libraryRules = vipCoreLibraryRulesConnector.getLibraryRulesByAgencyId(agencyId);
            return LOGGER.callChecked(log -> {
                for (LibraryRule libraryRule : libraryRules.getLibraryRule()) {
                    if (libraryRule.getName().equals(VipCoreLibraryRulesConnector.Rule.CATALOGING_TEMPLATE_SET.getValue())) {
                        log.debug(LIBRARY_RULE_MESSAGE, agencyId, VipCoreLibraryRulesConnector.Rule.CATALOGING_TEMPLATE_SET.getValue(), libraryRule);
                        log.info("Agency '{}' has LibraryGroup {}", agencyId, libraryRule.getString());
                        return libraryRule.getString();
                    }
                }

                throw new UpdateException("Could not find templateGroup for " + agencyId);
            });
        } finally {
            watch.stop();
        }
    }

    public Set<String> getLokbibLibraries() throws VipCoreException {
        return getLibrariesByCatalogingTemplateSet("lokbib");
    }

    public Set<String> getPHLibraries() throws VipCoreException {
        return getLibrariesByCatalogingTemplateSet("ph");
    }

    public Set<String> getFFULibraries() throws VipCoreException {
        return getLibrariesByCatalogingTemplateSet("ffu");
    }

    private Set<String> getLibrariesByCatalogingTemplateSet(String catalogingTemplateSet) throws VipCoreException {
        final LibraryRule libraryRule = new LibraryRule();
        libraryRule.setName(VipCoreLibraryRulesConnector.Rule.CATALOGING_TEMPLATE_SET.getValue());
        libraryRule.setString(catalogingTemplateSet);
        final LibraryRulesRequest request = new LibraryRulesRequest();
        request.setLibraryRule(Collections.singletonList(libraryRule));

        return vipCoreLibraryRulesConnector.getLibraries(request);
    }


    public Set<String> getAllowedLibraryRules(String agencyId) throws VipCoreException {
        final StopWatch watch = new Log4JStopWatch("service.vipcore.getAllowedLibraryRules");
        final Set<String> result = new HashSet<>();
        try {
            final LibraryRules libraryRules = vipCoreLibraryRulesConnector.getLibraryRulesByAgencyId(agencyId);

            for (LibraryRule libraryRule : libraryRules.getLibraryRule()) {
                if (libraryRule.getBool() != null && libraryRule.getBool()) {
                    result.add(libraryRule.getName());
                }
            }

            return result;
        } finally {
            watch.stop();
        }
    }

}
