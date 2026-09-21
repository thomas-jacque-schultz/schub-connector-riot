package schultz.thomas.schub.connector.riot.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import schultz.thomas.schub.connector.riot.business.ingest.IngestWorker;

/**
 * La cadence de l'ouvrier, déclarée ici plutôt que par {@code @Scheduled}.
 *
 * <p>{@code fixedDelayString} n'accepte qu'un entier de millisecondes ou une durée ISO-8601 :
 * un {@code 2s} y échoue au démarrage, alors que c'est la notation de toutes les autres durées
 * du fichier de configuration. L'enregistrer programmatiquement laisse lire une {@code Duration}
 * comme partout ailleurs.</p>
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class IngestConfiguration implements SchedulingConfigurer {

    private final RiotProperties properties;
    private final IngestWorker worker;

    /** Un thread, pas deux : « un seul ouvrier » doit être une propriété du câblage. */
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
    }
}
