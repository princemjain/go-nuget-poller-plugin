package com.tw.go.plugin.nuget.apimpl;

import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.material.packagerepository.*;
import com.thoughtworks.go.plugin.api.response.validation.ValidationResult;
import com.thoughtworks.go.plugin.api.response.validation.ValidationError;
import com.tw.go.plugin.nuget.config.NuGetPackageConfig;
import com.tw.go.plugin.nuget.config.NuGetRepoConfig;
import com.tw.go.plugin.util.RepoUrl;

import java.util.Arrays;

import static com.thoughtworks.go.plugin.api.material.packagerepository.Property.*;
import static com.tw.go.plugin.nuget.config.NuGetPackageConfig.*;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class PluginConfig implements PackageMaterialConfiguration {

    private static Logger LOGGER = Logger.getLoggerFor(PluginConfig.class);
    public static final Property REPO_CONFIG_REPO_URL =
            new Property(RepoUrl.REPO_URL).with(DISPLAY_NAME, "Package Source or Feed Server URL").with(DISPLAY_ORDER, 0);
    public static final Property REPO_CONFIG_USERNAME =
            new Property(RepoUrl.USERNAME).with(REQUIRED, false).with(DISPLAY_NAME, "UserName").with(DISPLAY_ORDER, 1).with(PART_OF_IDENTITY, false);
    public static final Property REPO_CONFIG_PASSWORD =
            new Property(RepoUrl.PASSWORD).with(REQUIRED, false).with(SECURE, true).with(DISPLAY_NAME, "Password").with(DISPLAY_ORDER, 2).with(PART_OF_IDENTITY, false);
    public static final Property PKG_CONFIG_PACKAGE_ID =
            new Property(PACKAGE_ID).with(DISPLAY_NAME, "Package Id").with(DISPLAY_ORDER, 0);
    public static final Property PKG_CONFIG_POLL_VERSION_FROM =
            new Property(POLL_VERSION_FROM).with(REQUIRED, false).with(DISPLAY_NAME, "Version to poll >=").with(DISPLAY_ORDER, 1).with(PART_OF_IDENTITY, false);
    public static final Property PKG_CONFIG_POLL_VERSION_TO =
            new Property(POLL_VERSION_TO).with(REQUIRED, false).with(DISPLAY_NAME, "Version to poll <").with(DISPLAY_ORDER, 2).with(PART_OF_IDENTITY, false);
    public static final Property PKG_CONFIG_INCLUDE_PRE_RELEASE =
            new Property(INCLUDE_PRE_RELEASE).with(REQUIRED, false).with(DISPLAY_NAME, "Include Prerelease? (yes/no, defaults to yes)").with(DISPLAY_ORDER, 3);

    public RepositoryConfiguration getRepositoryConfiguration() {
        RepositoryConfiguration configurations = new RepositoryConfiguration();
        configurations.addConfiguration(REPO_CONFIG_REPO_URL);
        configurations.addConfiguration(REPO_CONFIG_USERNAME);
        configurations.addConfiguration(REPO_CONFIG_PASSWORD);
        return configurations;
    }

    public PackageConfiguration getPackageConfiguration() {
        PackageConfiguration configurations = new PackageConfiguration();
        configurations.addConfiguration(PKG_CONFIG_PACKAGE_ID);
        configurations.addConfiguration(PKG_CONFIG_POLL_VERSION_FROM);
        configurations.addConfiguration(PKG_CONFIG_POLL_VERSION_TO);
        configurations.addConfiguration(PKG_CONFIG_INCLUDE_PRE_RELEASE);
        return configurations;
    }

    public ValidationResult isRepositoryConfigurationValid(RepositoryConfiguration repoConfigs) {
        NuGetRepoConfig nuGetRepoConfig = new NuGetRepoConfig(repoConfigs);
        ValidationResult validationResult = new ValidationResult();
        if (nuGetRepoConfig.isRepoUrlMissing()) {
            String message = "Repository url not specified";
            LOGGER.error(message);
            validationResult.addError(new ValidationError(RepoUrl.REPO_URL, message));
            return validationResult;
        }
        nuGetRepoConfig.getRepoUrl().validate(validationResult);
        detectInvalidKeys(repoConfigs, validationResult, nuGetRepoConfig.getValidKeys());
        return validationResult;
    }

    private void detectInvalidKeys(Configuration configs, ValidationResult errors, String[] validKeys){
        for(Property config : configs.list()){
            boolean valid = false;
            for(String validKey : validKeys){
                if(validKey.equals(config.getKey())) {
                    valid = true; break;
                }
            }
            if(!valid) errors.addError(new ValidationError(String.format("Unsupported key: %s. Valid keys: %s", config.getKey(), Arrays.toString(validKeys))));
        }
    }

    public ValidationResult isPackageConfigurationValid(PackageConfiguration packageConfig, RepositoryConfiguration repoConfig) {
        NuGetPackageConfig nuGetPackageConfig = new NuGetPackageConfig(packageConfig);
        ValidationResult validationResult = new ValidationResult();
        if (nuGetPackageConfig.isPackageIdMissing()) {
            String message = "Package id not specified";
            LOGGER.info(message);
            validationResult.addError(new ValidationError(PACKAGE_ID, message));
            return validationResult;
        }
        String packageId = nuGetPackageConfig.getPackageId();
        if (packageId == null) {
            String message = "Package id is null";
            LOGGER.info(message);
            validationResult.addError(new ValidationError(PACKAGE_ID, message));
        }
        if (packageId != null && isBlank(packageId.trim())) {
            String message = "Package id is empty";
            LOGGER.info(message);
            validationResult.addError(new ValidationError(PACKAGE_ID, message));
        }
        if (packageId != null && (packageId.contains("*") || packageId.contains("?"))) {
            String message = String.format("Package id [%s] is invalid", packageId);
            LOGGER.info(message);
            validationResult.addError(new ValidationError(PACKAGE_ID, message));
        }
        detectInvalidKeys(packageConfig, validationResult, NuGetPackageConfig.getValidKeys());
        NuGetRepoConfig nuGetRepoConfig = new NuGetRepoConfig(repoConfig);
        if(!nuGetRepoConfig.isHttp() && nuGetPackageConfig.hasBounds()){
            String message = "Version constraints are only supported for NuGet feed servers";
            LOGGER.info(message);
            validationResult.addError(new ValidationError(message));
        }
        return validationResult;
    }

    public void testConnection(PackageConfiguration packageConfiguration, RepositoryConfiguration repositoryConfigurations) {
        try {
            new PollerImpl().getLatestRevision(packageConfiguration, repositoryConfigurations);
        } catch (Exception e) {
            String message = "Test Connection failed: " + e.getMessage();
            LOGGER.warn(message);
            throw new RuntimeException(message, e);
        }
    }
}
