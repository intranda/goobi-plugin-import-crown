package de.intranda.goobi.plugins;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MetadataColumn {

    private String rulesetName;

    private String eadName;
    private int level;

    private String excelColumnName;

    private boolean identifierField;

    private String authorityColumnName;
}
