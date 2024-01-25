package org.tkit.maven.liquibase;

import liquibase.Liquibase;
import liquibase.change.Change;
import liquibase.change.core.CreateTableChange;
import liquibase.change.core.DropTableChange;
import liquibase.changelog.ChangeSet;
import liquibase.exception.LiquibaseException;
import liquibase.serializer.core.xml.XMLChangeLogSerializer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.liquibase.maven.plugins.AbstractLiquibaseChangeLogMojo;
import java.io.ByteArrayOutputStream;
import java.util.*;

@Mojo(name = "check", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public class CheckChangesMojo extends AbstractLiquibaseChangeLogMojo {

    /**
     * Skip changes map <change_name>comma separated list of specific names</change_name>
     * For example skip drop tables:
     *      <dropTable>table1,table2</dropTable>
     * For example skip create tables:
     *      <createTable>table1,table2</createTable>
     */
    @Parameter(name = "skipChanges", property = "liquibase.skipChange")
    protected Map<String,String> skipChanges;

    /**
     * The liquibase changeLog file.
     */
    @Parameter(name = "liquibaseChangeLogFile", property = "liquibase.changeLogFile", defaultValue = "target/liquibase-diff-changeLog.xml")
    protected String liquibaseChangeLogFile;

    /**
     * Error message
     */
    @Parameter(name = "helpMessage", property = "liquibase.helpMessage", defaultValue = "To generate a Liquibase changes in the 'target/liquibase-diff-changeLog.xml' file, run: 'mvn clean compile -Pdb-diff'")
    protected String helpMessage;

    /**
     * Enable liquibase verbose
     */
    @Parameter(name = "liquibaseVerbose", property = "liquibase.verbose", defaultValue = "false")
    protected boolean liquibaseVerbose;

    /**
     * The project being built.
     */
    @Parameter(readonly = true, required = true, defaultValue = "${project}")
    private MavenProject currentProject;

    private static final String LOG_LINE = "--------------------------------------------------------------";

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        logLevel = "INFO";
        changeLogFile = liquibaseChangeLogFile;
        project = currentProject;
        verbose = liquibaseVerbose;

        username = "quarkus";
        password = "secret";
        url = "offline:postgresql";

        getLog().info("Start liquibase changes check.");
        super.execute();
        getLog().info("Finished liquibase changes check.");

    }

    @Override
    protected void performLiquibaseTask(Liquibase liquibase) throws LiquibaseException {

        // generate skip changes
        Map<String, Set<String>> skip = new HashMap<>();
        for (Map.Entry<String, String> entry : skipChanges.entrySet()) {
            String[] tmp = entry.getValue().split(",");
            if (tmp.length > 0 ) {
                skip.put(entry.getKey(), Set.of(tmp));
            }
        }

        if (!skip.isEmpty()) {
            getLog().info("Skip changes: " + skip);
        }

        boolean valid = true;
        var changeLog = liquibase.getDatabaseChangeLog();

        List<ChangeSet> removeSet = new ArrayList<>();
        for (ChangeSet item : changeLog.getChangeSets()) {

            List<Change> remove = new ArrayList<>();
            for ( var change : item.getChanges()) {

                // skip dropTable
                if (change instanceof DropTableChange dt) {
                    var name = change.getSerializedObjectName();
                    Set<String> exclude = skip.get(name);
                    if (exclude != null && exclude.contains(dt.getTableName())) {
                        remove.add(change);
                        continue;
                    }
                }
                // skip createTable
                if (change instanceof CreateTableChange ct) {
                    var name = change.getSerializedObjectName();
                    Set<String> exclude = skip.get(name);
                    if (exclude != null && exclude.contains(ct.getTableName())) {
                        remove.add(change);
                        continue;
                    }
                }

                valid = false;
            }

            // remove all skip changes from changeSet
            if (!remove.isEmpty()) {
                item.removeAllChanges(remove);
            }

            // check if changeSet is empty, add to remove
            if (item.getChanges().isEmpty()) {
                removeSet.add(item);
            }
        }

        // remove empty changeSets
        if (!removeSet.isEmpty()) {
            changeLog.getChangeSets().removeAll(removeSet);
        }


        if (valid) {
            getLog().info("No unresolved liquibase changes found.");
            return;
        }

        // found unresolved changes in the liquibase file
        XMLChangeLogSerializer serializer = new XMLChangeLogSerializer();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            serializer.write(changeLog.getChangeSets(), out);
            getLog().info(LOG_LINE);
            getLog().info("Changes to resolve!");
            getLog().info(LOG_LINE);
            System.out.println(out);
            getLog().info(LOG_LINE);
            getLog().info(helpMessage);
            getLog().info(LOG_LINE);
        } catch (Exception ex) {
            throw new LiquibaseException("Check report status: INVALID. Error writing report to console.", ex);
        }
        throw new LiquibaseException("Check report status: INVALID");

    }
}
