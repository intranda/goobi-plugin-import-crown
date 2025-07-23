package de.intranda.goobi.plugins;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GroupColumns {

    private int level;

    private String rulesetName;
    private String eadName;

    private List<MetadataColumn> metadataList = new ArrayList<>();

    public void addMetadataColumn(MetadataColumn other) {
        metadataList.add(other);
    }
}
