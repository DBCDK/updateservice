/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.vipcore.exception.VipCoreException;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector;
import dk.dbc.vipcore.marshallers.LibraryRule;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Stateless
public class VipCoreService {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(VipCoreService.class);

    @Inject
    private VipCoreLibraryRulesConnector vipCoreLibraryRulesConnector;

    public boolean hasFeature(String agencyId, VipCoreLibraryRulesConnector.Rule feature) throws VipCoreException {
        LOGGER.entry(agencyId, feature);
        StopWatch watch = new Log4JStopWatch("service.vipcore.hasFeature");

        try {
            final List<LibraryRule> libraryRuleList = vipCoreLibraryRulesConnector.getLibraryRulesByAgencyId(agencyId);

            for (LibraryRule libraryRule : libraryRuleList) {
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

    public LibraryGroup getLibraryGroup(String agencyId) throws VipCoreException, UpdateException {
        LOGGER.entry(agencyId);
        StopWatch watch = new Log4JStopWatch("service.vipcore.getLibraryGroup");
        String reply = "";
        LibraryGroup result;
        try {
            final List<LibraryRule> libraryRuleList = vipCoreLibraryRulesConnector.getLibraryRulesByAgencyId(agencyId);

            for (LibraryRule libraryRule : libraryRuleList) {
                if (libraryRule.getName().equals(VipCoreLibraryRulesConnector.Rule.CATALOGING_TEMPLATE_SET.getValue())) {
                    LOGGER.debug("LibraryRule {} for {} is {}", agencyId, VipCoreLibraryRulesConnector.Rule.CATALOGING_TEMPLATE_SET.getValue(), libraryRule);
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

    public String getTemplateGroup(String agencyId) throws VipCoreException, UpdateException {
        LOGGER.entry(agencyId);
        StopWatch watch = new Log4JStopWatch("service.vipcore.getTemplateGroup");
        try {
            final List<LibraryRule> libraryRuleList = vipCoreLibraryRulesConnector.getLibraryRulesByAgencyId(agencyId);

            for (LibraryRule libraryRule : libraryRuleList) {
                if (libraryRule.getName().equals(VipCoreLibraryRulesConnector.Rule.CATALOGING_TEMPLATE_SET.getValue())) {
                    LOGGER.debug("LibraryRule {} for {} is {}", agencyId, VipCoreLibraryRulesConnector.Rule.CATALOGING_TEMPLATE_SET.getValue(), libraryRule);
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

    public Set<String> getLokbibLibraries() throws VipCoreException {
        LOGGER.entry();
        Set<String> result = null;
        try {
            result = vipCoreLibraryRulesConnector.getLibrariesByLibraryRule(VipCoreLibraryRulesConnector.Rule.CATALOGING_TEMPLATE_SET, "lokbib");
            return result;
        } finally {
            LOGGER.exit(result);
        }
    }

    public Set<String> getPHLibraries() throws VipCoreException {
        LOGGER.entry();
        Set<String> result = null;
        try {
            result = vipCoreLibraryRulesConnector.getLibrariesByLibraryRule(VipCoreLibraryRulesConnector.Rule.CATALOGING_TEMPLATE_SET, "ph");
            return result;
        } finally {
            LOGGER.exit(result);
        }
    }

    public Set<String> getFFULibraries() throws VipCoreException {
        LOGGER.entry();
        Set<String> result = null;
        try {
            result = vipCoreLibraryRulesConnector.getLibrariesByLibraryRule(VipCoreLibraryRulesConnector.Rule.CATALOGING_TEMPLATE_SET, "ffu");
            return result;
        } finally {
            LOGGER.exit(result);
        }
    }

    public Set<String> getAllowedLibraryRules(String agencyId) throws VipCoreException {
        LOGGER.entry(agencyId, agencyId);
        StopWatch watch = new Log4JStopWatch("service.vipcore.getAllowedLibraryRules");
        final Set<String> result = new HashSet<>();
        try {
            List<LibraryRule> libraryRuleList = vipCoreLibraryRulesConnector.getLibraryRulesByAgencyId(agencyId);

            for (LibraryRule libraryRule : libraryRuleList) {
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

}
