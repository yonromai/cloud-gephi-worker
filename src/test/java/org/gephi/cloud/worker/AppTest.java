package org.gephi.cloud.worker;

import com.amazonaws.services.sqs.model.Message;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple App.
 */
public class AppTest extends TestCase {

    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public AppTest(String testName) {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(AppTest.class);
    }

    /**
     * Rigourous Test :-)
     */
    public void testApp() {
        try {
            Properties properties = new Properties();
            properties.setProperty("input_bucket_name", "dev-cloudgephi");
            properties.setProperty("output_bucket_name", "dev-cloudgephi");
            properties.setProperty("input_queue_url", "https://queue.amazonaws.com/801673701464/cloudgephijobstest");
            properties.setProperty("output_queue_url", "https://queue.amazonaws.com/801673701464/cloudgephicallbackstest");
            Worker worker = new Worker(properties);

            AmazonClient awsClient = new AmazonClient(worker.getProperties());

            //Clean files
            awsClient.cleanFile("users/testUser/graphs/42/sample.gexf", awsClient.getInputBucketName());
            awsClient.cleanFile("users/testUser/graphs/42/sample.png", awsClient.getOutputBucketName());
            awsClient.cleanFile("users/testUser/graphs/42/sample.thumb.png", awsClient.getOutputBucketName());

            //Load sample GEXF
            InputStream gexfStrem = getClass().getResourceAsStream("/sample.gexf");
            byte[] gexfData = ByteStreams.toByteArray(gexfStrem);
            gexfStrem.close();

            //Upload it to the jobs under the foo project
            awsClient.upload(gexfData, awsClient.getInputBucketName(), "application/gexf+xml", "users/testUser/graphs/42/sample.gexf", "sample.gexf");
            awsClient.finishUploads();

            //Send message on the inputqueue
            HashMap<String,String> params = new HashMap<String,String>();
            params.put("user_id", "testUser");
            params.put("graph_id", "42");
            params.put("graph_name", "sample");
            params.put("graph_format", "gexf");
            JobMessage job = new JobMessage(JobMessage.JobType.RENDER, params);
            String serializedMessage = job.serialize();
            Logger.getLogger(AppTest.class.getName()).log(Level.INFO, "Sending message: {0}", serializedMessage);
            awsClient.sendMessages(serializedMessage, awsClient.getInputQueueUrl());

            //Wait a little bit so the message is in the input queue
            Thread.sleep(2000);

            //Run the worker - just once
            worker.run();

            //Look for result file on S3
            byte[] png = awsClient.download("users/testUser/graphs/42/sample.png", awsClient.getOutputBucketName());
            byte[] thumbPng = awsClient.download("users/testUser/graphs/42/sample.thumb.png", awsClient.getOutputBucketName());
            assert(png.length > thumbPng.length);

            //Look if received message on output queue
            List<Message> msgs = awsClient.getMessages(awsClient.getOutputQueueUrl());
            awsClient.deleteMessages(msgs, awsClient.getOutputQueueUrl());
            assertEquals(1, msgs.size());
            Message msg = msgs.get(0);
            params.clear();
            params.put("user_id", "testUser");
            params.put("graph_id", "42");
            params.put("graph_name", "sample");
            CallbackMessage expectedCallback = new CallbackMessage(CallbackMessage.CallbackType.RENDERED, params);
            assertEquals(expectedCallback.serialize(), msg.getBody());

        } catch (InterruptedException ex) {
            Logger.getLogger(AppTest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(AppTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
