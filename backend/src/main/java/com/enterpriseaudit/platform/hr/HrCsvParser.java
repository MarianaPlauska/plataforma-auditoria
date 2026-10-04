package com.enterpriseaudit.platform.hr;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HrCsvParser {
    private static final int maxRows = 10000;
    private static final List<String> headers = List.of("external_ref", "unit_code", "employment_status", "effective_date");
    private static final Set<String> statuses = Set.of("ACTIVE", "ON_LEAVE", "INACTIVE", "TERMINATED");

    private HrCsvParser()
    {
    }

    public static List<Person> parse(String text)
    {
        List<List<String>> rows = readCsv(text);
        if (rows.isEmpty() || !rows.getFirst().equals(headers))
        {
            throw new IllegalArgumentException("O cabeçalho deve ser external_ref,unit_code,employment_status,effective_date.");
        }
        if (rows.size() - 1 > maxRows)
        {
            throw new IllegalArgumentException("O CSV excede o limite de 10.000 linhas.");
        }

        List<Person> people = new ArrayList<>();
        Set<String> references = new HashSet<>();
        for (int index = 1; index < rows.size(); index++)
        {
            List<String> row = rows.get(index);
            if (row.size() != headers.size())
            {
                throw new IllegalArgumentException("A linha " + (index + 1) + " deve conter quatro colunas.");
            }
            String reference = required(row.get(0), "referência", index);
            String unit = required(row.get(1), "unidade", index);
            String status = required(row.get(2), "status", index).toUpperCase();
            if (reference.length() > 100 || unit.length() > 100 || !statuses.contains(status))
            {
                throw new IllegalArgumentException("A linha " + (index + 1) + " contém um valor inválido.");
            }
            if (!references.add(reference))
            {
                throw new IllegalArgumentException("A referência externa aparece mais de uma vez no CSV.");
            }
            people.add(new Person(reference, unit, status, parseDate(row.get(3), index)));
        }
        return people;
    }

    private static List<List<String>> readCsv(String text)
    {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++)
        {
            char current = text.charAt(index);
            if (current == '"')
            {
                if (quoted && index + 1 < text.length() && text.charAt(index + 1) == '"')
                {
                    field.append('"');
                    index++;
                }
                else
                {
                    quoted = !quoted;
                }
            }
            else if (current == ',' && !quoted)
            {
                row.add(field.toString().trim());
                field.setLength(0);
            }
            else if ((current == '\n' || current == '\r') && !quoted)
            {
                if (current == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') index++;
                row.add(field.toString().trim());
                field.setLength(0);
                if (!(row.size() == 1 && row.getFirst().isEmpty())) rows.add(row);
                row = new ArrayList<>();
            }
            else
            {
                field.append(current);
            }
        }
        if (quoted) throw new IllegalArgumentException("O CSV contém uma aspa sem fechamento.");
        if (!row.isEmpty() || !field.isEmpty())
        {
            row.add(field.toString().trim());
            rows.add(row);
        }
        if (text.startsWith("\uFEFF") && !rows.isEmpty())
        {
            rows.getFirst().set(0, rows.getFirst().getFirst().replace("\uFEFF", ""));
        }
        return rows;
    }

    private static String required(String value, String label, int row)
    {
        if (value.isBlank()) throw new IllegalArgumentException("Informe " + label + " na linha " + (row + 1) + ".");
        return value;
    }

    private static LocalDate parseDate(String value, int row)
    {
        if (value.isBlank()) return null;
        try
        {
            return LocalDate.parse(value);
        }
        catch (DateTimeParseException invalid)
        {
            throw new IllegalArgumentException("A data na linha " + (row + 1) + " deve usar AAAA-MM-DD.");
        }
    }

    public record Person(String externalRef, String unitCode, String status, LocalDate effectiveDate) {}
}
