#!/usr/bin/env bash

# Execute DendroTime (without frontend) with the given arguments.
# Allows connecting to the JVM with JMX on port 9010 (e.g. using VisualVM for profiling)
java -Xmx48g -Dfile.encoding=UTF-8 \
    -Dlogback.configurationFile=logback.xml \
    -Dconfig.file=common.conf \
    -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9010 \
    -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false \
    -jar DendroTime-runner.jar $@
