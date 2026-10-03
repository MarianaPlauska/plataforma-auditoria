package com.enterpriseaudit.platform.agent;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.Map;

@Component
public class AgentAuditTools {
    private final DemoAuditOperations operations;
    public AgentAuditTools(DemoAuditOperations operations) { this.operations = operations; }

    @Tool(description = "Consulta status demonstrativo dos eventos eSocial S-2220 e S-2230 para um funcionário")
    public Map<String, Object> checkESocialStatus(
            @ToolParam(description = "Identificador do funcionário") String employeeId,
            ToolContext toolContext) {
        return operations.checkESocialStatus(tenantId(toolContext), employeeId);
    }

    @Tool(description = "Recupera histórico de conformidade sintético de um funcionário")
    public Map<String, Object> getAuditComplianceHistory(
            @ToolParam(description = "Identificador do funcionário") String employeeId,
            ToolContext toolContext) {
        return operations.getAuditComplianceHistory(tenantId(toolContext), employeeId);
    }

    private UUID tenantId(ToolContext context) {
        Object tenantId = context.getContext().get("tenantId");
        if (tenantId == null) throw new IllegalStateException("Tenant context missing from agent tool call");
        return UUID.fromString(tenantId.toString());
    }
}
