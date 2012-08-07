package org.gephi.cloud.worker;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import org.codehaus.jackson.map.ObjectMapper;

/**
 *
 * @author yonromai
 */
public class Message {

    protected HashMap<String,String> params;

    protected final ObjectMapper mapper = new ObjectMapper();

    public Message() {
    }

    // Unserialize
    public Message(String serializedMessage) throws IOException {
      Message tempMsg = mapper.readValue(serializedMessage, Message.class);
      this.setParams(tempMsg.getParams());
    } 

    public Message(HashMap<String,String> params) {
        this.params = params;
    }

    public HashMap<String,String> getParams() {
        return params;
    }

    public void setParams(HashMap<String,String> params) {
        this.params = params;
    }

    public String serialize() throws IOException {
        StringWriter writer = new StringWriter();
        mapper.writeValue(writer, this);
        return writer.toString();
    } 
}
