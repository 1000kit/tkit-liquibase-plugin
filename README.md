# tkit-liquibase-plugin

1000kit liquibase maven plugin to generate the `DIFF` of the postgresql database and to validate changes through the `Check` target.

[![License](https://img.shields.io/badge/license-Apache--2.0-green?style=for-the-badge&logo=apache)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/org.tkit.maven/tkit-liquibase-plugin?logo=java&style=for-the-badge)](https://maven-badges.herokuapp.com/maven-central/org.tkit.maven/tkit-liquibase-plugin)
[![GitHub Actions Status](https://img.shields.io/github/actions/workflow/status/1000kit/tkit-liquibase-plugin/build.yml?logo=GitHub&style=for-the-badge)](https://github.com/1000kit/tkit-liquibase-plugin/actions/workflows/build.yml)

The plugin will execute this steps:
1. Start docker two postgresql container.
2. Start the `Hibernate` entity manager with `create-drop` flag. This will apply all the `Hibernate` changes to the `target` database.
3. Apply existing Liquibase changes to the `source` database. If there is not `changeLog` file in the `src/main/resource/db` directory the update will be not execute.
4. Compare the `source` and `target` database. The result of the execution will be in the `target/liquibase-diff-changeLog.xml` file. 

### Maven configuration

### Generate database diff

Create a profile in your maven project.
```xml
 <profile>
    <id>db-diff</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.tkit.maven</groupId>
                <artifactId>tkit-liquibase-plugin</artifactId>
                <version>latest-version</version>
                <executions>
                    <execution>
                        <id>default</id>
                        <goals>
                            <goal>diff</goal>
                        </goals>
                        <phase>compile</phase>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</profile>
```

Run maven command in the project directory:
```shell script
mvn clean compile -Pdb-diff
```
The result of the execution will be in the `target/liquibase-diff-changeLog.xml` file.

### Check liquibase changes

Create a profile in your maven project.
```xml
 <profile>
    <id>db-check</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.tkit.maven</groupId>
                <artifactId>tkit-liquibase-plugin</artifactId>
                <version>latest-version</version>
                <executions>
                    <execution>
                        <id>default</id>
                        <goals>
                            <goal>check</goal>
                        </goals>
                        <phase>validate</phase>
                        <configuration>
                            <skipChanges>
                                <dropTable>table1,table2</dropTable>
                                <createTable>newTable</createTable>
                            </skipChanges>
                        </configuration>                        
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</profile>
```

After you run `db-diff` run maven command in the project directory:
```shell script
mvn clean compile -Pdb-diff
mvn clean compile -Pdb-check
```
Check command will validate `target/liquibase-diff-changeLog.xml` file.

