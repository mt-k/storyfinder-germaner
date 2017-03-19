#!/bin/bash

# Load and build germaner
cd /germaner-src/src/
git clone https://github.com/tudarmstadt-lt/GermaNER.git
cd /germaner-src/src/GermaNER/germaner
cp /germaner-src/src/patch/build.xml /germaner-src/src/GermaNER/germaner/

mvn clean install -Drat.numUnapprovedLicenses=100 -Dmaven.test.skip=true
ant

# Build server resource

mkdir -p /usr/src/germaner/tmp/
cd /germaner-src/src/server
cp ../GermaNER/germaner/target/germanner-jar-with-dependencies.jar ./resources
mvn clean install -Drat.numUnapprovedLicenses=100 -Dmaven.test.skip=true
ant

# Build server

cd /germaner-src
ant

# Load resources

mkdir /germaner-src/resources
cd /germaner-src/resources
wget "https://github.com/tudarmstadt-lt/GermaNER/releases/download/germaNER0.9.1/config.properties"
wget "https://github.com/tudarmstadt-lt/GermaNER/releases/download/germaNER0.9.1/data.zip"