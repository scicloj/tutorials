(require '[alembic.still :refer [distill]])
(distill '[kixi/stats "0.5.0"])

(ns drafts.clj-example
  (:require [kixi.stats.core :as stats]))

(comment
  (->> (range 9)
       (transduce identity stats/mean))
  ;; => 4.0
)
