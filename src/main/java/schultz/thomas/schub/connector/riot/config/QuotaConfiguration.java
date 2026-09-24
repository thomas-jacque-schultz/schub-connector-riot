package schultz.thomas.schub.connector.riot.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import schultz.thomas.schub.connector.riot.business.quota.MethodRateLimiter;
import schultz.thomas.schub.connector.riot.business.quota.RiotRateLimiter;
import schultz.thomas.schub.connector.riot.business.quota.Sleeper;

import java.time.Clock;

@RequiredArgsConstructor
@Configuration
public class QuotaConfiguration {

    private final RiotProperties properties;

    @Bean
    public Clock riotClock() {
        return Clock.systemUTC();
    }

    @Bean
    public Sleeper riotSleeper() {
        return duration -> Thread.sleep(duration.toMillis());
    }

    @Bean
    public MethodRateLimiter methodRateLimiter(Clock riotClock, Sleeper riotSleeper) {
        return new MethodRateLimiter(properties.getQuota().getMethods(), properties.getQuota().getWindowGuard(),
                riotClock, riotSleeper);
    }

    @Bean
    public RiotRateLimiter riotRateLimiter(Clock riotClock, Sleeper riotSleeper) {
        return new RiotRateLimiter(properties.getQuota(), riotClock, riotSleeper);
    }
}
