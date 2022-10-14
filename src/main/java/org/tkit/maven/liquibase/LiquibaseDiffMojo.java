package org.tkit.maven.liquibase;


import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.spi.PersistenceUnitTransactionType;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.exception.LiquibaseException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.hibernate.boot.registry.classloading.internal.ClassLoaderServiceImpl;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl;
import org.hibernate.jpa.boot.internal.ParsedPersistenceXmlDescriptor;
import org.hibernate.jpa.boot.spi.Bootstrap;
import org.hibernate.tool.schema.Action;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.MergeIndexer;
import org.liquibase.maven.plugins.LiquibaseDatabaseDiff;
import org.liquibase.maven.plugins.MavenUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
//import org.jboss.jandex.MergeIndexer;

@Mojo(name = "diff", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public class LiquibaseDiffMojo extends LiquibaseDatabaseDiff {

    /**
     * Location of the output directory.
     */
    @Parameter(name = "postgresVersion", property = "postgresVersion")
    protected String postgresVersion;

    /**
     * The liquibase changeLog file.
     */
    @Parameter(name = "liquibaseChangeLogFile", property = "liquibase.changeLogFile", defaultValue = "${basedir}/src/main/resources/db/changeLog.xml")
    protected String liquibaseChangeLogFile;

    /**
     * The output file
     */
    @Parameter(name = "outputFile", property = "liquibase.diffChangeLogFile", defaultValue = "${project.build.directory}/liquibase-diff-changeLog.xml")
    protected String outputFile;

    /**
     * Show hibernate SQL
     */
    @Parameter(name = "showSql", property = "liquibase.hibernate.showSql", defaultValue = "true")
    protected String showSql;

    /**
     * Show hibernate format SQL
     */
    @Parameter(name = "formatSql", property = "liquibase.hibernate.formatSql", defaultValue = "true")
    protected String formatSql;

    /**
     * Enable liquibase verbose
     */
    @Parameter(name = "liquibaseVerbose", property = "liquibase.verbose", defaultValue = "false")
    protected boolean liquibaseVerbose;

    /**
     * Disable or enable the liquibase developer user name `dev`.
     */
    @Parameter(name = "liquibaseDevUser", property = "liquibase.verbose", defaultValue = "true")
    protected boolean liquibaseDevUser;

    /**
     * Plugin properties
     */
    @Parameter(name = "properties")
    protected Properties properties;

    /**
     * The project being built.
     */
    @Parameter(readonly = true, required = true, defaultValue = "${project}")
    private MavenProject currentProject;

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        verbose = liquibaseVerbose;
        diffChangeLogFile = outputFile;
        changeLogFile = liquibaseChangeLogFile;
        project = currentProject;

        DockerImageName postgresDockerName = DockerImageName.parse(PostgreSQLContainer.IMAGE).withTag(this.postgresVersion != null ? this.postgresVersion : PostgreSQLContainer.DEFAULT_TAG);

        PostgreSQLContainer liquibaseStateDB = new PostgreSQLContainer(postgresDockerName);
        PostgreSQLContainer hibernateStateDB = new PostgreSQLContainer(postgresDockerName);

        try {
            Stream.of(liquibaseStateDB, hibernateStateDB).parallel().forEach(c-> c.start());
            if (properties != null) {
                System.setProperties(properties);
            }

            if (liquibaseDevUser) {
                System.setProperty("user.name", "dev");
            }

            username = liquibaseStateDB.getUsername();
            password = liquibaseStateDB.getPassword();
            url = liquibaseStateDB.getJdbcUrl();

            referenceUsername = hibernateStateDB.getUsername();
            referencePassword = hibernateStateDB.getPassword();
            referenceUrl = hibernateStateDB.getJdbcUrl();

            getLog().info("--------------------------------------------------------------");
            getLog().info("Execute target database update from Hibernate.");
            getLog().info("--------------------------------------------------------------");
            EntityManager em = startHibernate(referenceUsername, referencePassword, referenceUrl);
            getLog().info("EntityManager entities: " + em.getMetamodel().getEntities());

            super.execute();
        } finally {
            Stream.of(liquibaseStateDB, hibernateStateDB).parallel().forEach(c-> c.stop());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void performLiquibaseTask(Liquibase liquibase) throws LiquibaseException {
        File changeFile = new File(liquibaseChangeLogFile);
        if (changeFile.exists()) {
            getLog().info("--------------------------------------------------------------");
            getLog().info("Execute source database update from file: " + liquibase.getChangeLogFile());
            getLog().info("--------------------------------------------------------------");
            liquibase.update(new Contexts(), new LabelExpression());
        }
        getLog().info("--------------------------------------------------------------");
        getLog().info("Execute source-target database diff. Output: " + outputFile);
        getLog().info("--------------------------------------------------------------");
        super.performLiquibaseTask(liquibase);
    }

    /**
     * Start the Hibernate entity manager.
     *
     * @param username the database user name.
     * @param password the database password.
     * @param url      the database URL.
     * @return the corresponding entity manager.
     * @throws MojoExecutionException if the method fails.
     */
    protected EntityManager startHibernate(String username, String password, String url) throws MojoExecutionException {

        Map<String, Object> properties = new HashMap<>();
        properties.put(AvailableSettings.USE_SECOND_LEVEL_CACHE, Boolean.FALSE.toString());

        properties.put(AvailableSettings.HBM2DDL_AUTO, Action.CREATE_DROP);
        properties.put(AvailableSettings.SHOW_SQL, showSql);
        properties.put(AvailableSettings.FORMAT_SQL, formatSql);

        properties.put(AvailableSettings.JPA_JDBC_URL, url);
        properties.put(AvailableSettings.JPA_JDBC_USER, username);
        properties.put(AvailableSettings.JPA_JDBC_PASSWORD, password);

        URLClassLoader classLoader = createClassLoader();

        ParsedPersistenceXmlDescriptor ppx = new ParsedPersistenceXmlDescriptor(null) {
            @Override
            public ClassLoader getClassLoader() {
                return classLoader;
            }

            @Override
            public ClassLoader getTempClassLoader() {
                return classLoader;
            }
        };
        ppx.getProperties().putAll(properties);
        ppx.setName(LiquibaseDiffMojo.class.getSimpleName());
        ppx.setProviderClassName(org.hibernate.jpa.HibernatePersistenceProvider.class.getName());
        ppx.setTransactionType(PersistenceUnitTransactionType.RESOURCE_LOCAL);
        ppx.setExcludeUnlistedClasses(false);
        ppx.getJarFileUrls().addAll(Arrays.asList(classLoader.getURLs()));

        var classesFromDependencies = getEntitiesFromDependencies();
        if (classesFromDependencies != null) {
            ppx.getManagedClassNames().addAll(classesFromDependencies);
        }

        getLog().info("EntityManager dependencies: ");
        ppx.getJarFileUrls().forEach(System.out::println);


        EntityManagerFactoryBuilderImpl builder = (EntityManagerFactoryBuilderImpl)
                Bootstrap.getEntityManagerFactoryBuilder(ppx, properties, new ClassLoaderServiceImpl(classLoader));
        builder.getConfigurationValues();

        EntityManagerFactory ef = builder.build();
        return ef.createEntityManager();
    }

    /**
     * Finds all classes in dependencies which are annotated with @Entity
     * @return
     * @throws MojoExecutionException
     */
    protected List<String> getEntitiesFromDependencies() throws MojoExecutionException {
        MergeIndexer indexer = new MergeIndexer();
        loadIndexFromDependencies(indexer);
        Index index = indexer.complete();
        DotName dotName = DotName.createSimple("javax.persistence.Entity");
        var classes = index.getAnnotations(dotName);
        return classes.stream().map(clazz -> clazz.target().asClass().toString()).collect(Collectors.toList());
    }

    /**
     * Returns an isolated classloader.
     *
     * @return URLClassLoader
     */
    protected URLClassLoader createClassLoader() throws MojoExecutionException {
        try {
            List<String> classpathElements = new ArrayList<>();
            classpathElements.add(currentProject.getBuild().getOutputDirectory());

            URL[] urls = new URL[classpathElements.size()];
            for (int i = 0; i < classpathElements.size(); ++i) {
                getLog().debug("URL: " + classpathElements.get(i));
                urls[i] = new File(classpathElements.get(i)).toURI().toURL();
            }

            ClassLoader u = MavenUtils.getArtifactClassloader(currentProject,
                    true, false, getClass(), getLog(), verbose);

            return new URLClassLoader(urls, u);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to create project classloader", e);
        }
    }

    private void loadIndexFromDependencies(final MergeIndexer indexer) throws MojoExecutionException {
        try {
            List<String> elements = null;
            elements = project.getRuntimeClasspathElements();

            if (elements != null && !elements.isEmpty()) {
                List<URL> tmp = new ArrayList<>();
                for (int i = 0; i < elements.size(); i++) {

                    String element = elements.get(i);
                    try {
                        tmp.add(new File(element).toURI().toURL());
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                }
                URL[] runtimeUrls = tmp.toArray(new URL[0]);
                URLClassLoader newLoader = new URLClassLoader(runtimeUrls, Thread.currentThread().getContextClassLoader());
                Enumeration<URL> items = newLoader.getResources(MergeIndexer.INDEX);
                while (items.hasMoreElements()) {
                    URL url = items.nextElement();
                    indexer.loadFromUrl(url);
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error loading index from libraries.", e);
        }
    }

    public Index loadFromUrl(URL url) throws Exception {
        try (InputStream input = url.openStream()) {
            IndexReader reader = new IndexReader(input);
            return reader.read();
        }
    }

}