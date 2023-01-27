#!/bin/sh
set -e

# This hack is needed because GitHub Actions will only pass the env vars through the command line.
# We assume that they are in the following order:
# B2_APP_KEY_ID, B2_APP_KEY, CF_KEY

B2_APP_KEY_ID=$1 B2_APP_KEY=$2 CF_KEY=$3 java -jar /app/app.jar