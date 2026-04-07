import java.time.LocalDateTime;
import java.time.ZoneId;

public class TestDate2 {
    public static void main(String[] args) {
        LocalDateTime ldt = LocalDateTime.now();
        System.out.println(ldt);
        LocalDateTime ldtBogota = ldt.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of("America/Bogota")).toLocalDateTime();
        System.out.println(ldtBogota);
    }
}
