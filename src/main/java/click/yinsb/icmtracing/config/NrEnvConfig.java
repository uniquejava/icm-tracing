package click.yinsb.icmtracing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NrEnvConfig {

    @Bean
    ApplicationRunner validateNrEnv(
            @Value("${NR_ENDPOINT:}") String nrEndpoint,
            @Value("${MY_NEW_RELIC_API_KEY:}") String newRelicApiKey) {
        return args -> {
            boolean failed = false;
            if (nrEndpoint.isBlank()) failed = true;
            if (newRelicApiKey.isBlank()) failed = true;
            if (failed) {
                System.err.println();
                System.err.println();
                System.err.println("  ╔══════════════════════════════════════════════════════════════════════════════╗");
                System.err.println("  ║              MISSING REQUIRED ENVIRONMENT VARIABLES                        ║");
                System.err.println("  ╠══════════════════════════════════════════════════════════════════════════════╣");
                if (nrEndpoint.isBlank())
                System.err.println("  ║  NR_ENDPOINT          is not set                                           ║");
                if (newRelicApiKey.isBlank())
                System.err.println("  ║  MY_NEW_RELIC_API_KEY is not set                                           ║");
                System.err.println("  ╠══════════════════════════════════════════════════════════════════════════════╣");
                System.err.println("  ║  export NR_ENDPOINT=https://otlp.nr-data.net:4317                          ║");
                System.err.println("  ║  export MY_NEW_RELIC_API_KEY=<your-key>                                    ║");
                System.err.println("  ╚══════════════════════════════════════════════════════════════════════════════╝");
                System.err.println();
                System.err.println();
                System.exit(1);
            }
        };
    }
}
