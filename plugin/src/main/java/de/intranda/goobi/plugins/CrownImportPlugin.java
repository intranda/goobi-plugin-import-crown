package de.intranda.goobi.plugins;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.goobi.interfaces.IArchiveManagementAdministrationPlugin;
import org.goobi.interfaces.IEadEntry;
import org.goobi.interfaces.IMetadataField;
import org.goobi.interfaces.INodeType;
import org.goobi.production.enums.ImportReturnValue;
import org.goobi.production.enums.ImportType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.importer.DocstructElement;
import org.goobi.production.importer.ImportObject;
import org.goobi.production.importer.Record;
import org.goobi.production.plugin.PluginLoader;
import org.goobi.production.plugin.interfaces.IImportPluginVersion2;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.properties.ImportProperty;

import de.intranda.goobi.plugins.model.FieldValue;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.forms.MassImportForm;
import de.sub.goobi.helper.exceptions.ImportPluginException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.exceptions.WriteException;
import ugh.fileformats.mets.MetsMods;

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

    private IArchiveManagementAdministrationPlugin archivePlugin;

    private transient IEadEntry rootEntry;

    private int startRow;

    private String eadFileName;
    private String databaseName;

    // metadata information
    private String docType;
    private String identifierMetadata;
    private String titleMetadata;


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
            eadFileName = myconfig.getString("/basex/filename");
            databaseName = myconfig.getString("/basex/database");
            startRow = myconfig.getInt("/startRow", 0);

            docType = myconfig.getString("/metadata/doctype", "Monograph");
            identifierMetadata = myconfig.getString("/metadata/title", "CatalogIDDigital");
            titleMetadata = myconfig.getString("/metadata/identifier", "TitleDocMain");
        }
    }

    /**
     * This method is used to generate records based on the imported data these records will then be used later to generate the Goobi processes
     */
    @Override
    public List<Record> generateRecordsFromFile() { //NOSONAR
        if (StringUtils.isBlank(workflowTitle)) {
            workflowTitle = form.getTemplate().getTitel();
        }
        readConfig();

        // open archive plugin, load ead file or create new one
        if (archivePlugin == null) {
            // find out if archive file is locked currently
            IPlugin ia = PluginLoader.getPluginByTitle(PluginType.Administration, "intranda_administration_archive_management");
            archivePlugin = (IArchiveManagementAdministrationPlugin) ia;

            archivePlugin.setDatabaseName(databaseName);
            archivePlugin.setFileName(eadFileName);
            archivePlugin.createNewDatabase();
            rootEntry = archivePlugin.getRootElement();
        }

        INodeType fileType = null;
        INodeType folderType = null;

        for (INodeType nodeType : archivePlugin.getConfiguredNodes()) {
            if ("folder".equals(nodeType.getNodeName())) {
                folderType = nodeType;
            } else if ("file".equals(nodeType.getNodeName())) {
                fileType = nodeType;
            }
        }

        // the list where the records are stored
        List<Record> recordList = new ArrayList<>();

        int rowCounter = 0;

        IEadEntry lastElement = rootEntry;

        // open excel file
        try (InputStream fileInputStream = new FileInputStream(file); BOMInputStream in = new BOMInputStream(fileInputStream, false);
                Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.rowIterator();
            // go to first data row
            while (rowCounter < startRow - 1) {
                rowCounter++;
                rowIterator.next();
            }

            // read all lines
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                int lastColumn = row.getLastCellNum();
                if (lastColumn == -1) {
                    // skip empty lines
                    continue;
                }

                int hierarchy = 0;
                String identifier = null;
                String label = null;
                boolean createProcess = false;

                for (int cellCounter = 0; cellCounter < lastColumn; cellCounter++) {
                    Cell cell = row.getCell(cellCounter, MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    if (cell == null || StringUtils.isBlank(cell.getStringCellValue())) {
                        // skip empty cell in order to find first column with content
                        continue;
                    }
                    // we found content
                    if (StringUtils.isBlank(identifier)) {
                        identifier = cell.getStringCellValue();
                        hierarchy = cellCounter;
                        // check if value is bold
                        CellStyle cs = cell.getCellStyle();
                        Font font = ((XSSFCellStyle) cs).getFont();
                        // if yes, create a process
                        if (font.getBold()) {
                            createProcess = true;
                        }
                    } else {
                        label = cell.getStringCellValue();
                    }
                }

                if (createProcess) {
                    Record rec = new Record();
                    rec.setData(label);
                    rec.setId(identifier);
                    rec.setObject(hierarchy);
                    recordList.add(rec);
                }

                if (hierarchy == 0) {
                    // root element
                    createEadMetadata(lastElement, identifier, label, createProcess);
                } else {
                    IEadEntry parentNode = null;

                    // if current hierarchy is > lastElement hierarchy -> current is sub element of last element
                    if (hierarchy > lastElement.getHierarchy().intValue()) {
                        parentNode = lastElement;
                    } else if (hierarchy == lastElement.getHierarchy().intValue()) {
                        // if current hierarchy == lastElement hierarchy -> current is sibling of last element, get parent element
                        parentNode = lastElement.getParentNode();
                    } else {
                        // else run recursive through all parents of last element until the direct parent is found
                        parentNode = lastElement.getParentNode();
                        while (hierarchy <= parentNode.getHierarchy().intValue()) {
                            parentNode = parentNode.getParentNode();
                        }
                    }

                    // set parent element in archivePlugin
                    archivePlugin.setSelectedEntry(parentNode);
                    // create new node
                    archivePlugin.addNode();
                    // get new node
                    lastElement = archivePlugin.getSelectedEntry();

                    // set node type
                    if (createProcess) {
                        lastElement.setNodeType(fileType);
                    } else {
                        lastElement.setNodeType(folderType);
                    }

                    // set metadata
                    createEadMetadata(lastElement, identifier, label, createProcess);
                }
            }

        } catch (Exception e) {
            log.error(e);
        }

        // save ead record
        archivePlugin.createEadDocument();

        // return the list of all generated records
        return recordList;
    }

    private void createEadMetadata(IEadEntry entry, String identifier, String label, boolean createProcess) {
        // add identifier and label
        for (IMetadataField field : entry.getIdentityStatementAreaList()) {
            if ("unittitle".equals(field.getName())) {
                FieldValue value = new FieldValue(field);
                value.setValue(label);
                field.setValues(Arrays.asList(value));
            }
            if ("Shelfmark".equalsIgnoreCase(field.getName())) {
                FieldValue value = new FieldValue(field);
                value.setValue(identifier);
                field.setValues(Arrays.asList(value));
            }
        }
        entry.setId(identifier);
        if (createProcess) {
            entry.setGoobiProcessTitle(identifier);
        }
        entry.setLabel(label);
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


        for (Record rec : records) {
            String processTitle = rec.getId().toLowerCase().replaceAll("\\W", "_");
            String  metsFileName = getImportFolder() + File.separator + processTitle + ".xml";
            try {
                Fileformat fileformat = new MetsMods(prefs);
                DigitalDocument digDoc = new DigitalDocument();
                fileformat.setDigitalDocument(digDoc);

                DocStruct logical = digDoc.createDocStruct(prefs.getDocStrctTypeByName(docType));
                DocStruct physical = digDoc.createDocStruct(prefs.getDocStrctTypeByName("BoundBook"));
                digDoc.setLogicalDocStruct(logical);
                digDoc.setPhysicalDocStruct(physical);

                Metadata imagePath = new Metadata(prefs.getMetadataTypeByName("pathimagefiles"));
                imagePath.setValue("./images/");
                physical.addMetadata(imagePath);

                Metadata idMetadata = new Metadata(prefs.getMetadataTypeByName(identifierMetadata));
                idMetadata.setValue(rec.getId());
                logical.addMetadata(idMetadata);

                Metadata mainTitle = new Metadata(prefs.getMetadataTypeByName(titleMetadata));
                mainTitle.setValue(rec.getData());
                logical.addMetadata(mainTitle);

                MetadataType eadIdType = prefs.getMetadataTypeByName("NodeId");
                if (eadIdType!=null) {
                    Metadata eadId = new Metadata(eadIdType);
                    eadId.setValue(rec.getId());
                    logical.addMetadata(eadId);
                }
                fileformat.write(metsFileName);
            } catch (PreferencesException|TypeNotAllowedForParentException | MetadataTypeNotAllowedException | WriteException e) {
                log.error(e);
            }

            // create process data
            ImportObject io = new ImportObject();
            io.setProcessTitle(processTitle);
            io.setMetsFilename(metsFileName);
            // copy images, create page order

            // TODO this needs clarification, where are the images, how are they organized?
            // One folder per process, all files in one folder, matching per prefix?



            io.setImportReturnValue(ImportReturnValue.ExportFinished);
            answer.add(io);
        }

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