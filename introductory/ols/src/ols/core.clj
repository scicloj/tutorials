(ns ols.core
  (:require
    [tech.ml.dataset :as ds]
    [tablecloth.api :as tcapi]
    [tech.v2.datatype.functional :as dfn])
  (:import
    (java.time LocalDate)
    (smile.regression OLS LinearModel)
    (smile.data.formula Formula))
  )

;Note we are using smile Java interop.
;There's a "native" Clojure implementation but more doc for the Java version, hence using that one.
(set! *warn-on-reflection* true)                            ;we'll be calling some Java methods

;We will be using closing prices for futures contract,
;for S&P500 e-mini September 2020, Eurostoxx 50, and a random combination of both.
;Our file also has a column of dates in yyyyMMdd format.
(def stock-data
  "We are explicitly parsing the dates.
  We are also explicitly parsing vgu0 as it comes as int by default, which will mess up the returns."
  ;TODO find a way to parse into float by default, except for some columns like date
  (ds/->dataset "resources/clj-ols-data.csv" {:parser-fn {"date" [:local-date "yyyyMMdd"]
                                                          "vgu0" :float32}}))

(defn stock-data-date-filter
  "Parsing dates allows for easy filtering"
  [yyyy-MM-dd]
  (ds/filter-column #(.isAfter ^LocalDate % (LocalDate/parse yyyy-MM-dd)) "date" stock-data))

(defn ds-returns
  "Gets returns for every numerical column, leaves others unchanged.
  This is neat but can lead to problems if one is using int for dates e.g. 20100101
  In Python this would be df.pct_change()
  WARNING: without the last line, we get an array of same length with 0 in the first row - would nil be better?"
  [dataset]
  (let [raw (tcapi/update-columns
              dataset
              :type/numerical
              #(dfn/fixed-rolling-window 2 (fn [[a b]] (dec (/ b a))) %))]
    (ds/tail (dec (ds/row-count raw)) raw)))                  ;we remove the first row that otherwise comes as 0.0

(defn return-ols
  "Smile OLS. Will include intercept by default. This is the object you want to query.
  Example queries if result is called ols:
  (.coefficients ols)
  (.RSquared ols)
  (.predict ols (double-array [1. 0.05])) ;seems you need the intercept here, set at 1."
  [y x dataset]
  (->> (ds/select-columns dataset [y x])
       (ds-returns)
       (tech.libs.smile.data/dataset->dataframe)
       (OLS/fit (Formula/lhs ^String y))))

(defn ols-beta
  "Get straight to the beta"
  [y x dataset]
  (nth (.coefficients ^LinearModel (return-ols y x dataset)) 1))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;We will now go through examples  ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(println (ds/head stock-data))
;|       date |     esu0 |   vgu0 |    random |
;|------------|----------|--------|-----------|
;| 2020-01-02 | 3238.375 | 3707.0 | 3459.6875 |
;| 2020-01-03 | 3214.625 | 3679.0 | 3438.8125 |
;| 2020-01-06 | 3222.875 | 3664.0 | 3453.4375 |
;| 2020-01-07 | 3214.625 | 3675.0 | 3422.8125 |
;| 2020-01-08 | 3239.875 | 3686.0 | 3477.9375 |

(println (ds/head (stock-data-date-filter "2020-06-10")))
;|       date |     esu0 |   vgu0 |    random |
;|------------|----------|--------|-----------|
;| 2020-06-11 | 2990.500 | 3125.0 | 3038.7500 |
;| 2020-06-12 | 3014.875 | 3112.0 | 3050.4375 |
;| 2020-06-15 | 3053.000 | 3108.0 | 3080.5000 |
;| 2020-06-16 | 3109.375 | 3212.0 | 3170.6875 |
;| 2020-06-17 | 3098.250 | 3236.0 | 3174.1250 |

(println (ds/head (ds-returns stock-data)))
;|       date |        esu0 |        vgu0 |      random |
;|------------|-------------|-------------|-------------|
;| 2020-01-03 | -0.00733393 | -0.00755328 | -0.00603378 |
;| 2020-01-06 |  0.00256640 | -0.00407719 |  0.00425292 |
;| 2020-01-07 | -0.00255983 |  0.00300218 | -0.00886798 |
;| 2020-01-08 |  0.00785473 |  0.00299320 |  0.01610518 |
;| 2020-01-09 |  0.00486130 |  0.00569723 | -0.00363002 |

(println (return-ols "esu0" "vgu0" stock-data))
;#object[smile.regression.LinearModel 0x5a2beda4 Linear Model:
;
;        Residuals:
;        Min          1Q      Median          3Q         Max
;        -0.0714     -0.0067      0.0001      0.0068      0.0970
;
;        Coefficients:
;        Estimate Std. Error    t value   Pr(>|t|)
;        Intercept           0.0009     0.0014     0.6875     0.4928
;        vgu0                0.6694     0.0615    10.8755     0.0000 ***
;        ---------------------------------------------------------------------
;        Significance codes:  0 '***' 0.001 '**' 0.01 '*' 0.05 '.' 0.1 ' ' 1
;
;        Residual standard error: 0.0176 on 163 degrees of freedom
;        Multiple R-squared: 0.4205,    Adjusted R-squared: 0.4169
;        F-statistic: 118.2771 on 2 and 163 DF,  p-value: 4.662e-21
;        ]

(println (.RSquared ^LinearModel (return-ols "esu0" "vgu0" stock-data)))
;0.4205001932692286

(println (.predict ^LinearModel (return-ols "esu0" "vgu0" stock-data) (double-array [1.0 0.05])))
;0.0344087602118561

(println (ols-beta "esu0" "vgu0" stock-data))
;0.6693674735898943

(println (ols-beta "esu0" "random" stock-data))
;0.8758305642013671


