# Experiments

This directory contains the scripts and configuration to perform the experiments in the paper.

Start the DendroTime runner with the following command:

```bash
# optional:
#timeout --signal=15 1h \
java -Xmx2g -Dconfig.file="application.conf" \
  -Dlogback.configurationFile="logback.xml" \
  -Dfile.encoding=UTF-8 \
  -jar DendroTime-runner.jar --dataset "Coffee"
```
