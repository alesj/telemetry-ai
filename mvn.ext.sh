#!/bin/bash -v

./mvnw --projects :telemetry-ai-ext --also-make $*
