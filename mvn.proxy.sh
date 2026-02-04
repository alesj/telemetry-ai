#!/bin/bash -v

mvn --projects :telemetry-ai-proxy --also-make $*
