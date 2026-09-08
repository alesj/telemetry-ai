package io.quarkus.telemetry.db;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
@Path("/")
public class PokeResource {
    private static final Logger log = Logger.getLogger(PokeResource.class);

    @GET
    @Path("/poke")
    @Produces(MediaType.APPLICATION_JSON)
    public Response poke(@QueryParam("name") String name,
                         @QueryParam("surname") String surname,
                         @QueryParam("age") Integer age) {
        log.infof("Poke query: name=%s, surname=%s, age=%s", name, surname, age);

        List<Person> results;
        if (name != null) {
            results = Person.findByName(name);
        } else if (surname != null) {
            results = Person.findBySurname(surname);
        } else if (age != null) {
            results = Person.findByAge(age);
        } else {
            results = Person.listAll();
        }

        return Response.ok(results).build();
    }

    @POST
    @Path("/poke")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response create(@QueryParam("name") String name,
                           @QueryParam("surname") String surname,
                           @QueryParam("age") Integer age) {
        log.infof("Creating person: name=%s, surname=%s, age=%s", name, surname, age);

        Person person = new Person();
        person.name = name;
        person.surname = surname;
        person.age = age;
        person.persist();

        return Response.status(Response.Status.CREATED).entity(person).build();
    }
}
