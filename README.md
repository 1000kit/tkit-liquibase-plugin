# tkit-liquibase-plugin

1000kit liquibase maven plugin to generate the `DIFF`

[![License](https://img.shields.io/badge/license-Apache--2.0-green?style=for-the-badge&logo=apache)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/org.tkit.maven/tkit-liquibase-plugin?logo=java&style=for-the-badge)](https://maven-badges.herokuapp.com/maven-central/org.tkit.maven/tkit-liquibase-plugin)
[![GitHub Actions Status](<https://img.shields.io/github/workflow/status/1000kit/tkit-liquibase-plugin/build?logo=GitHub&style=for-the-badge>)](https://github.com/1000kit/tkit-liquibase-plugin/actions/workflows/build.yml)

This plugin use `Testcontainers` with `tkit-quarkus-test` extension to start the database containers
for `Liquibase` diff action.

The plugin will exexute this steps:
1. Start docker container from `docker-compose.yml`. The default location is `src/test/liquibase/docker-compose.yml`
2. Start the `Hibernate` entity manager with `create-drop` flag. This will apply all the `Hibernate` changes to the `target` database.
3. Apply existing Liquibase changes to the `source` database. If there is not `changeLog` file in the `src/main/resource/db` directory the update will be not execute.
4. Compare the `source` and `target` database. The result of the execution will be in the `target/liquibase-diff-changeLog.xml` file. 
 
The plugin use these system properties for JDBC connection:
* liquibase.source.username
* liquibase.source.password
* liquibase.source.url
* liquibase.target.username
* liquibase.target.password
* liquibase.target.url

### Maven configuration

Add the plugin to your maven project and add the `JDBC` driver to the plugin.
```java
    <plugin>
        <groupId>org.tkit.maven</groupId>
        <artifactId>tkit-liquibase-plugin</artifactId>
        <version>0.1.0</version>
        <dependencies>
            <dependency>
                <groupId>org.postgresql</groupId>
                <artifactId>postgresql</artifactId>
                <version>42.2.2</version>
            </dependency>
        </dependencies>
    </plugin>
```

### Docker compose configuration

Create `docker-compose.yml` file in the `src/test/liquibase/` directory.
```yaml
version: '2.4'
services:
  source:
    image: postgres:10.5
    environment:
      POSTGRES_DB: "db"
      POSTGRES_USER: "db"
      POSTGRES_PASSWORD: "db"
    ports:
      - "5432:5432"
    labels:
      - "test.priority=90"
      - "test.Wait.forLogMessage.regex=.*database system is ready to accept connections.*\\s"
      - "test.Wait.forLogMessage.times=2"
      - "test.log=true"
      - "test.property.liquibase.source.username=db"
      - "test.property.liquibase.source.password=db"
      - "test.property.liquibase.source.url=jdbc:postgresql://$${host:source}:$${port:source:5432}/db"
  target:
    image: postgres:10.5
    environment:
      POSTGRES_DB: "db"
      POSTGRES_USER: "db"
      POSTGRES_PASSWORD: "db"
    ports:
      - "5433:5432"
    labels:
      - "test.priority=90"
      - "test.Wait.forLogMessage.regex=.*database system is ready to accept connections.*\\s"
      - "test.Wait.forLogMessage.times=2"
      - "test.log=true"
      - "test.property.liquibase.target.username=db"
      - "test.property.liquibase.target.password=db"
      - "test.property.liquibase.target.url=jdbc:postgresql://$${host:target}:$${port:target:5432}/db"
```
The docker compose file set up the required system properties for the plugin.

### Generate database diff

Run maven command in the project directory:
```shell script
mvn clean compile tkit-liquibase:diff
```
The result of the execution will be in the `target/liquibase-diff-changeLog.xml` file.

