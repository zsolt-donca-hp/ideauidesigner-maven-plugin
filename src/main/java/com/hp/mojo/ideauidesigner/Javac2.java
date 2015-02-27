package com.hp.mojo.ideauidesigner;

import com.intellij.ant.AntClassWriter;
import com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.compiler.FormErrorInfo;
import com.intellij.uiDesigner.compiler.NestedFormLoader;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.uiDesigner.lw.PropertiesProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.Javac;
import org.apache.tools.ant.types.Path;
import org.objectweb.asm.ClassReader;

/**
 * This is a copy of the class "Javac2" from the package "com.intellij.ant".
 *
 * The original class is very restrictive because every method is private in it. My only change is making the method
 * 'buildClasspathClassLoader' protected to make it possible to override it (to initialize the Jide license in that
 * classloader).
 */
public class Javac2 extends Javac {
    private ArrayList myFormFiles;

    public Javac2() {
    }

    protected void compile() {
        super.compile();
        ClassLoader loader = this.buildClasspathClassLoader();
        if(loader != null) {
            this.instrumentForms(loader);
            if(!this.isJdkVersion(5) && !this.isJdkVersion(6)) {
                this.log("Skipped @NotNull instrumentation because target JDK is not 1.5 or 1.6", 2);
            } else {
                int instrumented = this.instrumentNotNull(this.getDestdir(), loader);
                this.log("Added @NotNull assertions to " + instrumented + " files", 2);
            }

        }
    }

    private void instrumentForms(ClassLoader loader) {
        ArrayList formsToInstrument = this.myFormFiles;
        if(formsToInstrument.size() == 0) {
            this.log("No forms to instrument found", 3);
        } else {
            HashMap class2form = new HashMap();

            for(int i = 0; i < formsToInstrument.size(); ++i) {
                File formFile = (File)formsToInstrument.get(i);
                this.log("compiling form " + formFile.getAbsolutePath(), 3);
                byte[] bytes = new byte[(int)formFile.length()];

                try {
                    FileInputStream formFileContent = new FileInputStream(formFile);
                    formFileContent.read(bytes);
                    formFileContent.close();
                } catch (IOException var22) {
                    this.fireError(var22.getMessage());
                    continue;
                }

                String var23;
                try {
                    var23 = new String(bytes, "utf8");
                } catch (UnsupportedEncodingException var21) {
                    this.fireError(var21.getMessage());
                    continue;
                }

                LwRootContainer rootContainer;
                try {
                    rootContainer = Utils.getRootContainer(var23, new CompiledClassPropertiesProvider(loader));
                } catch (AlienFormFileException var19) {
                    continue;
                } catch (Exception var20) {
                    this.fireError("Cannot process form file " + formFile.getAbsolutePath() + ". Reason: " + var20);
                    continue;
                }

                String classToBind = rootContainer.getClassToBind();
                if(classToBind != null) {
                    String name = classToBind.replace('.', '/');
                    File classFile = this.getClassFile(name);
                    if(classFile == null) {
                        this.log(formFile.getAbsolutePath() + ": Class to bind does not exist: " + classToBind, 1);
                    } else {
                        File alreadyProcessedForm = (File)class2form.get(classToBind);
                        if(alreadyProcessedForm != null) {
                            this.fireError(formFile.getAbsolutePath() + ": " + "The form is bound to the class " + classToBind + ".\n" + "Another form " + alreadyProcessedForm.getAbsolutePath() + " is also bound to this class.");
                        } else {
                            class2form.put(classToBind, formFile);
                            AsmCodeGenerator codeGenerator = new AsmCodeGenerator(rootContainer, loader, new Javac2.AntNestedFormLoader(loader), false, new AntClassWriter(this.getAsmClassWriterFlags(), loader));
                            codeGenerator.patchFile(classFile);
                            FormErrorInfo[] warnings = codeGenerator.getWarnings();

                            for(int j = 0; j < warnings.length; ++j) {
                                this.log(formFile.getAbsolutePath() + ": " + warnings[j].getErrorMessage(), 1);
                            }

                            FormErrorInfo[] errors = codeGenerator.getErrors();
                            if(errors.length > 0) {
                                StringBuffer message = new StringBuffer();

                                for(int j1 = 0; j1 < errors.length; ++j1) {
                                    if(message.length() > 0) {
                                        message.append("\n");
                                    }

                                    message.append(formFile.getAbsolutePath()).append(": ").append(errors[j1].getErrorMessage());
                                }

                                this.fireError(message.toString());
                            }
                        }
                    }
                }
            }

        }
    }

    private int getAsmClassWriterFlags() {
        return this.isJdkVersion(6)?2:1;
    }

    private boolean isJdkVersion(int ver) {
        String versionString = Integer.toString(ver);
        String targetVersion = this.getTarget();
        if(targetVersion == null) {
            String[] strings = this.getCurrentCompilerArgs();

            for(int i = 0; i < strings.length; ++i) {
                this.log("currentCompilerArgs: " + strings[i], 3);
                if(strings[i].equals("-target") && i < strings.length - 1) {
                    targetVersion = strings[i + 1];
                    break;
                }
            }
        }

        if(targetVersion == null) {
            return this.getCompilerVersion().equals("javac1." + versionString);
        } else {
            this.log("targetVersion: " + targetVersion, 3);
            return targetVersion.equals(versionString) || targetVersion.equals("1." + versionString);
        }
    }

    protected ClassLoader buildClasspathClassLoader() {
        StringBuffer classPathBuffer = new StringBuffer();
        classPathBuffer.append(this.getDestdir().getAbsolutePath());
        Path classpath = this.getClasspath();
        if(classpath != null) {
            String[] classPath = classpath.list();

            for(int e = 0; e < classPath.length; ++e) {
                String pathElement = classPath[e];
                classPathBuffer.append(File.pathSeparator);
                classPathBuffer.append(pathElement);
            }
        }

        classPathBuffer.append(File.pathSeparator);
        classPathBuffer.append(getInternalClassPath());
        String var7 = classPathBuffer.toString();
        this.log("classpath=" + var7, 2);

        try {
            return createClassLoader(var7);
        } catch (MalformedURLException var6) {
            this.fireError(var6.getMessage());
            return null;
        }
    }

    private int instrumentNotNull(File dir, ClassLoader loader) {
        int instrumented = 0;
        File[] files = dir.listFiles();

        for(int i = 0; i < files.length; ++i) {
            File file = files[i];
            String name = file.getName();
            if(name.endsWith(".class")) {
                String path = file.getPath();
                this.log("Adding @NotNull assertions to " + path, 3);

                try {
                    FileInputStream e = new FileInputStream(file);

                    try {
                        ClassReader reader = new ClassReader(e);
                        AntClassWriter writer = new AntClassWriter(this.getAsmClassWriterFlags(), loader);
                        NotNullVerifyingInstrumenter instrumenter = new NotNullVerifyingInstrumenter(writer);
                        reader.accept(instrumenter, 0);
                        if(instrumenter.isModification()) {
                            FileOutputStream fileOutputStream = new FileOutputStream(path);

                            try {
                                fileOutputStream.write(writer.toByteArray());
                                ++instrumented;
                            } finally {
                                fileOutputStream.close();
                            }
                        }
                    } finally {
                        e.close();
                    }
                } catch (IOException var26) {
                    this.log("Failed to instrument @NotNull assertion for " + path + ": " + var26.getMessage(), 1);
                }
            } else if(file.isDirectory()) {
                instrumented += this.instrumentNotNull(file, loader);
            }
        }

        return instrumented;
    }

    private static String getInternalClassPath() {
        String class_path = System.getProperty("java.class.path");
        String boot_path = System.getProperty("sun.boot.class.path");
        String ext_path = System.getProperty("java.ext.dirs");
        ArrayList list = new ArrayList();
        getPathComponents(class_path, list);
        getPathComponents(boot_path, list);
        ArrayList dirs = new ArrayList();
        getPathComponents(ext_path, dirs);
        Iterator e = dirs.iterator();

        while(e.hasNext()) {
            File buf = new File((String)e.next());
            String[] e1 = buf.list(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    name = name.toLowerCase();
                    return name.endsWith(".zip") || name.endsWith(".jar");
                }
            });
            if(e1 != null) {
                for(int i = 0; i < e1.length; ++i) {
                    list.add(ext_path + File.separatorChar + e1[i]);
                }
            }
        }

        StringBuffer var9 = new StringBuffer();
        Iterator var10 = list.iterator();

        while(var10.hasNext()) {
            var9.append((String)var10.next());
            if(var10.hasNext()) {
                var9.append(File.pathSeparatorChar);
            }
        }

        return var9.toString().intern();
    }

    private static void getPathComponents(String path, ArrayList list) {
        if(path != null) {
            StringTokenizer tok = new StringTokenizer(path, File.pathSeparator);

            while(tok.hasMoreTokens()) {
                String name = tok.nextToken();
                File file = new File(name);
                if(file.exists()) {
                    list.add(name);
                }
            }
        }

    }

    private void fireError(String message) {
        if(this.failOnError) {
            throw new BuildException(message, this.getLocation());
        } else {
            this.log(message, 0);
        }
    }

    private File getClassFile(String className) {
        String classOrInnerName = this.getClassOrInnerName(className);
        return classOrInnerName == null?null:new File(this.getDestdir().getAbsolutePath(), classOrInnerName + ".class");
    }

    private String getClassOrInnerName(String className) {
        File classFile = new File(this.getDestdir().getAbsolutePath(), className + ".class");
        if(classFile.exists()) {
            return className;
        } else {
            int position = className.lastIndexOf(47);
            return position == -1?null:this.getClassOrInnerName(className.substring(0, position) + '$' + className.substring(position + 1));
        }
    }

    private static URLClassLoader createClassLoader(String classPath) throws MalformedURLException {
        ArrayList urls = new ArrayList();
        StringTokenizer tokenizer = new StringTokenizer(classPath, File.pathSeparator);

        while(tokenizer.hasMoreTokens()) {
            String urlsArr = tokenizer.nextToken();
            urls.add((new File(urlsArr)).toURL());
        }

        URL[] urlsArr1 = (URL[])urls.toArray(new URL[urls.size()]);
        return new URLClassLoader(urlsArr1, (ClassLoader)null);
    }

    protected void resetFileLists() {
        super.resetFileLists();
        this.myFormFiles = new ArrayList();
    }

    protected void scanDir(File srcDir, File destDir, String[] files) {
        super.scanDir(srcDir, destDir, files);

        for(int i = 0; i < files.length; ++i) {
            String file = files[i];
            if(file.endsWith(".form")) {
                this.log("Found form file " + file, 3);
                this.myFormFiles.add(new File(srcDir, file));
            }
        }

    }

    private class AntNestedFormLoader implements NestedFormLoader {
        private final ClassLoader myLoader;
        private final HashMap myFormCache = new HashMap();

        public AntNestedFormLoader(ClassLoader loader) {
            this.myLoader = loader;
        }

        public LwRootContainer loadForm(String formFileName) throws Exception {
            if(this.myFormCache.containsKey(formFileName)) {
                return (LwRootContainer)this.myFormCache.get(formFileName);
            } else {
                String formFileFullName = formFileName.toLowerCase();
                Javac2.this.log("Searching for form " + formFileFullName, 3);
                Iterator iterator = Javac2.this.myFormFiles.iterator();

                while(iterator.hasNext()) {
                    File resourceStream = (File)iterator.next();
                    String container = resourceStream.getAbsolutePath().replace(File.separatorChar, '/').toLowerCase();
                    Javac2.this.log("Comparing with " + container, 3);
                    if(container.endsWith(formFileFullName)) {
                        FileInputStream formInputStream = new FileInputStream(resourceStream);
                        LwRootContainer container1 = Utils.getRootContainer(formInputStream, (PropertiesProvider)null);
                        this.myFormCache.put(formFileName, container1);
                        return container1;
                    }
                }

                InputStream resourceStream1 = this.myLoader.getResourceAsStream("/" + formFileName + ".form");
                if(resourceStream1 != null) {
                    LwRootContainer container2 = Utils.getRootContainer(resourceStream1, (PropertiesProvider)null);
                    this.myFormCache.put(formFileName, container2);
                    return container2;
                } else {
                    throw new Exception("Cannot find nested form file " + formFileName);
                }
            }
        }

        public String getClassToBindName(LwRootContainer container) {
            String className = container.getClassToBind();
            String result = Javac2.this.getClassOrInnerName(className.replace('.', '/'));
            return result != null?result.replace('/', '.'):className;
        }
    }
}
