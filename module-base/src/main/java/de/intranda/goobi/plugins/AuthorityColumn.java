package de.intranda.goobi.plugins;

/**
 * Implemented by all column definitions that can carry authority data.
 */
public interface AuthorityColumn {

    /** name of the excel column that contains the authority uri or identifier */
    String getAuthorityColumnName();

    /** fixed name of the authority to use, used when no type column is configured or the column is empty */
    String getAuthorityType();

    /** name of the excel column that contains the name of the authority to use */
    String getAuthorityTypeColumnName();
}
