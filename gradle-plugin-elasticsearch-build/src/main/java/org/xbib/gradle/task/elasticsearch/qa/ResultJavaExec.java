package org.xbib.gradle.task.elasticsearch.qa;

import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.ExecResult;
import org.gradle.process.JavaDebugOptions;
import org.gradle.process.JavaExecSpec;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.ProcessForkOptions;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.process.internal.JavaExecAction;

import javax.inject.Inject;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Executes a Java application in a child process.
 * <p>
 * Similar to {@link org.gradle.api.tasks.Exec}, but starts a JVM with the given classpath and application class.
 * </p>
 * <pre class='autoTested'>
 * apply plugin: 'java'
 *
 * task runApp(type: JavaExec) {
 *   classpath = sourceSets.main.runtimeClasspath
 *
 *   main = 'package.Main'
 *
 *   // arguments to pass to the application
 *   args 'appArg1'
 * }
 * </pre>
 * <p>
 * The process can be started in debug mode (see {@link #getDebug()}) in an ad-hoc manner by supplying the `--debug-jvm` switch when invoking the build.
 * <pre>
 * gradle someJavaExecTask --debug-jvm
 * </pre>
 */
public class ResultJavaExec extends ConventionTask implements JavaExecSpec {

    private final JavaExecAction javaExecHandleBuilder;

    private ExecResult execResult;

    public ResultJavaExec() {
        javaExecHandleBuilder = getExecActionFactory().newJavaExecAction();
    }

    @Inject
    protected ExecActionFactory getExecActionFactory() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void exec() {
        setMain(getMain()); // make convention mapping work (at least for 'main'...
        setJvmArgs(getJvmArgs()); // ...and for 'jvmArgs')
        javaExecHandleBuilder.setIgnoreExitValue(true);
        try {
            execResult = javaExecHandleBuilder.execute();
        } catch (Throwable t) {
            getLogger().warn(t.getMessage(), t);
        }
    }

    /**
     * {@inheritDoc}
     */
    public List<String> getAllJvmArgs() {
        return javaExecHandleBuilder.getAllJvmArgs();
    }

    /**
     * {@inheritDoc}
     */
    public void setAllJvmArgs(List<String> arguments) {
        javaExecHandleBuilder.setAllJvmArgs(arguments);
    }

    /**
     * {@inheritDoc}
     */
    public void setAllJvmArgs(Iterable<?> arguments) {
        javaExecHandleBuilder.setAllJvmArgs(arguments);
    }

    /**
     * {@inheritDoc}
     */
    public List<String> getJvmArgs() {
        return javaExecHandleBuilder.getJvmArgs();
    }

    /**
     * {@inheritDoc}
     */
    public void setJvmArgs(List<String> arguments) {
        javaExecHandleBuilder.setJvmArgs(arguments);
    }

    /**
     * {@inheritDoc}
     */
    public void setJvmArgs(Iterable<?> arguments) {
        javaExecHandleBuilder.setJvmArgs(arguments);
    }

    /**
     * {@inheritDoc}
     */
    public ResultJavaExec jvmArgs(Iterable<?> arguments) {
        javaExecHandleBuilder.jvmArgs(arguments);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ResultJavaExec jvmArgs(Object... arguments) {
        javaExecHandleBuilder.jvmArgs(arguments);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public List<CommandLineArgumentProvider> getJvmArgumentProviders() {
        return javaExecHandleBuilder.getJvmArgumentProviders();
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Object> getSystemProperties() {
        return javaExecHandleBuilder.getSystemProperties();
    }

    /**
     * {@inheritDoc}
     */
    public void setSystemProperties(Map<String, ?> properties) {
        javaExecHandleBuilder.setSystemProperties(properties);
    }

    /**
     * {@inheritDoc}
     */
    public ResultJavaExec systemProperties(Map<String, ?> properties) {
        javaExecHandleBuilder.systemProperties(properties);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ResultJavaExec systemProperty(String name, Object value) {
        javaExecHandleBuilder.systemProperty(name, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public FileCollection getBootstrapClasspath() {
        return javaExecHandleBuilder.getBootstrapClasspath();
    }

    /**
     * {@inheritDoc}
     */
    public void setBootstrapClasspath(FileCollection classpath) {
        javaExecHandleBuilder.setBootstrapClasspath(classpath);
    }

    /**
     * {@inheritDoc}
     */
    public ResultJavaExec bootstrapClasspath(Object... classpath) {
        javaExecHandleBuilder.bootstrapClasspath(classpath);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public String getMinHeapSize() {
        return javaExecHandleBuilder.getMinHeapSize();
    }

    /**
     * {@inheritDoc}
     */
    public void setMinHeapSize(String heapSize) {
        javaExecHandleBuilder.setMinHeapSize(heapSize);
    }

    /**
     * {@inheritDoc}
     */
    public String getDefaultCharacterEncoding() {
        return javaExecHandleBuilder.getDefaultCharacterEncoding();
    }

    /**
     * {@inheritDoc}
     */
    public void setDefaultCharacterEncoding(String defaultCharacterEncoding) {
        javaExecHandleBuilder.setDefaultCharacterEncoding(defaultCharacterEncoding);
    }

    /**
     * {@inheritDoc}
     */
    public String getMaxHeapSize() {
        return javaExecHandleBuilder.getMaxHeapSize();
    }

    /**
     * {@inheritDoc}
     */
    public void setMaxHeapSize(String heapSize) {
        javaExecHandleBuilder.setMaxHeapSize(heapSize);
    }

    /**
     * {@inheritDoc}
     */
    public boolean getEnableAssertions() {
        return javaExecHandleBuilder.getEnableAssertions();
    }

    /**
     * {@inheritDoc}
     */
    public void setEnableAssertions(boolean enabled) {
        javaExecHandleBuilder.setEnableAssertions(enabled);
    }

    /**
     * {@inheritDoc}
     */
    public boolean getDebug() {
        return javaExecHandleBuilder.getDebug();
    }

    /**
     * {@inheritDoc}
     */
    public void setDebug(boolean enabled) {
        javaExecHandleBuilder.setDebug(enabled);
    }

    @Override
    public JavaDebugOptions getDebugOptions() {
        return javaExecHandleBuilder.getDebugOptions();
    }

    @Override
    public void debugOptions(Action<JavaDebugOptions> action) {
        javaExecHandleBuilder.debugOptions(action);
    }

    /**
     * {@inheritDoc}
     */
    public String getMain() {
        return javaExecHandleBuilder.getMain();
    }

    /**
     * {@inheritDoc}
     */
    public ResultJavaExec setMain(String mainClassName) {
        javaExecHandleBuilder.setMain(mainClassName);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public List<String> getArgs() {
        return javaExecHandleBuilder.getArgs();
    }

    /**
     * {@inheritDoc}
     */
    public ResultJavaExec setArgs(List<String> applicationArgs) {
        javaExecHandleBuilder.setArgs(applicationArgs);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ResultJavaExec setArgs(Iterable<?> applicationArgs) {
        javaExecHandleBuilder.setArgs(applicationArgs);
        return this;
    }

    @Override
    public List<CommandLineArgumentProvider> getArgumentProviders() {
        return javaExecHandleBuilder.getArgumentProviders();
    }

    /**
     * {@inheritDoc}
     */
    public ResultJavaExec args(Object... args) {
        javaExecHandleBuilder.args(args);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public JavaExecSpec args(Iterable<?> args) {
        javaExecHandleBuilder.args(args);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ResultJavaExec setClasspath(FileCollection classpath) {
        javaExecHandleBuilder.setClasspath(classpath);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ResultJavaExec classpath(Object... paths) {
        javaExecHandleBuilder.classpath(paths);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public FileCollection getClasspath() {
        return javaExecHandleBuilder.getClasspath();
    }

    /**
     * {@inheritDoc}
     */
    public ResultJavaExec copyTo(JavaForkOptions options) {
        javaExecHandleBuilder.copyTo(options);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Optional
    @Input
    public String getExecutable() {
        return javaExecHandleBuilder.getExecutable();
    }

    /**
     * {@inheritDoc}
     */
    public void setExecutable(String executable) {
        javaExecHandleBuilder.setExecutable(executable);
    }

    /**
     * {@inheritDoc}
     */
    public void setExecutable(Object executable) {
        javaExecHandleBuilder.setExecutable(executable);
    }

    /**
     * {@inheritDoc}
     */
    public ResultJavaExec executable(Object executable) {
        javaExecHandleBuilder.executable(executable);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    public File getWorkingDir() {
        return javaExecHandleBuilder.getWorkingDir();
    }

    /**
     * {@inheritDoc}
     */
    public void setWorkingDir(File dir) {
        javaExecHandleBuilder.setWorkingDir(dir);
    }

    /**
     * {@inheritDoc}
     */
    public void setWorkingDir(Object dir) {
        javaExecHandleBuilder.setWorkingDir(dir);
    }

    /**
     * {@inheritDoc}
     */
    public ResultJavaExec workingDir(Object dir) {
        javaExecHandleBuilder.workingDir(dir);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    public Map<String, Object> getEnvironment() {
        return javaExecHandleBuilder.getEnvironment();
    }

    /**
     * {@inheritDoc}
     */
    public void setEnvironment(Map<String, ?> environmentVariables) {
        javaExecHandleBuilder.setEnvironment(environmentVariables);
    }

    /**
     * {@inheritDoc}
     */
    public ResultJavaExec environment(String name, Object value) {
        javaExecHandleBuilder.environment(name, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ResultJavaExec environment(Map<String, ?> environmentVariables) {
        javaExecHandleBuilder.environment(environmentVariables);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ResultJavaExec copyTo(ProcessForkOptions target) {
        javaExecHandleBuilder.copyTo(target);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ResultJavaExec setStandardInput(InputStream inputStream) {
        javaExecHandleBuilder.setStandardInput(inputStream);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    public InputStream getStandardInput() {
        return javaExecHandleBuilder.getStandardInput();
    }

    /**
     * {@inheritDoc}
     */
    public ResultJavaExec setStandardOutput(OutputStream outputStream) {
        javaExecHandleBuilder.setStandardOutput(outputStream);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    public OutputStream getStandardOutput() {
        return javaExecHandleBuilder.getStandardOutput();
    }

    /**
     * {@inheritDoc}
     */
    public ResultJavaExec setErrorOutput(OutputStream outputStream) {
        javaExecHandleBuilder.setErrorOutput(outputStream);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    public OutputStream getErrorOutput() {
        return javaExecHandleBuilder.getErrorOutput();
    }

    /**
     * {@inheritDoc}
     */
    public JavaExecSpec setIgnoreExitValue(boolean ignoreExitValue) {
        javaExecHandleBuilder.setIgnoreExitValue(ignoreExitValue);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Input
    public boolean isIgnoreExitValue() {
        return javaExecHandleBuilder.isIgnoreExitValue();
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    public List<String> getCommandLine() {
        return javaExecHandleBuilder.getCommandLine();
    }

    /**
     * Returns the result for the command run by this task. Returns {@code null} if this task has not been executed yet.
     *
     * @return The result. Returns {@code null} if this task has not been executed yet.
     */
    @Internal
    public ExecResult getExecResult() {
        return execResult;
    }
}
