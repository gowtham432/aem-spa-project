package com.mycompany.core.schedulers;

import com.mycompany.core.services.IndexerService;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = Runnable.class, immediate = true)
@Designate(ocd = ContentIndexerScheduler.Config.class)
public class ContentIndexerScheduler implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ContentIndexerScheduler.class);

    @ObjectClassDefinition(
            name = "Content Indexer Scheduler",
            description = "Scheduled job to index AEM content into Pinecone"
    )
    public @interface Config {

        @AttributeDefinition(
                name = "Scheduler Expression",
                description = "Cron expression for scheduling (e.g., '0 0 2 * * ?' for daily at 2 AM)"
        )
        String scheduler_expression() default "0 0 2 * * ?";

        @AttributeDefinition(
                name = "Root Paths to Index",
                description = "Comma-separated list of root paths to index (e.g., /content/mysite/advice,/content/mysite/insurance)"
        )
        String[] root_paths() default {"/content/aisearchspa"};

        @AttributeDefinition(
                name = "Enabled",
                description = "Enable or disable the scheduler"
        )
        boolean enabled() default true;
    }

    @Reference
    private IndexerService indexerService;

    @Reference
    private Scheduler scheduler;

    private String schedulerExpression;
    private String[] rootPaths;
    private boolean enabled;
    private String jobName;

    @Activate
    protected void activate(Config config) {
        this.schedulerExpression = config.scheduler_expression();
        this.rootPaths = config.root_paths();
        this.enabled = config.enabled();
        this.jobName = this.getClass().getSimpleName();

        if (enabled) {
            scheduleJob();
            LOG.info("Content Indexer Scheduler activated. Schedule: {}, Paths: {}",
                    schedulerExpression, String.join(", ", rootPaths));
        } else {
            LOG.info("Content Indexer Scheduler is disabled");
        }
    }

    @Deactivate
    protected void deactivate() {
        unscheduleJob();
        LOG.info("Content Indexer Scheduler deactivated");
    }

    @Override
    public void run() {
        if (!enabled) {
            LOG.debug("Scheduler is disabled, skipping indexing");
            return;
        }

        LOG.info("Starting scheduled content indexing...");

        try {
            int totalIndexed = 0;

            for (String rootPath : rootPaths) {
                LOG.info("Indexing content under: {}", rootPath);
                int count = indexerService.indexContent(rootPath);
                totalIndexed += count;
                LOG.info("Indexed {} pages from {}", count, rootPath);
            }

            LOG.info("Scheduled indexing completed. Total pages indexed: {}", totalIndexed);

        } catch (Exception e) {
            LOG.error("Error during scheduled indexing: {}", e.getMessage(), e);
        }
    }

    /**
     * Schedule the job
     */
    private void scheduleJob() {
        try {
            ScheduleOptions options = scheduler.EXPR(schedulerExpression);
            options.name(jobName);
            options.canRunConcurrently(false);
            scheduler.schedule(this, options);
            LOG.info("Scheduled job '{}' with expression: {}", jobName, schedulerExpression);
        } catch (Exception e) {
            LOG.error("Failed to schedule job: {}", e.getMessage(), e);
        }
    }

    /**
     * Unschedule the job
     */
    private void unscheduleJob() {
        try {
            scheduler.unschedule(jobName);
            LOG.info("Unscheduled job: {}", jobName);
        } catch (Exception e) {
            LOG.error("Failed to unschedule job: {}", e.getMessage(), e);
        }
    }
}
