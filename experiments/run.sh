#!/usr/bin/env bash

java -Xmx48g -Dfile.encoding=UTF-8 \
    -Dlogback.configurationFile=logback.xml \
    -Dconfig.file=application.conf \
    -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9010 \
    -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false \
    -jar DendroTime-runner.jar $@
