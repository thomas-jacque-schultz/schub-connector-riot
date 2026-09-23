package schultz.thomas.schub.connector.riot.config;

import lombok.RequiredArgsConstructor;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import schultz.thomas.schub.connector.riot.business.ingest.BackgroundCrawler;
import schultz.thomas.schub.connector.riot.business.ingest.IngestWorker;
import schultz.thomas.schub.connector.riot.business.stats.MetricScaleService;

// Pas de @Scheduled(fixedDelayString) : il n'accepte que des ms ou de l'ISO-8601, pas « 2s ».
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class IngestConfiguration implements SchedulingConfigurer {

    private final RiotProperties properties;
    private final IngestWorker worker;
    private final MetricScaleService metricScale;
    private final BackgroundCrawler crawler;

    @Bean
    public TaskScheduler ingestScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("riot-ingest-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setTaskScheduler(ingestScheduler());
        registrar.addFixedDelayTask(worker::drain, properties.getIngest().getPollInterval());
        registrar.addFixedDelayTask(metricScale::refresh, Duration.ofHours(1));
        registrar.addFixedDelayTask(crawler::round, properties.getCrawler().getInterval());
    }
}
