package edu.buffalo.cse.cse486586.simpledynamo;

/**
 * Created by ashwin on 4/27/16.
 */
public class MsgObj {
    public String msgKey = null;
    public String msgValue = null;
    public String msgSender = null; //ADVnum
    public String msgType = null;

    public MsgObj(String msgType, String msgSender, String msgKey, String msgValue){
        this.msgKey = msgKey;
        this.msgType = msgType;
        this.msgSender = msgSender;
        this.msgValue = msgValue;
    }

    public String createMsg(){
        String msg = msgType+"~~"+msgSender+"~~"+msgKey+"~~"+msgValue;
        return msg;
    }

}
