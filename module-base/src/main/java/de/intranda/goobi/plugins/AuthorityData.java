package de.intranda.goobi.plugins;

import org.apache.commons.lang3.StringUtils;

import lombok.Getter;

/**
 * Holds the resolved authority information for a single metadata value. If the excel file contained a complete uri of one of the supported
 * authorities, the value is used as it is, otherwise the plain identifier is extracted from it.
 */
@Getter
public class AuthorityData {

    private static final String GND = "gnd";
    private static final String GEONAMES = "geonames";
    private static final String VIAF = "viaf";

    /** authority to use when neither the configuration nor the value itself tells which one it is */
    private static final String DEFAULT_TYPE = GND;

    private final String name;
    private final String uri;
    private final String value;

    private AuthorityData(String name, String uri, String value) {
        this.name = name;
        this.uri = uri;
        this.value = value;
    }

    /**
     * create the authority information for a value
     * 
     * @param rawValue content of the configured authority column, either a complete uri or a plain identifier
     * @param configuredType name of the authority as it was configured, may be blank
     * @return the authority information or null if the value does not contain a usable identifier
     */
    public static AuthorityData resolve(String rawValue, String configuredType) {
        if (StringUtils.isBlank(rawValue)) {
            return null;
        }
        String trimmedValue = rawValue.trim();

        String detectedType = detectType(trimmedValue);
        String type = normalizeType(configuredType);
        if (type == null) {
            type = detectedType;
        }
        if (type == null) {
            type = DEFAULT_TYPE;
        }

        // a complete uri of a supported authority is used as it is, everything else is reduced to its identifier
        String authorityValue = detectedType != null ? trimmedValue : extractId(trimmedValue);
        if (StringUtils.isBlank(authorityValue)) {
            return null;
        }
        return new AuthorityData(type, getBaseUri(type), authorityValue);
    }

    /**
     * check if a configured name belongs to one of the supported authorities
     * 
     * @param configuredType name of the authority as it was configured
     * @return the normalized name or null if the authority is unknown
     */
    private static String normalizeType(String configuredType) {
        if (StringUtils.isBlank(configuredType)) {
            return null;
        }
        switch (configuredType.trim().toLowerCase()) {
            case GND:
                return GND;
            case GEONAMES:
                return GEONAMES;
            case VIAF:
                return VIAF;
            default:
                return null;
        }
    }

    /**
     * find out which authority a value belongs to by looking at its uri
     * 
     * @param value complete uri or plain identifier
     * @return the name of the authority or null if the value is no uri of a supported authority
     */
    private static String detectType(String value) {
        String lowerCase = value.toLowerCase();
        if (lowerCase.contains("d-nb.info/gnd/")) {
            return GND;
        }
        if (lowerCase.contains("geonames.org/")) {
            return GEONAMES;
        }
        if (lowerCase.contains("viaf.org/viaf/")) {
            return VIAF;
        }
        return null;
    }

    /**
     * get the plain identifier out of a value that is no uri of a supported authority
     * 
     * @param value uri or plain identifier
     * @return the identifier
     */
    private static String extractId(String value) {
        String id = StringUtils.stripEnd(value, "/");
        int index = id.lastIndexOf('/');
        if (index > -1) {
            return id.substring(index + 1).trim();
        }
        return id;
    }

    /**
     * get the base uri of an authority
     * 
     * @param type name of the authority
     * @return the base uri
     */
    private static String getBaseUri(String type) {
        switch (type) {
            case GEONAMES:
                return "http://www.geonames.org/";
            case VIAF:
                return "http://viaf.org/viaf/";
            case GND:
            default:
                return "http://d-nb.info/gnd/";
        }
    }
}
