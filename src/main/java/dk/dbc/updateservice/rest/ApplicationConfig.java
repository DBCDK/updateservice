package dk.dbc.updateservice.rest;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

import java.util.Set;

/**
 * Created by stp on 25/11/15.
 */
@ApplicationPath("rest")
public class ApplicationConfig extends Application {
    public static final long LOG_DURATION_THRESHOLD_MS = 10;
    private static final Set<Class<?>> CLASSES = Set.of(
            StatusService.class,
            HowruService.class,
            DoubleRecordCheckServiceRest.class,
            ClassificationCheckServiceRest.class,
            UpdateServiceRest.class,
            OpenBuildRest.class
    );

    @Override
    public Set<Class<?>> getClasses() {
        return CLASSES;
    }
}
