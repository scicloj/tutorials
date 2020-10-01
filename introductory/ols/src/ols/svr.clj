(ns ols.svr
  (:require [tech.ml.dataset :as ds]
            [tech.v2.datatype.functional :as dfn]
            [tech.ml.dataset.pipeline :as ds-pipe]
            [tech.libs.smile.data :as ts])
  (:import (smile.math.kernel GaussianKernel)
           (smile.base.svm SVR KernelMachine)
           (smile.validation RSS)
           (smile.data.formula Formula)
           (smile.regression OLS LinearModel)
           (smile.data DataFrame)))

;Note we are using smile Java interop.
;There's a "native" Clojure implementation but more doc for the Java version, hence using that one.
;We'll be spending as much time as possible within Clojure / tech.ml, and calling smile only for the actual regression.
(set! *warn-on-reflection* true)                            ;we'll be calling some Java methods

;;;;;;;;;;;;;;;;;;;;;;;;;
;;; MODEL DEFINITIONS ;;;
;;;;;;;;;;;;;;;;;;;;;;;;;

;The source data columns are [:Bond :Used_Duration :Used_Rating_Score :Country :Sector]

(defn legacy-model-definition
  "log(Used_ZTW) = a.Used_Duration + b.Used_Rating_Score + categorical variables"
  [dataset]
  {:dataset         dataset
   :id-name         :Bond
   :y               {:column :Used_ZTW :predict-fn #(Math/exp %)}
   :transformations [{:column :Used_ZTW :fn #(dfn/log (ds-pipe/col))}]
   :one-hot         {:columns [:Country :Sector] :removals [:Country-BH :Sector-Fvanapvny]}
   :std-scale       {:columns [:Used_Duration :Used_Rating_Score] :args {:Used_Duration     [(dfn/mean (dataset :Used_Duration)) (dfn/standard-deviation (dataset :Used_Duration))]
                                                                         :Used_Rating_Score [(dfn/mean (dataset :Used_Rating_Score)) (dfn/standard-deviation (dataset :Used_Rating_Score))]}}})

(defn new-model-definition
  "log(Used_ZTW) = a.log(Used_Duration) + b.Used_Rating_Score + categorical variables"
  [dataset]
  {:dataset         dataset
   :id-name         :Bond
   :y               {:column :Used_ZTW :predict-fn #(Math/exp %)}
   :transformations [{:column :Used_ZTW          :fn #(dfn/log (ds-pipe/col))}
                     {:column :Used_Duration     :fn #(dfn/log (ds-pipe/col))}]
   :one-hot         {:columns [:Country :Sector] :removals [:Country-BH :Sector-Fvanapvny]}
   :std-scale       {:columns [:Used_Duration :Used_Rating_Score] :args {:Used_Duration nil
                                                                         :Used_Rating_Score [(dfn/mean (dataset :Used_Rating_Score)) (dfn/standard-deviation (dataset :Used_Rating_Score))]}}})

(defn svr-model-definition
  "Used_ZTW = SVR(Used_Duration, Used_Rating_Score, Country, Sector)"
  [dataset]
  {:dataset         dataset
   :id-name         :Bond
   :y               {:column :Used_ZTW :predict-fn identity}
   :one-hot         {:columns [:Country :Sector] :removals [:Country-BH :Sector-Fvanapvny]}
   :std-scale       {:columns [:Used_Duration :Used_Rating_Score] :args {:Used_Duration [(dfn/mean (dataset :Used_Duration)) (dfn/standard-deviation (dataset :Used_Duration))]
                                                                         :Used_Rating_Score [(dfn/mean (dataset :Used_Rating_Score)) (dfn/standard-deviation (dataset :Used_Rating_Score))]}}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; PREPARING THE DATA FOR smile PROCESSING ;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ols-prepare-data
  "The scaling parameters are added here for features that had been transformed."
  [model-definition]
  (let [transformed (reduce (fn [dataset line] (ds-pipe/m= dataset (:column line) (:fn line))) (:dataset model-definition) (:transformations model-definition))
        transformed-scaling (into {} (for [k (get-in model-definition [:std-scale :columns]) :when (some #{k} (map :column (:transformations model-definition)))]
                                       [k [(dfn/mean (transformed k)) (dfn/standard-deviation (transformed k))]]))
        clean-data (-> transformed
                       (ds-pipe/one-hot (get-in model-definition [:one-hot :columns]))
                       (ds/remove-columns (get-in model-definition [:one-hot :removals]))
                       (ds-pipe/std-scale (get-in model-definition [:std-scale :columns])))
        features-ds (ds/remove-columns clean-data [(:id-name model-definition) (get-in model-definition [:y :column])])]
    (assoc model-definition
      :std-scale      (assoc (:std-scale model-definition) :args (merge (get-in model-definition [:std-scale :args]) transformed-scaling))
      :ols-formula    (Formula/lhs (name (get-in model-definition [:y :column]))) ; warning - you need a name here, keyword will fail
      :dataframe      (ts/dataset->dataframe (ds/remove-column clean-data (:id-name model-definition)))
      :features-cols  (ds/column-names features-ds))))          ;we need to know the order

(defn svr-prepare-data
  "Note SVR wants Java arrays"
  [model-definition]
  (let [clean-data (-> (:dataset model-definition)
                       (ds-pipe/one-hot (get-in model-definition [:one-hot :columns]))
                       (ds/remove-columns (get-in model-definition [:one-hot :removals]))
                       (ds-pipe/std-scale (get-in model-definition [:std-scale :columns])))
        features-ds (ds/remove-columns clean-data [(:id-name model-definition) (get-in model-definition [:y :column])])]
    (assoc model-definition
      :features-cols  (ds/column-names features-ds)          ;we need to know the order
      :sigma          (Math/sqrt (* 0.5 (ds/column-count features-ds) (dfn/variance (flatten (ds/value-reader features-ds)))))
      :features-array (.toArray (ts/dataset->dataframe features-ds))
      :y-array        (.array (ts/column->smile (clean-data (get-in model-definition [:y :column])))))))


;;;;;;;;;;;;;;;;;;;;;;
;;; smile TRAINING ;;;
;;;;;;;;;;;;;;;;;;;;;;

(defn ols-full-training
  "Putting it all together, the model / the predictions and the R2"
  [data]
  (let [ols (OLS/fit (:ols-formula data) (:dataframe data))]
    (merge data {:ols ols :predictions (map (get-in data [:y :predict-fn]) (seq (.predict ^LinearModel ols ^DataFrame (:dataframe data)))) :rsq (.RSquared ^LinearModel ols)})))

(defn fit-RBF-SVR [x y sigma eps C]
  "This is the SVR fit function, returning a smile.base.svm.KernelMachine which we can use to predict.
  x id a double double array, y is a double array, the rest are scalars.
  tol = 0.01 is irrelevant for the RBF (= Gaussian) kernel.
  sigma tunes the Kernel. The Python sklearn default is gamma = 'scale' where gamma = 1 / (2 * sigma^2)
  'scale' means gamma = 1 / (n_features * x.var()) hence sigma = sqrt(0.5 * n_features * x.var()) where x is flattened"
  (.fit (SVR. (GaussianKernel. sigma) eps C 0.01) x y))

(defn svr-full-training [data eps C]
  "Putting it all together, the model / the predictions and the R2"
  ;todo find out why I'm still getting a Reflection warning: call to method predict on smile.base.svm.KernelMachine can't be resolved (no such method)
  (let [kernel-machine (fit-RBF-SVR (:features-array data) (:y-array data) (:sigma data) eps C)
        raw-predictions (.predict ^KernelMachine kernel-machine (:features-array data))
        rss (RSS/of (:y-array data) raw-predictions)]
    (merge data {:kernel-machine kernel-machine :predictions (map (get-in data [:y :predict-fn]) (seq raw-predictions)) :rsq (- 1 (/ rss (dfn/sum-of-squares (:y-array data))))})))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; SCALAR PREDICTOR FUNCTIONS ;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ols-predict-scalar [data xmap]
  "The order of the data needs to match the order of the training data AND we need the intercept first, set at 1.0"
  (letfn [(normalizer [id log?] (let [[m s] (get-in data [:std-scale :args id])] (/ (- (if log? (Math/log (xmap id)) (xmap id)) m) s)))]
    ((get-in data [:y :predict-fn])
     (.predict
       ^LinearModel (:ols data)
       (double-array
         (into [1.0] (for [c (:features-cols data)]          ;into [1.0] is the intercept. It's not obvious!!!
                       (condp = c
                         :Used_Duration (normalizer :Used_Duration (some #{:Used_Duration} (map :column (:transformations data))))
                         :Used_Rating_Score (normalizer :Used_Rating_Score false)
                         (keyword (str "Country-" (xmap :Country))) 1.0
                         (keyword (str "Sector-" (xmap :Sector))) 1.0
                         0.0))))))))

(defn svr-predict-scalar [data xmap]
  "The order of the data needs to match the order of the training data."
  ;todo find out why I'm still getting a Reflection warning: call to method predict on smile.base.svm.KernelMachine can't be resolved (no such method)
  (letfn [(normalizer [id] (let [[m s] (get-in data [:std-scale :args id])] (/ (- (xmap id) m) s)))]
    ((get-in data [:y :predict-fn])
     (.predict
       ^KernelMachine (:kernel-machine data)
       (double-array
         (into [] (for [c (:features-cols data)]
                    (condp = c
                      :Used_Duration (normalizer :Used_Duration)
                      :Used_Rating_Score (normalizer :Used_Rating_Score)
                      (keyword (str "Country-" (xmap :Country))) 1.0
                      (keyword (str "Sector-" (xmap :Sector))) 1.0
                      0.0))))))))

;;;;;;;;;;;;;;;;;;;;;;
;;; ACTUALLY DO IT ;;;
;;;;;;;;;;;;;;;;;;;;;;

(defn get-svr-model-output [dataset]                           ;(pull-quant-model)
  (-> dataset
      (svr-model-definition)
      (svr-prepare-data)
      (svr-full-training 0.05 1000)))

(defn get-legacy-model-output [dataset]
  (-> dataset
      (legacy-model-definition)
      (ols-prepare-data)
      (ols-full-training)))

(defn get-new-model-output [dataset]
  (-> dataset
      (new-model-definition)
      (ols-prepare-data)
      (ols-full-training)))


(def qm (ds/->dataset "resources/bonds.csv" {:key-fn keyword}) )
;This is an anonymized dataset of bonds across countries and sectors, with their durations, rating and spread.
;We are trying to infer the spread (Used_ZTW = z-spread to worst)

(def svrmodel (get-svr-model-output qm))

(def newmodel (get-new-model-output qm))

(def legacymodel (get-legacy-model-output qm))

(def res (assoc qm :legacy (:predictions legacymodel) :new (:predictions newmodel) :svr (:predictions svrmodel)))

(ds/filter-column "Bond-42" :Bond res)
;|   :Bond |     :Sector | :Country | :Used_Duration | :Used_Rating_Score | :Used_ZTW | :legacy |  :new |  :svr |
;|---------|-------------|----------|----------------|--------------------|-----------|---------|-------|-------|
;| Bond-42 | Ovy_naq_Gnf |       BH |           1.16 |                6.0 |      75.2 |   118.0 | 103.1 | 75.15 |

(ols-predict-scalar legacymodel {:Used_Duration 1.16 :Used_Rating_Score 6.0 :Country "BH" :Sector "Ovy_naq_Gnf"})
;=> 118.01535809799998

(ols-predict-scalar newmodel {:Used_Duration 1.16 :Used_Rating_Score 6.0 :Country "BH" :Sector "Ovy_naq_Gnf"})
;=> 103.1329755725273

(svr-predict-scalar svrmodel {:Used_Duration 1.16 :Used_Rating_Score 6.0 :Country "BH" :Sector "Ovy_naq_Gnf"})
;=> 75.14990884257375
