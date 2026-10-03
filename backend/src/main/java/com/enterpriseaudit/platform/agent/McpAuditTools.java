package com.enterpriseaudit.platform.agent;

import com.enterpriseaudit.platform.security.TenantContext;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class McpAuditTools {
    private final DemoAuditOperations operations;
    public McpAuditTools(DemoAuditOperations operations) { this.operations = operations; }

    @McpTool(name = "checkESocialStatus", description = "Consulta status demonstrativo dos eventos eSocial S-2220 e S-2230")
    public Map<String, Object> checkESocialStatus(@McpToolParam(description = "Identificador do funcionário") String employeeId) {
        return operations.checkESocialStatus(TenantContext.requireTenantId(), employeeId);
    }

    @McpTool(name = "getAuditComplianceHistory", description = "Recupera histórico de conformidade sintético de um funcionário")
    public Map<String, Object> getAuditComplianceHistory(@McpToolParam(description = "Identificador do funcionário") String employeeId) {
        return operations.getAuditComplianceHistory(TenantContext.requireTenantId(), employeeId);
    }
}
