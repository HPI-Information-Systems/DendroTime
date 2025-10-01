# DendroTime configuration

DendroTime exposes various parameters to control its execution and features as configuration options.
Configuration values can be set on the command line or using a configuration file:

- You can use `-D<config>` as a prefix to specify configuration options directly using the CLI, e.g.

  ```bash
  java -Ddendrotime.data-path=data/datasets -Ddendrotime.max-workers=1 -jar DendroTime-server.jar
  ```

- Alternatively, you can use a configuration file to overwrite selected configuration options.
  Example configuration file (`dendrotime.conf`):

  ```yaml
  dendrotime {
    data-path = data/datasets
    max-workers = 1
  }
  ```

  Then, execute DendroTime with `java -Dconfig.file=dendrotime.conf -Dfile.encoding=UTF-8 -jar DendroTime-server.jar`.

For a full description of all ways one can use and set configuration options, we refer to the [`lightbend/config` documentation](https://github.com/lightbend/config), which we make use of here.
You can find all parameters of DendroTime, their default values, and their description in the [`application.conf` file](/dendrotime-backend/src/main/resources/application.conf) bundled with DendroTime or [below](#default-configuration).

## Important options

### Folders and paths

DendroTime uses three folders to manage data and results:

| **Config** | **Default value** | **Description** |
| :--------- | :---------------- | :-------------- |
| `dendrotime.data-path` | data/datasets | Path to the data directory. Existing datasets are automatically added to the index |
| `dendrotime.ground-truth-path` | data/ground-truth | Path to the ground-truth directory. DendroTime uses the ground-truth data to evaluate the quality of the clustering results. |
| `dendrotime.results-path` | data/results | DendroTime stores the clustering results for each dataset in this directory. |

### Quality and convergence indicators

DendroTime provides three different convergence indicators that can all be turned off by setting the respective configuration option to `null`:

- **Hierarchy Similarity.**
  The hierarchy similarity indicator computes the similarity between the current and the previous hierarchy for every clustering step.
  It can be computed without any ground-truth data.

  _Options_: ariAt, labelChangesAt, averageAri, approxAverageAri, hierarchySimilarity, weightedHierarchySimilarity

  _Default_: labelChangesAt (_#CumulativeClusterChanges@k_)

- **Hierarchy Quality.**
  The hierarchy quality indicator computes the similarity between the current hierarchy and the final, exact hierarchy.
  This requires a ground-truth hierarchy (the final hierarchy) for each dataset.

  _Options_: ariAt, labelChangesAt, averageAri, approxAverageAri, hierarchySimilarity, weightedHierarchySimilarity

  _Default_: weightedHierarchySimilarity (_WHS_)

- **Cluster Quality.**
  The cluster quality indicator computes the Adjusted Rand Score (ARI) or the Adjusted Mutual Information (AMI) for the current hierarchy.
  It requires ground-truth class labels for each time series in the dataset and uses the observed number of clusters in the ground-truth classes as the target number of clusters (k).

  _Options_: ari, ami (not yet fully implemented!)

  _Default_: ari

## Default configuration

```yaml
dendrotime {
    # network configuration
    host = localhost
    port = 8080

    # Path to the data directory
    # Existing datasets are automatically added to the index. New datasets are also stored in this directory, when
    # uploaded using the web frontend.
    data-path = data/datasets

    # Path to the ground-truth directory
    # DendroTime uses the ground-truth data to evaluate the quality of the clustering results. Ground-truth data
    # includes the true cluster assignments as well as the final hierarchy. The ground-truth directory should
    # contain one subdirectory for each dataset with the different hierarchies (for distance and linkage methods)
    # and the true cluster assignments.
    ground-truth-path = data/ground-truth

    # Path to the results directory
    # The results directory contains the clustering results for each dataset. The results are stored in the
    # same format as the ground-truth data.
    results-path = data/results

    # Enable result storing
    # If enabled, the clustering results are stored in the results directory.
    store-results = no
    # If enabled, the approximated and exact distance matrices are stored in the results directory.
    store-distances = no

    # Limit the number of time series that are loaded from each dataset. Just for debugging and scaling experiments.
    # max-timeseries = 5

    ask-timeout = 5s

    max-workers = 4

    reporting-interval = 30s

    # Adaptive batching
    # The systems tries to batch multiple distance computations together to reduce the overhead of the actor system.
    # The batch size is calculated based on the target time and the number of distances that are currently in the queue.
    # DendroTime uses EWMA to estimate the computation time for one distance calculation and adjusts the batch size
    # accordingly. The batch size also affects the resolution of the progress indicators.
    batching {
        # The target time specifies the maximum time that a batch should take to be processed. Larger values reduce the
        # overhead but also the responsiveness of the system.
        target-time = 500ms

        # Maximum batch size for the adaptive batching. This allows to limit the number of distances that are processed
        # in one batch, and, thus, forces a certain resolution of the progress indicators. It is recommended to leave
        # this option commented out (use no maximum batch size and only the target time).
        # max-batch-size = 100
    }

    bloom-filter {
        # Number of bits used in the calculation of the MurmurHash3 hash functions for the bloom filter. You can
        # chose between 64bit and 128bit.
        murmurhash-size = 64
        # Controls the size of the bloom filter used to compare hierarchies. Lower values increase memory usage but
        # reduce the false positive rate.
        false-positive-rate = 0.01
    }

    progress-indicators {
        # The progress indicators are used to display the progress of the clustering process in the frontend. All
        # indicators can be disabled by commenting out the respective line or removing it.

        # The hierarchy similarity indicator computes the similarity between the current and the previous hierarchy
        # for every clustering step. It can be computed without any ground-truth data.
        hierarchy-similarity = labelChangesAt
        # The hierarchy quality indicator computes the similarity between the current hierarchy and the final
        # exact hierarchy. This requires a ground-truth hierarchy (the final hierarchy) for each dataset.
        hierarchy-quality = weightedHierarchySimilarity
        # The cluster quality indicator computes the Adjusted Rand Score (ARI) or the Adjusted Mutual Information (AMI)
        # for the current hierarchy. It requires ground-truth class labels for each time series in the dataset and uses
        # the observed number of clusters in the ground-truth classes as the target number of clusters (k).
        # Options: ari, ami
        cluster-quality = "ari"

        # Example configurations for quality measures between two hierarchies (either hierarchy-similarity or hierarchy-quality):
        ariAt {
            # Uses the Adjusted Rand Index (ARI) at a specific hierarchy cut.
            method = "ariAt"
            # Set k to the desired number of clusters.
            k = 10
        }
        amiAt {
            # Uses the Adjusted Mutual Information (AMI) at a specific hierarchy cut.
            method = "amiAt"
            # Set k to the desired number of clusters.
            k = 10
        }
        labelChangesAt {
            # Computes the number of label changes at a specific hierarchy cut.
            method = "labelChangesAt"
            # Per default, the quality is computed for k = hierarchy depth / 2 (cutting the hierarchy in the middle).
            # In this case, k needs to be omitted. If you want to compute the quality for a specific number of clusters,
            # set k to the desired value.
            # k = 10
        }
        averageAri {
            # Computes the average ARI for all possible numbers of clusters between this hierarchy and a target
            # hierarchy. This is very costly and should only be used for small hierarchies/datasets.
            method = "averageAri"
        }
        approxAverageAri {
            # Approximates the average ARI between this hierarchy and a target hierarchy by computing the ARI for a
            # subset of cluster numbers. The subset is determined by the `factor` parameter. The ARI is computed the
            # cluster numbers starting from 2 and increasing by a factor of `factor` until the maximum number of
            # clusters is reached. A higher `factor` will result in fewer ARI computations and thus faster but less
            # precise computation. A factor of 1.0 will compute the ARI for all possible numbers of clusters.
            method = "approxAverageAri"
            factor = 1.3
        }
        hierarchySimilarity {
            # Computes the Jaccard similarity between this hierarchy and another hierarchy based on representing each
            # cluster by its contained time series. The similarity is computed as the Jaccard similarity between the
            # sets of cluster representations (either bitsets or bloom filters).
            method = "hierarchySimilarity"
            # Per default uses bloom filters to represent the hierarchy nodes. This is more memory efficient but
            # can lead to false positives. If you want to use the exact node representation (BitSets), set this
            # option to false.
            use-bloom-filters = yes
            # Limit the similarity comparison to clusters/nodes with at least this number of members.
            # Set to 0 to disable.
            cardinality-lower-bound = 3
            # Limit the similarity comparison to clusters/nodes with at most N - this number of members.
            # Set to 0 to disable. 1 ignores the root node that includes all members (highly recommended).
            cardinality-upper-bound = 1
        }
        weightedHierarchySimilarity {
            # Computes the weighted similarity between this hierarchy and another hierarchy.
            # The weighted similarity is the average Jaccard similarity between all clusters of the two hierarchies.
            # The cluster matches are chosen greedily to maximize the similarity.
            method = "weightedHierarchySimilarity"
            # Per default uses bloom filters to represent the hierarchy nodes. This is more memory efficient but
            # can lead to false positives. If you want to use the exact node representation (BitSets), set this
            # option to false.
            use-bloom-filters = yes
        }

        # Initial delay before the ground truth information is loaded. This improves startup performance.
        ground-truth-loading-delay = 100ms
        # If enabled, prints progress information to the console. This could be quite verbose!
        stdout = off
    }

    # Configuration for the different time series distance measures
    distances {
        msm {
            cost = 0.5
            window = 0.05
            itakura-max-slope = NaN
        }
        sbd {
            standardize = no
            local-fftw-cache-size = 100
        }
        dtw {
            window = 0.05
            itakura-max-slope = NaN
        }
        # Minkowsky distance defaults to Euclidean distance
        minkowsky {
            p = 2
        }
        lorentzian {
            normalize = no
        }
        kdtw {
            gamma = 1.0
            epsilon = 1e-20
            # Input normalization is necessary to ensure numerical stability of the distance measure. Just disable it if
            # you know what you are doing.
            normalize-input = yes
            # Distance normalization is expensive because it involves computing the O(n²) self-distance for both input
            # time series x and y. This means that the distance calculation is executed three times.
            normalize-distance = yes
        }
        # Absolute number of points of each time series that are used to approximate the pairwise distance.
        approx-length = 20
    }

    # dispatchers [...]
}

# akka [...]
```
