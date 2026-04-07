import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import java.time.LocalDateTime;

public class TestSpEL {
    public static void main(String[] args) {
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("p", new DummyApp());
        String expr = "#p.createdAt().atZone(T(java.time.ZoneId).systemDefault()).withZoneSameInstant(T(java.time.ZoneId).of('America/Bogota'))";
        Object val = parser.parseExpression(expr).getValue(context);
        System.out.println(val);
    }

    public static class DummyApp {
        public LocalDateTime createdAt() {
            return LocalDateTime.now();
        }
    }
}
