package com.hp.mojo.ideauidesigner;

/*
 * Copyright 2001-2006 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License" );
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.lang.reflect.Method;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.artifact.Artifact;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.BuildListener;
import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.types.Path;

import java.io.File;
import java.util.Iterator;
import java.util.Collection;

/**
 * Generates the Java sources from the *.form files.
 * <p/>
 * Implemented as a wrapper around the IDEA UI Designer Ant tasks.
 *
 * @author <a href="jerome@coffeebreaks.org">Jerome Lacoste</a>
 * @version $Id$
 * @goal javac2
 * @phase process-classes
 * @requiresProject
 * @requiresDependencyResolution
 * @see <a href="http://www.intellij.org/twiki/bin/view/Main/IntelliJUIDesignerFAQ">ui designer Ant tasks documentation</a>.
 */
public class Javac2Mojo
    extends AbstractMojo
{
    // FIXME: it might be better to try to reuse the AbstractCompilerMojo ??
    // for now we limit the interface to the bare minimum

    /**
     * Project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * Source directory containing the *.form files.
     *
     * @parameter expression="${project.build.sourceDirectory}"
     * @required
     */
    private File sourceDirectory;

    /**
     * Directory where the classes to byte code manipulate are located.
     *
     * @parameter expression="${project.build.outputDirectory}"
     * @required
     */
    private File destDirectory;

    /**
     * Initial size, in megabytes, of the memory allocation pool, ex. "64", "64m".
     *
     * @parameter expression="${maven.compiler.meminitial}"
     */
    // private String meminitial;

    /**
     * maximum size, in megabytes, of the memory allocation pool, ex. "128", "128m".
     *
     * @parameter expression="${maven.compiler.maxmem}"
     */
    // private String maxmem;

    /**
     * Fork the compilation task.
     *
     * @parameter expression="${fork}" default-value="false"
     * @required
     */
    private boolean fork;

    /**
     * Fail on error ?
     *
     * @parameter expression="${failOnError}" default-value="true"
     * @required
     */
    private boolean failOnError;

    /**
     * Enable debug.
     *
     * @parameter expression="${debug}" default-value="false"
     */
    private boolean debug;

    /**
     * Enable verbose.
     *
     * @parameter expression="${verbose}" default-value="false"
     */
    private boolean verbose;

    /**
     * Wether you want to load a Jide license.
     *
     * @parameter expression="${verbose}" default-value="false"
     */
    private boolean loadJideLicense;

    /**
     * The company name of the Jide license.
     *
     * @parameter expression="${verbose}" default-value="false"
     */
    private String companyName;

    /**
     * The product name of the Jide license.
     *
     * @parameter expression="${verbose}" default-value="false"
     */
    private String productName;

    /**
     * The license key of the Jide license.
     *
     * @parameter expression="${verbose}" default-value="false"
     */
    private String licenseKey;

    public void execute()
        throws MojoExecutionException
    {

        if ( ! destDirectory.exists() && ! destDirectory.mkdirs() ) {
           getLog().warn( "the destination directory doesn't exists and couldn't be created. The goal with probably fail." );
        }

        final Project antProject = new Project();

        antProject.addBuildListener(new DebugAntBuildListener());

        final Javac2 task = new Javac2() {
            protected ClassLoader buildClasspathClassLoader() {
                ClassLoader classLoader = super.buildClasspathClassLoader();
                Javac2Mojo.this.loadJideLicense(classLoader);
                return classLoader;
            }
        };

        task.setProject( antProject );

        task.setDestdir( destDirectory );

        task.setFailonerror( failOnError );

        final Path classpath = new Path( antProject );

        final Collection artifacts = project.getArtifacts();

        for (Iterator iterator = artifacts.iterator(); iterator.hasNext();) {
            final Artifact artifact = (Artifact) iterator.next();
            if ( ! "jar".equals( artifact.getType() ) )
              continue;

            classpath.createPathElement().setLocation( artifact.getFile() );
        }

        classpath.createPathElement().setLocation( destDirectory );

        getLog().debug( "created classpath:" + classpath );

        task.setClasspath( classpath );

        task.setFork( fork );

        // task.setMemoryInitialSize();

        // task.setMemoryMaximumSize()

        task.setDebug( debug );

        task.setVerbose( verbose );

        task.setSrcdir( new Path( antProject, sourceDirectory.getAbsolutePath() ) );

        task.setIncludes( "**/*.form" );

        getLog().info( "Executing IDEA UI Designer task..." );

        try
        {
            task.execute();
        }
        catch ( BuildException e )
        {
            throw new MojoExecutionException( "command execution failed", e );
        }
    }

    private void loadJideLicense(ClassLoader classLoader) {
        if (loadJideLicense) {
            if ((companyName == null || productName == null || licenseKey == null)) {
                getLog().error("Cannot load the Jide license because it's not specified.");
            } else {
                try {
                    // com.jidesoft.utils.Lm.verifyLicense(companyName, productName, licenseKey);
                    Class clazz = classLoader.loadClass("com.jidesoft.utils.Lm");
                    Method verifyLicense = clazz.getMethod("verifyLicense", new Class[]{String.class, String.class, String.class});
                    verifyLicense.invoke(null, new Object[]{companyName, productName, licenseKey});
                } catch (Throwable e) {
                    getLog().error(e.getMessage(), e);
                }
            }
        }
    }

    private class DebugAntBuildListener implements BuildListener {
        public void buildStarted( final BuildEvent buildEvent ) {
            getLog().debug(buildEvent.getMessage());
        }

        public void buildFinished( final BuildEvent buildEvent ) {
            getLog().debug(buildEvent.getMessage());
        }

        public void targetStarted( final BuildEvent buildEvent ) {
            getLog().debug(buildEvent.getMessage());
        }

        public void targetFinished( final BuildEvent buildEvent ) {
            getLog().debug(buildEvent.getMessage());
        }

        public void taskStarted( final BuildEvent buildEvent ) {
            getLog().debug(buildEvent.getMessage());
        }

        public void taskFinished( final BuildEvent buildEvent ) {
            getLog().debug(buildEvent.getMessage());
        }

        public void messageLogged( final BuildEvent buildEvent ) {
            getLog().debug(buildEvent.getMessage());
        }
    }
}
