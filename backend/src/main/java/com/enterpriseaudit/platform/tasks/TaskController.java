package com.enterpriseaudit.platform.tasks;

import com.enterpriseaudit.platform.security.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final JdbcTemplate jdbc;

    public TaskController(JdbcTemplate jdbc)
    {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<TaskItem> list()
    {
        UUID tenantId = TenantContext.requireTenantId();
        return jdbc.query("""
                SELECT id, finding_id, title, assignee_email, due_date, state,
                       external_system, external_id, created_at, updated_at,
                       category, origin_reference, source_event
                FROM remediation_tasks
                WHERE tenant_id = ?
                ORDER BY CASE WHEN state IN ('DONE', 'CANCELLED') THEN 1 ELSE 0 END,
                         due_date NULLS LAST, created_at DESC
                LIMIT 200
                """, TaskController::mapTask, tenantId);
    }

    @GetMapping("/inbox")
    public Inbox inbox()
    {
        UUID tenantId = TenantContext.requireTenantId();
        Counts counts = jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE state IN ('OPEN', 'IN_PROGRESS')) AS open_count,
                       count(*) FILTER (WHERE state IN ('OPEN', 'IN_PROGRESS') AND due_date < current_date) AS overdue_count,
                       count(*) FILTER (WHERE state IN ('OPEN', 'IN_PROGRESS') AND due_date = current_date) AS due_today_count,
                       count(*) FILTER (WHERE state IN ('OPEN', 'IN_PROGRESS') AND due_date > current_date AND due_date <= current_date + 7) AS due_soon_count
                FROM remediation_tasks WHERE tenant_id = ?
                """, (rs, row) -> new Counts(rs.getInt("open_count"), rs.getInt("overdue_count"),
                rs.getInt("due_today_count"), rs.getInt("due_soon_count")), tenantId);
        List<TaskItem> items = jdbc.query("""
                SELECT id, finding_id, title, assignee_email, due_date, state,
                       external_system, external_id, created_at, updated_at,
                       category, origin_reference, source_event
                FROM remediation_tasks
                WHERE tenant_id = ? AND state IN ('OPEN', 'IN_PROGRESS')
                ORDER BY CASE WHEN due_date < current_date THEN 0
                              WHEN due_date = current_date THEN 1
                              WHEN due_date <= current_date + 7 THEN 2 ELSE 3 END,
                         due_date NULLS LAST, created_at DESC
                LIMIT 100
                """, TaskController::mapTask, tenantId);
        return new Inbox(counts.open(), counts.overdue(), counts.dueToday(), counts.dueSoon(), items);
    }

    @PostMapping
    @Transactional
    @PreAuthorize("hasAnyRole('AUDIT_ADMIN', 'AUDIT_ANALYST')")
    public TaskItem create(@Valid @RequestBody TaskInput input)
    {
        String assignee = clean(input.assigneeEmail());
        if (input.category() == TaskCategory.PGR_ACTION && (input.dueDate() == null || assignee == null))
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ação do PGR precisa de responsável e prazo.");
        }
        UUID tenantId = TenantContext.requireTenantId();
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO remediation_tasks(id, tenant_id, finding_id, title, assignee_email, due_date, category)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, input.findingId(), input.title().trim(),
                assignee, input.dueDate(), input.category() == null ? "GENERAL" : input.category().name());
        jdbc.update("""
                INSERT INTO task_notification_outbox(id, tenant_id, task_id)
                VALUES (?, ?, ?)
                """, UUID.randomUUID(), tenantId, id);
        jdbc.update("""
                INSERT INTO audit_logs(id, tenant_id, action, resource_id)
                VALUES (?, ?, 'TASK_CREATED', ?)
                """, UUID.randomUUID(), tenantId, id);
        return task(id, tenantId);
    }

    @PatchMapping("/{id}/state")
    @Transactional
    @PreAuthorize("hasAnyRole('AUDIT_ADMIN', 'AUDIT_ANALYST')")
    public TaskItem updateState(@PathVariable UUID id, @Valid @RequestBody StateInput input)
    {
        UUID tenantId = TenantContext.requireTenantId();
        String previous = jdbc.query("SELECT state FROM remediation_tasks WHERE id = ? AND tenant_id = ?",
                rs -> rs.next() ? rs.getString(1) : null, id, tenantId);
        if (previous == null)
        {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarefa não encontrada");
        }
        int updated = jdbc.update("""
                UPDATE remediation_tasks
                SET state = ?, updated_at = now()
                WHERE id = ? AND tenant_id = ?
                """, input.state().name(), id, tenantId);
        if (updated == 0)
        {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarefa não encontrada");
        }
        jdbc.update("""
                INSERT INTO audit_logs(id, tenant_id, action, resource_id, details)
                VALUES (?, ?, 'TASK_STATE_CHANGED', ?, jsonb_build_object('from', ?, 'to', ?))
                """, UUID.randomUUID(), tenantId, id, previous, input.state().name());
        return task(id, tenantId);
    }

    private TaskItem task(UUID id, UUID tenantId)
    {
        return jdbc.queryForObject("""
                SELECT id, finding_id, title, assignee_email, due_date, state,
                       external_system, external_id, created_at, updated_at,
                       category, origin_reference, source_event
                FROM remediation_tasks
                WHERE id = ? AND tenant_id = ?
                """, TaskController::mapTask, id, tenantId);
    }

    private static TaskItem mapTask(ResultSet rs, int row) throws SQLException
    {
        return new TaskItem(rs.getObject("id", UUID.class), rs.getObject("finding_id", UUID.class),
                rs.getString("title"), rs.getString("assignee_email"),
                rs.getObject("due_date", LocalDate.class), rs.getString("state"),
                rs.getString("external_system"), rs.getString("external_id"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
                rs.getString("category"), rs.getString("origin_reference"), rs.getString("source_event"));
    }

    private static String clean(String input)
    {
        return input == null || input.isBlank() ? null : input.trim();
    }

    public record TaskItem(UUID id, UUID findingId, String title, String assigneeEmail,
                           LocalDate dueDate, String state, String externalSystem, String externalId,
                           Instant createdAt, Instant updatedAt, String category,
                           String originReference, String sourceEvent) {}

    public record TaskInput(UUID findingId, @NotBlank @Size(max = 180) String title,
                            @Email @Size(max = 254) String assigneeEmail, LocalDate dueDate,
                            TaskCategory category) {}

    public record Counts(int open, int overdue, int dueToday, int dueSoon) {}

    public record Inbox(int open, int overdue, int dueToday, int dueSoon, List<TaskItem> items) {}

    public enum TaskCategory
    {
        GENERAL,
        PGR_ACTION,
        HR_CHANGE
    }

    public record StateInput(@NotNull TaskState state) {}

    public enum TaskState
    {
        OPEN,
        IN_PROGRESS,
        DONE,
        CANCELLED
    }
}
