import com.rabbitmq.client.*;
import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Scanner;

// Logs all frames recieved by a CMB
public class CMBLog {

    public static void main(String[] argv) throws Exception {

        // define CMBid
        System.out.println("Please enter CMBid: ");
        int CMBid = new Scanner(System.in).nextInt();
        String QUEUE_NAME = "LOGCMB" + Integer.toString(CMBid);

        // broker connection
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(FileHelper.RABBITMQ_HOST);
        Connection connection = factory.newConnection();

        // create channel
        final Channel channel = connection.createChannel();

        // create exchange
        String EXCHANGE_NAME = "CMB" + CMBid + "_FANOUT";
        channel.exchangeDeclare(EXCHANGE_NAME, "fanout");

        // create queue for frames
        boolean durable = false; // si es durable y se deja de usar quedan acumulados en rabbitmq para siempre los msjs
        channel.queueDeclare(QUEUE_NAME, durable, false, false, null);
        // only allow one message at a time
        int prefetchCount = 1;
        channel.basicQos(prefetchCount);

        // bind queue to exchange
        channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, "");

        System.out.println(" [*] CMB log waiting for messages. To exit press CTRL+C");
        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
                    throws IOException {

                // assemble json message
                JSONObject jsonMessage = new JSONObject(new String(body, "UTF-8"));

                // get file size
                int fileSize = jsonMessage.getInt("fileSize");

                // get file size
                String id = jsonMessage.getString("id");

                // get image bytes
                String encodedImage = jsonMessage.getString("fileByte64");
                byte[] decodedImage = Base64.decode(encodedImage);

                String decodedImageString = new String(decodedImage, "UTF-8");
                System.out.println(" [x] Received a frame with id: " + id + ", fileSize: " + Integer.toString(fileSize));

                // write image to test output folder
                try{
                    FileHelper.writeFrameBytes(decodedImage, "../test_output/" + id + ".png");
                } catch (IOException e){
                    System.out.println(" [-] Error logging image: " + id);
                } finally {
                    System.out.println(" [x] Done");
                    channel.basicAck(envelope.getDeliveryTag(), false);
                }
            }
        };

        boolean autoAck = false;
        boolean exclusiveConsumer = true;
        try {
            channel.basicConsume(QUEUE_NAME, autoAck, "", false, exclusiveConsumer, null, consumer);
        } catch (IOException e) {
            System.out.println(" [-] Couldn't do basicConsume, another CMBLog is probably already running for CMB: " + Integer.toString(CMBid));
            System.exit(0);
        }
    }

}
