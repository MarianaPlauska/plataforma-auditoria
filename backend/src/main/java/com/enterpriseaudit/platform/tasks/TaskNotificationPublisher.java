package com.enterpriseaudit.platform.tasks;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
public class TaskNotificationPublisher {
    private final JdbcTemplate jdbc;
    private final RestClient client;
    private final String webhookUrl;
    private final String webhookSecret;
    private final String workspaceUrl;

    public TaskNotificationPublisher(JdbcTemplate jdbc, RestClient.Builder builder,
                                     @Value("${app.tasks.webhook-url:}") String webhookUrl,
                                     @Value("${app.tasks.webhook-secret:}") String webhookSecret,
                                     @Value("${app.tasks.workspace-url:http://localhost:3000}") String workspaceUrl)
    {
        this.jdbc = jdbc;
        this.client = builder.build();
        this.webhookUrl = webhookUrl;
        this.webhookSecret = webhookSecret;
        this.workspaceUrl = workspaceUrl;
    }

    @Scheduled(fixedDelayString = "${app.tasks.webhook-poll-ms:5000}")
    @Transactional
    public void publishPending()
    {
        if (!StringUtils.hasText(webhookUrl))
        {
            return;
        }

        jdbc.queryForObject("select set_config('app.current_tenant', 'system', true)", String.class);
        jdbc.update("""
                INSERT INTO task_notification_outbox(id, tenant_id, task_id, event_type, reminder_for_date)
                SELECT gen_random_uuid(), tenant_id, id, 'TASK_REMINDER_DUE', current_date
                FROM remediation_tasks
                WHERE state IN ('OPEN', 'IN_PROGRESS')
                  AND due_date <= current_date + 3
                ON CONFLICT (tenant_id, task_id, event_type, reminder_for_date)
                    WHERE reminder_for_date IS NOT NULL DO NOTHING
                """);
        List<NotificationRow> rows = jdbc.query("""
                SELECT n.id, n.tenant_id, n.task_id, t.due_date, n.event_type
                FROM task_notification_outbox n
                JOIN remediation_tasks t ON t.id = n.task_id AND t.tenant_id = n.tenant_id
                WHERE n.sent_at IS NULL AND n.next_attempt_at <= now()
                ORDER BY n.created_at
                LIMIT 20
                FOR UPDATE OF n SKIP LOCKED
                """, (rs, row) -> new NotificationRow(rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class), rs.getObject("task_id", UUID.class),
                rs.getObject("due_date", LocalDate.class), rs.getString("event_type")));

        for (NotificationRow row : rows)
        {
            try
            {
                var request = client.post().uri(webhookUrl).body(new NotificationBody(
                        row.eventType(), row.taskId(), row.dueDate(), workspaceUrl));
                if (StringUtils.hasText(webhookSecret))
                {
                    request.header("X-Audit-Webhook-Secret", webhookSecret);
                }
                request.retrieve().toBodilessEntity();
                jdbc.update("UPDATE task_notification_outbox SET sent_at = now() WHERE id = ?", row.id());
            }
            catch (Exception failure)
            {
                jdbc.update("""
                        UPDATE task_notification_outbox
                        SET attempts = attempts + 1,
                            next_attempt_at = now() + make_interval(secs => LEAST(3600, power(2, LEAST(attempts, 10))::integer))
                        WHERE id = ?
                        """, row.id());
            }
        }
    }

    private record NotificationRow(UUID id, UUID tenantId, UUID taskId, LocalDate dueDate, String eventType) {}

    public record NotificationBody(String event, UUID taskId, LocalDate dueDate, String workspaceUrl) {}
}
