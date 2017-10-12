import com.rabbitmq.client.*;
import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.opencv_core;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.bytedeco.javacpp.opencv_face.createLBPHFaceRecognizer;

public class CMCWorker {

    public enum DatabaseRequest{
        UPLOAD_FACE(0), UPDATE_FACE(1), QUERY_FACE(2), QUERY_FACE_MOVEMENTS(3), ERROR(4);

        private int value;
        DatabaseRequest(int i){
            this.value = i;
        }

        public int toInt(){
            return this.value;
        }

        static public DatabaseRequest fromInt(int i){
            if (i == 0){
                return UPLOAD_FACE;
            } else if (i == 1){
                return UPDATE_FACE;
            } else if (i == 2){
                return QUERY_FACE;
            } else if (i == 3){
                return QUERY_FACE_MOVEMENTS;
            } else {
                return ERROR;
            }
        }
    }

    private static void init() {

        // negrada para que se importe bien la libreria JavaCV, sino falla al usar IntPointer y DoublePointer
        createLBPHFaceRecognizer();

        File faces_match_dir = new File("../database_faces_match");
        if (!faces_match_dir.exists()) {
            faces_match_dir.mkdir();
        }

        File faces_no_match_dir = new File("../database_faces_no_match");
        if (!faces_no_match_dir.exists()) {
            faces_no_match_dir.mkdir();
        }

        File frames_dir = new File("../database_frames");
        if (!frames_dir.exists()) {
            frames_dir.mkdir();
        }

        File database_SRE_dir = new File("../database_SRE");
        if (!database_SRE_dir.exists()) {
            database_SRE_dir.mkdir();
        }

        File database_SRPL_dir = new File("../database_SRPL");
        if (!database_SRPL_dir.exists()) {
            database_SRPL_dir.mkdir();
        }

        File database_SRE = new File("../database_SRE/lbph_database");
        if (!database_SRE.exists()) {
            MyFaceRecognizer.createFromDir("../database_SRE");
        }

        File database_SRPL = new File("../database_SRPL/lbph_database");
        if (!database_SRPL.exists()) {
            MyFaceRecognizer.createFromDir("../database_SRPL");
        }
    }

    public static void main(String[] argv) throws Exception {

        String rabbitmqHost;
        if (argv.length == 1) {
            rabbitmqHost = argv[0];
        } else {
            rabbitmqHost = FileHelper.RABBITMQ_HOST;
        }

        // load opencv library
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        // init databases
        init();

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

        // create CMB-CMC queue
        String CMC_QUEUE_NAME = "CMC";
        boolean durable = true;
        channel.queueDeclare(CMC_QUEUE_NAME, durable, false, false, null);
        // only allow one message at a time
        int prefetchCount = 1;
        channel.basicQos(prefetchCount);

        // bind CMB-CMC queue to exchange
        channel.queueBind(CMC_QUEUE_NAME, CMC_EXCHANGE_NAME, "");

        /* ******************************************************** */

        System.out.println(" [*] CMC worker waiting for messages. To exit press CTRL+C");
        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
                    throws IOException {

                // assemble json message
                JSONObject jsonMessage = new JSONObject(new String(body, "UTF-8"));

                // check request type (ver error aca)
                DatabaseRequest requestType = DatabaseRequest.fromInt(jsonMessage.getInt("requestType"));
                System.out.println(" [x] Received a request with type: " + requestType);

                if (requestType == DatabaseRequest.UPLOAD_FACE) {

                    // get database id
                    String databaseID = jsonMessage.getString("databaseID");

                    // get face id
                    int faceID = jsonMessage.getInt("faceID");
                    int faceIDsecundario = 0;
                    if (faceID == -1) { // es una cara nueva, hay que asignarle el siguiente faceID no usado

                        FilenameFilter imgFilter = new FilenameFilter() {
                            public boolean accept(File dir, String name) {
                                name = name.toLowerCase();
                                return name.endsWith(".jpg") || name.endsWith(".pgm") || name.endsWith(".png");
                            }
                        };

                        // veo el mas grande en SRE
                        File dir_SRE = new File("../database_SRE/");
                        File[] files_SRE = dir_SRE.listFiles(imgFilter);

                        for (File file : files_SRE) {
                            //System.out.println(file.getName());
                            int label = Integer.parseInt(file.getName().split("\\-")[0]);
                            if (label > faceID) {
                                faceID = label;
                            }
                        }

                        // veo el mas grande en SRPL
                        File dir_SRPL = new File("../database_SRPL/");
                        File[] files_SRPL = dir_SRPL.listFiles(imgFilter);

                        for (File file : files_SRPL) {
                            //System.out.println(file.getName());
                            int label = Integer.parseInt(file.getName().split("\\-")[0]);
                            if (label > faceID) {
                                faceID = label;
                            }
                        }

                        faceID++;
                        faceIDsecundario++;

                    } else {

                        int finalFaceID = faceID;
                        FilenameFilter imgFilter = new FilenameFilter() {
                            public boolean accept(File dir, String name) {
                                name = name.toLowerCase();
                                return (name.endsWith(".jpg") || name.endsWith(".pgm") || name.endsWith(".png")) && name.startsWith(Integer.toString(finalFaceID));
                            }
                        };

                        File dir;
                        if (databaseID == "SRE") {
                            dir = new File("../database_SRE/");
                        } else {
                            dir = new File("../database_SRPL/");
                        }
                        File[] files = dir.listFiles(imgFilter);

                        for (File file : files) {
                            //System.out.println(fi le.getName());

                            int labelSecundario = Integer.parseInt((file.getName().split("\\-")[1]).split("\\.(?=[^\\.]+$)")[0]);
                            if (labelSecundario > faceIDsecundario) {
                                faceIDsecundario = labelSecundario;
                            }
                        }
                        faceIDsecundario++;
                    }
                    String faceIDString = faceID + "-" + faceIDsecundario;

                    // get image bytes
                    String encodedImage = jsonMessage.getString("fileByte64");
                    byte[] decodedImage = Base64.decode(encodedImage);

                    // detect faces
                    List<BufferedImage> faces = FrameFaceDetector.detectFaces(FileHelper.imageBytesToBufferedImage(decodedImage));

                    if (faces.size() > 1) {

                        // reply more than one face found
                        JSONObject message = new JSONObject();
                        message.put("response", "Error: more than one face found in the image"); // cambiar
                        channel.basicPublish( "", properties.getReplyTo(), MessageProperties.BASIC, message.toString().getBytes("UTF-8"));

                    } else if (faces.size() < 1) {

                        // reply no faces found
                        JSONObject message = new JSONObject();
                        message.put("response", "Error: no faces found in the image"); // cambiar
                        channel.basicPublish( "", properties.getReplyTo(), MessageProperties.BASIC, message.toString().getBytes("UTF-8"));

                    } else {

                        // write image to database
                        try{
                            MyFaceRecognizer.addTrainingImage("../database_" + databaseID, faces.get(0), faceID);
                            ImageIO.write(faces.get(0), "png", new File("../database_" + databaseID + "/" + faceIDString + ".png"));

                            if (faceIDsecundario == 1)
                                CockroachConnector.addFace(faceID, Integer.toString(faceID), databaseID);

                            // reply ok
                            JSONObject message = new JSONObject();
                            message.put("response", "Success!"); // cambiar
                            channel.basicPublish( "", properties.getReplyTo(), MessageProperties.BASIC, message.toString().getBytes("UTF-8"));

                        } catch (IOException e){
                            System.out.println(" [-] Error adding image: " + faceIDString + " to database: " + databaseID);

                            // reply error
                            JSONObject message = new JSONObject();
                            message.put("response", "Error writing image to database"); // cambiar
                            channel.basicPublish( "", properties.getReplyTo(), MessageProperties.BASIC, message.toString().getBytes("UTF-8"));

                        }
                    }

                } else if (requestType == DatabaseRequest.QUERY_FACE) {

                    // get image bytes
                    String encodedImage = jsonMessage.getString("fileByte64");
                    byte[] decodedImage = Base64.decode(encodedImage);

                    // detect faces
                    List<BufferedImage> faces = FrameFaceDetector.detectFaces(FileHelper.imageBytesToBufferedImage(decodedImage));

                    if (faces.size() > 1) {

                        System.out.println(" [x] Received image from WEB, more than one face found on image");

                        // reply more than one face found
                        JSONObject message = new JSONObject();
                        message.put("match", false);
                        message.put("response", "Error: more than one face found in the image");
                        channel.basicPublish( "", properties.getReplyTo(), MessageProperties.BASIC, message.toString().getBytes("UTF-8"));

                    } else if (faces.size() < 1) {

                        System.out.println(" [x] Received image from WEB, no faces found on image");

                        // reply no faces found
                        JSONObject message = new JSONObject();
                        message.put("match", false);
                        message.put("response", "Error: no faces found in the image");
                        channel.basicPublish( "", properties.getReplyTo(), MessageProperties.BASIC, message.toString().getBytes("UTF-8"));

                    } else {

                        // run SRE face recognition
                        IntPointer label_SRE = new IntPointer(1);
                        DoublePointer confidence_SRE = new DoublePointer(1);
                        opencv_core.Mat m = FileHelper.bufferedImageToJavacvMat(faces.get(0));
                        MyFaceRecognizer.predict("../database_SRE", m, label_SRE, confidence_SRE);

                        // run SRPL face recognition
                        IntPointer label_SRPL = new IntPointer(1);
                        DoublePointer confidence_SRPL = new DoublePointer(1);
                        MyFaceRecognizer.predict("../database_SRPL", m, label_SRPL, confidence_SRPL);

                        // check no result
                        if (confidence_SRE.get(0) > 100.0 && confidence_SRPL.get(0) > 100.0) {

                            System.out.println(" [x] Recieved face from WEB " +
                                    ",  label SRE: " + label_SRE.get(0) + " confidence SRE: " + confidence_SRE.get(0) +
                                    ", lable SRPL: " + label_SRPL.get(0) + " confidence SRPL: " + confidence_SRPL.get(0) +
                                    ", NO MATCH");

                            // reply no match
                            JSONObject message = new JSONObject();
                            message.put("match", false);
                            message.put("response", "Error: NO MATCH");
                            channel.basicPublish( "", properties.getReplyTo(), MessageProperties.BASIC, message.toString().getBytes("UTF-8"));

                        } else {

                            // check SRE
                            if (confidence_SRE.get(0) < confidence_SRPL.get(0)) {

                                System.out.println(" [x] Recieved face from WEB " +
                                        ",  label SRE: " + label_SRE.get(0) + " confidence SRE: " + confidence_SRE.get(0) +
                                        ", lable SRPL: " + label_SRPL.get(0) + " confidence SRPL: " + confidence_SRPL.get(0) +
                                        ", SRE MATCH");

                                // reply face found in SRE
                                JSONObject message = new JSONObject();
                                message.put("match", true);
                                message.put("faceID", label_SRE.get(0));
                                message.put("response", "Success!");
                                message.put("databaseID", "SRE");
                                // mandar alguna imagen?
                                channel.basicPublish( "", properties.getReplyTo(), MessageProperties.BASIC, message.toString().getBytes("UTF-8"));

                            } else {

                                System.out.println(" [x] Recieved face from WEB " +
                                        ",  label SRE: " + label_SRE.get(0) + " confidence SRE: " + confidence_SRE.get(0) +
                                        ", lable SRPL: " + label_SRPL.get(0) + " confidence SRPL: " + confidence_SRPL.get(0) +
                                        ", SRPL MATCH");

                                // reply face found in SRE
                                JSONObject message = new JSONObject();
                                message.put("match", true);
                                message.put("faceID", label_SRPL.get(0));
                                message.put("response", "Success!");
                                message.put("databaseID", "SRPL");
                                // mandar alguna imagen?
                                channel.basicPublish( "", properties.getReplyTo(), MessageProperties.BASIC, message.toString().getBytes("UTF-8"));

                            }
                        }
                    }

                } else if (requestType == DatabaseRequest.QUERY_FACE_MOVEMENTS) {

                    // get face id
                    int faceID = jsonMessage.getInt("faceID");

                    // get database id
                    String databaseID = "";

                    FilenameFilter image_files = new FilenameFilter() {
                        public boolean accept(File dir, String name) {
                            name = name.toLowerCase();
                            return name.endsWith(".jpg") || name.endsWith(".pgm") || name.endsWith(".png");
                        }
                    };

                    // veo si esta en SRE
                    for (File file : (new File("../database_SRE/")).listFiles(image_files)) {
                        int label = Integer.parseInt(file.getName().split("\\-")[0]);
                        if (label == faceID) {
                            databaseID = "SRE";
                            break;
                        }
                    }

                    if (databaseID == "") {
                        // veo si esta en SRPL
                        for (File file : (new File("../database_SRPL/")).listFiles(image_files)) {
                            int label = Integer.parseInt(file.getName().split("\\-")[0]);
                            if (label > faceID) {
                                databaseID = "SRPL";
                                break;
                            }
                        }
                    }

                    // --------------- VER MOVIMIENTOS
                    // en la carpeta database_faces_match hay un archivo por cada cara detectada exitosamente, y su nombre tiene la forma:
                    // 'faceID'-'coordX'_'coordY'_'frameID'_'faceNum'.png
                    // la idea es agarrar todos donde coincide el faceID con el frameID, y devolver las coordenadas y frames originales (no caras blanco y negro)

                    // agarro imagenes de database_faces_match
                    FilenameFilter imgFilter = new FilenameFilter() {
                        public boolean accept(File dir, String name) {
                            name = name.toLowerCase();
                            return (name.endsWith(".jpg") || name.endsWith(".pgm") || name.endsWith(".png")) && name.startsWith(Integer.toString(faceID));
                        }
                    };
                    File matchesRoot = new File("../database_faces_match");
                    File[] matchesFiles = matchesRoot.listFiles(imgFilter);

                    // --------------- ENVIAR MOVIMIENTOS
                    JSONObject message = new JSONObject();

                    if (matchesFiles.length <= 0) {

                        message.put("match", false);
                        message.put("response", "Error: no results found for given faceID");

                    } else {

                        message.put("match", true);
                        message.put("response", "Success!");
                        message.put("databaseID", databaseID);

                        JSONArray data = new JSONArray();

                        for (File f : matchesFiles) {

                            JSONObject matchInfo = new JSONObject();

                            String[] tokens = (f.getName().split("-", 2)[1]).split("_");

                            // coords
                            double coordX = Double.parseDouble(tokens[0]);
                            double coordY = Double.parseDouble(tokens[1]);

                            // image
                            String frameFilename = "../database_frames/" + tokens[2] + "_" + tokens[3] + "_" + tokens[4] + ".png";
                            byte[] image = FileHelper.getFileBytes(frameFilename);
                            String encodedImage = Base64.encode(image);

                            // date
                            String originalDateString = tokens[2];
                            String finalDateString;
                            SimpleDateFormat input = new SimpleDateFormat("yyyyMMddHHmmssSSS");
                            try {
                                Date dateValue = input.parse(originalDateString);
                                SimpleDateFormat output = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                                finalDateString = output.format(dateValue);
                            } catch (ParseException e) {
                                e.printStackTrace();
                                finalDateString = "00/00/0000 00:00:00";
                            }

                            // assemble json
                            matchInfo.put("coordX", coordX);
                            matchInfo.put("coordY", coordY);
                            matchInfo.put("encodedImage", encodedImage);
                            matchInfo.put("date", finalDateString);

                            data.put(matchInfo);
                        }

                        message.put("dataSize", data.length());
                        message.put("data", data);
                    }

                    channel.basicPublish( "", properties.getReplyTo(), MessageProperties.BASIC, message.toString().getBytes("UTF-8"));

                } else if (requestType == DatabaseRequest.UPDATE_FACE) {

                    // get frame id
                    String frameID = jsonMessage.getString("frameID");
                    // get frame bytes
                    String encodedImage = jsonMessage.getString("fileByte64");
                    byte[] decodedImage = Base64.decode(encodedImage);
                    // write frame to database
                    FileHelper.writeFrameBytes(decodedImage, "../database_frames/" + frameID + ".png");

                    // get face id
                    String faceID = jsonMessage.getString("faceID");
                    // get face bytes
                    String encodedFace = jsonMessage.getString("faceByte64");
                    byte[] decodedFace = Base64.decode(encodedFace);
                    BufferedImage face = FileHelper.imageBytesToBufferedImage(decodedFace);
                    opencv_core.Mat m = FileHelper.bufferedImageToJavacvMat(face);

                    // get coordiantes
                    double coordinates_x = jsonMessage.getDouble("coordinatesX");
                    double coordinates_y = jsonMessage.getDouble("coordinatesY");

                    // run SRE face recognition
                    IntPointer label_SRE = new IntPointer(1);
                    DoublePointer confidence_SRE = new DoublePointer(1);
                    MyFaceRecognizer.predict("../database_SRE", m, label_SRE, confidence_SRE);

                    // run SRPL face recognition
                    IntPointer label_SRPL = new IntPointer(1);
                    DoublePointer confidence_SRPL = new DoublePointer(1);
                    MyFaceRecognizer.predict("../database_SRPL", m, label_SRPL, confidence_SRPL);

                    // check no result
                    if (confidence_SRE.get(0) > 100.0 && confidence_SRPL.get(0) > 100.0) {

                        System.out.println(" [x] Recieved face id: " + faceID +
                                ",  label SRE: " + label_SRE.get(0) + " confidence SRE: " + confidence_SRE.get(0) +
                                ", lable SRPL: " + label_SRPL.get(0) + " confidence SRPL: " + confidence_SRPL.get(0) +
                                ", NO MATCH");

                        String faceCoordsPath = "../database_faces_no_match/" + faceID + ".png";
                        ImageIO.write(face, "png", new File(faceCoordsPath));
                    } else {

                        String[] tokens = faceID.split("_");

                        // date
                        String originalDateString = tokens[0];
                        String finalDateString;
                        SimpleDateFormat input = new SimpleDateFormat("yyyyMMddHHmmssSSS");
                        try {
                            Date dateValue = input.parse(originalDateString);
                            SimpleDateFormat output = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                            finalDateString = output.format(dateValue);
                        } catch (ParseException e) {
                            e.printStackTrace();
                            finalDateString = "00-00-0000 00:00:00.000";
                        }

                        // camID
                        int camID = Integer.valueOf(tokens[1].substring(3));

                        // cmbID
                        int cmbID = Integer.valueOf(tokens[2].substring(3));


                        // check SRE
                        if (confidence_SRE.get(0) < confidence_SRPL.get(0)) {

                            System.out.println(" [x] Recieved face id: " + faceID +
                                    ",  label SRE: " + label_SRE.get(0) + " confidence SRE: " + confidence_SRE.get(0) +
                                    ", lable SRPL: " + label_SRPL.get(0) + " confidence SRPL: " + confidence_SRPL.get(0) +
                                    ", SRE MATCH");

                            String faceCoordsPath = "../database_faces_match/" +
                                    label_SRE.get(0) + "-" + coordinates_x + "_" + coordinates_y + "_" + faceID + ".png";
                            ImageIO.write(face, "png", new File(faceCoordsPath));

                            // write to cockroach
                            CockroachConnector.addMovement(label_SRE.get(0), cmbID, camID, (float)coordinates_x, (float)coordinates_y, finalDateString, decodedFace);

                        } else {

                            System.out.println(" [x] Recieved face id: " + faceID +
                                    ",  label SRE: " + label_SRE.get(0) + " confidence SRE: " + confidence_SRE.get(0) +
                                    ", lable SRPL: " + label_SRPL.get(0) + " confidence SRPL: " + confidence_SRPL.get(0) +
                                    ", SRPL MATCH");

                            String faceCoordsPath = "../database_faces_match/" +
                                    label_SRPL.get(0) + "-" + coordinates_x + "_" + coordinates_y + "_" + faceID + ".png";
                            ImageIO.write(face, "png", new File(faceCoordsPath));

                            // write to cockroach
                            CockroachConnector.addMovement(label_SRE.get(0), cmbID, camID, (float)coordinates_x, (float)coordinates_y, finalDateString, decodedFace);
                        }
                    }

                } else {

                    System.out.println(" [E] Received request with id ERROR");
                }

                System.out.println(" [x] Done");
                channel.basicAck(envelope.getDeliveryTag(), false);
            }
        };

        boolean autoAck = false;
        channel.basicConsume(CMC_QUEUE_NAME, autoAck, consumer);

        /*boolean autoAck = false;
        boolean exclusiveConsumer = true;
        try {
            channel.basicConsume(CMC_QUEUE_NAME, autoAck, "", false, exclusiveConsumer, null, consumer);
        } catch (IOException e) {
            System.out.println(" [-] Couldn't do basicConsume, another CMC database connector is probably already running");
            System.exit(0);
        }*/
    }
}
