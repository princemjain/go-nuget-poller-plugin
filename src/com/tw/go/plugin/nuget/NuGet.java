package com.tw.go.plugin.nuget;

import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.material.packagerepository.PackageRevision;
import com.tw.go.plugin.nuget.exe.NuGetCmdOutput;
import com.tw.go.plugin.nuget.exe.ProcessRunner;

import static java.lang.String.format;

public class NuGet {
    private final ProcessRunner processRunner;
    private static Logger LOGGER = Logger.getLoggerFor(NuGet.class);
    private final NuGetParams params;

    public NuGet(NuGetParams params) {
        this(new ProcessRunner(), params);
    }

    //for tests
    public NuGet(ProcessRunner processRunner, NuGetParams params) {
        this.processRunner = processRunner;
        this.params = params;
    }

    public PackageRevision poll() {
        if (!params.isHttp()) return nugetexe(null);
        try {
            return pollByAPI();
        } catch (NuGetException apiFail) {
            throw apiFail;
        } catch (RuntimeException apiError) {
            return nugetexe(apiError);
        }
    }

    private PackageRevision nugetexe(RuntimeException apiError) {
        if(params.eitherBoundGiven())
            throw new RuntimeException(
                    getErrorMessage(
                            apiError,
                            String.format("Polling with version constraints (%s) not supported via nuget.exe", params.getPackageAndVersion())));
        String[] command = getCommand();
        NuGetCmdOutput nuGetCmdOutput;
        synchronized (params.getRepoId().intern()) {
            nuGetCmdOutput = processRunner.execute(command, params.isHttp());
        }
        if (nuGetCmdOutput.isSuccess()) {
            nuGetCmdOutput.validateAndParse(params);
            return nuGetCmdOutput.getPackageRevision(params.getRepoUrl());
        }
        LOGGER.info(nuGetCmdOutput.getErrorDetail());
        throw new RuntimeException(
                getErrorMessage(apiError,
                        String.format("nuget.exe: Error while querying repository with path '%s' and packageId '%s'. %s",
                params.getRepoUrlStr(), params.getPackageId(), nuGetCmdOutput.getErrorSummary())));
    }

    private String[] getCommand() {
        if(params.shoudIncludePreRelease())
        return new String[]{"nuget", "list", params.getPrefixedPackageId(),
                "-Verbosity", "detailed", "-Prerelease","-Source", params.getRepoUrlStr()};
        return new String[]{"nuget", "list", params.getPrefixedPackageId(),
                    "-Verbosity", "detailed", "-Source", params.getRepoUrlStr()};
    }

    private PackageRevision pollByAPI() {
        String url = params.getQuery();
        LOGGER.info(url);
        return new NuGetFeedDocument(new Feed(url).download()).getPackageRevision(params.isLastVersionKnown());
    }

    private String getErrorMessage(RuntimeException apiError, String msg) {
        StringBuilder sb = new StringBuilder();
        if(apiError != null){
            sb.append("Falling back to nuget.exe after API error: ").
                    append(apiError.getClass().getSimpleName()).append(": ").append(apiError.getMessage()).append("\n");
        }
        sb.append(msg);
        return sb.toString();
    }

}