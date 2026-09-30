import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;

/** Local experiment helper. Never exposed as an application HTTP endpoint. */
public class MqProbe {
    public static void main(String[] args) throws Exception {
        var producer=new DefaultMQProducer("flashflow-experiment-probe");
        producer.setNamesrvAddr(args[0]);producer.setInstanceName("probe-"+ProcessHandle.current().pid());producer.start();
        try {
            byte[] payload=Files.readAllBytes(Path.of(args[2]));int count=Integer.parseInt(args[3]);int level=Integer.parseInt(args[4]);
            for(int i=0;i<count;i++) {
                var message=new Message(args[1],"PROBE",args[5],payload);if(level>0) message.setDelayTimeLevel(level);
                var result=producer.send(message);if(result.getSendStatus()!=SendStatus.SEND_OK) throw new IllegalStateException(result.toString());
            }
            System.out.println("ACKNOWLEDGED messages="+count+" delayLevel="+level);
        } finally { producer.shutdown(); }
    }
}
