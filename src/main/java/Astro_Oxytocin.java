import Astro_Oxytocin_Tools.Tools;
import ij.*;
import ij.plugin.PlugIn;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.BF;
import loci.plugins.util.ImageProcessorReader;
import loci.plugins.in.ImporterOptions;
import mcib3d.geom2.Object3DInt;
import mcib3d.geom2.Objects3DIntPopulation;
import mcib3d.geom2.measurements.MeasureVolume;
import org.apache.commons.io.FilenameUtils;
import org.scijava.util.ArrayUtils;


/**
 * Detect DAPI nuclei, astrocytes somas and oxytocin receptors foci
 * Keep astrocytes colocalizing with a nucleus only
 * Detect and count the number of oxytocin receptors foci in each astrocytes
 * @author Orion-CIRB
 */
public class Astro_Oxytocin implements PlugIn {
    
    Tools tools = new Tools();
    private String imageDir = "";
    public String outDirResults = "";
    public BufferedWriter results;
   
    
    public void run(String arg) {
        try {
            if ((!tools.checkInstalledModules()) || (!tools.checkStarDistModels())) {
                return;
            } 
            
            imageDir = IJ.getDirectory("Choose directory containing image files...");
            if (imageDir == null) {
                return;
            }   
            // Find images with extension
            String fileExt = tools.findImageType(new File(imageDir));
            ArrayList<String> imageFiles = tools.findImages(imageDir, fileExt);
            if (imageFiles == null) {
                IJ.showMessage("Error", "No images found with " + fileExt + " extension");
                return;
            }
            
            // Create output folder
            outDirResults = imageDir + File.separator+ "Results" + File.separator;
            File outDir = new File(outDirResults);
            if (!Files.exists(Paths.get(outDirResults))) {
                outDir.mkdir();
            }
            // Write header in results file
            String header = "Image name\tCell label\tCell volume (µm3)\tOxytocin receptor foci number\tOxytocin receptor foci total volume (µm3)\n";
            FileWriter fwResults = new FileWriter(outDirResults + "results.xls", false);
            results = new BufferedWriter(fwResults);
            results.write(header);
            results.flush();
            
            // Create OME-XML metadata store of the latest schema version
            ServiceFactory factory;
            factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata();
            ImageProcessorReader reader = new ImageProcessorReader();
            reader.setMetadataStore(meta);
            reader.setId(imageFiles.get(0));
            
            // Find image calibration
            tools.cal = tools.findImageCalib(meta);
            
            // Find channel names
            String[] chsName = tools.findChannels(imageFiles.get(0), meta, reader);
            
            // Channels dialog
            String[] channels = tools.dialog(chsName);
            if (channels == null) {
                IJ.showStatus("Plugin canceled");
                return;
            }

            for (String f : imageFiles) {
                String rootName = FilenameUtils.getBaseName(f);
                tools.print("--- ANALYZING IMAGE " + rootName + " ------");
                reader.setId(f);
                
                ImporterOptions options = new ImporterOptions();
                options.setId(f);
                options.setSplitChannels(true);
                options.setQuiet(true);
                options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
                
                // Open DAPI channel
                tools.print("- Analyzing nuclei channel -");
                int indexCh = ArrayUtils.indexOf(chsName, channels[0]);
                ImagePlus imgDAPI = BF.openImagePlus(options)[indexCh];
                // Detect DAPI nuclei with CellPose
                System.out.println("Finding nuclei...");
                Objects3DIntPopulation dapiPop = tools.cellposeDetection(imgDAPI, tools.cellposeNucleiDiameter, tools.minNucleusVol, tools.maxNucleusVol);
                System.out.println(dapiPop.getNbObjects() + " nuclei found");
                tools.flush_close(imgDAPI);
                
                // Open astrocytes channel
                tools.print("- Analyzing astrocytes channel -");
                indexCh = ArrayUtils.indexOf(chsName, channels[2]);
                ImagePlus imgAstro = BF.openImagePlus(options)[indexCh];
                // Detect astrocyte cells with CellPose
                System.out.println("Finding astrocytes somas...");
                Objects3DIntPopulation astroPop = tools.cellposeDetection(imgAstro, tools.cellposeCellDiameter, tools.minCellVol, tools.maxCellVol);
                System.out.println(astroPop.getNbObjects() + " astrocytes somas found");
                tools.flush_close(imgAstro);
                
                // Find astrocytes with a nucleus
                System.out.println("Colocalizing nuclei with astrocytes somas...");
                Objects3DIntPopulation cellPop = tools.findColocPop(astroPop, dapiPop);
                System.out.println(cellPop.getNbObjects() + " astrocytes colocalized with a nucleus");
              
                // Open oxytocin receptor channel
                tools.print("- Analyzing oxytocin receptor channel -");
                indexCh = ArrayUtils.indexOf(chsName, channels[1]);
                ImagePlus imgOcytocin = BF.openImagePlus(options)[indexCh];
                // Detect oxytocin receptor foci in astrocytes
                System.out.println("Finding oxytocin receptor foci in astrocytes...");
                Objects3DIntPopulation ocytocinFociPop = tools.stardistFociInCellsPop(imgOcytocin, cellPop);
                
                // Draw results
                tools.print("- Saving results -");
                tools.drawResults(cellPop, dapiPop, ocytocinFociPop, imgOcytocin, rootName, outDirResults);
                tools.flush_close(imgOcytocin);
                
                // Write results
                for (Object3DInt cell : cellPop.getObjects3DInt()) {
                    double cellVol = new MeasureVolume(cell).getVolumeUnit();
                    results.write(rootName+"\t"+cell.getLabel()+"\t"+cellVol+"\t"+cell.getType()+"\t"+cell.getCompareValue()+"\n");
                    results.flush();
                }
            }
            results.close();
        } catch (IOException | DependencyException | ServiceException | FormatException | io.scif.DependencyException  ex) {
            Logger.getLogger(Astro_Oxytocin.class.getName()).log(Level.SEVERE, null, ex);
        }
        tools.print("--- All done! ---");
    }    
}    
