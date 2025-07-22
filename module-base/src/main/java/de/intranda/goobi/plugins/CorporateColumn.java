package de.intranda.goobi.plugins;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CorporateColumn {

    private int level;

    private String rulesetName;
    private String eadName;

    private String nameColumnName;
    private String subNameColumnName;
    private String partNameColumnName;

    private String authorityColumnName;

    private boolean splitName;
    private String splitChar;
}
