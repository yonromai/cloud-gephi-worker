package org.gephi.cloud.worker;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import org.codehaus.jackson.map.ObjectMapper;

/**
 *
 * @author yonromai
 */
public class CallbackMessage extends Message{

    public enum CallbackType {
        RENDERED,
        ERROR
    };
    private CallbackType type;

    public CallbackMessage(){

    }

    public CallbackMessage(String serializedMessage) throws IOException {
      super();
      CallbackMessage tempMsg = mapper.readValue(serializedMessage, CallbackMessage.class);
      this.params = tempMsg.getParams();
      this.type = type;
    } 

    public CallbackMessage(CallbackType type, HashMap<String,String> params) {
        super(params);
        this.type = type;
    }

    public CallbackType getType() {
        return type;
    }

    public void setType(CallbackType type) {
        this.type = type;
    }

}
