package dev.schlaubi.mikbot.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

public abstract class PatchPropertiesTask extends DefaultTask {

    @InputFile
    public abstract Property<Path> getPropertiesFile();

    @TaskAction
    public void runTask() throws IOException {
        var properties = new Properties();

        var extension = ((PluginExtension) getProject().getExtensions()
                .getByName(ExtensionKt.pluginExtensionName));

        var file = getPropertiesFile().get();
        properties.load(Files.newBufferedReader(file));
        properties.setProperty("plugin.id", ExtensionKt.getPluginId(getProject()));
        properties.setProperty("plugin.version", String.valueOf(getProject().getVersion()));
        var requires = extension.getRequires().getOrNull();
        if (requires != null) {
            properties.setProperty("plugin.requires", requires);
        }

        var dependenciesString = UtilKt.buildDependenciesString(getProject());
        if (!dependenciesString.isBlank()) {
            properties.setProperty("plugin.dependencies", dependenciesString);
        }

        properties.setProperty("plugin.description",
                extension.getDescription().getOrElse("<no description>"));
        properties.setProperty("plugin.provider",
                extension.getProvider().get());
        properties.setProperty("plugin.license",
                extension.getLicense().get());

        try (var writer = Files.newBufferedWriter(file, StandardOpenOption.WRITE)) {
            properties.store(writer, "Generated by Mikbot processor");
        }
    }
}
