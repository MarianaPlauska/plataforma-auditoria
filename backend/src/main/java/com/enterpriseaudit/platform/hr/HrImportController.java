package com.enterpriseaudit.platform.hr;

import com.enterpriseaudit.platform.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hr")
public class HrImportController {
    private final JdbcTemplate jdbc;
    private final List<HrConnector> connectors;

    public HrImportController(JdbcTemplate jdbc, List<HrConnector> connectors)
    {
        this.jdbc = jdbc;
        this.connectors = connectors;
    }

    @PostMapping("/import")
    @Transactional
    @PreAuthorize("hasAnyRole('AUDIT_ADMIN', 'HR_INTEGRATION')")
    public ImportResult importPeople(@RequestParam("file") MultipartFile file) throws IOException
    {
        HrConnector connector = connectors.stream()
                .filter(item -> item.supports(file.getOriginalFilename()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato de integração RH não suportado."));
        HrConnector.ImportBatch batch = connector.read(file);
        List<HrCsvParser.Person> people = batch.people();
        if (people.isEmpty())
        {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "O CSV não contém registros de vínculo.");
        }

        UUID tenantId = TenantContext.requireTenantId();
        UUID importId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO hr_imports(id, tenant_id, source_name, imported_rows)
                VALUES (?, ?, ?, ?)
                """, importId, tenantId, batch.sourceName(), people.size());

        for (HrCsvParser.Person person : people)
        {
            PersonState previous = jdbc.query("""
                    SELECT unit_code, employment_status, effective_date
                    FROM hr_people
                    WHERE tenant_id = ? AND external_ref = ?
                    """, rs -> rs.next() ? new PersonState(rs.getString("unit_code"), rs.getString("employment_status"),
                    rs.getObject("effective_date", LocalDate.class)) : null,
                    tenantId, person.externalRef());
            jdbc.update("""
                    INSERT INTO hr_people(tenant_id, external_ref, unit_code, employment_status, effective_date, import_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (tenant_id, external_ref)
                    DO UPDATE SET unit_code = EXCLUDED.unit_code,
                                  employment_status = EXCLUDED.employment_status,
                                  effective_date = EXCLUDED.effective_date,
                                  import_id = EXCLUDED.import_id,
                                  updated_at = now()
                    """, tenantId, person.externalRef(), person.unitCode(), person.status(), person.effectiveDate(), importId);

            if ((previous == null && person.status().equals("ACTIVE"))
                    || (previous != null && (!previous.status().equals(person.status())
                    || !previous.unitCode().equals(person.unitCode())
                    || !Objects.equals(previous.effectiveDate(), person.effectiveDate()))))
            {
                String changeType = changeType(previous, person);
                if (changeType != null)
                {
                    UUID personId = jdbc.queryForObject("""
                            SELECT id FROM hr_people WHERE tenant_id = ? AND external_ref = ?
                            """, UUID.class, tenantId, person.externalRef());
                    createChangeTask(tenantId, personId, person, previous, changeType);
                }
            }
        }

        jdbc.update("""
                INSERT INTO audit_logs(id, tenant_id, action, resource_id, details)
                VALUES (?, ?, 'HR_CSV_IMPORTED', ?, jsonb_build_object('rows', ?))
                """, UUID.randomUUID(), tenantId, importId, people.size());
        return new ImportResult(importId, people.size());
    }

    @GetMapping("/records")
    public List<PersonItem> records(@RequestParam(defaultValue = "250") int limit)
    {
        if (limit < 1 || limit > 500)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O limite deve estar entre 1 e 500.");
        }
        UUID tenantId = TenantContext.requireTenantId();
        return jdbc.query("""
                SELECT id, external_ref, unit_code, employment_status, effective_date, updated_at
                FROM hr_people
                WHERE tenant_id = ?
                ORDER BY unit_code, external_ref
                LIMIT ?
                """, HrImportController::mapPerson, tenantId, limit);
    }

    @GetMapping("/imports")
    public List<ImportItem> imports()
    {
        UUID tenantId = TenantContext.requireTenantId();
        return jdbc.query("""
                SELECT id, source_name, imported_rows, created_at
                FROM hr_imports
                WHERE tenant_id = ?
                ORDER BY created_at DESC
                LIMIT 50
                """, (rs, row) -> new ImportItem(rs.getObject("id", UUID.class),
                rs.getString("source_name"), rs.getInt("imported_rows"),
                rs.getTimestamp("created_at").toInstant()), tenantId);
    }

    private static PersonItem mapPerson(ResultSet rs, int row) throws SQLException
    {
        return new PersonItem(rs.getObject("id", UUID.class), rs.getString("external_ref"),
                rs.getString("unit_code"), rs.getString("employment_status"),
                rs.getObject("effective_date", LocalDate.class), rs.getTimestamp("updated_at").toInstant());
    }

    private String changeType(PersonState previous, HrCsvParser.Person person)
    {
        if (previous == null) return "NEW_HIRE";
        if (!previous.status().equals(person.status())) return "STATUS_CHANGED";
        if (!previous.unitCode().equals(person.unitCode())) return "UNIT_CHANGED";
        if (!Objects.equals(previous.effectiveDate(), person.effectiveDate())) return "EFFECTIVE_DATE_CHANGED";
        return null;
    }

    private void createChangeTask(UUID tenantId, UUID personId, HrCsvParser.Person person,
                                  PersonState previous, String changeType)
    {
        jdbc.update("""
                INSERT INTO hr_change_events(id, tenant_id, person_id, external_ref, change_type,
                                             previous_unit, new_unit, previous_status, new_status, effective_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), tenantId, personId, person.externalRef(), changeType,
                previous == null ? null : previous.unitCode(), person.unitCode(),
                previous == null ? null : previous.status(), person.status(), person.effectiveDate());

        UUID taskId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO remediation_tasks(id, tenant_id, title, due_date, category,
                                              origin_reference, source_event)
                VALUES (?, ?, ?, ?, 'HR_CHANGE', ?, ?)
                """, taskId, tenantId, "Revisar rotinas de SST após mudança de vínculo",
                LocalDate.now().plusDays(7),
                person.externalRef(), changeType);
        jdbc.update("""
                INSERT INTO task_notification_outbox(id, tenant_id, task_id, event_type)
                VALUES (?, ?, ?, 'TASK_CREATED')
                """, UUID.randomUUID(), tenantId, taskId);
        jdbc.update("""
                INSERT INTO audit_logs(id, tenant_id, action, resource_id, details)
                VALUES (?, ?, 'HR_CHANGE_TASK_CREATED', ?, jsonb_build_object('changeType', ?))
                """, UUID.randomUUID(), tenantId, taskId, changeType);
    }

    public record ImportResult(UUID id, int importedRows) {}

    public record PersonItem(UUID id, String externalRef, String unitCode, String employmentStatus,
                             LocalDate effectiveDate, Instant updatedAt) {}

    public record ImportItem(UUID id, String sourceName, int importedRows, Instant createdAt) {}

    private record PersonState(String unitCode, String status, LocalDate effectiveDate) {}
}
