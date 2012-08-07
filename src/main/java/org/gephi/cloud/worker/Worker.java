package org.gephi.cloud.worker;

import com.amazonaws.services.sqs.model.Message;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
// import org.codehaus.jackson.map.ObjectMapper;
import java.awt.Color;
import java.io.File;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.preview.api.*;
import org.gephi.preview.types.DependantOriginalColor;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.openide.util.Lookup;
import org.gephi.io.exporter.preview.PNGExporter;
import org.gephi.io.exporter.api.ExportController;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.gephi.io.importer.plugin.file.ImporterGEXF;

public class Worker {

    //Logger
    private static final Logger logger = Logger.getLogger(Worker.class.getName());
    //Conf
    private static final int DELAY = 1000;
    //Stuff
    private final Properties properties;
    private final AmazonClient awsClient;
    // private final ObjectMapper mapper = new ObjectMapper();
    //State
    private boolean stop = false;
    //

    public Worker() {
        //Load properties
        properties = loadProperties();

        //Init Amazon Client
        awsClient = new AmazonClient(properties);
    }

    public Worker(Properties props) {
        //Load properties
        properties = loadProperties();
        for (Entry<Object, Object> e : props.entrySet()) {
            properties.put(e.getKey(), e.getValue());
        }

        //Init Amazon Client
        awsClient = new AmazonClient(properties);
    }

    /**
     * Continuously run the worker and pull messages every second
     */
    public void runContinuously() {
        while (!stop) {
            run();
        }

        //Cleanup
        awsClient.finishUploads();
        awsClient.shutdownNow();
    }

    public void run() {
        //Coninuously pull and process messages
        List<Message> messages = awsClient.getMessages(awsClient.getInputQueueUrl());
        while (messages != null && !messages.isEmpty()) {
//            logger.log(Level.INFO, "Worker received {0} messages to process. Starting... ", messages.size());
            for (Message message : messages) {
                String msg = message.getBody();
                logger.log(Level.INFO, "Starting processing message={0}", msg.substring(0, Math.min(msg.length(), 100)));
                processMessage(message);
                logger.log(Level.INFO, "End processing");
            }
            awsClient.deleteMessages(messages, awsClient.getInputQueueUrl());
            messages = awsClient.getMessages(awsClient.getInputQueueUrl());
        }
        try {
//            logger.log(Level.INFO, "Now sleeping {0} ms", DELAY);
            Thread.sleep(DELAY);
        } catch (InterruptedException ex) {
            Logger.getLogger(Worker.class.getName()).log(Level.SEVERE, null, ex);
        }

//        logger.log(Level.INFO, "Stopping worker...");
    }

    public void stop() {
        stop = true;
    }

    private void processMessage(Message message) {
        try {
            //Unserialize job message
            JobMessage job = new JobMessage(message.getBody());
            String fileKey = job.getParams().get("fileKey");
            String graphDir = fileKey.substring(0, fileKey.lastIndexOf("/"));
            String gaphName = fileKey.substring(fileKey.lastIndexOf("/") + 1, fileKey.lastIndexOf("."));

            //Init a project - and therefore a workspace
            ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
            pc.newProject();
            Workspace workspace = pc.getCurrentWorkspace();

            //Import file from S3
            ImportController importController = Lookup.getDefault().lookup(ImportController.class);
            Container container;
            try {
                container = importController.importFile(
                   new ByteArrayInputStream(awsClient.download(fileKey, awsClient.getOutputBucketName())), 
                   new ImporterGEXF());
            } catch (Exception ex) {
                ex.printStackTrace();
                return;
            }

            //Append imported data to GraphAPI
            importController.process(container, new DefaultProcessor(), workspace);

            //Preview configuration
            PreviewController previewController = Lookup.getDefault().lookup(PreviewController.class);
            PreviewModel previewModel = previewController.getModel();
            previewModel.getProperties().putValue(PreviewProperty.BACKGROUND_COLOR, Color.BLACK);
            previewController.refreshPreview();

            //Simple PNG export
            ExportController ec = Lookup.getDefault().lookup(ExportController.class);
             
            //PNG Exporter config and export to Byte array
            PNGExporter pngExporter = (PNGExporter) ec.getExporter("png");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // Export full size png
            pngExporter.setHeight(2048);
            pngExporter.setWidth(2048);
            ec.exportStream(baos, pngExporter);
            //Write output on S3
            awsClient.upload(
              baos.toByteArray(), 
              awsClient.getOutputBucketName(), 
              "image/png", 
              graphDir + "/" + gaphName + ".png",  
              gaphName + ".png");

            // Export thumbnail png
            baos = new ByteArrayOutputStream();
            pngExporter.setHeight(256);
            pngExporter.setWidth(256);
            ec.exportStream(baos, pngExporter);
            //Write output on S3
            awsClient.upload(
              baos.toByteArray(), 
              awsClient.getOutputBucketName(), 
              "image/png", 
              graphDir + "/" + gaphName + ".thumb.png",  
              gaphName + ".thumb.png");
            awsClient.finishUploads();
            

            //Send message to the ouptut queue
            HashMap<String,String> params = new HashMap<String,String>();
            params.put("fileKey", graphDir + "/" + gaphName + ".png");
            CallbackMessage callback = new CallbackMessage(CallbackMessage.CallbackType.RENDERED, params);
            awsClient.sendMessages(callback.serialize(), awsClient.getOutputQueueUrl());
        } catch (IOException ex) {
            Logger.getLogger(Worker.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private Properties loadProperties() {
        Properties prop = new Properties();

        try {
            //load a properties file
            prop.load(getClass().getResourceAsStream("/aws.properties"));
            return prop;
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public Properties getProperties() {
        return properties;
    }

    public static void main(String[] args) {
        Worker app = new Worker();
        app.runContinuously();
    }
}
