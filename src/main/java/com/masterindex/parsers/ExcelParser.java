package com.masterindex.parsers;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Microsoft Excel文档解析器。
 * @author liwenchao
 */
public class ExcelParser implements DocumentParser {
    private static final Logger logger = LoggerFactory.getLogger(ExcelParser.class);

    @Override
    public boolean supports(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        return fileName.endsWith(".xlsx") || fileName.endsWith(".xls");
    }

    @Override
    public String extractText(Path filePath) throws IOException {
        StringBuilder text = new StringBuilder();

        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
            String fileName = filePath.getFileName().toString().toLowerCase();

            Workbook workbook;
            if (fileName.endsWith(".xlsx")) {
                // 处理 .xlsx 格式
                workbook = new XSSFWorkbook(fis);
            } else if (fileName.endsWith(".xls")) {
                // 处理 .xls 格式
                workbook = new HSSFWorkbook(fis);
            } else {
                throw new IOException("不支持的Excel文件格式: " + fileName);
            }

            try {
                DataFormatter formatter = new DataFormatter();

                for (Sheet sheet : workbook) {
                    for (Row row : sheet) {
                        for (Cell cell : row) {
                            String cellValue = formatter.formatCellValue(cell);
                            if (cellValue != null && !cellValue.trim().isEmpty()) {
                                text.append(cellValue).append(" ");
                            }
                        }
                        text.append("\n");
                    }
                }
            } finally {
                workbook.close();
            }
        } catch (Exception e) {
            logger.error("从Excel提取文本失败: {}", filePath, e);
            throw new IOException("parsing the excel document failed", e);
        }

        return text.toString();
    }

    @Override
    public String getType() {
        return "document";
    }
}
