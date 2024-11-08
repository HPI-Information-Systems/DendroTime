#!/usr/bin/env bash

java -Xmx48g -Dfile.encoding=UTF-8 \
    -Dlogback.configurationFile=logback.xml \
    -Dconfig.file=application.conf \
    -jar DendroTime-runner.jar $@
