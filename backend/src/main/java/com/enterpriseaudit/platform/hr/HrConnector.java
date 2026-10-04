package com.enterpriseaudit.platform.hr;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface HrConnector {
    boolean supports(String fileName);

    ImportBatch read(MultipartFile file) throws IOException;

    record ImportBatch(String sourceName, List<HrCsvParser.Person> people) {}
}
