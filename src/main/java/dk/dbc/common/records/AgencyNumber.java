package dk.dbc.common.records;

/**
 * Utility class to represent an agency number.
 */
public class AgencyNumber {
    private static final Integer AGENCY_NUMBER_SIZE = 6;

    private Integer agencyId;

    public AgencyNumber(Integer agencyId) {
        this.agencyId = agencyId;
    }

    /**
     * Constructs an agency number from a string.
     * <p>
     * The string is assumed to be an integer in 10 radix.
     * </p>
     *
     * @param agencyId
     */
    public AgencyNumber(String agencyId) {
        setAgencyId(agencyId);
    }

    public Integer getAgencyId() {
        return agencyId;
    }

    public void setAgencyId(Integer agencyId) {
        this.agencyId = agencyId;
    }

    /**
     * Sets the agency number from a string.
     * <p>
     * The string is assumed to be an integer in 10 radix.
     * </p>
     *
     * @param agencyId The agency number.
     */
    public void setAgencyId(String agencyId) {
        this.agencyId = Integer.valueOf(agencyId, 10);
    }

    /**
     * Converts the agency id to a String of minimum length of <code>AGENCY_NUMBER_SIZE</code>
     * <p>
     * If the number as a string is shorter than <code>AGENCY_NUMBER_SIZE</code> when it is prepended with
     * the char '0'.
     * </p>
     *
     * @return The agency id as a string.
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(agencyId.toString());

        while (result.length() < AGENCY_NUMBER_SIZE) {
            result.insert(0, "0");
        }

        return result.toString();
    }

}
