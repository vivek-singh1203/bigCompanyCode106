package com.swissre.bigcompany;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.swissre.bigcompany.helper.OrganizationAnalyzer;

public class BigCompanyOrganizationAnalyzerApplication {

    private static final Logger logger = LogManager.getLogger(BigCompanyOrganizationAnalyzerApplication.class);

    public static void main(String[] args) {

        if (args.length < 1) {
            logger.error("Usage: java -jar organization-analyzer.jar <path_to_csv_file>");
            return;
        }
        String filePath = args[0];
        OrganizationAnalyzer analyzer = new OrganizationAnalyzer();

        try {
            logger.info("Reading employee data from: " + filePath);
            analyzer.readEmployee(filePath);
            logger.info("Data loaded successfully.");

            analyzer.analyzeSalaries();
            analyzer.analyzeReportingLines();

        } catch (Exception e) {
            logger.error("An error occurred: "+ e.getMessage());
        }
    }
}