import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.*;

public class FileHelper {

    public static byte[] getFileBytes(String chosenFile) throws IOException {
        return getFileBytes(new File(chosenFile));
    }

    public static byte[] getFileBytes(File chosenFile) throws IOException {

        // read file
        FileInputStream fileInputStream = new FileInputStream(chosenFile);
        byte[] bytes = new byte[(int) chosenFile.length()];
        fileInputStream.read(bytes);
        fileInputStream.close();

        return bytes;
    }

    public static byte[] getFileBytes(BufferedImage image) throws IOException {
        return ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
    }

    public static void writeFrameBytes(byte[] bytes, String path) throws IOException {
        File myFile = new File(path);
        myFile.createNewFile();
        FileOutputStream fos = new FileOutputStream(myFile, false);

        fos.write(bytes);
        fos.close();
    }

    public static BufferedImage imageBytesToBufferedImage(byte[] imageBytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(imageBytes));
    }

    public static byte[] bufferedImageToBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    public static BufferedImage opencvMatToBufferedImage(Mat frame) {
        int type = 0;
        if (frame.channels() == 1) {
            type = BufferedImage.TYPE_BYTE_GRAY;
        } else if (frame.channels() == 3) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        BufferedImage image = new BufferedImage(frame.width() ,frame.height(), type);
        WritableRaster raster = image.getRaster();
        DataBufferByte dataBuffer = (DataBufferByte) raster.getDataBuffer();
        byte[] data = dataBuffer.getData();
        frame.get(0, 0, data);
        return image;
    }

    public static opencv_core.Mat bufferedImageToJavacvMat(BufferedImage bi) {
        OpenCVFrameConverter.ToMat cv = new OpenCVFrameConverter.ToMat();
        return cv.convertToMat(new Java2DFrameConverter().convert(bi));
    }

    /*public static byte[] opencvMatToByteArray(Mat m) {
        byte[] b = new byte[(int) (m.total() * m.elemSize())];
        m.get(0, 0, b);
        return b;
    }

    public static Mat byteArrayToOpencvMat(byte[] b) {
        Mat m = new Mat(100, 100, CvType.CV_8UC3);
        m.put(0, 0, b);
        return m;
    }*/
}
