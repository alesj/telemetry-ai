#!/bin/bash -v

mvn --projects :telemetry-ai-app --also-make $*
