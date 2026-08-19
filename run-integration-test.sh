#!/usr/bin/env bash
set -euo pipefail

mvn clean install -DskipTests -pl common
mvn clean test -pl ai -Dintegration.run=true -Dtest=FullIntegrationTest
