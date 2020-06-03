/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.rest;

import javax.ws.rs.core.Application;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by stp on 25/11/15.
 */
@javax.ws.rs.ApplicationPath("rest")
public class ApplicationConfig extends Application {
    private static final Set<Class<?>> classes = new HashSet<>(
            Arrays.asList(
                    StatusService.class,
                    HowruService.class,
                    DoubleRecordCheckService.class,
                    ClassificationCheckService.class,
                    ClassificationCheckServiceRest.class,
                    UpdateServiceRest.class,
                    OpenBuildRest.class)
    );

    @Override
    public Set<Class<?>> getClasses() {
        return classes;
    }
}
