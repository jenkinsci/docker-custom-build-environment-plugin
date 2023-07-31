Changelog
===

### Newer Versions

[See GitHub Releases](https://github.com/jenkinsci/docker-custom-build-environment-plugin/releases)

### 1.6.4

- Fixed a NPE for jobs created prior to 1.6.2 ([JENKINS-31220](https://issues.jenkins-ci.org/browse/JENKINS-31220))

### 1.6.3

- Fixed a regression introduced in 1.6.2, corrupting environment variables ([JENKINS-31166](https://issues.jenkins-ci.org/browse/JENKINS-31166))

### 1.6.2

- Support bridge 'net' flag ([commit](https://github.com/jenkinsci/docker-custom-build-environment-plugin/commit/8acce61132bca9d3c1c1838fe551dcbd8453decd))
- Do not append command if not set ([JENKINS-30692](https://issues.jenkins-ci.org/browse/JENKINS-30692)) ([#33](https://github.com/jenkinsci/docker-custom-build-environment-plugin/pull/33))
- Added an option to force pull the image
- Expose build wrappers contributed environment variables
- Ensure docker0 is up before trying to resolve it

### 1.6.1

- Use the Java API to lookup docker0 ip ([JENKINS-30512](https://issues.jenkins-ci.org/browse/JENKINS-30512)) ([#32](https://github.com/jenkinsci/docker-custom-build-environment-plugin/pull/32))
- Add buildwrapper environment variables to the docker context

### 1.6

- support maven job type
- expose dockerhost IP as "dockerhost" in /etc/host 
- configure container with a subset of build environment, as node environment doesn't make sense inside container.

### 1.5

- Option to configure volumes
- plugin now can run docker-in-docker and comparable advanced use-cases

### 1.4

- Support Node Properties environment variables (to define `DOCKER_HOST` per node for example)

### 1.3

- Support use of alternate Dockerfile
- Allows to run containers from the container hosting the build (see "Docker in Docker")
- Expose build container identifier as `BUILD_CONTAINER_ID` environment variable
- Code cleanup

### 1.2

- Initial release as "CloudBees Docker Custom Build Environment Plugin" (plugin was previously known as "*Oki-Docki*").
