#!/bin/bash -v

./mvnw --projects :telemetry-ai-core --also-make $*
