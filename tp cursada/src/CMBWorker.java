import com.rabbitmq.client.*;
import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;
import org.json.JSONObject;
import org.opencv.core.Mat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

// Recieves a frame and does face detection on it
public class CMBWorker {

    public static void main(String[] argv) throws Exception {

        String rabbitmqHost;
        if (argv.length == 1) {
            rabbitmqHost = argv[0];
        } else {
            rabbitmqHost = FileHelper.RABBITMQ_HOST;
        }

        // define CMBid
        System.out.println("Please enter CMBid: ");
        int CMBid = new Scanner(System.in).nextInt();
        String CMB_QUEUE_NAME = "CMB" + Integer.toString(CMBid);

        // broker connection
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitmqHost);
        Connection connection = factory.newConnection();

        // create channel
        final Channel channel = connection.createChannel();

        /* ******************************************************** */

        // create CMB-CMC exchange
        final String CMC_EXCHANGE_NAME = "CMC" + "_FANOUT";
        channel.exchangeDeclare(CMC_EXCHANGE_NAME, "fanout");

        /* ******************************************************** */

        // create CAMERAS-CMB exchange
        String CMB_EXCHANGE_NAME = "CMB" + CMBid + "_FANOUT";
        channel.exchangeDeclare(CMB_EXCHANGE_NAME, "fanout");

        // create CAMERAS-CMB queue
        boolean durable = true;
        channel.queueDeclare(CMB_QUEUE_NAME, durable, false, false, null);
        // only allow one message at a time
        int prefetchCount = 1;
        channel.basicQos(prefetchCount);

        // bind CAMERAS-CMB queue to exchange
        channel.queueBind(CMB_QUEUE_NAME, CMB_EXCHANGE_NAME, "");

        /* ******************************************************** */

        System.out.println(" [*] CMB worker waiting for messages. To exit press CTRL+C");
        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
            throws IOException {

                // assemble json message
                JSONObject jsonMessage = new JSONObject(new String(body, "UTF-8"));

                // get file size
                int fileSize = jsonMessage.getInt("fileSize");

                // get frame id
                String id = jsonMessage.getString("id");

                // get coordiantes
                double coordinates_x = jsonMessage.getDouble("coordinatesX");
                double coordinates_y = jsonMessage.getDouble("coordinatesY");

                // get image bytes
                String encodedImage = jsonMessage.getString("fileByte64");
                byte[] decodedImage = Base64.decode(encodedImage);

                //String decodedImageString = new String(decodedImage, "UTF-8");
                System.out.println(" [x] Received a frame with id: " + id + ", fileSize: " + Integer.toString(fileSize) +
                                        ", Coordinates: " + Double.toString(coordinates_x) + ", " + Double.toString(coordinates_y));

                // detect faces
                List<BufferedImage> faces = FrameFaceDetector.detectFaces(FileHelper.imageBytesToBufferedImage(decodedImage));
                //int numFacesDetected = new Random().nextInt(4); // entre 0 y 3
                int numFacesDetected = faces.size();

                // send each face to CMC
                for (int i = 0; i < numFacesDetected; ++i){

                    // assemble json message
                    JSONObject faceMessage = new JSONObject();

                    // put original frame id
                    String frameID = id + "_" + "CMB" + CMBid;
                    faceMessage.put("frameID", frameID);
                    // put original frame
                    faceMessage.put("fileByte64", encodedImage);

                    // put face id
                    String faceId = frameID + "_FACE" + i;
                    faceMessage.put("faceID", faceId);
                    // put face bytes
                    faceMessage.put("faceByte64", Base64.encode(FileHelper.bufferedImageToBytes(faces.get(i))));

                    // put coordinates
                    faceMessage.put("coordinatesX", coordinates_x);
                    faceMessage.put("coordinatesY", coordinates_y);

                    // put request type
                    faceMessage.put("requestType", CMCWorker.DatabaseRequest.UPDATE_FACE.toInt());

                    channel.basicPublish(CMC_EXCHANGE_NAME, "", MessageProperties.PERSISTENT_TEXT_PLAIN, faceMessage.toString().getBytes("UTF-8"));
                    System.out.println(" [x] Sent image with filename: " + faceId);
                }

                System.out.println(" [x] Done");
                channel.basicAck(envelope.getDeliveryTag(), false);
            }
        };

        boolean autoAck = false;
        channel.basicConsume(CMB_QUEUE_NAME, autoAck, consumer);
    }
}
