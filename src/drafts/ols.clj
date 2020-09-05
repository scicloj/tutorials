(ns drafts.ols
  (:require
    [tech.ml.dataset :as ds]
    [tablecloth.api :as tcapi]
    [tech.v2.datatype.functional :as dfn])
  (:import (java.time LocalDate)
           (smile.regression OLS)
           (smile.data.formula Formula)))

(def stock-data
  "We are explicitly parsing the dates
  We are also explicitly parsing vgu0 as it comes as int by default, which will mess up the returns"
  ;TODO find a way to parse into float by default, except for some columns like date
  (ds/->dataset "clj-ols-data.csv" {:parser-fn {"date" [:local-date "yyyyMMdd"]
                                                "vgu0" :float32}}))
(defn stock-data-date-filter [local-date]
  "Parsing dates allows for easy filtering"
  (ds/filter-column #(.isAfter % (LocalDate/parse "2020-03-10")) "date" stock-data))

(defn ds-returns [dataset]
  "Gets returns for every numerical column, leaves others unchanged.
  This is neat but can lead to problems if one is using int for dates e.g. 20100101
  In Python this would be df.pct_change()
  WARNING: without the last line, we get an array of same length with 0 in the first row - would nil be better?
  "
  (let [raw (tcapi/update-columns
              dataset
              :type/numerical
              #(dfn/fixed-rolling-window 2 (fn [[a b]] (dec (/ b a))) %))]
    (ds/tail (dec (ds/row-count raw)) raw)))                  ;we remove the first row that otherwise comes as 0.0

(defn return-ols [y x dataset]
  "Smile OLS. Will include intercept by default. This is the object you want to query.
  Example queries:
  (.coefficients ols)
  (.RSquared ols)
  (.predict res (double-array [0. 0.05])) ;seems you need the intercept here
  "
  (->> (ds/select-columns dataset [y x])
       (ds-returns)
       (tech.libs.smile.data/dataset->dataframe)
       (OLS/fit (Formula/lhs y))))

(defn ols-beta [y x dataset]
  "Get straight to the beta"
  (nth (.coefficients (return-ols y x dataset)) 1))

