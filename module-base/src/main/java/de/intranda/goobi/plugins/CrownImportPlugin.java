package de.intranda.goobi.plugins;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.goobi.interfaces.IArchiveManagementAdministrationPlugin;
import org.goobi.interfaces.IEadEntry;
import org.goobi.interfaces.IFieldValue;
import org.goobi.interfaces.IMetadataField;
import org.goobi.interfaces.INodeType;
import org.goobi.production.enums.ImportReturnValue;
import org.goobi.production.enums.ImportType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.importer.DocstructElement;
import org.goobi.production.importer.ImportObject;
import org.goobi.production.importer.Record;
import org.goobi.production.plugin.PluginLoader;
import org.goobi.production.plugin.interfaces.IImportPluginVersion3;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.properties.ImportProperty;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.forms.MassImportForm;
import de.sub.goobi.helper.ProcessTitleGenerator;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.enums.ManipulationType;
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
public class CrownImportPlugin implements IImportPluginVersion3 {

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
    private String workflowName;

    private boolean runAsGoobiScript = false;

    private IArchiveManagementAdministrationPlugin archivePlugin;

    private transient IEadEntry rootEntry;

    private int startRow;

    private String eadFileName;

    // metadata information
    private String docType;

    private String imageRootFolder;

    private String nodeTypeColumnName = null;
    private int headerRowNumber;

    private transient List<MetadataColumn> columnList = new ArrayList<>();

    private transient MetadataColumn firstColumn = null;
    private transient MetadataColumn secondColumn = null;

    // maximum length of each component that is to be used to generate the process title
    private int lengthLimit;
    // separator that will be used to join all components into a process title
    private String separator;

    private transient List<String> titleParts = new ArrayList<>();

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
            myconfig = xmlConfig.configurationAt("//config[./template = '" + workflowName + "']");
        } catch (IllegalArgumentException e) {
            myconfig = xmlConfig.configurationAt("//config[./template = '*']");
        }

        if (myconfig != null) {
            runAsGoobiScript = myconfig.getBoolean("/runAsGoobiScript", false);
            imageRootFolder = myconfig.getString("/images");

            eadFileName = myconfig.getString("/basex/filename");

            startRow = myconfig.getInt("/startRow", 0);
            headerRowNumber = myconfig.getInt("/headerRow", 0);

            nodeTypeColumnName = myconfig.getString("/nodetype");
            docType = myconfig.getString("/metadata/doctype", "Monograph");

            SubnodeConfiguration firstFieldDefinition = myconfig.configurationAt("/metadata/firstField");

            firstColumn = new MetadataColumn();
            firstColumn.setRulesetName(firstFieldDefinition.getString("@metadataField"));
            firstColumn.setEadName(firstFieldDefinition.getString("@eadField"));
            firstColumn.setLevel(firstFieldDefinition.getInt("@level", 0));
            firstColumn.setIdentifierField(firstFieldDefinition.getBoolean("@identifier", false));

            SubnodeConfiguration secondFieldDefinition = myconfig.configurationAt("/metadata/secondField");
            if (secondFieldDefinition.getBoolean("@enabled")) {
                secondColumn = new MetadataColumn();
                secondColumn.setRulesetName(secondFieldDefinition.getString("@metadataField"));
                secondColumn.setEadName(secondFieldDefinition.getString("@eadField"));
                secondColumn.setLevel(secondFieldDefinition.getInt("@level", 0));
                secondColumn.setIdentifierField(secondFieldDefinition.getBoolean("@identifier", false));
            }

            columnList.clear();

            for (HierarchicalConfiguration field : myconfig.configurationsAt("/metadata/additionalField")) {
                MetadataColumn mc = new MetadataColumn();
                mc.setRulesetName(field.getString("@metadataField"));
                mc.setEadName(field.getString("@eadField"));
                mc.setLevel(field.getInt("@level", 0));
                mc.setIdentifierField(field.getBoolean("@identifier", false));
                mc.setExcelColumnName(field.getString("@column"));
                columnList.add(mc);
            }

            // process title generation

            lengthLimit = myconfig.getInt("/metadata/lengthLimit", 0);
            separator = myconfig.getString("/metadata/separator", "_");
            titleParts = Arrays.asList(myconfig.getStringArray("/metadata/title"));

        }
    }

    /**
     * This method is used to generate records based on the imported data these records will then be used later to generate the Goobi processes
     */
    @Override
    public List<Record> generateRecordsFromFile() { //NOSONAR
        if (StringUtils.isBlank(workflowName)) {
            workflowName = form.getTemplate().getTitel();
        }
        readConfig();

        // open archive plugin, load ead file or create new one
        if (archivePlugin == null) {
            // find out if archive file is locked currently
            IPlugin ia = PluginLoader.getPluginByTitle(PluginType.Administration, "intranda_administration_archive_management");
            archivePlugin = (IArchiveManagementAdministrationPlugin) ia;

            archivePlugin.setDatabaseName(eadFileName);
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
        try (InputStream fileInputStream = new FileInputStream(file);
                BOMInputStream in = BOMInputStream.builder().setInputStream(fileInputStream).setInclude(false).get();
                Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.rowIterator();

            Row headerRow = null;
            if (headerRowNumber != 0) {
                while (rowCounter < headerRowNumber) {
                    rowCounter++;
                    headerRow = rowIterator.next();
                }
            }

            Map<String, Integer> headerOrder = new HashMap<>();

            if (headerRow != null) {
                //  read and validate the header row
                int numberOfCells = headerRow.getLastCellNum();
                for (int i = 0; i < numberOfCells; i++) {
                    Cell cell = headerRow.getCell(i);
                    if (cell != null) {
                        String value = getCellValue(headerRow, i);
                        headerOrder.put(value, i);
                    }
                }
            }

            // go to first data row
            while (rowCounter < startRow - headerRowNumber) {
                rowCounter++;
                rowIterator.next();
            }

            Integer identifierOrder = null;
            for (MetadataColumn col : columnList) {
                if (col.isIdentifierField()) {
                    identifierOrder = headerOrder.get(col.getExcelColumnName());
                }
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
                String firstColumnValue = null;
                String secondColumnValue = null;
                String identifierValue = null;
                boolean createProcess = false;
                Map<Integer, String> map = new HashMap<>();

                for (int cellCounter = 0; cellCounter < lastColumn; cellCounter++) {
                    Cell cell = row.getCell(cellCounter, MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    if (cell == null || cell.getCellType() == CellType.BLANK) {
                        // skip empty cell in order to find first column with content
                        continue;
                    }
                    // we found content
                    if (StringUtils.isBlank(firstColumnValue)) {
                        firstColumnValue = cell.getStringCellValue();
                        hierarchy = cellCounter;
                        // check if value is bold
                        CellStyle cs = cell.getCellStyle();
                        Font font = ((XSSFCellStyle) cs).getFont();
                        // if yes, create a process
                        if (font.getBold()) {
                            createProcess = true;
                        }
                    } else if (StringUtils.isBlank(secondColumnValue)) {
                        secondColumnValue = cell.getStringCellValue();
                    }
                }

                // get other columns
                for (int cn = 0; cn < lastColumn; cn++) {
                    String cellValue = getCellValue(row, cn);
                    if (identifierOrder != null && cn == identifierOrder.intValue()) {
                        identifierValue = cellValue;
                    }

                    map.put(cn, cellValue);
                }

                // put varikable data to map
                map.put(999, firstColumnValue);
                map.put(998, secondColumnValue);

                if (createProcess) {
                    Record rec = new Record();
                    rec.setData(firstColumnValue);
                    rec.setId(identifierValue == null ? firstColumnValue : identifierValue);
                    List<Map<?, ?>> list = new ArrayList<>();
                    list.add(headerOrder);
                    list.add(map);
                    rec.setObject(list);
                    recordList.add(rec);
                }

                if (hierarchy == 0) {
                    // root element
                    createEadMetadata(lastElement, firstColumnValue, secondColumnValue, createProcess, map, headerOrder);
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
                    if (nodeTypeColumnName != null && headerOrder.containsKey(nodeTypeColumnName)) {
                        String nodeName = map.get(headerOrder.get(nodeTypeColumnName));
                        for (INodeType nodeType : archivePlugin.getConfiguredNodes()) {
                            if (nodeType.getNodeName().equalsIgnoreCase(nodeName)) {
                                lastElement.setNodeType(nodeType);
                                break;
                            }
                        }
                    } else if (createProcess) {
                        lastElement.setNodeType(fileType);
                    } else {
                        lastElement.setNodeType(folderType);
                    }
                    // no node type found, use default value
                    if (lastElement.getNodeType() == null) {
                        lastElement.setNodeType(folderType);
                    }
                    // set metadata
                    createEadMetadata(lastElement, firstColumnValue, secondColumnValue, createProcess, map, headerOrder);
                }
            }

        } catch (Exception e) {
            log.error(e);
        }

        archivePlugin.setSelectedEntry(rootEntry);

        // return the list of all generated records
        return recordList;
    }

    private void createEadMetadata(IEadEntry entry, String firstValue, String secondValue, boolean createProcess, Map<Integer, String> data,
            Map<String, Integer> headerMap) {
        // add identifier and label

        if (StringUtils.isNotBlank(firstColumn.getEadName())) {
            addMetadataToNode(entry, firstColumn, firstValue);
        }

        if (secondColumn != null && StringUtils.isNotBlank(secondColumn.getEadName())) {
            addMetadataToNode(entry, secondColumn, secondValue);

        }

        for (MetadataColumn col : columnList) {
            String metadataValue = data.get(headerMap.get(col.getExcelColumnName()));
            addMetadataToNode(entry, col, metadataValue);
            if ("TitleDocMain".equals(col.getRulesetName())) {
                entry.setLabel(metadataValue);
            }
        }

        // identifierField

        if (firstColumn.isIdentifierField()) {
            entry.setId(firstValue);
        } else if (secondColumn != null && secondColumn.isIdentifierField()) {
            entry.setId(secondValue);
        } else {
            for (MetadataColumn col : columnList) {
                if (col.isIdentifierField()) {
                    entry.setId(data.get(headerMap.get(col.getExcelColumnName())));
                }
            }
        }
        if (createProcess) {
            entry.setGoobiProcessTitle(entry.getId());
        }
        if (StringUtils.isBlank(entry.getLabel())) {
            entry.setLabel(secondValue);
        }
    }

    private void addMetadataToNode(IEadEntry entry, MetadataColumn column, String stringValue) {
        if (StringUtils.isBlank(stringValue)) {
            return;
        }

        switch (column.getLevel()) {
            case 1:
                for (IMetadataField field : entry.getIdentityStatementAreaList()) {
                    if (field.getName().equals(column.getEadName())) {
                        IFieldValue value = field.createFieldValue();
                        value.setValue(stringValue);
                        field.setValues(Arrays.asList(value));
                        return;
                    }
                }
                break;
            case 2:
                for (IMetadataField field : entry.getContextAreaList()) {
                    if (field.getName().equals(column.getEadName())) {
                        IFieldValue value = field.createFieldValue();
                        value.setValue(stringValue);
                        field.setValues(Arrays.asList(value));
                        return;
                    }
                }
                break;
            case 3:
                for (IMetadataField field : entry.getContentAndStructureAreaAreaList()) {
                    if (field.getName().equals(column.getEadName())) {
                        IFieldValue value = field.createFieldValue();
                        value.setValue(stringValue);
                        field.setValues(Arrays.asList(value));
                        return;
                    }
                }
                break;
            case 4:
                for (IMetadataField field : entry.getAccessAndUseAreaList()) {
                    if (field.getName().equals(column.getEadName())) {
                        IFieldValue value = field.createFieldValue();
                        value.setValue(stringValue);
                        field.setValues(Arrays.asList(value));
                        return;
                    }
                }
                break;
            case 5:
                for (IMetadataField field : entry.getAlliedMaterialsAreaList()) {
                    if (field.getName().equals(column.getEadName())) {
                        IFieldValue value = field.createFieldValue();
                        value.setValue(stringValue);
                        field.setValues(Arrays.asList(value));
                        return;
                    }
                }

                break;
            case 6:
                for (IMetadataField field : entry.getNotesAreaList()) {
                    if (field.getName().equals(column.getEadName())) {
                        IFieldValue value = field.createFieldValue();
                        value.setValue(stringValue);
                        field.setValues(Arrays.asList(value));
                        return;
                    }
                }
                break;
            case 7:
                for (IMetadataField field : entry.getDescriptionControlAreaList()) {
                    if (field.getName().equals(column.getEadName())) {
                        IFieldValue value = field.createFieldValue();
                        value.setValue(stringValue);
                        field.setValues(Arrays.asList(value));
                        return;
                    }
                }
                break;
            default:
                break;
        }
    }

    /**
     * This method is used to actually create the Goobi processes this is done based on previously created records
     */
    @Override
    public List<ImportObject> generateFiles(List<Record> records) {
        if (StringUtils.isBlank(workflowName)) {
            workflowName = form.getTemplate().getTitel();
        }
        readConfig();

        // collect all image folder
        Map<String, Path> allImageFolder = new HashMap<>();

        Path folder = Paths.get(imageRootFolder);
        if (StorageProvider.getInstance().isDirectory(folder)) {

            try (Stream<Path> stream = Files.find(folder, 10, (p, attr) -> attr.isDirectory())) {
                stream.forEach(p -> allImageFolder.put(p.getFileName().toString(), p));
            } catch (IOException e) {
                log.error(e);
            }
        }

        List<ImportObject> answer = new ArrayList<>();

        for (Record rec : records) {
            Map<String, Integer> headerMap = getHeaderOrder(rec);
            Map<Integer, String> data = getRowMap(rec);
            String firstCol = data.get(999);
            String secondCol = data.get(998);

            // processTitleRule

            ProcessTitleGenerator titleGenerator = new ProcessTitleGenerator();
            titleGenerator.setSeparator(separator);
            titleGenerator.setBodyTokenLengthLimit(lengthLimit);

            for (String comp : titleParts) {
                if (comp.startsWith("'") && comp.endsWith("'")) {
                    titleGenerator.addToken(comp.substring(1, comp.length() - 1), ManipulationType.NORMAL);
                } else if ("first".equals(comp)) {
                    titleGenerator.addToken(firstCol, ManipulationType.NORMAL);
                } else if ("second".equals(comp)) {
                    titleGenerator.addToken(secondCol, ManipulationType.NORMAL);
                } else {
                    String s = data.get(headerMap.get(comp));
                    titleGenerator.addToken(s, ManipulationType.NORMAL);
                }
            }

            String identifier = titleGenerator.generateTitle();

            Path currentImageFolder = allImageFolder.get(rec.getId());
            List<Path> filesToImport = null;
            if (currentImageFolder != null) {
                filesToImport = StorageProvider.getInstance().listFiles(currentImageFolder.toString(), fileFilter);
            }

            String metsFileName = getImportFolder() + File.separator + identifier + ".xml";
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

                Metadata idMetadata = new Metadata(prefs.getMetadataTypeByName(firstColumn.getRulesetName()));
                idMetadata.setValue(firstCol);
                logical.addMetadata(idMetadata);

                if (secondColumn != null && StringUtils.isNotBlank(secondCol)) {
                    Metadata desc = new Metadata(prefs.getMetadataTypeByName(secondColumn.getRulesetName()));
                    desc.setValue(secondCol);
                    logical.addMetadata(desc);
                }

                // additional metadata
                for (MetadataColumn col : columnList) {
                    if (StringUtils.isNotBlank(col.getRulesetName())) {
                        String value = data.get(headerMap.get(col.getExcelColumnName()));
                        if (StringUtils.isNotBlank(value)) {
                            Metadata meta = new Metadata(prefs.getMetadataTypeByName(col.getRulesetName()));
                            meta.setValue(value);
                            logical.addMetadata(meta);
                        }
                    }
                }

                String nodeId = null;
                if (firstColumn.isIdentifierField()) {
                    nodeId = firstCol;
                } else if (secondColumn != null && secondColumn.isIdentifierField()) {
                    nodeId = secondCol;
                } else {
                    for (MetadataColumn col : columnList) {
                        if (col.isIdentifierField()) {
                            nodeId = data.get(headerMap.get(col.getExcelColumnName()));
                        }
                    }
                }

                MetadataType eadIdType = prefs.getMetadataTypeByName("NodeId");
                if (eadIdType != null) {
                    Metadata eadId = new Metadata(eadIdType);
                    eadId.setValue(nodeId);
                    logical.addMetadata(eadId);
                }

                // add selected
                for (String colItem : form.getDigitalCollections()) {
                    Metadata mdColl = new Metadata(prefs.getMetadataTypeByName("singleDigCollection"));
                    mdColl.setValue(colItem);
                    logical.addMetadata(mdColl);
                }

                fileformat.write(metsFileName);
            } catch (PreferencesException | TypeNotAllowedForParentException | MetadataTypeNotAllowedException | WriteException e) {
                log.error(e);
            }

            // create process data
            ImportObject io = new ImportObject();
            io.setProcessTitle(identifier);
            io.setMetsFilename(metsFileName);

            // copy images
            if (filesToImport != null) {
                Path imageBasePath = Paths.get(metsFileName.replace(".xml", ""), "images", identifier + "_media");
                try {
                    StorageProvider.getInstance().createDirectories(imageBasePath);

                    for (Path fileToCopy : filesToImport) {
                        String filename = fileToCopy.getFileName().toString();
                        boolean containsEdited = false;
                        boolean betterFileExists = false;
                        if (filename.contains("bearbeitet")) {
                            containsEdited = true;
                        }

                        // check if edited version exists
                        if (!containsEdited && (filename.endsWith(".jpg") || filename.endsWith(".tif"))) {
                            String editFileName = filename.replace(".jpg", "").replace(".tif", "") + "_bearbeitet.tif";
                            for (Path fileToCheck : filesToImport) {
                                String filenameToCheck = fileToCheck.getFileName().toString();
                                if (editFileName.equals(filenameToCheck)) {
                                    // if this is the case, use the tif instead and skip this jpg
                                    betterFileExists = true;
                                    break;
                                }
                            }
                        }

                        if (filename.endsWith(".jpg")) {
                            // in case of jpg check if a tif with the same name exists
                            String tifFilename = filename.replace(".jpg", ".tif");

                            for (Path fileToCheck : filesToImport) {
                                String filenameToCheck = fileToCheck.getFileName().toString();
                                if (tifFilename.equals(filenameToCheck)) {
                                    // if this is the case, use the tif instead and skip this jpg
                                    betterFileExists = true;
                                    break;
                                }
                                // else check if a file starting with the same name exists containing "_bearbeitet"
                            }

                            // otherwise copy the jpg
                            if (!betterFileExists) {
                                StorageProvider.getInstance()
                                        .copyFile(fileToCopy, Paths.get(imageBasePath.toString(), fileToCopy.getFileName().toString()));
                            }
                        } else {
                            // always copy other file formats
                            StorageProvider.getInstance()
                                    .copyFile(fileToCopy, Paths.get(imageBasePath.toString(), fileToCopy.getFileName().toString()));
                        }
                    }
                } catch (IOException e) {
                    log.error(e);
                }
            }

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

    public static final DirectoryStream.Filter<Path> fileFilter = path -> {
        String filename = path.getFileName().toString();
        return !filename.contains("komprimiert") && (filename.endsWith(".tif") || filename.endsWith(".jpg") || filename.endsWith(".wmv"));
    };

    public String getCellValue(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex, MissingCellPolicy.CREATE_NULL_AS_BLANK);
        String value = "";
        switch (cell.getCellType()) {
            case BOOLEAN:
                value = cell.getBooleanCellValue() ? "true" : "false";
                break;
            case FORMULA:
                value = cell.getRichStringCellValue().getString();
                break;
            case NUMERIC:
                value = String.valueOf((long) cell.getNumericCellValue());
                break;
            case STRING:
                value = cell.getStringCellValue();
                break;
            default:
                // none, error, blank
                value = "";
                break;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public Map<Integer, String> getRowMap(Record rec) {
        Object tempObject = rec.getObject();
        List<Map<?, ?>> list = (List<Map<?, ?>>) tempObject;
        return (Map<Integer, String>) list.get(1);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Integer> getHeaderOrder(Record rec) {
        Object tempObject = rec.getObject();
        List<Map<?, ?>> list = (List<Map<?, ?>>) tempObject;
        return (Map<String, Integer>) list.get(0);
    }

}