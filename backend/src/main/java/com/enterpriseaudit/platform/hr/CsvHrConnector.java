package com.enterpriseaudit.platform.hr;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Component
public class CsvHrConnector implements HrConnector {
    private static final long maxBytes = 5L * 1024 * 1024;

    @Override
    public boolean supports(String fileName)
    {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    @Override
    public ImportBatch read(MultipartFile file) throws IOException
    {
        if (file.isEmpty() || file.getSize() > maxBytes)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie um CSV de até 5 MB.");
        }

        List<HrCsvParser.Person> people;
        try
        {
            people = HrCsvParser.parse(new String(file.getBytes(), StandardCharsets.UTF_8));
        }
        catch (IllegalArgumentException invalid)
        {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, invalid.getMessage());
        }

        String sourceName = file.getOriginalFilename().replaceAll("[^A-Za-z0-9._-]", "_");
        return new ImportBatch(sourceName, people);
    }
}
