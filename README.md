# FAIR Data Station

*FAIR Data Station API*

## Usage

*Documentation to be done*

## Development

For development, we highly recommend using IDE and run Maven commands or the Spring Boot application from it.

### Technology Stack

- **Java** (JDK 17)
- **PostgreSQL DB** (13 or higher)
- **Maven** (3.6 or higher)
- **Docker** (20.10 or higher) - *for building Docker image only*

### Build & Run

To run the application, a PostgreSQL DB is required to be running. To configure the standard connection
suitable for development (`postgresql://localhost/fds`), simply instruct Spring Boot to use the `dev` profile:

```bash
$ mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Alternatively, create an `application.yml` file  (or edit appropriate one) in the project root, and then run:

```bash
$ mvn spring-boot:run
```

### Run tests

Run from the root of the project:

```bash
$ mvn test
```

### Package the application

Run from the root of the project:

```bash
$ mvn package
```

### Create a Docker image

Run from the root of the project (requires building `jar` file using `mvn package` as shown above):

```bash
$ docker build -t fairdatastation:local .
```

Note: Supplied [`Dockerfile`](Dockerfile) is multistage and as such does not require Java nor Maven to be
installed directly.

## Contributing

We maintain a [CHANGELOG](CHANGELOG.md), you should also take a look at our [Contributing Guidelines](CONTRIBUTING.md)
and [Security Policy](SECURITY.md).

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for more details.

## Acknowledgments

This code is based on work contucted across a number of projects, in particular C4yourself (funder: Health Holland—Top Sector Life Sciences and Health, grant number: LSHM 21044_C4YOUR- SELF) and Personal Genetic Locker (funder: Nederlandse Organisa- tie voor Wetenschappelijk Onderzoek, grant number: 628.011.022).
