import com.fasterxml.jackson.databind.ObjectMapper;
import com.lang.portal.upstream.newapi.probe.AggregationSensitiveScanner;

class ScanEvidence {
  public static void main(String[] args) throws Exception {
    try {
      new AggregationSensitiveScanner().scanOrThrow(new ObjectMapper().readTree(System.in));
    } catch (RuntimeException e) {
      System.exit(1); // 不输出路径、snippet 或原始负载
    }
  }
}
