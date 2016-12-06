package dk.dbc.updateservice.actions;

import java.util.MissingResourceException;

public class UpdateMode {

    private Mode mode;

    public enum Mode {
        FBS, DATAIO
    }

    public UpdateMode(Mode mode) {
        this.mode = mode;
    }

    public UpdateMode(String s) throws MissingResourceException {
        if (s.equals("fbs")) {
            this.mode = Mode.FBS;
        } else if (s.equals("dataio")) {
            this.mode = Mode.DATAIO;
        } else {
            throw new MissingResourceException("Unknown mode!", UpdateMode.class.toString(), "UpdateMode");
        }
    }

    public Boolean isFBSMode() {
        return this.mode == Mode.FBS;
    }

    public Boolean isDataIOMode() {
        return this.mode == Mode.DATAIO;
    }

}
