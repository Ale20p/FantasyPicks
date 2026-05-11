public class TestURI {
    public static void main(String[] args) {
        try {
            java.net.URI uri = java.net.URI.create("https://api.sleeper.com/projections/nfl/2024?season_type=regular&position[]=DEF");
            System.out.println("URI created successfully: " + uri);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
