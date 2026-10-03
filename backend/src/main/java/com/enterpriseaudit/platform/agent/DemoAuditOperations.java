package com.enterpriseaudit.platform.agent;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DemoAuditOperations {
    public Map<String, Object> checkESocialStatus(UUID tenantId, String employeeId) {
        return Map.of("tenantId", tenantId, "employeeId", employeeId, "events", List.of(
                Map.of("code", "S-2220", "status", "DEMO_VALIDATED"),
                Map.of("code", "S-2230", "status", "DEMO_PENDING")), "source", "synthetic-demo-adapter");
    }

    public Map<String, Object> getAuditComplianceHistory(UUID tenantId, String employeeId) {
        return Map.of("tenantId", tenantId, "employeeId", employeeId, "history", List.of(),
                "source", "synthetic-demo-adapter");
    }
}
