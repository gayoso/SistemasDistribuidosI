import java.sql.*;

public class CockroachConnector {

    private static String host = "localhost";
    private static String port = "26257";
    private static String dbName = "movements";

    private static String username = "gabriel";
    private static String password = "";

    public static void main(String[] args) throws ClassNotFoundException, SQLException {

        System.out.println(addFace(1, "macri", "SRPL"));
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
                System.out.println("Timestamp: " + datetime);
                ps.setTimestamp(6, Timestamp.valueOf(datetime));
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
}
