(ns ols.svr
  (:require [notespace.api :as notespace]
            [notespace.kinds :as kind]
            [tech.v3.dataset :as ds]
            [tech.v3.datatype.functional :as dfn]
            [tech.v3.dataset.categorical :as categorical]
            [tech.v3.dataset.math :as dsm]
            [tech.v3.libs.smile.data :as ds-smile])
  (:import (smile.math.kernel GaussianKernel)
           (smile.base.svm SVR KernelMachine)
           (smile.validation RSS)
           (smile.data.formula Formula)
           (smile.regression OLS LinearModel)
           (smile.data DataFrame)))


^kind/hidden
(comment
  ;; Manually start an empty notespace, and open the browser view
  (notespace/init-with-browser)

  ;; Clear an existing notespace (and the browser view)
  (notespace/init)

  ;; Evaluate a whole notespace (updating the browser view)
  (notespace/eval-this-notespace)

  ;; Rended for static html view
  (notespace/render-static-html))

["# Multiple factor OLS and Support Vector regression with tech.ml.dataset and smile"]

["The purpose of this notebook is to show a basic machine learning pipeline with tech.ml.dataset."]
["We take an anonymized dataset of bonds across countries and sectors, with their durations, ratings and spreads. We are trying to infer the spread from the other four factors. We will use two OLS models and one support vector regression model. Note that rating and duration are continuous variables, whereas country and sector are categorical variables."]

["Note we are using smile Java interop. There's a \"native\" Clojure implementation but more doc for the Java version, hence using that one.\n"]


(comment
  ;; we'll be calling some Java methods
  ;; commenting this out, since it fails as a notespace note
  ;; (Can't set!: *warn-on-reflection* from non-binding thread)
  (set! *warn-on-reflection* true))

["## Model definitions"]
["The source data columns are `[:Bond :Used_Duration :Used_Rating_Score :Country :Sector]`. The target variable is called `:Used_ZTW` (z-spread to worst). The three models we run are:"
"* legacy: `log(Used_ZTW) = a.Used_Duration + b.Used_Rating_Score + categorical variables`"
 "* new: `log(Used_ZTW) = a.log(Used_Duration) + b.Used_Rating_Score + categorical variables`"
 "* svr: `Used_ZTW = SVR(Used_Duration, Used_Rating_Score, Country, Sector)`"]

(def qm (ds/->dataset "resources/bonds.csv" {:key-fn keyword}))
(ds/head qm)

["For each model, we define which columns are categorical and need one-hot treatment, which need standard scaling, and which transformations will be applied to the data."]

(defn legacy-model-definition
  [dataset]
  {:dataset         dataset
   :id-name         :Bond
   :y               {:column :Used_ZTW :predict-fn dfn/exp}
   :transformations [{:column :Used_ZTW :assoc-fn #(dfn/log (% :Used_ZTW))}]
   :one-hot         {:columns [:Country :Sector] :removals [:Country-BH :Sector-Fvanapvny]}
   :std-scale       {:columns [:Used_Duration :Used_Rating_Score] :scaler nil}})

(defn new-model-definition
  [dataset]
  {:dataset         dataset
   :id-name         :Bond
   :y               {:column :Used_ZTW :predict-fn dfn/exp}
   :transformations [{:column :Used_ZTW          :assoc-fn #(dfn/log (% :Used_ZTW))}
                     {:column :Used_Duration     :assoc-fn #(dfn/log (% :Used_Duration))}]
   :one-hot         {:columns [:Country :Sector] :removals [:Country-BH :Sector-Fvanapvny]}
   :std-scale       {:columns [:Used_Duration :Used_Rating_Score] :scaler nil}})

(defn svr-model-definition
  [dataset]
  {:dataset         dataset
   :id-name         :Bond
   :y               {:column :Used_ZTW :predict-fn identity}
   :one-hot         {:columns [:Country :Sector] :removals [:Country-BH :Sector-Fvanapvny]}
   :std-scale       {:columns [:Used_Duration :Used_Rating_Score] :scaler nil}})


["## Preparing the data for smile processing"]

["We define a function that can create new columns into a dataset either from another column or from a row-by-row operation. We also define a reducer so we can apply several transformations at once."]

(defn build-column
  [dataset line]
  (if (contains? line :assoc-fn)
    (assoc dataset (:column line) ((:assoc-fn line) dataset))
    (ds/column-map dataset (:column line) (:map-fn line) nil (:arg-cols line))))

(defn build-many-columns [dataset features]
  (reduce build-column dataset features))

["We define a one-hot reducer than can remove columns - this can be important for OLS models with small amount of features to avoid collinearity"]

(defn one-hot-reducer
  [source-dataset cols removals]
  (ds/remove-columns
    (reduce (fn [dataset col] (categorical/transform-one-hot dataset (categorical/fit-one-hot dataset col))) source-dataset cols)
    removals))

["We now are ready to prepare the data. There are several steps here:"
 "* transform the data"
 "* scale columns and save the scaling function so it can be reused later when predicting"
 "* apply one-hot transformation to categorical variables"
 "* get the data into a shape that's acceptable for smile."
 ]

["smile needs to receive double arrays and double double arrays as inputs. We also need to calculate `sigma` for the support vector regression. We will match the default in `scikit-learn` (more on that in the svr fit function). Finally, it is very important to keep a memory of the order of the features so we can use it to predict scalars later."]

(defn prepare-data
  "model-type is :ols or :svr"
  [model-definition model-type]
  (let [transformed (build-many-columns (:dataset model-definition) (:transformations model-definition))
        scaler (dsm/fit-std-scale (ds/select-columns transformed (get-in model-definition [:std-scale :columns])))
        clean-data (-> transformed
                       (one-hot-reducer (get-in model-definition [:one-hot :columns]) (get-in model-definition [:one-hot :removals]))
                       (dsm/transform-std-scale scaler))
        features-ds (ds/remove-columns clean-data [(:id-name model-definition) (get-in model-definition [:y :column])])]
    (assoc model-definition
      :std-scale      (assoc (:std-scale model-definition) :scaler scaler)
      :features-cols  (ds/column-names features-ds) ;we need to know the order to predict later
      :ols-formula    (if (= :ols model-type) (Formula/lhs (name (get-in model-definition [:y :column])))) ; need a name here, keyword will fail
      :dataframe      (if (= :ols model-type) (ds-smile/dataset->dataframe (ds/remove-column clean-data (:id-name model-definition))))
      :sigma          (if (= :svr model-type) (Math/sqrt (* 0.5 (ds/column-count features-ds) (dfn/variance (vec (flatten (ds/value-reader features-ds)))))))
      :features-array (if (= :svr model-type) (.toArray (ds-smile/dataset->dataframe features-ds)))
      :y-array        (if (= :svr model-type) (.array (ds-smile/column->smile-column (clean-data (get-in model-definition [:y :column]))))))))


["## Preparing the data for smile processing"]

["Training an OLS model is easy - we add the predictions and the R2."]

(defn ols-full-training
  [data]
  (let [ols (OLS/fit (:ols-formula data) (:dataframe data))]
    (merge data
           {:ols ols
            :predictions ((get-in data [:y :predict-fn]) (vec (.predict ^LinearModel ols ^DataFrame (:dataframe data))))
            :rsq (.RSquared ^LinearModel ols)})))

["Training the support vector regression model is slightly more tricky and will be done in two steps. The first function fits the model, returning a Kernel which we can then use to predict. Note smile uses `sigma` for the RBF Kernel whereas `scikit-learn` uses `gamma`, but they're equivalent."]

(defn fit-RBF-SVR [x y sigma eps C]
  "This is the SVR fit function, returning a smile.base.svm.KernelMachine which we can use to predict.
  x id a double double array, y is a double array, the rest are scalars.
  tol = 0.01 is irrelevant for the RBF (= Gaussian) kernel.
  sigma tunes the Kernel. The Python sklearn default is gamma = 'scale' where gamma = 1 / (2 * sigma^2)
  'scale' means gamma = 1 / (n_features * x.var()) hence sigma = sqrt(0.5 * n_features * x.var()) where x is flattened"
  (.fit (SVR. (GaussianKernel. sigma) eps C 0.01) x y))

["This is the training function."]

(defn svr-full-training [data eps C]
  ;todo find out why I'm still getting a Reflection warning: call to method predict on smile.base.svm.KernelMachine can't be resolved (no such method)
  (let [kernel-machine (fit-RBF-SVR (:features-array data) (:y-array data) (:sigma data) eps C)
        raw-predictions (.predict ^KernelMachine kernel-machine (:features-array data))
        rss (RSS/of (:y-array data) raw-predictions)]
    (merge data
           {:kernel-machine kernel-machine
            :predictions ((get-in data [:y :predict-fn]) (vec raw-predictions))
            :rsq (- 1 (/ rss (dfn/distance-squared (:y-array data) (repeat 0.))))})))


["## Scalar predictor functions"]

["The order of the data needs to match the order of the training data and *we need the intercept first, set at 1.0*. Note there's an ugly hack here in the ols predictor - normalizer is set to use log for the new model."]

(defn ols-predict-scalar [data xmap]
  (letfn [(normalizer [id log?] (let [{m :mean s :standard-deviation} (get-in data [:std-scale :scaler id])]
                                  (/ (- (if log? (Math/log (xmap id)) (xmap id)) m) s)))]
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
  ;todo still getting a Reflection warning: call to method predict on smile.base.svm.KernelMachine can't be resolved (no such method)
  (letfn [(normalizer [id] (let [{m :mean s :standard-deviation} (get-in data [:std-scale :scaler id])]
                             (/ (- (xmap id) m) s)))]
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


["## Putting it all together"]

["Piping is very natural with all functions taking dataset first."]

(defn get-legacy-model-output
  [dataset]
  (-> dataset
      (legacy-model-definition)
      (prepare-data :ols)
      (ols-full-training)))

(defn get-new-model-output
  [dataset]
  (-> dataset
      (new-model-definition)
      (prepare-data :ols)
      (ols-full-training)))

(defn get-svr-model-output
  [dataset]
  (-> dataset
      (svr-model-definition)
      (prepare-data :svr)
      (svr-full-training 0.05 1)))

(def legacymodel (get-legacy-model-output qm))
(def newmodel (get-new-model-output qm))
(def svrmodel (get-svr-model-output qm))


["Let's add columns with all predictions to the original dataset."]

(def res (assoc qm :legacy (:predictions legacymodel) :new (:predictions newmodel) :svr (:predictions svrmodel)))
(ds/head res)

["It's now easy to look at the predictions for any one bond in the dataset."]
(ds/filter-column res :Bond "Bond-42")

["We can also try and predict the spread for any bond."]
(ols-predict-scalar legacymodel {:Used_Duration 1.16 :Used_Rating_Score 6.0 :Country "BH" :Sector "Ovy_naq_Gnf"})
(ols-predict-scalar newmodel {:Used_Duration 1.16 :Used_Rating_Score 6.0 :Country "BH" :Sector "Ovy_naq_Gnf"})
(svr-predict-scalar svrmodel {:Used_Duration 1.16 :Used_Rating_Score 6.0 :Country "BH" :Sector "Ovy_naq_Gnf"})

["## END"]
