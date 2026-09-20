#!/bin/bash -v

./mvnw --projects :telemetry-ai-app --also-make $*
