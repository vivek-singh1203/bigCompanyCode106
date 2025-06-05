package com.swissre.bigcompany.model;


import lombok.*;

import java.util.List;


/**
 * Represents an employee in the organization.
 */


@Builder
@EqualsAndHashCode
@Getter
@Setter
public class Employee {
    private final String id;
    private final String firstName;
    private final String lastName;
    private final int salary;
    private final String managerId; // Manager's ID as read from CSV
    private Employee manager; // Reference to the actual manager object
    private final List<Employee> subordinates; // Direct subordinates

    public void addSubordinate(Employee subordinate) {
        this.subordinates.add(subordinate);
    }

    /**
     * Checks if this employee is a manager (i.e., has subordinates).
     * @return true if the employee has one or more subordinates, false otherwise.
     */
    public boolean isManager() {
        return !subordinates.isEmpty();
    }

}
