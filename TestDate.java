import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class TestDate {
    public static void main(String[] args) {
        LocalDateTime ldt = LocalDateTime.now(ZoneId.of("UTC")); // simulate DB
        ZonedDateTime zdt = ldt.atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.of("America/Bogota"));
        System.out.println(zdt.toLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss")));
    }
}
