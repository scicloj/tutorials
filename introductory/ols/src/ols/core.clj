(ns ols.core
  (:require
    [notespace.api :as notespace]
    [notespace.kinds :as kind]
    [tech.v3.dataset :as ds]
    [tech.v3.libs.smile.data :as ds-smile]
    [tech.v3.datatype.rolling :as rolling]
    ;[ols.svr :as svr]
    )
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

["# Basic OLS regression with tech.ml.dataset and smile"]

["The purpose of this notebook is to show basic data manipulation with tech.ml.dataset, and perform a linear regression between two vectors with the smile Java package. We will be comparing with Python / pandas where appropriate."]
["We will use equity index futures data: closing prices for S&P500 e-mini September 2020 contract, same for Eurostoxx 50, and a random combination of both. Our file also has a column of dates in yyyyMMdd format."]

["Note we are using smile Java interop. There's a \"native\" Clojure implementation but more doc for the Java version, hence using that one.\n"]

(comment
  ;; we'll be calling some Java methods
  ;; commenting this out, since it fails as a notespace note
  ;; (Can't set!: *warn-on-reflection* from non-binding thread)
  (set! *warn-on-reflection* true))

["## Reading the file"]
["We are explicitly parsing the dates.  We are also explicitly parsing vgu0 as it comes as `int` by default, which can mess up the returns later."]

(def stock-data
  ;TODO find a way to parse into float by default, except for some columns like date
  (ds/->dataset "resources/clj-ols-data.csv" {:parser-fn {"date" [:local-date "yyyyMMdd"]
                                                          "vgu0" :float32}}))
["## Helpful functions"]
["First we define a date filter. The below will filter the data for dates *after* the input date. Think of it as pandas `df.loc[df.index>date]`."]

(defn stock-data-date-filter
  [yyyy-MM-dd]
  (ds/filter-column stock-data "date" #(.isAfter ^LocalDate % (LocalDate/parse yyyy-MM-dd))))

["We need the stock market returns to perform our regression. We will calculate them column by column through a rolling window of 2 rows, excluding the date column. Note that by default tech.ml returns arrays of the same length as the input, which is generally a good thing. However in this case I will remove the first result, which tech.ml defaults to 0, while it should be undefined. This function is the rough equivalent of `df.pct_change()` in pandas."]

(defn ds-returns
  [dataset]
  (let [raw (reduce
              (fn [dts col] (assoc dts col (rolling/fixed-rolling-window (dts col) 2 (fn [[a b]] (dec (/ b a))))))
              dataset
              (remove #{"date"} (ds/column-names dataset)))]
    (ds/tail raw (dec (ds/row-count raw)))))                  ;we remove the first row that otherwise comes as 0.0

["## smile OLS"]

^kind/md-nocode
["smile will return an OLS object that can be queried. The regression includes the intercept by default. Example queries:"
"* `(.coefficients ols)`
* `(.RSquared ols)`
* `(.predict ols (double-array [1. 0.05]))` *you need the intercept here, set at 1*"]
(defn return-ols
  [dataset y x]
  (OLS/fit
    (Formula/lhs ^String y)
    (-> dataset
        (ds/select-columns [y x])
        (ds-returns)
        (ds-smile/dataset->dataframe))))

["We also define a function to access the beta directly."]
(defn ols-beta
  [dataset y x]
  (nth (.coefficients ^LinearModel (return-ols dataset y x)) 1))

["## Examples"]

["Get the head of a dataset"]
^kind/dataset
(ds/head stock-data)

["Get the tail of a dataset"]
^kind/dataset
(ds/tail stock-data)

["Get the head of the dataset past 10Jun20."]
^kind/dataset
(ds/head (stock-data-date-filter "2020-06-10"))

["Get the head of the dataset returns"]
(ds/head (ds-returns stock-data))

["Get the full smile output"]
^kind/md ; rendering this as code inside markdown
["```"
 (return-ols stock-data "esu0" "vgu0")
 "```"]

["Get the R2 of a regression"]
(.RSquared ^LinearModel (return-ols stock-data "esu0" "vgu0"))

["Get a prediction - be careful that you need to include the intercept as 1.0 and that smile expects a Java double array"]
(.predict ^LinearModel (return-ols stock-data "esu0" "vgu0") (double-array [1.0 0.05]))

["Get a couple of betas"]
(ols-beta stock-data "esu0" "vgu0")
(ols-beta stock-data "esu0" "random")


["## End"]
