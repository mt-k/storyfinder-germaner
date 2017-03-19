package de.tu.darmstadt.lt.storyfinder.nerservice;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngine;
import static org.apache.uima.fit.pipeline.SimplePipeline.runPipeline;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.apache.uima.UIMAException;
import org.apache.uima.UIMAFramework;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.cleartk.ml.jar.GenericJarClassifierFactory;
import org.cleartk.util.cr.FilesCollectionReader;

import de.tu.darmstadt.lt.ner.annotator.NERAnnotator;
import de.tu.darmstadt.lt.ner.preprocessing.ChangeColon;
import de.tu.darmstadt.lt.ner.preprocessing.Configuration;
import de.tu.darmstadt.lt.ner.preprocessing.GermaNERMain;
import de.tu.darmstadt.lt.ner.reader.NERReader;
import de.tu.darmstadt.lt.ner.writer.EvaluatedNERWriter;
import de.tudarmstadt.ukp.dkpro.core.io.text.StringReader;

/**
 * Root resource (exposed at "germaner" path)
 */
@Path("germaner")
public class GermanerResource {
    private static final Logger LOG = Logger.getLogger(GermanerResource.class.getName());
    static File modelDirectory;
    static InputStream configFile = null;
    static Properties prop;

    public static Properties getPropFile()
    {
        return prop;
    }
    
    
    public static void initNERModel()
            throws IOException
        {
            configFile = configFile == null ? ClassLoader.getSystemResourceAsStream("config.properties")
                    : configFile;
            GermaNERMain.configFile = configFile;
            
            prop = new Properties();
            GermaNERMain.prop = prop;
            loadConfig();
        }
    
    private static void setModelDir()
            throws IOException, FileNotFoundException
        {
            modelDirectory = (Configuration.modelDir == null || Configuration.modelDir.isEmpty())
                    ? new File("output") : new File(Configuration.modelDir);
            modelDirectory.mkdirs();

            if (!new File(modelDirectory, "model.jar").exists()) {
                IOUtils.copyLarge(ClassLoader.getSystemResourceAsStream("model/model.jar"),
                        new FileOutputStream(new File(modelDirectory, "model.jar")));
            }
            if (!new File(modelDirectory, "MANIFEST.MF").exists()) {
                IOUtils.copyLarge(ClassLoader.getSystemResourceAsStream("model/MANIFEST.MF"),
                        new FileOutputStream(new File(modelDirectory, "MANIFEST.MF")));
            }
            if (!new File(modelDirectory, "feature.xml").exists()) {
                IOUtils.copyLarge(ClassLoader.getSystemResourceAsStream("feature/feature.xml"),
                        new FileOutputStream(new File(modelDirectory, "feature.xml")));
            }
        }

        public static void loadConfig()
            throws IOException
        {
            prop.load(configFile);
            if (Configuration.testFileName != null) {
                if (Configuration.trainFileName != null) {
                    Configuration.mode = "ft";
                }
                else {
                    Configuration.mode = "t";
                }
            }
            if (Configuration.trainFileName != null) {
                if (Configuration.testFileName != null) {
                    Configuration.mode = "ft";
                }
                else {
                    Configuration.mode = "f";
                }
            }
            Configuration.useClarkPosInduction = prop.getProperty("useClarkPosInduction").equals("1")
                    ? true : false;
            Configuration.usePosition = prop.getProperty("usePosition").equals("1") ? true : false;
            Configuration.useFreeBase = prop.getProperty("useFreeBase").equals("1") ? true : false;
            Configuration.modelDir = prop.getProperty("modelDir");
        }
    
        public static void classifyTestFile(File aClassifierJarPath, File testPosFile, File outputFile,
                File aNodeResultFile, List<Integer> aSentencesIds)
                    throws UIMAException, IOException
        {
            runPipeline(
                    FilesCollectionReader.getCollectionReaderWithSuffixes(testPosFile.getAbsolutePath(),
                            NERReader.CONLL_VIEW, testPosFile.getName()),
                    
                    createEngine(NERReader.class),
                    createEngine(NERAnnotator.class, NERAnnotator.PARAM_FEATURE_EXTRACTION_FILE,
                            aClassifierJarPath.getAbsolutePath() + "/feature.xml",
                            NERAnnotator.FEATURE_FILE, aClassifierJarPath.getAbsolutePath(),
                            GenericJarClassifierFactory.PARAM_CLASSIFIER_JAR_PATH,
                            aClassifierJarPath.getAbsolutePath() + "/model.jar"),
                    createEngine(EvaluatedNERWriter.class, EvaluatedNERWriter.OUTPUT_FILE, outputFile,
                            EvaluatedNERWriter.IS_GOLD, false, EvaluatedNERWriter.NOD_OUTPUT_FILE,
                            aNodeResultFile, EvaluatedNERWriter.SENTENCES_ID, aSentencesIds));
        }
        
    /**
     * Method handling HTTP GET requests. The returned object will be sent
     * to the client as "text/plain" media type.
     *
     * @return String that will be returned as a text/plain response.
     * @throws IOException 
     */    
    @POST
    @Consumes({"text/plain"})
    @Produces(MediaType.TEXT_PLAIN)
    public String run(String input) throws IOException {
    	//return "ok";
    	
    	//System.out.println(input);
    	
        long startTime = System.currentTimeMillis();
        long start = System.currentTimeMillis();

        ChangeColon c = new ChangeColon();
        File basePath = new File("/usr/src/germaner/tmp/");
        
        //Write input to file
        String tmpFileName = File.createTempFile("in-", ".tmp", basePath).getAbsolutePath();
        Writer fileWriter = new FileWriter(tmpFileName);
		fileWriter.write(input);
		fileWriter.close();

        Configuration.testFileName = tmpFileName;
        
        if(prop == null){
	        try {
	            // load a properties file
	            initNERModel();
	        }
	        catch (IOException ex) {
	            ex.printStackTrace();
	        }
    	}

        //List<String> responseData = new ArrayList<String>();
        String content = "";
        
        try {
            setModelDir();

            File outputtmpFile = new File(modelDirectory, "result.tmp");
            File outputFile = new File(tmpFileName + ".out");
           
          
                c.normalize(Configuration.testFileName, Configuration.testFileName + ".normalized");
                System.out.println("Start tagging");
                classifyTestFile(modelDirectory, new File(Configuration.testFileName + ".normalized"), outputtmpFile, null, null);
                // re-normalized the colon changed text
                c.deNormalize(outputtmpFile.getAbsolutePath(), outputFile.getAbsolutePath());
                
                InputStream inputStream       = new FileInputStream(tmpFileName + ".out");
                Reader      inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader in = new BufferedReader(inputStreamReader);
                
                String line;
                while ((line = in.readLine()) != null) {
                	String[] splitted = line.split("  ");
                	
                	//System.out.println(line);
                	//System.out.println(splitted.length);
                	
                	if(splitted.length != 2)continue;
                	if(splitted[1].equals("O"))continue;
                	if(splitted[1].startsWith("B-")){
                		if(content.length() != 0)
                			content += "\n";
                		content += splitted[1].substring(2);
                		content += " ";
                		content += " " + splitted[0];
                	}else if(splitted[1].startsWith("I-")){
                		content += " " + splitted[0];
                	}else{
                		System.out.println("Unknown type: " + splitted[1]);
                	}
                }
                
                /*while(data != -1){
                    char theChar = (char) data;
                    content += theChar;
                    data = inputStreamReader.read();
                }*/

                inputStreamReader.close();
  
            //long now = System.currentTimeMillis();
            //UIMAFramework.getLogger().log(Level.INFO, "Time: " + (now - start) + "ms");
                
            //ToDo Delete tmp files
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        System.out.println("Done in " + totalTime / 1000 + " seconds");
        return content;
    }
}
