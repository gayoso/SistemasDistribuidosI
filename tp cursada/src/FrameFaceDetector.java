import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;

//import javax.imageio.ImageIO;
import org.opencv.core.Core;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FrameFaceDetector {

    public static void main(String[] args) throws IOException {

        File dir = new File("../camera_frames_test/");
        File[] files = dir.listFiles();

        // send some frames
        for (int i = 0; i < files.length; ++i) {

            File chosenFile = files[i];
            //System.out.println(chosenFile.getName());
            List<BufferedImage> faces = detectFaces(FileHelper.imageBytesToBufferedImage(FileHelper.getFileBytes(chosenFile)));

            for (int j = 0; j < faces.size(); ++j) {

                ImageIO.write(faces.get(j), "png", new File("../database_faces/" + i + "_" + j + ".png")); // old
            }
        }
    }

    public static List<BufferedImage> detectFaces(BufferedImage image) throws IOException {

        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);


        // Here we convert into *supported* format
        BufferedImage imageCopy =
                new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        imageCopy.getGraphics().drawImage(image, 0, 0, null);

        byte[] data = ((DataBufferByte) imageCopy.getRaster().getDataBuffer()).getData();
        Mat frame = new Mat(image.getHeight(),image.getWidth(), CvType.CV_8UC3);
        frame.put(0, 0, data);
        //Imgcodecs.imwrite("C:\\File\\input.jpg", img);

        // ----------

        CascadeClassifier faceCascade = new CascadeClassifier();
        faceCascade.load("./src/opencv/resources/haarcascades/haarcascade_frontalface_alt.xml");
        //faceCascade.load("./src/opencv/resources/haarcascades/haarcascade_frontalface_default.xml");
        int absoluteFaceSize = 0;

        MatOfRect faces = new MatOfRect();
        Mat grayFrame = new Mat();

        // convert the frame in gray scale
        Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY);
        // equalize the frame histogram to improve the result
        Imgproc.equalizeHist(grayFrame, grayFrame);

        // compute minimum face size (20% of the frame height, in our case)
        if (absoluteFaceSize == 0)
        {
            int height = grayFrame.rows();
            if (Math.round(height * 0.2f) > 0)
            {
                absoluteFaceSize = Math.round(height * 0.2f);
            }
        }

        // detect faces
        faceCascade.detectMultiScale(grayFrame, faces, 1.1, 2, 0 | Objdetect.CASCADE_SCALE_IMAGE,
                new Size(absoluteFaceSize, absoluteFaceSize), new Size());

        // each rectangle in faces is a face: draw them!
        /*Rect[] facesArray = faces.toArray();
        for (int i = 0; i < facesArray.length; i++)
            Imgproc.rectangle(frame, facesArray[i].tl(), facesArray[i].br(), new Scalar(0, 255, 0), 3);

        Imgcodecs.imwrite("../output.jpg", frame);

        System.out.println(String.format("Detected %s faces", faces.toArray().length));*/

        //int i = 0;
        List<BufferedImage> croppedFaces = new ArrayList<>();
        for (Rect rect : faces.toArray()) {
            Rect rect_Crop = new Rect(rect.x, rect.y, rect.width, rect.height);

            Mat image_roi = new Mat(frame,rect_Crop);
            Size sz = new Size(100,100);
            Imgproc.resize( image_roi, image_roi, sz );
            Imgproc.cvtColor( image_roi, image_roi, Imgproc.COLOR_RGB2GRAY);
            croppedFaces.add(FileHelper.opencvMatToBufferedImage(image_roi));
        }

        return croppedFaces;
    }
}
