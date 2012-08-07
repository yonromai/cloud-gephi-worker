package org.gephi.cloud.worker;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import org.codehaus.jackson.map.ObjectMapper;
/**
 *
 * @author mbastian
 * @author yonromai
 */
public class JobMessage extends Message{

    public enum JobType {

        RENDER
    };
    private JobType type;
    public JobMessage() {
    }

    public JobMessage(String serializedMessage) throws IOException {
      super();
      JobMessage tempMsg = mapper.readValue(serializedMessage, JobMessage.class);
      this.params = tempMsg.getParams();
      this.type = type;
    }

    public JobMessage(JobType type, HashMap<String,String> params) {
        super(params);
        this.type = type;
    }

    public JobType getType() {
        return type;
    }

    public void setType(JobType type) {
        this.type = type;
    }
}
