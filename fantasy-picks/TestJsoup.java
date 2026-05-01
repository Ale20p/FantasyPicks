import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class TestJsoup {
    public static void main(String[] args) throws Exception {
        String html = "<table><tbody><tr class=\"player-row\" x-data=\"playerRow({ player: {&quot;id&quot;:4488,&quot;player_id&quot;:5871,&quot;rank&quot;:1,&quot;projected_points&quot;:364.21,&quot;name&quot;:&quot;Puka Nacua&quot;} })\"></tr></tbody></table>";
        Document doc = Jsoup.parse(html);
        Element row = doc.selectFirst("tr");
        String name = "";
        String xData = row.attr("x-data");
        System.out.println("xData: " + xData);
        if (xData != null && xData.contains("\"name\":\"")) {
            int start = xData.indexOf("\"name\":\"") + 8;
            int end = xData.indexOf("\"", start);
            if (start > 7 && end > start) {
                name = xData.substring(start, end);
            }
        }
        System.out.println("Extracted name: " + name);
    }
}
