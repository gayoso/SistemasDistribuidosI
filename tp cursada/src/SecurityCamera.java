import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;
import org.json.JSONObject;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class SecurityCamera {

    public static void main(String[] argv) throws Exception {

        String rabbitmqHost;
        if (argv.length == 2) {
            rabbitmqHost = argv[1];
        } else {
            rabbitmqHost = FileHelper.RABBITMQ_HOST;
        }

        String fileOrDirToSend;
        if (argv.length < 1) {
            System.out.println(" [E] Please specify a file or directory to send as the first argument. Defaulting to 'camera_frames_test' dir");
            //fileOrDirToSend = "../camera_frames_test/";
            fileOrDirToSend = "../camera_frames_test/arnold-7.jpg";
        } else {
            fileOrDirToSend = argv[0];
        }

        // define CMBid
        System.out.println("Please enter CMBid: ");
        int CMBid = new Scanner(System.in).nextInt();
        String QUEUE_NAME = "CMB" + Integer.toString(CMBid);

        // define CAMid
        System.out.println("Please enter CAMid: ");
        int CAMid = new Scanner(System.in).nextInt();
        String CAM_NAME = "CAM" + Integer.toString(CAMid);

        // define x location
        System.out.println("Please enter x coordinates: ");
        double coordinates_x = new Scanner(System.in).nextDouble();

        // define y location
        System.out.println("Please enter y coordinates: ");
        double coordinates_y = new Scanner(System.in).nextDouble();

        // define sleep between frames
        System.out.println("Please enter sleep time (miliseconds) between frames: ");
        int sleepMilis = new Scanner(System.in).nextInt();

        /* ******************************************************** */

        // broker connection
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitmqHost);
        Connection connection = factory.newConnection();

        // create channel
        Channel channel = connection.createChannel();

        /* ******************************************************** */

        // create exchange
        String EXCHANGE_NAME = "CMB" + CMBid + "_FANOUT";
        channel.exchangeDeclare(EXCHANGE_NAME, "fanout");

        // create queue for frames
        //boolean durable = true;
        //channel.queueDeclare(QUEUE_NAME, durable, false, false, null);

        /* ******************************************************** */

        File dir = new File(fileOrDirToSend);
        List<File> framesToSend = new ArrayList<>();

        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            for (File f : files) {
                framesToSend.add(f);
            }
        } else {
            framesToSend.add(dir);
        }

        // choose frames to send
        /*File dir = new File("../camera_frames_test/");
        File[] files = dir.listFiles();
        List<File> framesToSend = new ArrayList<>();

        // send all files in a folder
        // get a random frame filename
        for (File f : files) {
            framesToSend.add(f);
        }*/

        // send 'framesAmount' random frames form a folder
        /*System.out.println("Please enter amount of frames to send: ");
        int framesAmount = new Scanner(System.in).nextInt();
        for (int i = 0; i < framesAmount; ++i) {
            File chosenFile = files[new Random().nextInt(files.length)];
            while (chosenFile.isDirectory()) {
                files = chosenFile.listFiles();
                chosenFile = files[new Random().nextInt(files.length)];
            }
            framesToSend.add(chosenFile);
        }*/

        // send a specific file from a folder

        // send some frames
        for(int i = 0; i < framesToSend.size(); ++i) {

            File chosenFile = framesToSend.get(i);
            String filename = chosenFile.getName();

            // get bytes
            byte[] image = FileHelper.getFileBytes(chosenFile);

            // encode image in base64
            String encodedImage = Base64.encode(image);

            // determine image id
            DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmssSSS");
            String imageID = CAM_NAME + dateFormat.format(new Date()); //2016_11_16_12_08_43

            // assemble json message
            JSONObject message = new JSONObject();
            message.put("id", imageID);
            message.put("fileSize", image.length);
            message.put("fileByte64", encodedImage);
            message.put("coordinatesX", coordinates_x);
            message.put("coordinatesY", coordinates_y);

            //channel.basicPublish("", QUEUE_NAME, MessageProperties.PERSISTENT_TEXT_PLAIN, message.toString().getBytes("UTF-8"));
            channel.basicPublish(EXCHANGE_NAME, "", MessageProperties.PERSISTENT_TEXT_PLAIN, message.toString().getBytes("UTF-8"));
            System.out.println(" [x] Sent image with filename: " + filename + ", fileSize: " + Integer.toString(image.length));

            // sleep
            Thread.sleep(sleepMilis);
        }

        channel.close();
        connection.close();
    }
}
