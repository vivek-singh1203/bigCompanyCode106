package com.swissre.bigcompany.helper;

import com.swissre.bigcompany.model.Employee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class OrganizationAnalyzerTest {

    private OrganizationAnalyzer analyzer;

    @TempDir
    Path tempDir; // JUnit 5 provides a temporary directory for tests

    @BeforeEach
    void setUp() {
        analyzer = new OrganizationAnalyzer();
    }

    private Path createCsvFile(String fileName, List<String> lines) throws IOException {
        Path filePath = tempDir.resolve(fileName);
        Files.write(filePath, lines);
        return filePath;
    }

    @Test
    @DisplayName("Should correctly read CSV and build employee hierarchy")
    void shouldReadCsvAndBuildHierarchy() throws IOException {
        List<String> csvContent = Arrays.asList(
                "Id,firstName,lastName,salary,managerId",
                "123,Joe,Doe,60000",
                "124,Martin,Chekov,45000,123",
                "125,Bob,Ronstad,47000,123",
                "300,Alice,Hasacat,50000,124",
                "305,Brett,Hardleaf,34000,300"
        );
        Path csvFile = createCsvFile("employees.csv", csvContent);

        analyzer.readEmployee(csvFile.toString());

        Map<String, Employee> employees = analyzer.getEmployeesById();
        assertNotNull(employees);
        assertEquals(5, employees.size());

        Employee ceo = employees.get("123");
        assertNotNull(ceo);
        assertEquals("Joe", ceo.getFirstName());
        assertTrue(ceo.getManagerId() == null); // CEO has no managerId

    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for invalid salary format")
    void shouldThrowForInvalidSalaryFormat() throws IOException {
        List<String> csvContent = Arrays.asList(
                "Id,firstName,lastName,salary,managerId",
                "123,Joe,Doe,abc,", // Invalid salary
                "124,Martin,Chekov,45000,123"
        );
        Path csvFile = createCsvFile("invalid_salary.csv", csvContent);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> analyzer.readEmployee(csvFile.toString()));
        assertTrue(thrown.getMessage().contains("Invalid salary format"));
    }

    @Test
    @DisplayName("Should detect managers earning more than they should")
    void shouldDetectOverpaidManagers() throws IOException {
        // CEO (123) subordinates: Martin (45000), Bob (47000) -> Avg: 46000
        // CEO max expected: 46000 * 1.50 = 69000
        // CEO actual: 70000. CEO is overpaid by 1000.
        List<String> csvContent = Arrays.asList(
                "Id,firstName,lastName,salary,managerId",
                "123,Joe,Doe,70000,", // Joe is overpaid
                "124,Martin,Chekov,45000,123",
                "125,Bob,Ronstad,47000,123",
                "300,Alice,Hasacat,50000,124",
                "305,Brett,Hardleaf,34000,300"
        );
        Path csvFile = createCsvFile("overpaid_managers.csv", csvContent);
        analyzer.readEmployee(csvFile.toString());

        analyzer.analyzeSalaries();

    }

    @Test
    @DisplayName("Should detect employees with too long reporting lines")
    void shouldDetectLongReportingLines() throws IOException {
        // Structure:
        // CEO (123)
        //   -> Martin (124) - depth 1 (1 manager)
        //     -> Alice (300) - depth 2 (2 managers)
        //       -> Brett (305) - depth 3 (3 managers)
        //         -> Carol (400) - depth 4 (4 managers)
        //           -> David (500) - depth 5 (5 managers) -> This is too long (>4)
        //             -> Eve (600) - depth 6 (6 managers) -> This is too long (>4)

        List<String> csvContent = Arrays.asList(
                "Id,firstName,lastName,salary,managerId",
                "123,Joe,Doe,100000,",
                "124,Martin,Chekov,70000,123",
                "300,Alice,Hasacat,55000,124",
                "305,Brett,Hardleaf,40000,300",
                "400,Carol,Smith,35000,305",
                "500,David,Jones,30000,400", // 5 managers (CEO, Martin, Alice, Brett, Carol)
                "600,Eve,White,25000,500"    // 6 managers
        );
        Path csvFile = createCsvFile("long_reporting_lines.csv", csvContent);
        analyzer.readEmployee(csvFile.toString());

        java.io.ByteArrayOutputStream outContent = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(outContent));

        analyzer.analyzeReportingLines();

        String output = outContent.toString();
        assertTrue(output.contains("David Jones (ID: 500) has a reporting line which is too long. Managers above: 5 (Max allowed: 4)."));
        assertTrue(output.contains("Eve White (ID: 600) has a reporting line which is too long. Managers above: 6 (Max allowed: 4)."));
        assertFalse(output.contains("Carol Smith")); // Carol has 4 managers, which is not "more than 4"
        System.setOut(System.out);
    }
}
