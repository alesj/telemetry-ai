package io.quarkiverse.telemetry.ext;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;

import java.util.List;

@Entity
public class Person extends PanacheEntity {

    public String name;
    public String surname;
    public Integer age;

    public static List<Person> findByName(String name) {
        return list("name", name);
    }

    public static List<Person> findBySurname(String surname) {
        return list("surname", surname);
    }

    public static List<Person> findByAge(Integer age) {
        return list("age", age);
    }
}
