import java.io.IOException;
import java.sql.*;
import java.util.LinkedList;
import java.util.List;

public class CockroachConnector {

    private static String host = "localhost";
    private static String port = "26257";
    private static String dbName = "movements";

    private static String username = "gabriel";
    private static String password = "";

    public static void main(String[] args) throws ClassNotFoundException, SQLException, IOException {

        LinkedList<MovementsStruct> movs = new LinkedList<>();
        getMovements(7, movs);

        for (int i = 0; i < movs.size(); ++i) {

            MovementsStruct ms = movs.get(i);

            System.out.println("id: " + ms.id + ", cmb id: " + ms.cmbID + ", cam id: " + ms.camID + ", coord x: " + ms.coordX + ", coord y: " + ms.coordX + ", date: " + ms.date);

            FileHelper.writeFrameBytes(ms.image, "../test_output/" + ms.id + "-" + i + ".png");
        }
    }

    public static void init(String host, String port, String dbName, String username, String password) {
        CockroachConnector.init(host, port);
        CockroachConnector.dbName = dbName;
        CockroachConnector.username = username;
        CockroachConnector.password = password;
    }

    public static void init(String host, String port) {
        CockroachConnector.host = host;
        CockroachConnector.port = port;
    }

    public static boolean addFace(int id, String name, String database) {

        boolean retVal = false;

        try {

            // Load the postgres JDBC driver
            Class.forName("org.postgresql.Driver");

            // Connect to the "movements" database
            Connection db = DriverManager.getConnection("jdbc:postgresql://" + host + ":" + port + "/" + dbName + "?sslmode=disable", username, password);

            try {

                // Insert row into the "faces" table
                db.createStatement().execute("INSERT INTO faces (id, name, database) VALUES (" + Integer.toString(id) + ", '" + name + "', '" + database + "')");
                retVal = true;

            } catch (Exception e) {

                e.printStackTrace();
                retVal = false;

            } finally {

                // Close the database connection
                db.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
            retVal = false;
        }

        return retVal;
    }

    public static boolean addMovement(int id, int cmbid, int camid, float coordx, float coordy, String datetime, byte[] image) {

        boolean retVal = false;

        try {

            // Load the postgres JDBC driver
            Class.forName("org.postgresql.Driver");

            // Connect to the "movements" database
            Connection db = DriverManager.getConnection("jdbc:postgresql://" + host + ":" + port + "/" + dbName + "?sslmode=disable", username, password);

            try {

                // Insert row into the "movements" table
                PreparedStatement ps = db.prepareStatement("INSERT INTO movements VALUES (?, ?, ?, ?, ?, ?, ?)");
                ps.setInt(1, id);
                ps.setInt(2, cmbid);
                ps.setInt(3, camid);
                ps.setFloat(4, coordx);
                ps.setFloat(5, coordy);
                //System.out.println("Timestamp: " + datetime);
                ps.setString(6, datetime);
                ps.setBytes(7, image);
                ps.execute();
                ps.close();

                retVal = true;

            } catch (Exception e) {

                e.printStackTrace();
                retVal = false;

            } finally {

                // Close the database connection
                db.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
            retVal = false;
        }

        return retVal;
    }

    public static boolean getMovements(int id, List<MovementsStruct> movements) {

        boolean retVal = false;

        try {

            // Load the postgres JDBC driver
            Class.forName("org.postgresql.Driver");

            // Connect to the "movements" database
            Connection db = DriverManager.getConnection("jdbc:postgresql://" + host + ":" + port + "/" + dbName + "?sslmode=disable", username, password);

            try {

                // Get movements of face 'id'
                Statement statement = db.createStatement();
                ResultSet rs = statement.executeQuery("SELECT * FROM movements WHERE face_id = " + id);

                while (rs.next()) {

                    int rsID = rs.getInt(1);
                    int rsCmbID = rs.getInt(2);
                    int rsCamID = rs.getInt(3);
                    float rsCoordX = rs.getFloat(4);
                    float rsCoordY = rs.getFloat(5);
                    String rsDate = rs.getString(6);
                    byte[] rsImage = rs.getBytes(7);

                    //System.out.println("id: " + rsID + ", cmb id: " + rsCmbID + ", cam id: " + rsCamID + ", coord x: " + rsCoordX + ", coord y: " + rsCoordY + ", date: " + rsDate);

                    MovementsStruct mov = new MovementsStruct();
                    mov.id = rsID;
                    mov.cmbID = rsCmbID;
                    mov.camID = rsCamID;
                    mov.coordX = rsCoordX;
                    mov.coordY = rsCoordY;
                    mov.date = rsDate;
                    mov.image  = rsImage;

                    movements.add(mov);
                }

                rs.close();
                statement.close();

                retVal = true;

            } catch (Exception e) {

                e.printStackTrace();
                retVal = false;

            } finally {

                // Close the database connection
                db.close();
            }

        }catch (Exception e) {
            e.printStackTrace();
            retVal = false;
        }

        return retVal;

    }
}
