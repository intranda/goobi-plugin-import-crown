package de.intranda.goobi.plugins;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;
import org.goobi.production.enums.ImportType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.importer.DocstructElement;
import org.goobi.production.importer.ImportObject;
import org.goobi.production.importer.Record;
import org.goobi.production.plugin.interfaces.IImportPluginVersion2;
import org.goobi.production.properties.ImportProperty;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.forms.MassImportForm;
import de.sub.goobi.helper.exceptions.ImportPluginException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;

@PluginImplementation
@Log4j2
public class CrownImportPlugin implements IImportPluginVersion2 {

    private static final long serialVersionUID = 8789683342709053966L;

    @Getter
    private String title = "intranda_import_crown";
    @Getter
    private PluginType type = PluginType.Import;

    @Getter
    private List<ImportType> importTypes;

    @Getter
    @Setter
    private Prefs prefs;
    @Getter
    @Setter
    private String importFolder;

    @Setter
    private MassImportForm form;

    @Setter
    private boolean testMode = false;

    @Getter
    @Setter
    private File file;

    @Setter
    private String workflowTitle;

    private boolean runAsGoobiScript = false;

    /**
     * define what kind of import plugin this is
     */
    public CrownImportPlugin() {
        importTypes = new ArrayList<>();
        importTypes.add(ImportType.FILE);
    }

    /**
     * read the configuration file
     */
    private void readConfig() {
        XMLConfiguration xmlConfig = ConfigPlugins.getPluginConfig(title);
        xmlConfig.setExpressionEngine(new XPathExpressionEngine());
        xmlConfig.setReloadingStrategy(new FileChangedReloadingStrategy());

        SubnodeConfiguration myconfig = null;
        try {
            myconfig = xmlConfig.configurationAt("//config[./template = '" + workflowTitle + "']");
        } catch (IllegalArgumentException e) {
            myconfig = xmlConfig.configurationAt("//config[./template = '*']");
        }

        if (myconfig != null) {
            runAsGoobiScript = myconfig.getBoolean("/runAsGoobiScript", false);
        }
    }

    /**
     * This method is used to generate records based on the imported data these records will then be used later to generate the Goobi processes
     */
    @Override
    public List<Record> generateRecordsFromFile() {
        if (StringUtils.isBlank(workflowTitle)) {
            workflowTitle = form.getTemplate().getTitel();
        }
        readConfig();

        // open excel file
        // create new ead file
        // read all lines
        // for each line:
        // - get hierarchy by checking which column contains the first text
        // - create ead node
        // - the last entry of the higher hierarchy level is used as the parent node.
        // - first text: identifier
        // - second column: title/label
        // - if bold: create process/Record
        // - try to import metadata based on identifier



        // the list where the records are stored
        List<Record> recordList = new ArrayList<>();

        // return the list of all generated records
        return recordList;
    }

    /**
     * This method is used to actually create the Goobi processes this is done based on previously created records
     */
    @Override
    public List<ImportObject> generateFiles(List<Record> records) {
        if (StringUtils.isBlank(workflowTitle)) {
            workflowTitle = form.getTemplate().getTitel();
        }
        readConfig();


        List<ImportObject> answer = new ArrayList<>();

        return answer;
    }

    /**
     * decide if the import shall be executed in the background via GoobiScript or not
     */
    @Override
    public boolean isRunnableAsGoobiScript() {
        readConfig();
        return runAsGoobiScript;
    }

    /* *************************************************************** */
    /*                                                                 */
    /* the following methods are mostly not needed for typical imports */
    /*                                                                 */
    /* *************************************************************** */

    @Override
    public List<Record> splitRecords(String string) {
        return new ArrayList<>();
    }

    @Override
    public List<String> splitIds(String ids) {
        return null; //NOSONAR
    }

    @Override
    public String addDocstruct() {
        return null;
    }

    @Override
    public String deleteDocstruct() {
        return null;
    }

    @Override
    public void deleteFiles(List<String> arg0) {
        // do nothing
    }

    @Override
    public List<Record> generateRecordsFromFilenames(List<String> arg0) {
        return null; //NOSONAR
    }

    @Override
    public List<String> getAllFilenames() {
        return null; //NOSONAR
    }

    @Override
    public List<? extends DocstructElement> getCurrentDocStructs() {
        return null; //NOSONAR
    }

    @Override
    public DocstructElement getDocstruct() {
        return null;
    }

    @Override
    public List<String> getPossibleDocstructs() {
        return null; //NOSONAR
    }

    @Override
    public String getProcessTitle() {
        return null;
    }

    @Override
    public List<ImportProperty> getProperties() {
        return null; //NOSONAR
    }

    @Override
    public void setData(Record arg0) {
        // do nothing
    }

    @Override
    public void setDocstruct(DocstructElement arg0) {
        // do nothing
    }

    @Override
    public Fileformat convertData() throws ImportPluginException {
        return null;
    }

}