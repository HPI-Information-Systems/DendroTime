from __future__ import annotations

from typing import Any, Optional

import numpy as np
from scipy.cluster.hierarchy import cut_tree, linkage
from sklearn.base import BaseEstimator, ClusterMixin


class LinkageClustering(BaseEstimator, ClusterMixin):
    def __init__(
        self,
        n_clusters: int,
        linkage: str = "single",
        n_jobs: int = 1,
        verbose: bool = False,
    ) -> None:
        super().__init__()

        self.n_clusters = n_clusters
        self.linkage = linkage
        self.n_jobs = n_jobs
        self.verbose = verbose

    def fit(self, X: np.ndarray, y: Optional[np.ndarray] = None) -> LinkageClustering:
        self._linkage_matrix = linkage(X, method=self.linkage)
        return self

    def predict(self, X: Optional[Any] = None) -> np.ndarray:
        return cut_tree(self._linkage_matrix, n_clusters=self.n_clusters).flatten()

    def fit_predict(self, X: np.ndarray, y: Optional[np.ndarray] = None) -> np.ndarray:
        return self.fit(X).predict(X)
