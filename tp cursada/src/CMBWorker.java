import com.rabbitmq.client.*;
import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opencv.core.Mat;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

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
        String CMB_WEB_QUEUE_NAME = "CMB_WEB_" + Integer.toString(CMBid);

        CockroachConnector.init("localhost", Integer.toString(26257 + CMBid));

        // broker connection
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitmqHost);
        Connection connection = factory.newConnection();

        // create channel
        final Channel channel = connection.createChannel();

        /* ******************************************************** */

        // create CMB-CMC exchange
        final String CMC_EXCHANGE_NAME = "CMC" + "_DIRECT";
        channel.exchangeDeclare(CMC_EXCHANGE_NAME, "direct");

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

        // create WEB-CMB queue
        durable = true;
        channel.queueDeclare(CMB_WEB_QUEUE_NAME, durable, false, false, null);
        // only allow one message at a time
        prefetchCount = 1;
        channel.basicQos(prefetchCount);

        // bind WEB-CMB queue to exchange
        channel.queueBind(CMB_WEB_QUEUE_NAME, CMC_EXCHANGE_NAME, "cmb" + Integer.toString(CMBid));

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

                    channel.basicPublish(CMC_EXCHANGE_NAME, "cmc", MessageProperties.PERSISTENT_TEXT_PLAIN, faceMessage.toString().getBytes("UTF-8"));
                    System.out.println(" [x] Sent image with filename: " + faceId);
                }

                System.out.println(" [x] Done");
                channel.basicAck(envelope.getDeliveryTag(), false);
            }
        };

        boolean autoAck = false;
        channel.basicConsume(CMB_QUEUE_NAME, autoAck, consumer);


        Consumer consumer2 = new DefaultConsumer(channel) {

            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
                    throws IOException {

                // assemble json message
                JSONObject jsonMessage = new JSONObject(new String(body, "UTF-8"));

                // check request type (ver error aca)
                CMCWorker.DatabaseRequest requestType = CMCWorker.DatabaseRequest.fromInt(jsonMessage.getInt("requestType"));
                System.out.println(" [x] Received a request with type: " + requestType);

                if (requestType == CMCWorker.DatabaseRequest.QUERY_FACE_MOVEMENTS) {

                    // get face id
                    int faceID = jsonMessage.getInt("faceID");

                    // get database id
                    String databaseID = "";

                    LinkedList<MovementsStruct> movements = new LinkedList<>();
                    CockroachConnector.getMovements(faceID, movements);

                    // --------------- ENVIAR MOVIMIENTOS
                    JSONObject message = new JSONObject();

                    if (movements.size() <= 0) {

                        message.put("match", false);
                        message.put("response", "Error: no results found for given faceID");

                    } else {

                        message.put("match", true);
                        message.put("response", "Success!");
                        message.put("databaseID", databaseID);

                        JSONArray data = new JSONArray();

                        for (MovementsStruct ms : movements) {

                            JSONObject matchInfo = new JSONObject();

                            // image
                            String encodedImage = Base64.encode(ms.image);

                            // date
                            String originalDateString = ms.date;
                            String finalDateString = originalDateString;
                            /*SimpleDateFormat input = new SimpleDateFormat("yyyyMMddHHmmssSSS");
                            try {
                                Date dateValue = input.parse(originalDateString);
                                SimpleDateFormat output = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                                finalDateString = output.format(dateValue);
                            } catch (ParseException e) {
                                e.printStackTrace();
                                finalDateString = "00/00/0000 00:00:00";
                            }*/

                            // assemble json
                            matchInfo.put("coordX", ms.coordX);
                            matchInfo.put("coordY", ms.coordY);
                            matchInfo.put("encodedImage", encodedImage);
                            matchInfo.put("date", finalDateString);

                            data.put(matchInfo);
                        }

                        message.put("dataSize", data.length());
                        message.put("data", data);
                    }

                    channel.basicPublish("", properties.getReplyTo(), MessageProperties.BASIC, message.toString().getBytes("UTF-8"));

                } else {
                    System.out.println(" [E] Received request with id different from QUERY_FACE_MOVEMENTS");
                }

                System.out.println(" [x] Done");
                channel.basicAck(envelope.getDeliveryTag(), false);
            }
        };

        autoAck = false;
        channel.basicConsume(CMB_WEB_QUEUE_NAME, autoAck, consumer2);
    }
}
