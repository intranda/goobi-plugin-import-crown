package de.intranda.goobi.plugins;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PersonColumn {

    private String rulesetName;

    private String eadName;
    private int level;

    private String firstColumnName;
    private String nameColumnName;
    private String authorityColumnName;

    private boolean splitName;
    private String splitChar;
    private boolean firstNameIsFirst;
}
