#!/bin/bash -v

mvn --projects :telemetry-ai-core --also-make $*
