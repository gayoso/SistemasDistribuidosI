import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.nio.IntBuffer;

import static org.bytedeco.javacpp.opencv_core.CV_32SC1;
import static org.bytedeco.javacpp.opencv_face.createEigenFaceRecognizer;
import static org.bytedeco.javacpp.opencv_face.createFisherFaceRecognizer;
import static org.bytedeco.javacpp.opencv_face.createLBPHFaceRecognizer;
import static org.bytedeco.javacpp.opencv_imgcodecs.imread;
import static org.bytedeco.javacpp.opencv_imgcodecs.CV_LOAD_IMAGE_GRAYSCALE;

import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.opencv_face.FaceRecognizer;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.MatVector;

/**
 *
 * Source: http://pcbje.com/2012/12/doing-face-recognition-with-javacv/
 *
 * @originalauthor Petter Christian Bjelland
 */

public class MyFaceRecognizer {

    public static void addTrainingImage(String databasePath, String path, int label) {
        addTrainingImage(databasePath, imread(new File(path).getAbsolutePath(), CV_LOAD_IMAGE_GRAYSCALE), label);
    }

    public static void addTrainingImage(String databasePath, BufferedImage img, int label) {
        addTrainingImage(databasePath, FileHelper.bufferedImageToJavacvMat(img), label);
    }

    public static void addTrainingImage(String databasePath, Mat img, int label) {

        //FaceRecognizer faceRecognizer = createFisherFaceRecognizer();
        //FaceRecognizer faceRecognizer = createEigenFaceRecognizer();
        FaceRecognizer faceRecognizer = createLBPHFaceRecognizer();

        MatVector images = new MatVector(1);
        Mat labels = new Mat(1, 1, CV_32SC1);
        IntBuffer labelsBuf = labels.createBuffer();

        images.put(0, img);
        labelsBuf.put(0, label);

        try {
            faceRecognizer.load(databasePath + "/lbph_database");
            faceRecognizer.update(images, labels);

        } catch (Exception e) {
            // database doesn't exist yet
            //faceRecognizer.save(databasePath + "/lbph_database");
            //createFromDir(databasePath);
            faceRecognizer.train(images, labels);

        } finally {
            faceRecognizer.save(databasePath + "/lbph_database");
        }
    }

    public static void createFromDir(String databasePath) {

        FilenameFilter imgFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                name = name.toLowerCase();
                return name.endsWith(".jpg") || name.endsWith(".pgm") || name.endsWith(".png");
            }
        };

        // ------- TRAINING -------

        File trainingDirRoot = new File(databasePath);
        File[] trainingFiles = trainingDirRoot.listFiles(imgFilter);
        for (File image : trainingFiles) {
            //System.out.println(image.getAbsolutePath());
            //System.out.println(image.getName());
            int label = Integer.parseInt(image.getName().split("\\-")[0]);
            addTrainingImage(databasePath, image.getAbsolutePath(), label);
        }
    }

    public static void predict(String databasePath, Mat testImage, IntPointer label, DoublePointer confidence) {

        //FaceRecognizer faceRecognizer = createFisherFaceRecognizer();
        //FaceRecognizer faceRecognizer = createEigenFaceRecognizer();
        FaceRecognizer faceRecognizer = createLBPHFaceRecognizer();

        try {
            faceRecognizer.load(databasePath + "/lbph_database");
        } catch (Exception e) {
            //createFromDir(databasePath);
            //faceRecognizer.load(databasePath + "/lbph_database");

            label.put(-1);
            confidence.put(999);
            return;
        }

        faceRecognizer.predict(testImage, label, confidence);
        //System.out.println("Predicted label: " + label.get(0) + ", with confidence: " + confidence.get(0));
    }

    public static void predict(String databasePath, String path, IntPointer label, DoublePointer confidence) {

        Mat testImage = imread(path, CV_LOAD_IMAGE_GRAYSCALE);

        predict(databasePath, testImage, label, confidence);
    }

    public static void main(String[] args) {

        FilenameFilter imgFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                name = name.toLowerCase();
                return name.endsWith(".jpg") || name.endsWith(".pgm") || name.endsWith(".png");
            }
        };

        // ------- TRAINING -------

        String trainingDir = "../opencv_database"; // old
        File trainingDirRoot = new File(trainingDir);
        File[] trainingFiles = trainingDirRoot.listFiles(imgFilter);

        for (File image : trainingFiles) {

            int label = Integer.parseInt(image.getName().split("\\-")[0]);
            addTrainingImage(trainingDir, image.getAbsolutePath(), label);
        }

        // ------- PREDICTING -------

        String predictionsDir = "../opencv_tests"; // old
        File predictionsDirRoot = new File(predictionsDir);
        File[] predictionFiles = predictionsDirRoot.listFiles(imgFilter);

        for (File image : predictionFiles) {

            IntPointer label = new IntPointer(1);
            DoublePointer confidence = new DoublePointer(1);
            predict(trainingDir, image.getAbsolutePath(), label, confidence);
        }
    }
}
