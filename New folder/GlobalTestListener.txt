import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.Extensions;

@Extensions({
    @ExtendWith(WireMockExtension.class)
})
public class GlobalTestListener {
}
