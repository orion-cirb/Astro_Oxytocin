package Astro_Oxytocin_Tools;

import Astro_Oxytocin_Tools.Cellpose.CellposeSegmentImgPlusAdvanced;
import Astro_Oxytocin_Tools.Cellpose.CellposeTaskSettings;
import Astro_Oxytocin_Tools.StardistOrion.StarDist2D;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.plugin.RGBStackMerge;
import io.scif.DependencyException;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.ImageIcon;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom2.BoundingBox;
import mcib3d.geom2.Object3DInt;
import mcib3d.geom2.Objects3DIntPopulation;
import mcib3d.geom2.Objects3DIntPopulationComputation;
import mcib3d.geom2.VoxelInt;
import mcib3d.geom2.measurements.MeasureCentroid;
import mcib3d.geom2.measurements.MeasureVolume;
import mcib3d.geom2.measurementsPopulation.MeasurePopulationColocalisation;
import mcib3d.geom2.measurementsPopulation.PairObjects3DInt;
import mcib3d.image3d.ImageHandler;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
import org.apache.commons.io.FilenameUtils;
import org.scijava.util.ArrayUtils;


/**
 * @author Orion-CIRB
 */
public class Tools {
    private final ImageIcon icon = new ImageIcon(this.getClass().getResource("/Orion_icon.png"));
    private final String helpUrl = "https://github.com/orion-cirb/Astro_Oxytocin";
    private CLIJ2 clij2 = CLIJ2.getInstance();
    
    public Calibration cal;
    public double pixVol = 0;
    String[] channelNames = {"Nuclei", "Oxytocin receptors", "Astrocytes"};

    // Nuclei and cells detection with Cellpose
    private String cellposeEnvDirPath = IJ.isWindows()? System.getProperty("user.home")+"\\miniconda3\\envs\\CellPose" : "/opt/miniconda3/envs/cellpose";
    public final String cellposeModelPath = IJ.isWindows()? System.getProperty("user.home")+"\\.cellpose\\models\\" : "";
    public final String cellposeModel = "cyto2";
    public final int cellposeNucleiDiameter = 45;
    public double minNucleusVol = 50;
    public double maxNucleusVol = 2000;
    public int cellposeCellDiameter = 60;
    public double minCellVol = 50;
    public double maxCellVol = 4000;
    
    // Foci detection with StarDist
    private final File modelsPath = new File(IJ.getDirectory("imagej")+File.separator+"models");
    private final String stardistFociModel = "fociRNA-1.2.zip";
    private Object syncObject = new Object();
    private final String stardistOutput = "Label Image";
    private final double stardistPercentileBottom = 0.2;
    private final double stardistPercentileTop = 99.8;
    private double stardistFociProbThresh = 0.2;
    private final double stardistFociOverlayThresh = 0.25;
    public double minFociVol = 0.02;
    public double maxFociVol = 10;
    
    
    /**
     * Display a message in the ImageJ console and status bar
     */
    public void print(String log) {
        System.out.println(log);
        IJ.showStatus(log);
    }
    
    
    /**
     * Check that needed modules are installed
     */
    public boolean checkInstalledModules() {
        ClassLoader loader = IJ.getClassLoader();
        try {
            loader.loadClass("net.haesleinhuepf.clij2.CLIJ2");
        } catch (ClassNotFoundException e) {
            IJ.log("CLIJ not installed, please install from update site");
            return false;
        }
        try {
            loader.loadClass("mcib3d.geom.Object3D");
        } catch (ClassNotFoundException e) {
            IJ.log("3D ImageJ Suite not installed, please install from update site");
            return false;
        }
        return true;
    }
    
    
    /**
     * Check that required StarDist models are present in Fiji models folder
     */
    public boolean checkStarDistModels() {
        FilenameFilter filter = (dir, name) -> name.endsWith(".zip");
        File[] modelList = modelsPath.listFiles(filter);
        int index = ArrayUtils.indexOf(modelList, new File(modelsPath+File.separator+stardistFociModel));
        if (index == -1) {
            IJ.showMessage("Error", stardistFociModel + " StarDist model not found, please add it in Fiji models folder");
            return false;
        }
        return true;
    }
    
    
     /**
     * Find image type
     */
    public String findImageType(File imagesFolder) {
        String ext = "";
        String[] files = imagesFolder.list();
        for (String name : files) {
            String fileExt = FilenameUtils.getExtension(name);
            switch (fileExt) {
                case "nd" :
                   ext = fileExt;
                   break;
                case "nd2" :
                   ext = fileExt;
                   break;
                case "czi" :
                   ext = fileExt;
                   break;
                case "lif"  :
                    ext = fileExt;
                    break;
                case "ics" :
                    ext = fileExt;
                    break;
                case "ics2" :
                    ext = fileExt;
                    break;
                case "tif" :
                    ext = fileExt;
                    break;
                case "tiff" :
                    ext = fileExt;
                    break;
            }
        }
        return(ext);
    }
    
    
    /**
     * Find images in folder
     */
    public ArrayList<String> findImages(String imagesFolder, String imageExt) {
        File inDir = new File(imagesFolder);
        String[] files = inDir.list();
        if (files == null) {
            System.out.println("No image found in " + imagesFolder);
            return null;
        }
        ArrayList<String> images = new ArrayList();
        for (String f : files) {
            // Find images with extension
            String fileExt = FilenameUtils.getExtension(f);
            if (fileExt.equals(imageExt) && !f.startsWith("."))
                images.add(imagesFolder + File.separator + f);
        }
        Collections.sort(images);
        return(images);
    }
    
    
    /**
     * Find image calibration
     */
    public Calibration findImageCalib(IMetadata meta) {
        cal = new Calibration();
        cal.pixelWidth = meta.getPixelsPhysicalSizeX(0).value().doubleValue();
        cal.pixelHeight = cal.pixelWidth;
        if (meta.getPixelsPhysicalSizeZ(0) != null)
            cal.pixelDepth = meta.getPixelsPhysicalSizeZ(0).value().doubleValue();
        else
            cal.pixelDepth = 1;
        cal.setUnit("microns");
        System.out.println("XY calibration = " + cal.pixelWidth + ", Z calibration = " + cal.pixelDepth);
        return(cal);
    }
    
    
     /**
     * Find channels name
     * @param imageName
     * @return 
     * @throws loci.common.services.DependencyException
     * @throws loci.common.services.ServiceException
     * @throws loci.formats.FormatException
     * @throws java.io.IOException
     */
    public String[] findChannels (String imageName, IMetadata meta, ImageProcessorReader reader) throws DependencyException, ServiceException, FormatException, IOException {
        ArrayList<String> channels = new ArrayList<>();
        int chs = reader.getSizeC();
        String imageExt =  FilenameUtils.getExtension(imageName);
        switch (imageExt) {
            case "nd" :
                for (int n = 0; n < chs; n++) 
                {
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelName(0, n).toString());
                }
                break;
            case "nd2" :
                for (int n = 0; n < chs; n++) 
                {
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelName(0, n).toString());
                }
                break;
            case "lif" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null || meta.getChannelName(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelName(0, n).toString());
                break;
            case "czi" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelFluor(0, n).toString());
                break;
            case "ics" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelExcitationWavelength(0, n).value().toString());
                break; 
            case "ics2" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelExcitationWavelength(0, n).value().toString());
                break;        
            default :
                for (int n = 0; n < chs; n++)
                    channels.add(Integer.toString(n));

        }
        channels.add("None");
        return(channels.toArray(new String[channels.size()]));         
    }
    
    
    
    /**
     * Generate dialog box
     */
    public String[] dialog(String[] chs) {
        GenericDialogPlus gd = new GenericDialogPlus("Parameters");
        gd.setInsets​(0, 80, 0);
        gd.addImage(icon);
        
        gd.addMessage("Channels", Font.getFont("Monospace"), Color.blue);
        int index = 0;
        for (String chNames: channelNames) {
            gd.addChoice(chNames + ": ", chs, chs[index]);
            index++;
        }
        
        gd.addMessage("Nuclei detection", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Min nucleus volume (µm3): ", minNucleusVol);
        gd.addNumericField("Max nucleus volume (µm3): ", maxNucleusVol);
    
        gd.addMessage("Astrocytes detection", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Min cell volume (µm3): ", minCellVol);
        gd.addNumericField("Max cell volume (µm3): ", maxCellVol);
        
        gd.addMessage("Oxytocin receptor detection", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Min foci volume (µm3): ", minFociVol);
        gd.addNumericField("Max foci volume (µm3): ", maxFociVol);
        
        gd.addMessage("Image calibration", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("XY pixel size (µm): ", cal.pixelWidth);
        gd.addNumericField("Z pixel depth (µm):", cal.pixelDepth);
        gd.addHelp(helpUrl);
        gd.showDialog();
        
        String[] chChoices = new String[channelNames.length];
        for (int n = 0; n < chChoices.length; n++) 
            chChoices[n] = gd.getNextChoice();
        
        minNucleusVol = gd.getNextNumber();
        maxNucleusVol = gd.getNextNumber();
        minCellVol = gd.getNextNumber();
        maxCellVol = gd.getNextNumber();
        minFociVol = gd.getNextNumber();
        maxFociVol = gd.getNextNumber();
        cal.pixelWidth = cal.pixelHeight = gd.getNextNumber();
        cal.pixelDepth = gd.getNextNumber();
        pixVol = cal.pixelWidth*cal.pixelWidth*cal.pixelDepth;
        
        if(gd.wasCanceled())
            chChoices = null;
        
        return(chChoices);
    }
    
    
    /**
     * Flush and close an image
     */
    public void flush_close(ImagePlus img) {
        img.flush();
        img.close();
    }
    
    
    /**
     * Look for all 3D cells in a Z-stack: 
     * - apply CellPose in 2D slice by slice 
     * - let CellPose reconstruct cells in 3D using the stitch threshold parameters
     */
    public Objects3DIntPopulation cellposeDetection(ImagePlus img, int diameter, double volMin, double volMax) throws IOException{
        // Resize image
        double resizeFactor = 0.5;
        ImagePlus imgResized = img.resize((int)(img.getWidth()*resizeFactor), (int)(img.getHeight()*resizeFactor), 1, "none");
        
        // Define CellPose settings
        CellposeTaskSettings settings = new CellposeTaskSettings(cellposeModel, 1, diameter, cellposeEnvDirPath);
        settings.setStitchThreshold(0.5);
        settings.useGpu(true);
        
        // Run CellPose
        CellposeSegmentImgPlusAdvanced cellpose = new CellposeSegmentImgPlusAdvanced(settings, imgResized);
        ImagePlus imgOut = cellpose.run().resize(img.getWidth(), img.getHeight(), "none");
        imgOut.setCalibration(cal);
        
        // Get cells as a population of objects and filter them
        Objects3DIntPopulation pop = new Objects3DIntPopulation(ImageHandler.wrap(imgOut));
        System.out.println(pop.getNbObjects() + " Cellpose detections");
        pop = new Objects3DIntPopulationComputation(pop).getFilterSize(volMin/pixVol, volMax/pixVol);
        pop = new Objects3DIntPopulationComputation(pop).getExcludeBorders(ImageHandler.wrap(img), false);
        pop = zFilterPop(pop);
        pop.resetLabels();
        System.out.println(pop.getNbObjects() + " detections remaining after size filtering");
        
        flush_close(imgResized);
        flush_close(imgOut);
        
        return(pop);
    }
    
    
    /**
     * Remove objects present in only one z slice from population 
     */
    public Objects3DIntPopulation zFilterPop(Objects3DIntPopulation pop) {
        Objects3DIntPopulation popZ = new Objects3DIntPopulation();
        for (Object3DInt obj : pop.getObjects3DInt()) {
            if (obj.getBoundingBox().zmax != obj.getBoundingBox().zmin)
                popZ.addObject(obj);
        }
        return popZ;
    }
      
    
    /**
     * Find coloc objects in pop1 colocalized with pop2
     */
    public Objects3DIntPopulation findColocPop(Objects3DIntPopulation pop1, Objects3DIntPopulation pop2) {
        AtomicInteger cellIndex = new AtomicInteger(1);
        double pourc = 0.25;
        Objects3DIntPopulation colocPop = new Objects3DIntPopulation();
        if (pop1.getNbObjects() > 0 && pop2.getNbObjects() > 0) {
            MeasurePopulationColocalisation coloc = new MeasurePopulationColocalisation(pop1, pop2);
            pop1.getObjects3DInt().forEach(obj1 -> {
                List<PairObjects3DInt> list = coloc.getPairsObject1(obj1.getLabel(), true);
                if (!list.isEmpty()) {
                    list.forEach(p -> {
                        Object3DInt obj2 = p.getObject3D2();
                        if (p.getPairValue() > obj2.size()*pourc) {
                            obj1.setLabel(cellIndex.get());
                            obj2.setIdObject(cellIndex.getAndIncrement());
                            colocPop.addObject(obj1);
                        }
                    });
                }
            });
        }
        return(colocPop);
    }

    
    /**
    * In each astrocyte, detect foci with Stardist
    * Return the entire population of foci in astrocytes
    */
    public Objects3DIntPopulation stardistFociInCellsPop(ImagePlus img, Objects3DIntPopulation cellPop) throws IOException{
        int fociIndex = 1;
        Objects3DIntPopulation allFociPop = new Objects3DIntPopulation();
        for (Object3DInt cell: cellPop.getObjects3DInt()) {
            // Crop image around nucleus
            BoundingBox box = cell.getBoundingBox();
            Roi roiBox = new Roi(box.xmin, box.ymin, box.xmax-box.xmin, box.ymax-box.ymin);
            img.setRoi(roiBox);
            img.updateAndDraw();
            ImagePlus imgCell = new Duplicator().run(img, box.zmin+1, box.zmax+1);
            imgCell.deleteRoi();
            imgCell.updateAndDraw();
            
            // Median filter
            ImagePlus imgM = median_filter(imgCell, 1, 1);
            
            // StarDist
            File starDistModelFile = new File(modelsPath+File.separator+stardistFociModel);
            StarDist2D star = new StarDist2D(syncObject, starDistModelFile);
            star.loadInput(imgM);
            star.setParams(stardistPercentileBottom, stardistPercentileTop, stardistFociProbThresh, stardistFociOverlayThresh, stardistOutput);
            star.run();
            
            // Label foci in 3D
            ImagePlus imgLabels = star.associateLabels();
            imgLabels.resize(imgCell.getWidth(), imgCell.getHeight(), 1, "none");
            imgLabels.setCalibration(cal);
            Objects3DIntPopulation fociPop = new Objects3DIntPopulation(ImageHandler.wrap(imgLabels));
            fociPop = new Objects3DIntPopulationComputation(fociPop).getFilterSize(minFociVol/pixVol, maxFociVol/pixVol);
            flush_close(imgCell);
            flush_close(imgM);
            flush_close(imgLabels);
            
            // Find foci in cells
            fociPop.translateObjects(box.xmin, box.ymin, box.zmin);
            Objects3DIntPopulation fociColocPop = findFociInCell(cell, fociPop);
            System.out.println(fociColocPop.getNbObjects() + " foci found in astrocyte " + cell.getLabel());
            double popVolume = findPopVolume(fociColocPop);
            for (Object3DInt foci: fociColocPop.getObjects3DInt()) {
                foci.setLabel(fociIndex);
                fociIndex++;
                cell.setType(fociColocPop.getNbObjects());
                cell.setCompareValue(popVolume);
                allFociPop.addObject(foci);
            }
        }
        return(allFociPop);
    }
    
    
    /**
     * Median filter using CLIJ2
     */ 
    public ImagePlus median_filter(ImagePlus img, double sizeXY, double sizeZ) {
       ClearCLBuffer imgCL = clij2.push(img);
       ClearCLBuffer imgCLMed = clij2.create(imgCL);
       clij2.median3DBox(imgCL, imgCLMed, sizeXY, sizeXY, sizeZ);
       clij2.release(imgCL);
       ImagePlus imgMed = clij2.pull(imgCLMed);
       clij2.release(imgCLMed);
       return(imgMed);
    }
     
    
    /**
     * Find dots population colocalizing with a cell objet
     */
    public Objects3DIntPopulation findFociInCell(Object3DInt cellObj, Objects3DIntPopulation dotsPop) {
        Objects3DIntPopulation colocPop = new Objects3DIntPopulation();
        if (dotsPop.getNbObjects() > 0) {
            for (Object3DInt dot: dotsPop.getObjects3DInt()) {
                VoxelInt vox = new MeasureCentroid(dot).getCentroidRoundedAsVoxelInt();
                if (cellObj.contains(vox)) colocPop.addObject(dot);
            }
        }
        return(colocPop);
    }
    
    
    /**
     * Find total volume of objects in population  
     */
    public double findPopVolume(Objects3DIntPopulation pop) {
        double sumVol = 0;
        for(Object3DInt obj: pop.getObjects3DInt()) {
            sumVol += new MeasureVolume(obj).getVolumeUnit();
        }
        return(sumVol);
    }

    
    /**
     * Save detected cells and foci in image
     */
    public void drawResults(Objects3DIntPopulation cellPop, Objects3DIntPopulation dapiPop, Objects3DIntPopulation fociPop, ImagePlus img, String imageName, String outDir) {
        ImageHandler imgCellObj = ImageHandler.wrap(img).createSameDimensions();
        ImageHandler imgDapiObj = imgCellObj.createSameDimensions();
        ImageHandler imgFociObj = imgCellObj.createSameDimensions();
        cellPop.drawInImage(imgCellObj);
        for (Object3DInt nuc: dapiPop.getObjects3DInt())
            if (nuc.getIdObject() != 0) nuc.drawObject(imgDapiObj, nuc.getIdObject());
        for (Object3DInt foci: fociPop.getObjects3DInt())
            foci.drawObject(imgFociObj, 255);

        ImagePlus[] imgColors = {imgFociObj.getImagePlus(), imgCellObj.getImagePlus(), imgDapiObj.getImagePlus(), img};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, true);
        imgObjects.setCalibration(cal);
        FileSaver imgObjectsFile = new FileSaver(imgObjects);
        imgObjectsFile.saveAsTiff(outDir + imageName + ".tif");
        
        flush_close(imgObjects);
        imgCellObj.closeImagePlus();
        imgDapiObj.closeImagePlus();
        imgFociObj.closeImagePlus();
    }
} 
