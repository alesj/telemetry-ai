#!/usr/bin/env bash
set -euo pipefail

mvn install -DskipTests -pl common
mvn test -pl ai -Dintegration.run=true -Dtest=FullIntegrationTest
