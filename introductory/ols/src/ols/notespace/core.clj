(ns ols.notespace.core
  (:require
    [tech.v3.dataset :as ds]
    [tech.v3.libs.smile.data :as ds-smile]
    [tech.v3.datatype.rolling :as rolling]
    [ols.notespace.svr :as svr]
    [notespace.api :as notespace]
    [notespace.kinds :as kind])
  (:import
    (java.time LocalDate)
    (smile.regression OLS LinearModel)
    (smile.data.formula Formula)))

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

;Note we are using smile Java interop.
;There's a "native" Clojure implementation but more doc for the Java version, hence using that one.

(comment
  ;; we'll be calling some Java methods
  ;; commenting this out, since it fails as a notespace note
  ;; (Can't set!: *warn-on-reflection* from non-binding thread)
  (set! *warn-on-reflection* true))

;We will be using closing prices for futures contract,
;for S&P500 e-mini September 2020, Eurostoxx 50, and a random combination of both.
;Our file also has a column of dates in yyyyMMdd format.
(def stock-data
  "We are explicitly parsing the dates.
  We are also explicitly parsing vgu0 as it comes as int by default, which will mess up the 333333333333333333333333333333333rns."
  ;TODO find a way to parse into float by default, except for some columns like date
  (ds/->dataset "resources/clj-ols-data.csv" {:parser-fn {"date" [:local-date "yyyyMMdd"]
                                                          "vgu0" :float32}}))

(defn stock-data-date-filter
  "Parsing dates allows for easy filtering"
  [yyyy-MM-dd]
  (ds/filter-column stock-data "date" #(.isAfter ^LocalDate % (LocalDate/parse yyyy-MM-dd))))

(defn ds-returns
  "Gets returns for every column except date.
  In Python this would be df.pct_change()
  WARNING: without the last line, we get an array of same length with 0 in the first row - would nil be better?"
  [dataset]
  (let [raw (reduce
              (fn [dts col] (assoc dts col (rolling/fixed-rolling-window (dts col) 2 (fn [[a b]] (dec (/ b a))))))
              dataset
              (remove #{"date"} (ds/column-names dataset)))]
    (ds/tail raw (dec (ds/row-count raw)))))                  ;we remove the first row that otherwise comes as 0.0

(defn return-ols
  "Smile OLS. Will include intercept by default. This is the object you want to query.
  Example queries if result is called ols:
  (.coefficients ols)
  (.RSquared ols)
  (.predict ols (double-array [1. 0.05])) ;you need the intercept here, set at 1."
  [y x dataset]
  (->> (ds/select-columns dataset [y x])
       (ds-returns)
       (ds-smile/dataset->dataframe)
       (OLS/fit (Formula/lhs ^String y))))

(defn ols-beta
  "Get straight to the beta"
  [y x dataset]
  (nth (.coefficients ^LinearModel (return-ols y x dataset)) 1))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;We will now go through examples  ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

^kind/dataset
(ds/head stock-data)

^kind/dataset
(ds/head (stock-data-date-filter "2020-06-10"))

^kind/dataset
(ds/head (ds-returns stock-data))

^kind/md ; rendering this as code inside markdown
["```"
 (return-ols "esu0" "vgu0" stock-data)
 "```"]

(.RSquared ^LinearModel (return-ols "esu0" "vgu0" stock-data))

(.predict ^LinearModel (return-ols "esu0" "vgu0" stock-data) (double-array [1.0 0.05]))

(ols-beta "esu0" "vgu0" stock-data)

(ols-beta "esu0" "random" stock-data)


