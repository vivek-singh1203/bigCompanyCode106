package com.swissre.bigcompany.helper;

import com.swissre.bigcompany.BigCompanyOrganizationAnalyzerApplication;
import com.swissre.bigcompany.model.Employee;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analyzes the organizational structure based on employee data.
 */


public class OrganizationAnalyzer {

    private static final Logger logger = LogManager.getLogger(BigCompanyOrganizationAnalyzerApplication.class);
    private final Map<String, Employee> employeesById;
    private Employee ceo; // The CEO of the company

    public OrganizationAnalyzer() {
        this.employeesById = new HashMap<>();
        this.ceo = null;
    }

    /**
     * Reads employee data from a CSV file and builds the organizational structure.
     *
     * @param filePath The path to the CSV file.
     * @throws IOException If an I/O error occurs while reading the file.
     * @throws IllegalArgumentException If the CSV format is incorrect or data is invalid.
     */
    public void readEmployee(String filePath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean isHeader = true;
            while ((line = br.readLine()) != null) {
                if (isHeader) {
                    isHeader = false; // Skip the header row
                    continue;
                }

                String[] parts = line.split(",");
                if (parts.length < 4  ) {
                    throw new IllegalArgumentException("Invalid CSV line format: " + line);
                }

                String id = parts[0].trim();
                String firstName = parts[1].trim();
                String lastName = parts[2].trim();
                int salary;
                try {
                    salary = Integer.parseInt(parts[3].trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid salary format for employee " + id + ": " + parts[3], e);
                }
                String managerId = "" ;
                if(parts.length ==5){
                    managerId=  parts[4].trim(); // Can be empty for CEO
                } // Can be empty for CEO

                Employee employee = Employee.builder().id(id)
                        .firstName(firstName)
                        .lastName(lastName)
                        .salary(salary)
                        .managerId(managerId.isEmpty() ? null : managerId)
                        .subordinates(new ArrayList<>()).build();
                employeesById.put(id, employee);

                if (managerId.isEmpty()) {
                    if (ceo != null) {
                        logger.error("Warning: Multiple CEOs found. Using the first one encountered.");
                    }
                    ceo = employee;
                }
            }
        }

        // After all employees are loaded, establish manager-subordinate relationships
        for (Employee employee : employeesById.values()) {
            if (employee.getManagerId() != null) {
                Employee manager = employeesById.get(employee.getManagerId());
                if (manager == null) {
                    logger.warn("Warning: Manager with ID " + employee.getManagerId() + " for employee " + employee.getId() + " not found.");
                } else {
                    employee.setManager(manager);
                    manager.addSubordinate(employee);
                }
            }
        }

        if (ceo == null && !employeesById.isEmpty()) {
            throw new IllegalStateException("No CEO found in the employee data.");
        }
    }

    /**
     * Analyzes manager salaries to ensure they are within the specified range (20% to 50% more)
     * than the average salary of their direct subordinates.
     */
    public void analyzeSalaries() {
        logger.info("\n--- Manager Salary Analysis ---");
        boolean foundDiscrepancy = false;
        for (Employee employee : employeesById.values()) {
            if (employee.isManager()) {
                List<Employee> subordinates = employee.getSubordinates();
                if (subordinates.isEmpty()) {
                    // This case should ideally not happen if isManager() is true, but good for robustness
                    continue;
                }

                double averageSubordinateSalary = subordinates.stream()
                        .mapToInt(Employee::getSalary)
                        .average()
                        .orElse(0.0);

                double minExpectedSalary = averageSubordinateSalary * 1.20; // At least 20% more
                double maxExpectedSalary = averageSubordinateSalary * 1.50; // No more than 50% more

                if (employee.getSalary() < minExpectedSalary) {
                    double difference = minExpectedSalary - employee.getSalary();
                    System.out.printf("Manager:: %s %s (ID: %s) earns less than they should. Current: %d, Expected Min: %.2f. Needs %.2f more.%n",
                            employee.getFirstName(), employee.getLastName(), employee.getId(),
                            employee.getSalary(), minExpectedSalary, difference);
                    foundDiscrepancy = true;
                } else if (employee.getSalary() > maxExpectedSalary) {
                    double difference = employee.getSalary() - maxExpectedSalary;
                    System.out.printf("Manager %s %s (ID: %s) earns more than they should. Current: %d, Expected Max: %.2f. Needs %.2f less.%n",
                            employee.getFirstName(), employee.getLastName(), employee.getId(),
                            employee.getSalary(), maxExpectedSalary, difference);
                    foundDiscrepancy = true;
                }
            }
        }
        if (!foundDiscrepancy) {
            logger.info("All managers earn within the expected range relative to their subordinates.");
        }
    }

    /**
     * Analyzes reporting lines to identify employees with more than 4 managers
     * between them and the CEO.
     */
    public void analyzeReportingLines() {
        logger.info("\n--- Reporting Line Analysis ---");
        if (ceo == null) {
            logger.warn("No CEO found, cannot analyze reporting lines.");
            return;
        }

        boolean foundLongLine = false;
        for (Employee employee : employeesById.values()) {
            if (employee.equals(ceo)) {
                continue; // CEO has 0 managers above them
            }
            int managersCount = getReportingLineLength(employee);
            // "more than 4 managers between them and the CEO" means 5 or more managers.
            // CEO is at depth 0. Direct reports depth 1 (1 manager). Depth 5 means 5 managers.
            // So, we are looking for depth > 5, or managersCount > 4.
            if (managersCount > 4) {
                System.out.printf("Employee:: %s %s (ID: %s) has a reporting line which is too long. Managers above: %d (Max allowed: 4).%n",
                        employee.getFirstName(), employee.getLastName(), employee.getId(), managersCount);
                foundLongLine = true;
            }
        }
        if (!foundLongLine) {
            logger.info("No employees found with excessively long reporting lines.");
        }
    }

    /**
     * Recursively calculates the number of managers an employee has above them
     * until the CEO.
     *
     * @param employee The employee to start from.
     * @return The number of managers in the reporting line, or -1 if the CEO is not reachable (shouldn't happen with valid data).
     */
    private int getReportingLineLength(Employee employee) {
        if (employee == null) {
            return -1; // Should not happen if data is valid and CEO is reachable
        }
        if (employee.equals(ceo)) {
            return 0; // CEO has 0 managers above them
        }
        // Let's use a simpler approach: count depth from employee to CEO.
        int depth = 0;
        while (employee.getManagerId() != null) {
            employee = employeesById.get(employee.getManagerId());
            depth++;
        }
        return depth;
    }

    // For testing purposes, to get the employee map
    public Map<String, Employee> getEmployeesById() {
        return employeesById;
    }

}
